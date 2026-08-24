#pragma once

#include <array>
#include <cstddef>
#include "../core/SmoothedValue.h"

/**
 * High-performance 64x8 Routing & Sidechain Matrix.
 * Manages send levels and sidechain source mappings without dynamic memory allocation.
 */
class RoutingMatrix {
public:
    static constexpr size_t MAX_TRACKS = 64;
    static constexpr size_t MAX_RETURNS = 8;

    RoutingMatrix();
    ~RoutingMatrix() = default;

    void prepare(double sampleRate);
    void reset();

    void setSendLevel(size_t srcTrack, size_t returnIndex, float levelLinear);
    float getSendLevel(size_t srcTrack, size_t returnIndex) const;
    float getNextSmoothedSend(size_t srcTrack, size_t returnIndex);

    void setSidechainRoute(size_t destTrack, int srcTrack);
    int getSidechainSource(size_t destTrack) const;

private:
    std::array<std::array<SmoothedValue<float>, MAX_RETURNS>, MAX_TRACKS> sendSmoothers_;
    std::array<int, MAX_TRACKS> sidechainSources_;
};
