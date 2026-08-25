#include "OboeDriver.h"

// Implementation notes are in the header; every choice here follows the
// researched Oboe FullDuplex pattern and blueprint D2/D5.

namespace daw {

bool OboeDriver::open(RenderSink& sink, const Config& cfg) noexcept {
    close();
    sink_ = &sink;

    // 1) Output first: it defines the clock, rate and burst size.
    oboe::AudioStreamBuilder out;
    out.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)      // falls back to Shared
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setDeviceId(cfg.outputDeviceId)
        ->setDataCallback(this)
        ->setErrorCallback(this);
    if (out.openStream(outputStream_) != oboe::Result::OK || !outputStream_) {
        close();
        return false;
    }

    if (outputStream_->getChannelCount() != 2) {      // engine is stereo-out (D3)
        close();
        return false;
    }
    sampleRate_ = outputStream_->getSampleRate();
    framesPerBurst_ = outputStream_->getFramesPerBurst();
    if (framesPerBurst_ <= 0) framesPerBurst_ = 192;
    outputStream_->setBufferSizeInFrames(framesPerBurst_ * cfg.requestedBufferBursts);

    // 2) Input second: no callback, matched rate, double capacity, drained
    //    non-blocking inside the output callback.
    if (cfg.enableInput) {
        oboe::AudioStreamBuilder in;
        in.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Stereo)
            ->setSampleRate(static_cast<int32_t>(sampleRate_))
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
            ->setDeviceId(cfg.inputDeviceId)
            ->setBufferCapacityInFrames(framesPerBurst_ * cfg.requestedBufferBursts * 2)
            ->setErrorCallback(this);
        if (in.openStream(inputStream_) != oboe::Result::OK) {
            inputStream_.reset();                     // output-only is still a session
        }
    }

    inputRing_.prepare(framesPerBurst_ * 4, kMaxChannels, framesPerBurst_ * 2);
    latencyRefreshCountdown_ = 0;
    needsReopen_.store(false, std::memory_order_release);
    return true;
}

bool OboeDriver::start() noexcept {
    if (!outputStream_) return false;
    if (inputStream_) inputStream_->requestStart();   // input first so frames exist
    return outputStream_->requestStart() == oboe::Result::OK;
}

void OboeDriver::stop() noexcept {
    if (outputStream_) outputStream_->requestStop();
    if (inputStream_) inputStream_->requestStop();
}

void OboeDriver::close() noexcept {
    stop();
    if (inputStream_) { inputStream_->close(); inputStream_.reset(); }
    if (outputStream_) { outputStream_->close(); outputStream_.reset(); }
    sink_ = nullptr;
}

void OboeDriver::drainInput(int32_t numFrames) noexcept {
    if (!inputStream_) return;
    // Pull everything available (never more than the ring can hold usefully),
    // zero timeout: this must not block the output callback.
    const auto avail = inputStream_->getAvailableFrames();
    int32_t wanted = avail ? avail.value() : numFrames;   // best effort on API failure
    while (wanted > 0) {
        const int32_t chunk = wanted < kMaxBlock ? wanted : kMaxBlock;
        const auto res = inputStream_->read(inputStage_, chunk, 0 /*timeoutNanos*/);
        if (!res || res.value() <= 0) break;
        inputRing_.push(inputStage_, res.value());
        wanted -= res.value();
        if (res.value() < chunk) break;               // drained dry
    }
}

oboe::DataCallbackResult OboeDriver::onAudioReady(oboe::AudioStream* stream,
                                                  void* audioData, int32_t numFrames) {
    drainInput(numFrames);

    // Anchor this callback: DAC-side frame position and time.
    StreamTime time;
    const auto ts = stream->getTimestamp(CLOCK_MONOTONIC);
    if (ts) {
        // getTimestamp reports a *recent DAC* position/time pair; extrapolate
        // to this callback's first frame using frames written so far.
        const int64_t written = stream->getFramesWritten();
        time.framePosition = written;
        time.monotonicNanos = ts.value().timestamp +
            static_cast<int64_t>((written - ts.value().position) * 1.0e9 / sampleRate_);
        time.valid = true;
    }

    // Sub-chunk to <= kMaxBlock (D3) and hand de-interleaved slices to the sink.
    auto* interleavedOut = static_cast<float*>(audioData);
    float* outs[kMaxChannels] = { outL_, outR_ };
    int32_t done = 0;
    while (done < numFrames) {
        const int32_t n = (numFrames - done) < kMaxBlock ? (numFrames - done) : kMaxBlock;

        StreamTime sliceTime = time;
        if (time.valid) {
            sliceTime.framePosition += done;
            sliceTime.monotonicNanos += static_cast<int64_t>(done * 1.0e9 / sampleRate_);
        }

        sink_->render(outs, n, inputRing_, sliceTime);

        for (int32_t f = 0; f < n; ++f) {
            interleavedOut[(done + f) * 2]     = outL_[f];
            interleavedOut[(done + f) * 2 + 1] = outR_[f];
        }
        done += n;
    }

    // Cheap periodic bookkeeping (no syscalls beyond oboe's own accessors).
    if (--latencyRefreshCountdown_ <= 0) {
        latencyRefreshCountdown_ = 256;               // ~1 s at 192-frame bursts
        refreshLatency();
        const auto x = stream->getXRunCount();
        if (x) xruns_.store(x.value(), std::memory_order_relaxed);
    }
    return oboe::DataCallbackResult::Continue;
}

void OboeDriver::refreshLatency() noexcept {
    if (outputStream_) {
        const auto l = outputStream_->calculateLatencyMillis();
        if (l) outputLatencyMs_.store(l.value(), std::memory_order_relaxed);
    }
    if (inputStream_) {
        const auto l = inputStream_->calculateLatencyMillis();
        if (l) inputLatencyMs_.store(l.value(), std::memory_order_relaxed);
    }
}

void OboeDriver::onErrorAfterClose(oboe::AudioStream*, oboe::Result) {
    // Route change / device lost. Non-RT context: just flag; EngineController
    // runs the D5 re-prepare sequence (close -> reopen -> re-key caches ->
    // re-prime -> resume) from a normal thread.
    needsReopen_.store(true, std::memory_order_release);
}

} // namespace daw
