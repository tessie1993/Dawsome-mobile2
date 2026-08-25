#pragma once

#include "RtAssert.h"

// Linear parameter smoothing (CONTRACTS.md seam 1; blueprint 5). Every
// audible continuous parameter passes through one of these after resolution;
// the ramp time comes from the ParamDescriptor (smoothingMs, 0 = stepped).
// Linear ramps are the industry default for gain-class parameters: constant
// slope, exact arrival, no denormal tail (unlike one-pole exponential).
//
// State migrates across graph swaps (current value AND target travel in the
// NodeState block), so a fader ridden through a rebuild never jumps.

namespace daw {

class SmoothedValue {
public:
    void prepare(double sampleRate, float smoothingMs) noexcept {
        DAW_RT_ASSERT(sampleRate > 0.0);
        rampSamples_ = smoothingMs <= 0.0f
                           ? 0
                           : static_cast<int>(sampleRate * (smoothingMs * 0.001f) + 0.5f);
        snap(target_);
    }

    // Jump immediately (initialisation, reset, stepped params).
    void snap(float value) noexcept {
        current_ = target_ = value;
        remaining_ = 0;
        step_ = 0.0f;
    }

    void setTarget(float value) noexcept {
        if (value == target_) return;
        target_ = value;
        if (rampSamples_ <= 0) { snap(value); return; }
        remaining_ = rampSamples_;
        step_ = (target_ - current_) / static_cast<float>(rampSamples_);
    }

    // Per-sample advance.
    float getNext() noexcept {
        if (remaining_ <= 0) return current_;
        current_ += step_;
        if (--remaining_ == 0) current_ = target_;   // exact arrival
        return current_;
    }

    // Advance n samples without producing values (control-rate consumers).
    void skip(int n) noexcept {
        if (remaining_ <= 0) return;
        if (n >= remaining_) { current_ = target_; remaining_ = 0; return; }
        current_ += step_ * static_cast<float>(n);
        remaining_ -= n;
    }

    bool  isSmoothing() const noexcept { return remaining_ > 0; }
    float current() const noexcept { return current_; }
    float target() const noexcept { return target_; }

private:
    float current_ = 0.0f;
    float target_ = 0.0f;
    float step_ = 0.0f;
    int   remaining_ = 0;
    int   rampSamples_ = 0;
};

} // namespace daw
