#pragma once

#include "../core/RtAssert.h"
#include "DspMath.h"

// State-variable filter after Andrew Simper's trapezoidal-integration ZDF
// derivation (Cytomic, "SvfLinearTrapOptimised2"): the modern standard for
// synth and auto filters because coefficients can be modulated at audio rate
// without instability or state glitches - unlike direct-form biquads, which
// this engine reserves for static EQ bands (BiquadFilter).
//
// One tick produces low/band/high simultaneously; the remaining responses
// are linear combinations. Per-sample cost: 2 mul-adds per integrator plus
// the mixdown.

namespace daw::dsp {

class SvfFilter {
public:
    enum class Mode : uint8_t { Lowpass, Bandpass, Highpass, Notch, Peak, Allpass };

    void prepare(double sampleRate) noexcept {
        DAW_RT_ASSERT(sampleRate > 0.0);
        sampleRate_ = sampleRate;
        setParams(cutoffHz_, q_);
        reset();
    }

    void reset() noexcept { ic1eq_ = ic2eq_ = 0.0f; }

    void setMode(Mode m) noexcept { mode_ = m; }

    // Safe under per-sample modulation. Q >= 0.5 (self-oscillation approached
    // as Q grows; k = 1/Q).
    void setParams(float cutoffHz, float q) noexcept {
        cutoffHz_ = clamp(cutoffHz, 10.0f, static_cast<float>(sampleRate_) * 0.49f);
        q_ = clamp(q, 0.5f, 40.0f);
        const float g = std::tan(kPi * cutoffHz_ / static_cast<float>(sampleRate_));
        k_  = 1.0f / q_;
        a1_ = 1.0f / (1.0f + g * (g + k_));
        a2_ = g * a1_;
        a3_ = g * a2_;
    }

    float process(float in) noexcept {
        const float v3 = in - ic2eq_;
        const float v1 = a1_ * ic1eq_ + a2_ * v3;          // band
        const float v2 = ic2eq_ + a2_ * ic1eq_ + a3_ * v3; // low
        ic1eq_ = 2.0f * v1 - ic1eq_;
        ic2eq_ = 2.0f * v2 - ic2eq_;

        switch (mode_) {
            case Mode::Lowpass:  return v2;
            case Mode::Bandpass: return v1;
            case Mode::Highpass: return in - k_ * v1 - v2;
            case Mode::Notch:    return in - k_ * v1;
            case Mode::Peak:     return in - k_ * v1 - 2.0f * v2;
            case Mode::Allpass:  return in - 2.0f * k_ * v1;
        }
        return v2;
    }

private:
    double sampleRate_ = 0.0;
    float cutoffHz_ = 1000.0f;
    float q_ = 0.707f;
    float k_ = 1.414f;
    float a1_ = 0.0f, a2_ = 0.0f, a3_ = 0.0f;
    float ic1eq_ = 0.0f, ic2eq_ = 0.0f;   // trapezoidal integrator states
    Mode  mode_ = Mode::Lowpass;
};

} // namespace daw::dsp
