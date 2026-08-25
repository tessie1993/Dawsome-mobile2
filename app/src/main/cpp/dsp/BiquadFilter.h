#pragma once

#include "../core/RtAssert.h"
#include "DspMath.h"

// Direct-form-II-transposed biquad with RBJ Audio-EQ-Cookbook designs -
// the engine's workhorse for *static or slowly-automated* EQ bands
// (ChannelEq, ParametricEq8, shelves, crossovers). Audio-rate-modulated
// filters use SvfFilter instead (blueprint filter policy).
//
// TDF-II is chosen for its superior float noise behaviour and single pair
// of states; coefficients are normalised by a0 at design time.

namespace daw::dsp {

class BiquadFilter {
public:
    enum class Type : uint8_t {
        Lowpass, Highpass, Bandpass, Notch, Allpass, Peaking, LowShelf, HighShelf
    };

    void prepare(double sampleRate) noexcept {
        DAW_RT_ASSERT(sampleRate > 0.0);
        sampleRate_ = sampleRate;
        design(type_, freqHz_, q_, gainDb_);
        reset();
    }

    void reset() noexcept { z1_ = z2_ = 0.0f; }

    // RBJ cookbook design. gainDb applies to Peaking and shelves only.
    void design(Type type, float freqHz, float q, float gainDb = 0.0f) noexcept {
        type_ = type; freqHz_ = freqHz; q_ = q; gainDb_ = gainDb;
        if (sampleRate_ <= 0.0) return;

        const float f  = clamp(freqHz, 10.0f, static_cast<float>(sampleRate_) * 0.49f);
        const float Q  = clamp(q, 0.1f, 40.0f);
        const float w0 = kTwoPi * f / static_cast<float>(sampleRate_);
        const float cw = std::cos(w0);
        const float sw = std::sin(w0);
        const float alpha = sw / (2.0f * Q);
        const float A  = std::pow(10.0f, gainDb * 0.025f);   // sqrt of linear gain

        float b0, b1, b2, a0, a1, a2;
        switch (type) {
            case Type::Lowpass:
                b0 = (1 - cw) * 0.5f; b1 = 1 - cw; b2 = b0;
                a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha;
                break;
            case Type::Highpass:
                b0 = (1 + cw) * 0.5f; b1 = -(1 + cw); b2 = b0;
                a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha;
                break;
            case Type::Bandpass:                              // constant 0 dB peak
                b0 = alpha; b1 = 0.0f; b2 = -alpha;
                a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha;
                break;
            case Type::Notch:
                b0 = 1.0f; b1 = -2 * cw; b2 = 1.0f;
                a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha;
                break;
            case Type::Allpass:
                b0 = 1 - alpha; b1 = -2 * cw; b2 = 1 + alpha;
                a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha;
                break;
            case Type::Peaking:
                b0 = 1 + alpha * A; b1 = -2 * cw; b2 = 1 - alpha * A;
                a0 = 1 + alpha / A; a1 = -2 * cw; a2 = 1 - alpha / A;
                break;
            case Type::LowShelf: {
                const float sqA2a = 2.0f * std::sqrt(A) * alpha;
                b0 = A * ((A + 1) - (A - 1) * cw + sqA2a);
                b1 = 2 * A * ((A - 1) - (A + 1) * cw);
                b2 = A * ((A + 1) - (A - 1) * cw - sqA2a);
                a0 = (A + 1) + (A - 1) * cw + sqA2a;
                a1 = -2 * ((A - 1) + (A + 1) * cw);
                a2 = (A + 1) + (A - 1) * cw - sqA2a;
                break;
            }
            case Type::HighShelf: {
                const float sqA2a = 2.0f * std::sqrt(A) * alpha;
                b0 = A * ((A + 1) + (A - 1) * cw + sqA2a);
                b1 = -2 * A * ((A - 1) + (A + 1) * cw);
                b2 = A * ((A + 1) + (A - 1) * cw - sqA2a);
                a0 = (A + 1) - (A - 1) * cw + sqA2a;
                a1 = 2 * ((A - 1) - (A + 1) * cw);
                a2 = (A + 1) - (A - 1) * cw - sqA2a;
                break;
            }
        }

        const float inv = 1.0f / a0;
        b0_ = b0 * inv; b1_ = b1 * inv; b2_ = b2 * inv;
        a1_ = a1 * inv; a2_ = a2 * inv;
    }

    float process(float in) noexcept {                 // transposed direct form II
        const float out = b0_ * in + z1_;
        z1_ = b1_ * in - a1_ * out + z2_;
        z2_ = b2_ * in - a2_ * out;
        return out;
    }

private:
    double sampleRate_ = 0.0;
    Type  type_ = Type::Lowpass;
    float freqHz_ = 1000.0f, q_ = 0.707f, gainDb_ = 0.0f;
    float b0_ = 1.0f, b1_ = 0.0f, b2_ = 0.0f, a1_ = 0.0f, a2_ = 0.0f;
    float z1_ = 0.0f, z2_ = 0.0f;
};

} // namespace daw::dsp
