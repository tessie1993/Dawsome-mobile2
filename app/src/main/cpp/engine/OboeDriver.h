#pragma once

#include <atomic>
#include <cstdint>
#include <memory>

#include <oboe/Oboe.h>

#include "../core/EngineConfig.h"
#include "InputJitterRing.h"

// Audio device I/O (blueprint D2, engine/OboeDriver). Owns the Oboe streams
// and nothing else - all musical work lives behind RenderSink (AudioEngine).
//
// Duplex model (Oboe FullDuplex guidance, researched pattern):
//   - OUTPUT stream is the clock master: built first, callback-driven,
//     PerformanceMode::LowLatency, SharingMode::Exclusive with automatic
//     fallback to Shared, device-native sample rate, float, stereo.
//   - INPUT stream is opened WITHOUT a callback, matched to the output's
//     sample rate, buffer capacity ~2x the output's, and drained with
//     non-blocking reads at the top of each output callback into the
//     InputJitterRing (three-phase sync lives in the ring).
//   - Route/rate changes arrive via the error callback AFTER close; the
//     driver only flags them - EngineController runs the re-prepare
//     sequence (D5) from a normal thread, never from RT.
//
// Timestamps: each callback samples getTimestamp() on the output stream to
// anchor {framePosition, monotonicNanos}; the sink publishes it as the
// TimeAnchor. Latency reports come from calculateLatencyMillis() per stream
// and feed RecordingAligner via EnginePrefs.

namespace daw {

struct StreamTime {
    int64_t framePosition = 0;     // DAC frame position of this callback's first frame
    int64_t monotonicNanos = 0;    // CLOCK_MONOTONIC estimate for that frame
    bool    valid = false;
};

class RenderSink {
public:
    virtual ~RenderSink() = default;
    // De-interleaved stereo output, input available through the jitter ring.
    // The DRIVER sub-chunks variable callback bursts, so numFrames is always
    // <= kMaxBlock here (D3) and `time` is advanced per sub-chunk.
    virtual void render(float* const* outputs, int numFrames,
                        InputJitterRing& input, const StreamTime& time) = 0;
};

class OboeDriver final : public oboe::AudioStreamDataCallback,
                         public oboe::AudioStreamErrorCallback {
public:
    struct Config {
        int  requestedBufferBursts = 2;   // latency vs stability knob
        bool enableInput = true;
        int  inputDeviceId = oboe::kUnspecified;
        int  outputDeviceId = oboe::kUnspecified;
    };

    // [any, non-RT] Open output (clock master) then input. Returns false and
    // closes everything on failure. Safe to call again after close().
    bool open(RenderSink& sink, const Config& cfg) noexcept;
    bool start() noexcept;
    void stop() noexcept;
    void close() noexcept;

    // Stream facts for EnginePrefs / RecordingAligner.
    double sampleRate() const noexcept { return sampleRate_; }
    int    framesPerBurst() const noexcept { return framesPerBurst_; }
    double outputLatencyMs() const noexcept { return outputLatencyMs_.load(std::memory_order_relaxed); }
    double inputLatencyMs() const noexcept { return inputLatencyMs_.load(std::memory_order_relaxed); }
    int32_t xrunCount() const noexcept { return xruns_.load(std::memory_order_relaxed); }
    // Duplex input stream is open (output-only sessions report false). Atomic
    // because the readback poll thread asks while open/close runs elsewhere.
    bool inputOpen() const noexcept { return inputOpen_.load(std::memory_order_acquire); }

    // Set when a stream died (route change, device gone). EngineController
    // polls this and runs the D5 re-prepare sequence off-thread.
    bool needsReopen() const noexcept { return needsReopen_.load(std::memory_order_acquire); }
    void clearReopenFlag() noexcept { needsReopen_.store(false, std::memory_order_release); }

    // oboe::AudioStreamDataCallback ------------------------------------- [RT]
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream,
                                          void* audioData, int32_t numFrames) override;

    // oboe::AudioStreamErrorCallback -------------------------------- [non-RT]
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    void drainInput(int32_t numFrames) noexcept;
    void refreshLatency() noexcept;

    std::shared_ptr<oboe::AudioStream> outputStream_;
    std::shared_ptr<oboe::AudioStream> inputStream_;
    RenderSink* sink_ = nullptr;

    InputJitterRing inputRing_;
    // Interleaved staging for non-blocking input reads (one burst at a time).
    float inputStage_[kMaxBlock * kMaxChannels]{};
    // De-interleaved staging handed to the sink for output.
    float outL_[kMaxBlock]{};
    float outR_[kMaxBlock]{};

    double sampleRate_ = 0.0;
    int    framesPerBurst_ = 0;
    std::atomic<double>  outputLatencyMs_{0.0};
    std::atomic<double>  inputLatencyMs_{0.0};
    std::atomic<int32_t> xruns_{0};
    std::atomic<bool>    needsReopen_{false};
    std::atomic<bool>    inputOpen_{false};
    int latencyRefreshCountdown_ = 0;
};

} // namespace daw
