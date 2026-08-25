#pragma once

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>

#include "../engine/EngineModel.h"
#include "../sequencer/TempoMap.h"
#include "../sequencer/TimelineSnapshot.h"

// The background compile thread (blueprint 2.3/2.4, graph/ module): owns the
// EngineModel, applies ModelDelta bundles, and compiles immutable artifacts
// for the audio thread - TimelineSnapshots and consolidated TempoMapBases
// now, the PlaybackGraph + MigrationPlan at M2. All handovers ride OfferSlot
// epoch mechanics; this thread is the ONLY one that frees retired artifacts,
// and only after the RT epoch ack (the audio thread never frees memory).
//
// Inputs:
//   - submitDeltas(): ModelDelta payloads (envelope + StateCodec frames),
//     copied into a mutex-guarded inbox. The single engine-io producer
//     preserves edit-dispatch order, which is the application order.
//   - a periodic wake (wait_for) that also polls the seqlock-published tempo
//     tail for the forced-consolidation condition - the builder never reads
//     RT-owned state directly.
//
// Lifecycle: constructed and started by AudioEngine (any non-RT thread);
// stop() joins. The thread runs whether or not audio streams are open, so
// the model syncs while the engine is idle - but artifact compiles that
// need a sample rate (tempo bases) wait until prepare() has run.

namespace daw {

class AudioEngine;

class GraphBuilder {
public:
    explicit GraphBuilder(AudioEngine& engine);
    ~GraphBuilder();

    void start();
    void stop();

    // [any non-RT thread; the engine-io thread in practice] Copies one
    // ModelDelta payload (ModelDeltaEnvelope + StateCodec frames) into the
    // inbox and wakes the thread.
    void submitDeltas(const uint8_t* payload, size_t len);

    // Wake without work (e.g. after prepare(), so deferred compiles retry).
    void nudge();

    // ---- diagnostics [any] --------------------------------------------------
    uint32_t deltasApplied() const noexcept { return applied_.load(std::memory_order_relaxed); }
    uint32_t deltasRejected() const noexcept { return rejected_.load(std::memory_order_relaxed); }
    uint32_t timelineBuilds() const noexcept { return timelineBuilds_.load(std::memory_order_relaxed); }
    uint32_t tempoBuilds() const noexcept { return tempoBuilds_.load(std::memory_order_relaxed); }
    uint32_t danglingRefs() const noexcept { return danglingRefs_.load(std::memory_order_relaxed); }

private:
    void threadMain();
    void applyBundle(const std::vector<uint8_t>& bundle);
    void buildTimeline();
    void rebuildTempoBaseFromModel();
    void consolidateTempoTail(const TempoMap::Snapshot& snap);
    void offerTempoBase(std::unique_ptr<TempoMapBase> built);
    void gcTimeline();
    void gcTempo();

    AudioEngine& engine_;
    EngineModel model_;

    std::thread thread_;
    std::mutex mx_;
    std::condition_variable cv_;
    std::vector<std::vector<uint8_t>> inbox_;   // guarded by mx_
    bool running_ = false;                      // guarded by mx_
    bool wake_ = false;                         // guarded by mx_

    uint64_t nextEpoch_ = 1;
    bool pendingTempoRebuild_ = false;          // waits for a sample rate

    struct TimelineArtifact {
        std::unique_ptr<TimelineSnapshot> snapshot;
    };
    struct TempoArtifact {
        std::unique_ptr<TempoMapBase> base;
        uint64_t predecessorEpoch = 0;   // whose ack proves this one was claimed
        bool bgPublished = false;
    };
    std::vector<TimelineArtifact> timelineArtifacts_;   // oldest -> newest
    std::vector<TempoArtifact>    tempoArtifacts_;      // oldest -> newest

    std::atomic<uint32_t> applied_{0};
    std::atomic<uint32_t> rejected_{0};
    std::atomic<uint32_t> timelineBuilds_{0};
    std::atomic<uint32_t> tempoBuilds_{0};
    std::atomic<uint32_t> danglingRefs_{0};   // skipped dangling refs (seam-4 skew)
};

} // namespace daw
