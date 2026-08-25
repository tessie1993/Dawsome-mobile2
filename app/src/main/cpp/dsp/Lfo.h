#pragma once

#include "../core/RtRandom.h"
#include "DspMath.h"

// Low-frequency oscillator for modulation (blueprint ModulationEngine and
// per-voice mod sources). Phase-accumulator design; naive shapes are correct
// here because LFO rates sit far below Nyquist - no band-limiting needed.
// Sample & hold and smooth-random use the engine RNG (seedable for offline
// render parity). Tempo sync is the caller's job: it sets frequency from
// the TempoMap (rate = 1 / (beatsPerCycle * secondsPerBeat)).

namespace daw::dsp {

class Lfo {
public:
    enum class Shape : uint8_t {
        Sine, Triangle, SawUp, SawDown, Square, SampleHold, SmoothRandom
    };

    void prepare(double sampleRate) noexcept {
        sampleRate_ = sampleRate;
        setFrequency(freqHz_);
        reset();
    }

    void reset(double phase = 0.0) noexcept {
        phase_ = phase - std::floor(phase);
        held_ = rng_.nextBipolar();
        target_ = rng_.nextBipolar();
        smooth_ = held_;
    }

    void setShape(Shape s) noexcept { shape_ = s; }
    void setFrequency(float hz) noexcept {
        freqHz_ = clamp(hz, 0.0f, 80.0f);
        inc_ = sampleRate_ > 0.0 ? static_cast<double>(freqHz_) / sampleRate_ : 0.0;
    }
    void reseed(uint64_t seed) noexcept { rng_.reseed(seed); }

    // Bipolar output [-1, 1]; unipolar mapping happens at the mod-matrix edge.
    float process() noexcept {
        const float t = static_cast<float>(phase_);
        float out = 0.0f;
        switch (shape_) {
            case Shape::Sine:      out = std::sin(t * kTwoPi); break;
            case Shape::Triangle:  out = t < 0.5f ? 4.0f * t - 1.0f : 3.0f - 4.0f * t; break;
            case Shape::SawUp:     out = 2.0f * t - 1.0f; break;
            case Shape::SawDown:   out = 1.0f - 2.0f * t; break;
            case Shape::Square:    out = t < 0.5f ? 1.0f : -1.0f; break;
            case Shape::SampleHold: out = held_; break;
            case Shape::SmoothRandom: {
                // One-pole glide toward the per-cycle target.
                smooth_ += (target_ - smooth_) * static_cast<float>(inc_ * 6.0);
                out = smooth_;
                break;
            }
        }

        phase_ += inc_;
        if (phase_ >= 1.0) {
            phase_ -= 1.0;
            held_ = rng_.nextBipolar();     // new S&H value per cycle
            target_ = rng_.nextBipolar();   // new glide target per cycle
        }
        return out;
    }

    double phase() const noexcept { return phase_; }

private:
    double sampleRate_ = 0.0;
    double phase_ = 0.0;
    double inc_ = 0.0;
    float  freqHz_ = 1.0f;
    float  held_ = 0.0f;
    float  target_ = 0.0f;
    float  smooth_ = 0.0f;
    daw::RtRandom rng_{0x5851f42d4c957f2dull};
    Shape  shape_ = Shape::Sine;
};

} // namespace daw::dsp
