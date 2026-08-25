#pragma once

#include "../core/RtAssert.h"
#include "DspMath.h"

// ADSR with analog-style exponential segments: each stage is a one-pole
// chase toward an overshoot target, which yields the fast-then-settling
// curve of RC envelopes (the shape ears expect from subtractive synths;
// linear segments sound static by comparison). Attack chases an overshoot
// above 1.0 so it actually arrives; decay/release chase below their floor.
//
// The envelope reports idle for voice-stealing; retrigger restarts attack
// from the current level (click-free legato behaviour).

namespace daw::dsp {

class AdsrEnvelope {
public:
    enum class Stage : uint8_t { Idle, Attack, Decay, Sustain, Release };

    void prepare(double sampleRate) noexcept {
        DAW_RT_ASSERT(sampleRate > 0.0);
        sampleRate_ = sampleRate;
        setTimes(attackMs_, decayMs_, sustain_, releaseMs_);
    }

    void setTimes(float attackMs, float decayMs, float sustain, float releaseMs) noexcept {
        attackMs_ = attackMs; decayMs_ = decayMs; releaseMs_ = releaseMs;
        sustain_ = clamp01(sustain);
        attackCoef_  = segmentCoef(attackMs);
        decayCoef_   = segmentCoef(decayMs);
        releaseCoef_ = segmentCoef(releaseMs);
    }

    void noteOn() noexcept { stage_ = Stage::Attack; }

    void noteOff() noexcept {
        if (stage_ != Stage::Idle) stage_ = Stage::Release;
    }

    void reset() noexcept { level_ = 0.0f; stage_ = Stage::Idle; }

    float process() noexcept {
        switch (stage_) {
            case Stage::Idle:
                return 0.0f;
            case Stage::Attack:
                level_ += (kAttackTarget - level_) * attackCoef_;
                if (level_ >= 1.0f) { level_ = 1.0f; stage_ = Stage::Decay; }
                break;
            case Stage::Decay:
                level_ += (sustain_ * kDecayFloorScale - level_) * decayCoef_;
                if (level_ <= sustain_ + 0.0005f) { level_ = sustain_; stage_ = Stage::Sustain; }
                break;
            case Stage::Sustain:
                level_ = sustain_;
                break;
            case Stage::Release:
                level_ += (kReleaseFloor - level_) * releaseCoef_;
                if (level_ <= 0.0005f) { level_ = 0.0f; stage_ = Stage::Idle; }
                break;
        }
        return level_;
    }

    bool  isActive() const noexcept { return stage_ != Stage::Idle; }
    bool  isReleasing() const noexcept { return stage_ == Stage::Release; }
    Stage stage() const noexcept { return stage_; }
    float level() const noexcept { return level_; }

private:
    // One-pole coefficient reaching ~99.99% of a segment in the given time.
    float segmentCoef(float ms) const noexcept {
        const float samples = static_cast<float>(sampleRate_) * clamp(ms, 0.05f, 60000.0f) * 0.001f;
        return 1.0f - std::exp(-9.21034f / samples);   // ln(1e4)
    }

    static constexpr float kAttackTarget    = 1.3f;    // overshoot: exponential attack that lands
    static constexpr float kDecayFloorScale = 0.998f;  // slight undershoot so the gate closes
    static constexpr float kReleaseFloor    = -0.005f;

    double sampleRate_ = 0.0;
    float attackMs_ = 5.0f, decayMs_ = 120.0f, sustain_ = 0.8f, releaseMs_ = 150.0f;
    float attackCoef_ = 0.01f, decayCoef_ = 0.001f, releaseCoef_ = 0.001f;
    float level_ = 0.0f;
    Stage stage_ = Stage::Idle;
};

} // namespace daw::dsp
