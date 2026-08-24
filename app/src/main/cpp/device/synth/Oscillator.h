#pragma once

#include <cmath>
#include <string>

namespace synth {

/**
 * Polyphonic SIMD-friendly Oscillator.
 * Inspired by seclorum/SIMDsynth (MIT).
 */
class Oscillator {
public:
    enum class Waveform { SINE, SQUARE, SAW, TRIANGLE };

    Oscillator(double sampleRate) : sampleRate_(sampleRate) {}

    void setFrequency(float freq) {
        phaseIncrement_ = (2.0f * M_PI * freq) / sampleRate_;
    }

    void setWaveform(Waveform type) {
        waveform_ = type;
    }

    float getNextSample() {
        float out = 0.0f;
        
        // Very basic procedural wave generation
        switch (waveform_) {
            case Waveform::SINE:
                out = std::sin(phase_);
                break;
            case Waveform::SQUARE:
                out = (phase_ < M_PI) ? 1.0f : -1.0f;
                break;
            case Waveform::SAW:
                out = (phase_ / M_PI) - 1.0f;
                break;
            case Waveform::TRIANGLE:
                out = 2.0f * std::abs(2.0f * (phase_ / (2.0f * M_PI)) - 1.0f) - 1.0f;
                break;
        }

        phase_ += phaseIncrement_;
        if (phase_ >= 2.0f * M_PI) {
            phase_ -= 2.0f * M_PI;
        }

        return out;
    }

private:
    double sampleRate_;
    float phase_{0.0f};
    float phaseIncrement_{0.0f};
    Waveform waveform_{Waveform::SINE};
};

} // namespace synth
