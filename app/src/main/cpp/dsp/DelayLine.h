#pragma once

#include <memory>

#include "../core/RtAssert.h"
#include "DspMath.h"

// Mono delay line with integer, linear and cubic-Hermite fractional reads.
// Storage is allocated in prepare() (builder thread) at the next power of
// two above the requested maximum, so reads mask instead of wrapping by
// branch. Hermite (Catmull-Rom, 4-point) is the accepted quality/cost point
// for modulated delays (chorus, tape, comb) - linear reads suffice for
// static echo taps.

namespace daw::dsp {

class DelayLine {
public:
    // [builder] Allocate for maxDelaySamples of history.
    void prepare(int maxDelaySamples) noexcept {
        DAW_RT_ASSERT(maxDelaySamples > 0);
        size_ = 1;
        while (size_ < maxDelaySamples + 4) size_ <<= 1;   // headroom for hermite taps
        buffer_ = std::make_unique<float[]>(static_cast<size_t>(size_));
        mask_ = size_ - 1;
        clear();
    }

    void clear() noexcept {
        if (buffer_) for (int i = 0; i < size_; ++i) buffer_[i] = 0.0f;
        writeIndex_ = 0;
    }

    // [RT] Push one sample of history.
    void write(float in) noexcept {
        buffer_[writeIndex_] = in;
        writeIndex_ = (writeIndex_ + 1) & mask_;
    }

    // [RT] Read `delay` whole samples back (1 <= delay <= size-2, which the
    // +4 headroom guarantees covers maxDelay()+2 for hermite's outer taps).
    float read(int delay) const noexcept {
        DAW_RT_ASSERT(delay >= 1 && delay <= size_ - 2);
        return buffer_[(writeIndex_ - delay) & mask_];
    }

    // [RT] Linear-interpolated fractional read.
    float readLinear(float delay) const noexcept {
        const int   di = static_cast<int>(delay);
        const float fr = delay - static_cast<float>(di);
        const float a = read(di);
        const float b = read(di + 1);
        return a + (b - a) * fr;
    }

    // [RT] 4-point cubic Hermite (Catmull-Rom) fractional read.
    float readHermite(float delay) const noexcept {
        const int   di = static_cast<int>(delay);
        const float t  = delay - static_cast<float>(di);
        const float xm1 = read(di - 1 >= 1 ? di - 1 : 1);
        const float x0  = read(di);
        const float x1  = read(di + 1);
        const float x2  = read(di + 2);
        const float c  = (x1 - xm1) * 0.5f;
        const float v  = x0 - x1;
        const float w  = c + v;
        const float a  = w + v + (x2 - x0) * 0.5f;
        const float bn = w + a;
        return ((((a * t) - bn) * t + c) * t + x0);
    }

    int maxDelay() const noexcept { return size_ - 4; }

private:
    std::unique_ptr<float[]> buffer_;
    int size_ = 0;
    int mask_ = 0;
    int writeIndex_ = 0;
};

} // namespace daw::dsp
