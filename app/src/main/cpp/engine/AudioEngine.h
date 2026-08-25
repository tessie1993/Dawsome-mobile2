#pragma once

#include <atomic>
#include <memory>

#include "../core/EngineConfig.h"
#include "../core/EventRing.h"
#include "../core/MeterFrame.h"
#include "../core/OfferSlot.h"
#include "../core/ParamMoveTable.h"
#include "../core/Seqlock.h"
#include "../core/SpscRing.h"
#include "../core/TimeAnchor.h"
#include "../graph/PlaybackGraph.h"
#include "../sequencer/MidiScheduler.h"
#include "../sequencer/TimelineSnapshot.h"
#include "../sequencer/TransportEngine.h"
#include "OboeDriver.h"

// Engine facade and the realtime callback spine (blueprint engine/ module).
// Owns the driver, the per-producer message channels (CONTRACTS.md seam 2),
// the TransportEngine (TempoMap + span-splitting advance, M1), and the
// readback publications; renders silence until the PlaybackGraph swaps in
// at M2 (the ParamMoveTables already retain values for post-swap re-apply);
// instruments consume note events from M4.
//
// Threading: start/stop and the producer accessors are non-RT (JNI and MIDI
// threads own their respective channels); render() is the audio thread.

namespace daw {

class GraphBuilder;

// Per-block transport snapshot for UI playheads (blueprint 2.5 readback).
struct TransportClockData {
    int64_t samplePos = 0;
    double  beat = 0.0;         // via the installed TempoMap
    double  bpm = 120.0;
    uint32_t flags = 0;         // kClock* bits
};

inline constexpr uint32_t kClockPlaying   = 1u << 0;
inline constexpr uint32_t kClockRecording = 1u << 1;
inline constexpr uint32_t kClockLooping   = 1u << 2;
inline constexpr uint32_t kClockMetronome = 1u << 3;

class AudioEngine final : public RenderSink {
public:
    // The builder thread spawns with the engine and lives until destruction,
    // so the model syncs even while audio streams are closed.
    AudioEngine();
    ~AudioEngine() override;

    // ---- lifecycle [non-RT] -------------------------------------------------
    bool start(const OboeDriver::Config& cfg) noexcept;
    void stop() noexcept;

    // ---- producer channels (one owner thread each) --------------------------
    EventRing<>&    jniEvents() noexcept { return jniEvents_; }
    ParamMoveTable& jniParams() noexcept { return jniParams_; }
    EventRing<>&    midiEvents() noexcept { return midiEvents_; }
    ParamMoveTable& midiParams() noexcept { return midiParams_; }

    // ---- readback [any] -----------------------------------------------------
    bool popMeter(MeterFrame& out) noexcept { return meterBus_.tryPop(out); }
    TransportClockData clock() const noexcept {
        TransportClockData d; clock_.read(d); return d;
    }
    TimeAnchor anchor() const noexcept {
        TimeAnchor a; anchor_.read(a); return a;
    }
    const OboeDriver& driver() const noexcept { return driver_; }
    OboeDriver&       driver() noexcept { return driver_; }
    // Non-RT reads of live transport facts go through clock(); this accessor
    // exists for the bridge's status assembly and the builder's map access.
    const TransportEngine& transport() const noexcept { return transport_; }
    TransportEngine&       transport() noexcept { return transport_; }
    GraphBuilder&          builder() noexcept { return *builder_; }
    // Builder -> RT handover slots for compiled artifacts (seams 3/4).
    OfferSlot<TimelineSnapshot>& timelineOffer() noexcept { return timelineOffer_; }
    OfferSlot<PlaybackGraph>&    graphOffer() noexcept { return graphOffer_; }
    // [RT] the currently installed graph (null before the first claim).
    const PlaybackGraph* graph() const noexcept { return graph_; }
    // [RT] the currently installed timeline (null before the first claim).
    const TimelineSnapshot* timeline() const noexcept { return timeline_; }
    // [RT] block-local scheduled MIDI (instruments consume from M4).
    const MidiScheduler& midi() const noexcept { return midiScheduler_; }
    uint64_t droppedNotes() const noexcept { return droppedNotes_.load(std::memory_order_relaxed); }
    uint64_t panics() const noexcept { return panics_.load(std::memory_order_relaxed); }
    // Param applies that resolved to nothing in the installed graph (seam-4
    // skew; transient by design, converges at the next swap).
    uint32_t paramSkews() const noexcept { return paramSkews_; }

    // ---- RenderSink [RT] ----------------------------------------------------
    void render(float* const* outputs, int numFrames,
                InputJitterRing& input, const StreamTime& time) override;

private:
    void drainEvents(EventRing<>& ring) noexcept;
    void applyTransport(const EngineMessage& m) noexcept;

    static constexpr int kEventDrainCap = 256;   // per ring per slice

    OboeDriver driver_;

    EventRing<>    jniEvents_;
    ParamMoveTable jniParams_;
    EventRing<>    midiEvents_;
    ParamMoveTable midiParams_;

    TimeAnchorPublisher        anchor_;
    Seqlock<TransportClockData> clock_;
    SpscRing<MeterFrame, 512>  meterBus_;

    // The real transport (M1): TempoMap + state machine + span splitting.
    TransportEngine transport_;

    // Compiled-artifact handover (builder offers, RT claims + acks).
    OfferSlot<TimelineSnapshot> timelineOffer_;
    const TimelineSnapshot* timeline_ = nullptr;   // RT-owned current pointer
    OfferSlot<PlaybackGraph> graphOffer_;
    PlaybackGraph* graph_ = nullptr;               // RT-owned current pointer

    MidiScheduler midiScheduler_;

    std::unique_ptr<GraphBuilder> builder_;

    // Input drain scratch (monitoring paths arrive at M2/M6).
    float inScratchL_[kMaxBlock]{};
    float inScratchR_[kMaxBlock]{};

    std::atomic<uint64_t> droppedNotes_{0};
    std::atomic<uint64_t> panics_{0};
    uint32_t paramSkews_ = 0;   // RT-written; read is best-effort diagnostics
};

} // namespace daw
