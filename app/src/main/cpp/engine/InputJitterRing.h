#pragma once

#include "../core/EngineConfig.h"
#include "../core/RtAssert.h"

// Duplex input smoothing buffer (blueprint D2). Both ends run on the audio
// thread inside the output callback - the input stream is read non-blocking
// and pushed here, then exactly numFrames are consumed for monitoring and
// record taps - so this is a plain FIFO, no atomics. What it absorbs is the
// burst-size and clock mismatch between the input device and the output
// callback cadence (Oboe FullDuplex three-phase discipline):
//
//   prime:     after (re)start, skip consuming until ~2 output bursts of
//              input have accumulated, so early reads don't underflow.
//   underfill: consume returns what exists and zero-fills the rest; counted.
//   overfill:  when a slower output clock lets input accumulate past the
//              high-water mark, oldest frames are dropped; counted. Recording
//              truth is the input stream's own clock (D2), so the record tap
//              consumes BEFORE the monitoring drop point.

namespace daw {

class InputJitterRing {
public:
    // Capacity in frames; sized by the driver to ~4 output bursts.
    void prepare(int capacityFrames, int channels, int primeFrames) noexcept {
        DAW_RT_ASSERT(capacityFrames > 0 && channels >= 1 && channels <= kMaxChannels);
        capacity_ = 1;
        while (capacity_ < capacityFrames && capacity_ < kMaxJitterFrames) capacity_ <<= 1;
        DAW_RT_ASSERT(capacity_ >= capacityFrames);   // request exceeded the inline store
        mask_ = capacity_ - 1;
        channels_ = channels;
        primeTarget_ = primeFrames < capacity_ ? primeFrames : capacity_ / 2;
        reset();
    }

    void reset() noexcept {
        head_ = tail_ = 0;
        primed_ = false;
        underflowFrames_ = overflowFrames_ = 0;
    }

    // Push interleaved input frames (as read from the input stream).
    void push(const float* interleaved, int frames) noexcept {
        for (int f = 0; f < frames; ++f) {
            if (size() >= highWater()) {          // overfill: drop-oldest
                head_ += 1;
                ++overflowFrames_;
            }
            const int w = tail_ & mask_;
            for (int c = 0; c < channels_; ++c)
                store_[w * kMaxChannels + c] = interleaved[f * channels_ + c];
            tail_ += 1;
        }
        if (!primed_ && size() >= primeTarget_) primed_ = true;
    }

    // Consume exactly `frames` into de-interleaved outputs; zero-fills any
    // shortfall (unprimed or underflowing) and counts it.
    void consume(float* const* deinterleaved, int frames) noexcept {
        int available = primed_ ? size() : 0;
        for (int f = 0; f < frames; ++f) {
            if (available > 0) {
                const int r = head_ & mask_;
                for (int c = 0; c < channels_; ++c)
                    deinterleaved[c][f] = store_[r * kMaxChannels + c];
                head_ += 1;
                --available;
            } else {
                for (int c = 0; c < channels_; ++c) deinterleaved[c][f] = 0.0f;
                if (primed_) ++underflowFrames_;
            }
        }
        if (primed_ && size() == 0) primed_ = false;   // re-prime after a dry-out
    }

    int  size() const noexcept { return static_cast<int>(tail_ - head_); }
    bool primed() const noexcept { return primed_; }
    long underflowFrames() const noexcept { return underflowFrames_; }
    long overflowFrames() const noexcept { return overflowFrames_; }

private:
    int highWater() const noexcept { return capacity_ - 1; }

    static constexpr int kMaxJitterFrames = 8192;   // >= 4 bursts at any sane burst size

    float store_[kMaxJitterFrames * kMaxChannels]{};
    uint64_t head_ = 0, tail_ = 0;
    int capacity_ = 0, mask_ = 0, channels_ = 2;
    int primeTarget_ = 0;
    bool primed_ = false;
    long underflowFrames_ = 0, overflowFrames_ = 0;
};

} // namespace daw
