#pragma once

#include "../core/RtAssert.h"
#include "DspMath.h"

// Band-limited oscillator using polyBLEP residual correction (Valimaki /
// Pekonen lineage): a two-sample polynomial band-limited step is added at
// each waveform discontinuity, suppressing aliasing at a fraction of the
// cost of BLIT/wavetable oversampling - the standard choice for virtual-
// analog saw/pulse on mobile. Triangle is produced by leaky-integrating the
// band-limited square, sine directly. Phase runs in double for tuning
// stability across long renders; supports hard sync and PWM.
//
// Replaces the condemned device/synth/Oscillator.h.

namespace daw::dsp {

class Oscillator {
public:
    enum class Wave : uint8_t { Sine, Saw, Pulse, Triangle };

    void prepare(double sampleRate) noexcept {
        DAW_RT_ASSERT(sampleRate > 0.0);
        sampleRate_ = sampleRate;
        setFrequency(freqHz_);
        reset();
    }

    void reset(double phase = 0.0) noexcept {
        phase_ = phase - std::floor(phase);
        triState_ = 0.0f;
    }

    void setWave(Wave w) noexcept { wave_ = w; }

    void setFrequency(float hz) noexcept {
        freqHz_ = hz;
        inc_ = sampleRate_ > 0.0 ? static_cast<double>(hz) / sampleRate_ : 0.0;
    }

    // Pulse width 0.01..0.99 (0.5 = square).
    void setPulseWidth(float pw) noexcept { pw_ = clamp(pw, 0.01f, 0.99f); }

    // Hard sync: retrigger phase from a master oscillator's wrap.
    void sync(double phase = 0.0) noexcept { phase_ = phase - std::floor(phase); }

    double phase() const noexcept { return phase_; }
    bool   wrappedThisSample() const noexcept { return wrapped_; }

    float process() noexcept {
        const float t   = static_cast<float>(phase_);
        const float dt  = static_cast<float>(inc_);
        float out = 0.0f;

        switch (wave_) {
            case Wave::Sine:
                out = std::sin(t * kTwoPi);
                break;

            case Wave::Saw:
                out = 2.0f * t - 1.0f;
                out -= polyBlep(t, dt);
                break;

            case Wave::Pulse: {
                out = t < pw_ ? 1.0f : -1.0f;
                out += polyBlep(t, dt);                       // rising edge at 0
                out -= polyBlep(wrap01(t - pw_), dt);         // falling edge at pw
                break;
            }

            case Wave::Triangle: {
                float sq = t < 0.5f ? 1.0f : -1.0f;
                sq += polyBlep(t, dt);
                sq -= polyBlep(wrap01(t - 0.5f), dt);
                // Leaky integrator scaled so peak amplitude ~= 1 across pitch.
                triState_ = leak_ * triState_ + (4.0f * dt) * sq;
                out = triState_;
                break;
            }
        }

        phase_ += inc_;
        wrapped_ = phase_ >= 1.0;
        if (wrapped_) phase_ -= 1.0;
        return out;
    }

private:
    static float wrap01(float x) noexcept { return x < 0.0f ? x + 1.0f : x; }

    // Two-sided polyBLEP residual for a unit step at phase 0.
    static float polyBlep(float t, float dt) noexcept {
        if (dt <= 0.0f) return 0.0f;
        if (t < dt) {                       // just after the discontinuity
            const float x = t / dt;
            return x + x - x * x - 1.0f;
        }
        if (t > 1.0f - dt) {                // just before the discontinuity
            const float x = (t - 1.0f) / dt;
            return x * x + x + x + 1.0f;
        }
        return 0.0f;
    }

    double sampleRate_ = 0.0;
    double phase_ = 0.0;
    double inc_ = 0.0;
    float  freqHz_ = 440.0f;
    float  pw_ = 0.5f;
    float  triState_ = 0.0f;
    static constexpr float leak_ = 0.9995f;   // removes integrator DC drift
    Wave   wave_ = Wave::Saw;
    bool   wrapped_ = false;
};

} // namespace daw::dsp
