#pragma once

#include <cstdint>
#include <string>
#include <unordered_map>
#include <vector>

#include "../core/NodeUid.h"
#include "../jni/DeltaSchemas.h"
#include "../jni/StateCodec.h"

// Compact C++ mirror of the edit model (blueprint 2.3): owned and mutated by
// the GraphBuilder THREAD ONLY, fed exclusively by StateCodec entity deltas
// (idempotent upserts / removes, application order by editSeq belongs to the
// caller). Everything the builder compiles - TimelineSnapshots now, the
// PlaybackGraph at M2 - reads from here; the audio thread never sees this
// object.
//
// The jni/ wire headers included here are the PURE halves of seam 5 (no
// <jni.h> anywhere in them); pulling them in keeps one source of entity-kind
// numbering and payload layout. The jni RUNTIME still depends on engine/,
// never the reverse.
//
// Removal is non-cascading: removing a track does not remove its clips -
// EngineSync sends explicit removes per entity, and compiles skip dangling
// references (the same skew tolerance rule the RT side lives by, seam 4).
//
// Heap use is free here (builder thread): std containers, no RT constraints.

namespace daw {

struct ModelNote {
    uint32_t id = 0;
    uint16_t pitch = 60;
    uint16_t velocity = 100;
    double   startBeat = 0.0;     // content-local
    double   lengthBeats = 0.0;
};

struct ModelTrack {
    uint8_t  type = 0;            // TrackDeltaPayload numbering
    uint8_t  flags = 0;           // kTrackFlag*
    uint16_t order = 0;
    float    volumeDb = 0.0f;
    float    pan = 0.0f;
    float    sendA = 0.0f;
    float    sendB = 0.0f;
};

struct ModelClip {
    NodeUid  trackUid = 0;
    NodeUid  contentUid = 0;
    double   startBeat = 0.0;     // arrangement placement
    double   lengthBeats = 0.0;
    int32_t  slotIndex = -1;      // -1 = arrangement, >= 0 = session slot
    bool     looping = true;
};

struct ModelClipContent {
    double lengthBeats = 4.0;     // content loop length
    std::vector<ModelNote> notes; // unsorted here; snapshots sort
};

struct ModelDevice {
    NodeUid  trackUid = 0;
    uint8_t  type = 0;                // DeviceTypeId wire numbering
    bool     enabled = true;
    uint16_t order = 0;
    // Current plain values by semantic key hash - baked into rebuilt graphs.
    std::vector<std::pair<uint32_t, float>> params;
};

struct ModelScene {
    int32_t index = 0;
};

// One sample assignment on a device (CONTRACTS v1.2 SampleRef): slot 0 for
// single-sample devices, the pad index for DrumRack. fileId is the durable
// identity (Kotlin fnv1a64); path lets the builder load on a cache miss.
struct ModelSampleRef {
    uint32_t    slot = 0;
    uint64_t    fileId = 0;      // media FileId
    std::string path;
};

// The transient audition request (CONTRACTS v1.2 Preview). fileId == 0 means
// "stop". serial bumps on EVERY frame so a repeat of the same file still
// rebuilds the offer - a fresh preview retriggers from the top.
struct ModelPreview {
    uint64_t    fileId = 0;
    std::string path;
    uint32_t    serial = 0;
};

struct ModelTempo {
    struct Ev  { double beat; double bpm; };
    struct Sig { double beat; uint16_t num; uint16_t den; };
    std::vector<Ev>  events;      // sorted by beat at apply time
    std::vector<Sig> sigs;
};

// Payload sanity bounds for the v1.2 sample kinds: a UTF-8 path longer than
// any real filesystem path, or more slots than any device exposes, is a
// malformed frame (refused + counted), not data.
inline constexpr uint32_t kMaxSamplePathBytes     = 4096;
inline constexpr size_t   kMaxSampleSlotsPerDevice = 64;

// Dirty classes the builder rebuilds from (consumed per build cycle).
inline constexpr uint32_t kDirtyTimeline = 1u << 0;
inline constexpr uint32_t kDirtyTempo    = 1u << 1;
inline constexpr uint32_t kDirtyGraph    = 1u << 2;
inline constexpr uint32_t kDirtyPreview  = 1u << 3;  // audition offer rebuild

class EngineModel {
public:
    // Apply one decoded delta. Returns false on a malformed payload (the
    // caller counts and surfaces; the model stays untouched by bad frames).
    bool applyDelta(const EntityDelta& d);

    // Returns the accumulated dirty mask and clears it.
    uint32_t consumeDirty() noexcept {
        const uint32_t d = dirty_;
        dirty_ = 0;
        return d;
    }

    // ---- builder-side reads -------------------------------------------------
    const std::unordered_map<NodeUid, ModelTrack>&       tracks()   const noexcept { return tracks_; }
    const std::unordered_map<NodeUid, ModelClip>&        clips()    const noexcept { return clips_; }
    const std::unordered_map<NodeUid, ModelClipContent>& contents() const noexcept { return contents_; }
    const std::unordered_map<NodeUid, ModelDevice>&      devices()  const noexcept { return devices_; }
    const std::unordered_map<NodeUid, ModelScene>&       scenes()   const noexcept { return scenes_; }
    const ModelTempo& tempo() const noexcept { return tempo_; }
    bool hasTempoDelta() const noexcept { return hasTempoDelta_; }
    // Sample refs for one device uid, or null when it has none.
    const std::vector<ModelSampleRef>* sampleRefsFor(NodeUid deviceUid) const noexcept {
        const auto it = sampleRefs_.find(deviceUid);
        return it != sampleRefs_.end() ? &it->second : nullptr;
    }
    const ModelPreview& preview() const noexcept { return preview_; }

    uint32_t lastEditSeq() const noexcept { return lastEditSeq_; }
    void     noteEditSeq(uint32_t seq) noexcept { if (seq > lastEditSeq_) lastEditSeq_ = seq; }

    uint64_t deferredKindCount() const noexcept { return deferredKinds_; }

private:
    bool applyTrack(NodeUid id, const uint8_t* p, uint32_t len);
    bool applyClip(NodeUid id, const uint8_t* p, uint32_t len);
    bool applyContent(NodeUid id, const uint8_t* p, uint32_t len);
    bool applyDevice(NodeUid id, const uint8_t* p, uint32_t len);
    bool applyScene(NodeUid id, const uint8_t* p, uint32_t len);
    bool applyTempoMap(const uint8_t* p, uint32_t len);
    bool applySampleRef(NodeUid deviceUid, const uint8_t* p, uint32_t len);
    bool applyPreview(uint64_t fileId, const uint8_t* p, uint32_t len);

    std::unordered_map<NodeUid, ModelTrack>       tracks_;
    std::unordered_map<NodeUid, ModelClip>        clips_;
    std::unordered_map<NodeUid, ModelClipContent> contents_;
    std::unordered_map<NodeUid, ModelDevice>      devices_;
    std::unordered_map<NodeUid, ModelScene>       scenes_;
    std::unordered_map<NodeUid, std::vector<ModelSampleRef>> sampleRefs_;
    ModelPreview preview_;
    ModelTempo tempo_;
    bool       hasTempoDelta_ = false;

    uint32_t dirty_ = 0;
    uint32_t lastEditSeq_ = 0;
    uint64_t deferredKinds_ = 0;
};

} // namespace daw
