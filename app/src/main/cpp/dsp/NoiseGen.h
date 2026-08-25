#pragma once

#include "../core/RtRandom.h"

// Noise sources for synthesis. White noise from the engine RNG; pink noise
// via Paul Kellet's economy -3 dB/octave filter network (the standard cheap
// approximation: three one-pole stages, accurate within ~0.5 dB across the
// audio band - ample for musical noise).

namespace daw::dsp {

class NoiseGen {
public:
    explicit NoiseGen(uint64_t seed = 0x6a09e667f3bcc908ull) noexcept : rng_(seed) {}

    void reseed(uint64_t seed) noexcept { rng_.reseed(seed); }

    // Uniform white noise in [-1, 1).
    float white() noexcept { return rng_.nextBipolar(); }

    // Pink (-3 dB/oct) noise, roughly unit peak.
    float pink() noexcept {
        const float w = white();
        b0_ = 0.99765f * b0_ + w * 0.0990460f;
        b1_ = 0.96300f * b1_ + w * 0.2965164f;
        b2_ = 0.57000f * b2_ + w * 1.0526913f;
        return (b0_ + b1_ + b2_ + w * 0.1848f) * 0.25f;
    }

    void reset() noexcept { b0_ = b1_ = b2_ = 0.0f; }

private:
    daw::RtRandom rng_;
    float b0_ = 0.0f, b1_ = 0.0f, b2_ = 0.0f;
};

} // namespace daw::dsp
