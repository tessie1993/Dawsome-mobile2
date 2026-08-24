#pragma once

#include <cstdint>

/**
 * Fixed-size Metering Frame pushed from real-time audio thread to telemetry queue.
 * Guarantees zero heap allocation and trivial copyability.
 */
struct MeterFrame {
    int32_t trackId{-1};
    float peakL{0.0f};
    float peakR{0.0f};
    float rmsL{0.0f};
    float rmsR{0.0f};
    float truePeak{0.0f};
    float gainReductionDb{0.0f};
    bool isClipping{false};
};
