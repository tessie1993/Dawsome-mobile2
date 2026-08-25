#pragma once

#include <cmath>

#include "../../dsp/DspMath.h"
#include "../../dsp/NoiseGen.h"
#include "../../dsp/SvfFilter.h"
#include "../../media/SampleBuffer.h"

// One drum pad: settings POD + the mono one-shot voice (blueprint
// device/instruments/DrumPadSampler - here the pad voice INSIDE
// DrumRackDevice; the frozen wire enum has no standalone pad id).
//
// Five synthesis modes (researched classics) + sample playback:
//   Sub   - sine with exponential pitch envelope dropping onto the base
//           frequency (the single-osc 808 kick/tom design); `shape` = drop
//           depth, `tone` = drive brightness.
//   Noise - tonal sine body + white noise through an SVF (the classic
//           snare split); `tone` = filter cutoff, `shape` = body/noise mix.
//   Metal - the TR-808 cymbal scheme: six detuned squares summed into an
//           inharmonic cluster, highpassed; `tone` = cutoff, `shape` =
//           cluster spread. Cheap sign-squares: the aliasing smear is part
//           of the classic character at these frequencies.
//   Ring  - two multiplied sines (sum+difference partials, cowbell
//           family); `shape` = partner ratio.
//   Bit   - square through sample-hold rate crush + bit quantize;
//           `tone` = bit depth, `shape` = crush factor.
//   Sample- tuned playback of a cache-pinned SampleHandle (linear interp,
//           one-shot). The handle arrives with the media-library milestone;
//           an empty handle renders silence.
//
// Envelopes are drum-idiomatic: instant attack with a 0.5 ms declick ramp,
// exponential one-pole decay from `decayMs`. fastRelease (steal/choke) is
// a 5 ms ramp; the 30 ms transient window protects fresh hits from steals.

namespace daw {

struct DrumPadShared {                 // per-pad settings, POD
    float mode = 0.0f;                 // 0 Sub, 1 Noise, 2 Metal, 3 Ring, 4 Bit, 5 Sample
    float levelDb = 0.0f;              // -60..+6
    float tuneSemi = 0.0f;             // -24..+24 around the pad's root
    float decayMs = 300.0f;
    float tone = 0.5f;                 // mode color (see table above)
    float shape = 0.5f;                // mode shape (see table above)
    float chokeGroup = 0.0f;           // 0 = none; matches Kotlin DrumPadType
};

class DrumPadVoice {
public:
    enum class Mode : int { Sub = 0, Noise, Metal, Ring, Bit, Sample };

    void prepare(double sampleRate) noexcept {
        rate_ = sampleRate;
        filter_.prepare(sampleRate);
        transientSamples_ = static_cast<int>(sampleRate * 0.03);
        declickInc_ = static_cast<float>(1.0 / (sampleRate * 0.0005));
        fastFallMul_ = decayMulFor(5.0f);
        active_ = false;
    }

    // rootPitch: the pad's fixed MIDI note (DrumPadType mirror).
    void trigger(float velocity01, uint16_t rootPitch,
                 const DrumPadShared& p, uint64_t serial) noexcept {
        mode_ = static_cast<Mode>(dsp::clamp(static_cast<int>(p.mode + 0.5f), 0, 5));
        baseHz_ = dsp::midiToHz(static_cast<float>(rootPitch) + p.tuneSemi);
        gain_ = dsp::dbToGain(p.levelDb) * (0.25f + 0.75f * velocity01);
        tone_ = dsp::clamp01(p.tone);
        shape_ = dsp::clamp01(p.shape);
        ampMul_ = decayMulFor(p.decayMs);
        amp_ = 1.0f;
        declick_ = 0.0f;
        // Sub pitch envelope: start (1 + 8*shape)x above base, ~45 ms fall.
        pitchMul_ = 1.0f + 8.0f * shape_;
        pitchFall_ = decayMulFor(45.0f);
        phase_ = phaseB_ = 0.0;
        for (int i = 0; i < 6; ++i) metalPhase_[i] = 0.0;
        holdCount_ = 0;
        holdValue_ = 0.0f;
        samplePos_ = 0.0;
        sampleRatio_ = std::exp2(static_cast<double>(p.tuneSemi) / 12.0);
        bodyAmp_ = 1.0f;
        bodyMul_ = decayMulFor(60.0f);
        releasing_ = false;
        ageSamples_ = 0;
        serial_ = serial;
        filter_.reset();
        switch (mode_) {
            case Mode::Noise:
                filter_.setMode(dsp::SvfFilter::Mode::Bandpass);
                filter_.setParams(noiseCutoff(), 1.2f);
                break;
            case Mode::Metal:
                filter_.setMode(dsp::SvfFilter::Mode::Highpass);
                filter_.setParams(metalCutoff(), 0.9f);
                break;
            default: break;
        }
        active_ = true;
    }

    void setSample(SampleHandle h) noexcept { sample_ = static_cast<SampleHandle&&>(h); }

    // ---- VoiceGroup candidate facts ----------------------------------------
    bool  active() const noexcept { return active_; }
    bool  releasing() const noexcept { return releasing_; }
    float level() const noexcept { return amp_ * gain_; }
    bool  inTransientWindow() const noexcept { return ageSamples_ < transientSamples_; }
    uint64_t serial() const noexcept { return serial_; }
    void  beginRelease() noexcept { releasing_ = true; ampMul_ = fastFallMul_; }
    void  fastRelease() noexcept { releasing_ = true; ampMul_ = fastFallMul_; }
    void  kill() noexcept { active_ = false; amp_ = 0.0f; }

    void renderAdd(float* l, float* r, int n) noexcept {
        if (!active_) return;
        for (int i = 0; i < n; ++i) {
            float s = 0.0f;
            switch (mode_) {
                case Mode::Sub:    s = tickSub(); break;
                case Mode::Noise:  s = tickNoise(); break;
                case Mode::Metal:  s = tickMetal(); break;
                case Mode::Ring:   s = tickRing(); break;
                case Mode::Bit:    s = tickBit(); break;
                case Mode::Sample: s = tickSample(); break;
            }
            if (declick_ < 1.0f) {
                declick_ += declickInc_;
                if (declick_ > 1.0f) declick_ = 1.0f;
                s *= declick_;
            }
            s *= amp_ * gain_;
            amp_ *= ampMul_;
            l[i] += s;
            r[i] += s;
        }
        ageSamples_ += n;
        if (amp_ < 1e-4f) active_ = false;
    }

private:
    float decayMulFor(float ms) const noexcept {
        // Reach -60 dB over `ms`: mul = exp(ln(0.001) / (ms * rate/1000)).
        const double samples = rate_ * (ms > 1.0f ? ms : 1.0f) * 0.001;
        return static_cast<float>(std::exp(-6.907755278982137 / samples));
    }
    float noiseCutoff() const noexcept {
        return 200.0f * std::exp2(tone_ * 5.3f);       // 200 Hz .. ~8 kHz
    }
    float metalCutoff() const noexcept {
        return 3000.0f * std::exp2(tone_ * 2.0f);      // 3 .. 12 kHz
    }
    float sine(double& phase, double hz) noexcept {
        const float v = std::sin(static_cast<float>(phase * 6.28318530718));
        phase += hz / rate_;
        phase -= std::floor(phase);
        return v;
    }

    float tickSub() noexcept {
        pitchMul_ = 1.0f + (pitchMul_ - 1.0f) * pitchFall_;
        const float v = sine(phase_, baseHz_ * pitchMul_);
        // tone = tanh drive brightness (adds harmonics as it rises).
        const float d = 1.0f + tone_ * 4.0f;
        return std::tanh(v * d) / std::tanh(d);
    }

    float tickNoise() noexcept {
        bodyAmp_ *= bodyMul_;
        const float body = sine(phase_, baseHz_) * bodyAmp_;
        const float noise = filter_.process(noise_.white());
        return body * shape_ + noise * (1.0f - shape_ * 0.5f);
    }

    float tickMetal() noexcept {
        // 808 cymbal bank ratios (Hz at unity tune), spread by shape.
        static constexpr double kBank[6] = {263.0, 400.0, 421.0, 474.0, 587.0, 845.0};
        const double scale = static_cast<double>(baseHz_) / 400.0;
        float sum = 0.0f;
        for (int o = 0; o < 6; ++o) {
            const double spread = 1.0 + (shape_ - 0.5f) * 0.3 * (o - 2.5) / 2.5;
            metalPhase_[o] += kBank[o] * scale * spread / rate_;
            metalPhase_[o] -= std::floor(metalPhase_[o]);
            sum += metalPhase_[o] < 0.5 ? 1.0f : -1.0f;
        }
        return filter_.process(sum * (1.0f / 6.0f));
    }

    float tickRing() noexcept {
        const float a = sine(phase_, baseHz_);
        const float b = sine(phaseB_, baseHz_ * (1.5 + 3.0 * shape_));
        return a * b;
    }

    float tickBit() noexcept {
        // Source runs at FULL rate; the sample-hold reads it every N ticks
        // (true decimation - pitch stays put while the crush deepens).
        const float raw = phase_ < 0.5 ? 1.0f : -1.0f;
        phase_ += baseHz_ / rate_;
        phase_ -= std::floor(phase_);
        if (holdCount_ <= 0) {
            const float bits = 2.0f + tone_ * 10.0f;   // 2..12 bit
            const float steps = std::exp2(bits);
            holdValue_ = std::floor(raw * steps + 0.5f) / steps;
            holdCount_ = 1 + static_cast<int>(shape_ * 31.0f);
        }
        --holdCount_;
        return holdValue_;
    }

    float tickSample() noexcept {
        const SampleBuffer* buf = sample_.get();
        if (buf == nullptr || buf->frames <= 0) return 0.0f;
        if (samplePos_ >= static_cast<double>(buf->frames - 1)) {
            active_ = false;
            return 0.0f;
        }
        const int64_t i0 = static_cast<int64_t>(samplePos_);
        const float fr = static_cast<float>(samplePos_ - static_cast<double>(i0));
        const float* ch = buf->channel(0);   // pads read mono (left/summed)
        const float v = ch[i0] + (ch[i0 + 1] - ch[i0]) * fr;
        samplePos_ += sampleRatio_;          // relative repitch: 2^(tune/12)
        return v;
    }

    double rate_ = 48000.0;
    Mode mode_ = Mode::Sub;
    bool active_ = false;
    bool releasing_ = false;
    int ageSamples_ = 0;
    int transientSamples_ = 1440;
    uint64_t serial_ = 0;

    float baseHz_ = 55.0f;
    float gain_ = 1.0f;
    float tone_ = 0.5f;
    float shape_ = 0.5f;
    float amp_ = 0.0f;
    float ampMul_ = 0.999f;
    float fastFallMul_ = 0.99f;
    float declick_ = 1.0f;
    float declickInc_ = 0.1f;
    float pitchMul_ = 1.0f;
    float pitchFall_ = 0.999f;
    float bodyAmp_ = 0.0f;
    float bodyMul_ = 0.999f;

    double phase_ = 0.0;
    double phaseB_ = 0.0;
    double metalPhase_[6] = {0, 0, 0, 0, 0, 0};
    int holdCount_ = 0;
    float holdValue_ = 0.0f;
    double samplePos_ = 0.0;
    double sampleRatio_ = 1.0;

    dsp::NoiseGen noise_;
    dsp::SvfFilter filter_;
    SampleHandle sample_;
};

} // namespace daw
