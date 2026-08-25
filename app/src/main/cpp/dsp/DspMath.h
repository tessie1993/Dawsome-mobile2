#pragma once

#include <cmath>

// Shared DSP math: constants and tiny inline helpers. Pure, header-only,
// host-compilable (blueprint dsp/ rules). dB and pitch conventions per
// CONTRACTS.md cross-cutting constants.

namespace daw::dsp {

inline constexpr float kPi     = 3.14159265358979323846f;
inline constexpr float kTwoPi  = 6.28318530717958647692f;
inline constexpr float kInvPi  = 0.31830988618379067154f;

inline constexpr float kSilenceDb = -72.0f;   // treated as -inf (contract)

// gain = 10^(dB/20); below the silence floor -> 0.
inline float dbToGain(float db) noexcept {
    return db <= kSilenceDb ? 0.0f : std::pow(10.0f, db * 0.05f);
}

inline float gainToDb(float gain) noexcept {
    // Symmetric with dbToGain: everything at/below the -72 dB floor is floor.
    return gain <= 0.000251189f ? kSilenceDb : 20.0f * std::log10(gain);
}

// 12-TET, A4 = 440 Hz at MIDI 69. Fractional notes are legal (detune, MPE).
inline float midiToHz(float note) noexcept {
    return 440.0f * std::exp2((note - 69.0f) * (1.0f / 12.0f));
}

inline float hzToMidi(float hz) noexcept {
    return 69.0f + 12.0f * std::log2(hz / 440.0f);
}

inline float clamp01(float v) noexcept { return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v); }

template <typename T>
inline T clamp(T v, T lo, T hi) noexcept { return v < lo ? lo : (v > hi ? hi : v); }

inline float lerp(float a, float b, float t) noexcept { return a + (b - a) * t; }

// Equal-power crossfade gains for t in [0,1] (contract fade shape).
inline void equalPower(float t, float& gainA, float& gainB) noexcept {
    const float theta = clamp01(t) * (kPi * 0.5f);
    gainA = std::cos(theta);
    gainB = std::sin(theta);
}

// Constant-power pan, -3 dB center (contract pan law). pan in [-1, 1].
inline void panGains(float pan, float& left, float& right) noexcept {
    const float theta = (clamp(pan, -1.0f, 1.0f) + 1.0f) * (kPi * 0.25f);
    left  = std::cos(theta);
    right = std::sin(theta);
}

} // namespace daw::dsp
