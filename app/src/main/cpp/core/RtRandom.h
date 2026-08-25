#pragma once

#include <cstdint>

#include "NodeUid.h"

// Deterministic, allocation-free RNG for musical chance (note probability,
// humanize, random modulation). xorshift64* core - fast, decent spectral
// quality, one u64 of state.
//
// Seeding policy (CONTRACTS.md cross-cutting): musical randomness is seeded
// from (clipUid, quantized position, loopPassIndex) so loop passes differ
// musically while offline render replays identical pass indices and matches
// audition exactly (blueprint 2.5 determinism).

namespace daw {

class RtRandom {
public:
    explicit RtRandom(uint64_t seed = 0x9e3779b97f4a7c15ull) noexcept { reseed(seed); }

    void reseed(uint64_t seed) noexcept {
        state_ = seed != 0 ? seed : 0x9e3779b97f4a7c15ull;   // xorshift state must be non-zero
    }

    uint64_t nextU64() noexcept {
        uint64_t x = state_;
        x ^= x >> 12;
        x ^= x << 25;
        x ^= x >> 27;
        state_ = x;
        return x * 0x2545f4914f6cdd1dull;
    }

    // Uniform [0, 1).
    float nextFloat01() noexcept {
        return static_cast<float>(nextU64() >> 40) * (1.0f / 16777216.0f);
    }

    // Uniform [-1, 1).
    float nextBipolar() noexcept { return nextFloat01() * 2.0f - 1.0f; }

    // True with probability p (0..1).
    bool chance(float p) noexcept { return nextFloat01() < p; }

    // Musical seed: identical inputs -> identical rolls, across RT and
    // offline render. Position is quantized by the caller (e.g. note start
    // in ticks) so float jitter can't change the seed.
    static uint64_t musicalSeed(NodeUid clipUid, int64_t quantizedPos,
                                uint32_t loopPassIndex) noexcept {
        uint64_t h = clipUid ^ 0x9e3779b97f4a7c15ull;
        h ^= static_cast<uint64_t>(quantizedPos) + 0x9e3779b97f4a7c15ull + (h << 6) + (h >> 2);
        h ^= (static_cast<uint64_t>(loopPassIndex) << 32) + 0x9e3779b97f4a7c15ull + (h << 6) + (h >> 2);
        return h != 0 ? h : 1;
    }

private:
    uint64_t state_;
};

} // namespace daw
