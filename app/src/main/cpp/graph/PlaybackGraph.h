#pragma once

#include <memory>
#include <utility>
#include <vector>

#include "../core/EngineConfig.h"
#include "../core/FixedVector.h"
#include "../core/MeterFrame.h"
#include "../core/NodeUid.h"
#include "../device/DelayComp.h"
#include "../device/DeviceChain.h"
#include "../device/DeviceNode.h"
#include "MeterProbe.h"
#include "MigrationPlan.h"
#include "SendNode.h"
#include "TrackStrip.h"
#include "VoiceBudgetLedger.h"

// The compiled realtime mixer (blueprint 2.3, graph/PlaybackGraph): an
// immutable-topology artifact built whole by the GraphBuilder, offered via
// OfferSlot epochs, claimed by the audio thread at a block boundary
// (executing the MigrationPlan's adopt moves first), freed by the builder
// after the epoch ack.
//
// M2 topology (device chains join at M3, instruments write into track
// buffers from M4):
//
//   track buffer (zeroed = silence source) -> TrackStrip ->
//       post-fader SendNode taps (+= into return buffers) ->
//       [DelayCompNode] -> += master mix
//   return buffer (send accumulation) -> strip -> [comp] -> += master mix
//   master mix -> MasterStrip -> Main bus (OutputBusMatrix; Cue folds into
//       Main on stereo hardware until routed sources exist)
//
// All buffers live in one builder-allocated arena; the audio thread only
// reads/writes float slices, never allocates. Meters accumulate per unit
// and ready frames collect in pendingMeters for the engine to drain into
// the MeterBus after processBlock.

namespace daw {

// The master strip's well-known identity - same derivation as Kotlin
// WireProtocol.masterNodeUid.
inline constexpr NodeUid kMasterNodeUid = makeNodeUid("master", "master");

// Graph-internal identity for a track's send node on bus 0/1. Only the
// builder derives these (migration matching across compiles); the Kotlin
// side addresses sends on the TRACK uid and the resolver bridges.
inline constexpr NodeUid sendNodeUid(NodeUid trackUid, int busIndex) noexcept {
    return trackUid ^ (busIndex == 0 ? 0xA11CE5E17D000001ull : 0xA11CE5E17D000002ull);
}

// Graph-internal identity for a lane's device chain.
inline constexpr NodeUid chainNodeUid(NodeUid laneUid) noexcept {
    return laneUid ^ 0xC4A170DE00000001ull;
}

// Key -> {node, denseIndex} resolution (CONTRACTS.md seam 6): exists only
// inside a compiled graph, consulted [RT] at apply time. Built by the
// compiler; open addressing over a power-of-two table.
class ParamResolver {
public:
    // ---- builder ------------------------------------------------------------
    void reserve(size_t paramCount) {
        size_t cap = 16;
        while (cap < paramCount * 2) cap <<= 1;
        table_.assign(cap, Entry{});
        mask_ = cap - 1;
    }

    bool add(NodeUid uid, ParamKeyHash key, DeviceNode* node, int dense) {
        if (table_.empty()) return false;
        size_t i = indexFor(uid, key);
        for (size_t probe = 0; probe < table_.size(); ++probe, i = (i + 1) & mask_) {
            Entry& e = table_[i];
            if (e.node == nullptr) {
                e = {uid, key, node, dense};
                return true;
            }
            if (e.uid == uid && e.key == key) return false;   // duplicate registration
        }
        return false;                                          // full (never at 2x reserve)
    }

    // ---- audio thread -------------------------------------------------------
    // Returns false when the key resolves to nothing in THIS graph (seam-4
    // skew: caller counts and skips silently; convergence at the next swap).
    bool apply(NodeUid uid, ParamKeyHash key, float plain) const noexcept {
        if (table_.empty()) return false;
        size_t i = indexFor(uid, key);
        for (size_t probe = 0; probe < table_.size(); ++probe, i = (i + 1) & mask_) {
            const Entry& e = table_[i];
            if (e.node == nullptr) return false;
            if (e.uid == uid && e.key == key) {
                e.node->setParamImmediate(e.dense, plain);
                return true;
            }
        }
        return false;
    }

private:
    struct Entry {
        NodeUid uid = 0;
        ParamKeyHash key = 0;
        DeviceNode* node = nullptr;
        int dense = 0;
    };

    size_t indexFor(NodeUid uid, ParamKeyHash key) const noexcept {
        uint64_t h = uid ^ (uint64_t(key) * 0x9e3779b97f4a7c15ull);
        h ^= h >> 29;
        return size_t(h) & mask_;
    }

    std::vector<Entry> table_;
    size_t mask_ = 0;
};

// Per-block facts handed to every node's ProcessContext.
struct RenderFacts {
    double  sampleRate = 0.0;
    int64_t blockStartSample = 0;
    double  blockStartBeat = 0.0;
    double  bpm = 120.0;
    bool    playing = false;
    bool    recording = false;
};

// One mixer lane (track, return, or the master).
struct TrackUnit {
    NodeUid uid = 0;
    uint8_t wireType = 0;             // TrackDeltaPayload numbering
    float* bufL = nullptr;            // arena slices
    float* bufR = nullptr;
    DeviceChain* chain = nullptr;     // pre-strip device chain (null = none)
    TrackStrip* strip = nullptr;      // owned by PlaybackGraph::nodes
    SendNode* sendA = nullptr;        // regular tracks only; -> returns[0]
    SendNode* sendB = nullptr;        // -> returns[1]
    DelayCompNode* comp = nullptr;    // pre-join compensation (null = zero)
    MeterProbe meter;
};

struct PlaybackGraph {
    uint64_t epoch = 0;               // OfferSlot contract
    uint32_t builtFromEditSeq = 0;    // ordering rule (blueprint 2.2)
    double   sampleRate = 0.0;

    // Storage (builder-allocated, frozen from offer() on).
    std::vector<float> arena;
    std::vector<std::unique_ptr<DeviceNode>> nodes;
    std::vector<std::unique_ptr<DelayCompNode>> comps;

    std::vector<TrackUnit> tracks;    // arranger order
    std::vector<TrackUnit> returns;   // <= kMaxReturns; sends null
    TrackUnit master;                 // uid = kMasterNodeUid

    float* mixL = nullptr;            // master accumulation (== master.bufL/R)
    float* mixR = nullptr;
    // OutputBusMatrix: Main + Cue (blueprint 3.2). Cue folds into Main on
    // stereo hardware; routable sources (metronome, preview, solo-to-cue)
    // arrive with their milestones.
    float* mainL = nullptr;
    float* mainR = nullptr;
    bool cueFolded = true;

    ParamResolver resolver;
    MigrationPlan migration;          // consumed once at swap
    // Global polyphony accounting; instruments register at compile (M4+),
    // ask requestVoice() at note-on, recounted every block.
    VoiceBudgetLedger voices;

    // Builder-only: adoption scan data for the NEXT compile. configHash
    // participates in the seam-3 adopt condition (uid AND hash AND rate).
    struct NodeIndexEntry {
        NodeUid uid = 0;
        DeviceNode* node = nullptr;
        uint64_t configHash = 0;
    };
    std::vector<NodeIndexEntry> nodeIndex;

    // Ready meter frames collected during processBlock; the engine drains
    // them into the MeterBus ring (single consumer stays engine-side).
    FixedVector<MeterFrame, kMaxTracks + kMaxReturns + 1> pendingMeters;

    // [RT] one whole render slice (numFrames <= kMaxBlock).
    void processBlock(int numFrames, const RenderFacts& facts) noexcept;
};

} // namespace daw
