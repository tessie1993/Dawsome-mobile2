#pragma once

#include <atomic>

#include "../core/EngineConfig.h"
#include "../core/EventRing.h"
#include "../core/MeterFrame.h"
#include "../core/ParamMoveTable.h"
#include "../core/Seqlock.h"
#include "../core/SpscRing.h"
#include "../core/TimeAnchor.h"
#include "OboeDriver.h"

// Engine facade and the realtime callback spine (blueprint engine/ module).
// M0 skeleton scope: owns the driver, the per-producer message channels
// (CONTRACTS.md seam 2), and the readback publications; renders silence.
// The seams left open are exactly the blueprint's: TransportEngine replaces
// the placeholder transport state at M1, PlaybackGraph swap-in arrives at M2
// (the ParamMoveTables already retain values for post-swap re-apply), and
// instruments consume note events from M4.
//
// Threading: start/stop and the producer accessors are non-RT (JNI and MIDI
// threads own their respective channels); render() is the audio thread.

namespace daw {

// Per-block transport snapshot for UI playheads (blueprint 2.5 readback).
struct TransportClockData {
    int64_t samplePos = 0;
    double  beat = 0.0;         // real beat mapping arrives with TempoMap (M1)
    double  bpm = 120.0;
    uint32_t flags = 0;         // bit0 playing, bit1 recording, bit2 looping
};

inline constexpr uint32_t kClockPlaying   = 1u << 0;
inline constexpr uint32_t kClockRecording = 1u << 1;
inline constexpr uint32_t kClockLooping   = 1u << 2;

class AudioEngine final : public RenderSink {
public:
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
    uint64_t droppedNotes() const noexcept { return droppedNotes_.load(std::memory_order_relaxed); }
    uint64_t panics() const noexcept { return panics_.load(std::memory_order_relaxed); }

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

    // Placeholder transport state until TransportEngine lands (M1).
    int64_t samplePos_ = 0;
    double  bpm_ = 120.0;
    bool    playing_ = false;
    bool    recording_ = false;
    bool    looping_ = true;

    // Input drain scratch (monitoring paths arrive at M2/M6).
    float inScratchL_[kMaxBlock]{};
    float inScratchR_[kMaxBlock]{};

    std::atomic<uint64_t> droppedNotes_{0};
    std::atomic<uint64_t> panics_{0};
};

} // namespace daw
