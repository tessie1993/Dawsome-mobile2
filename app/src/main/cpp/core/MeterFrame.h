#pragma once

#include <cstddef>
#include <cstdint>
#include <type_traits>

#include "NodeUid.h"

// Metering readback POD (blueprint 2.5): produced by MeterProbe /
// MasterNode on the audio thread into the MeterBus SPSC ring, polled by
// Kotlin at UI rate. Lossy by design - only the newest frames matter.

namespace daw {

struct MeterFrame {
    NodeUid  uid = 0;             // track/return/master node identity
    float    peakL = 0.0f;        // linear peak since last frame
    float    peakR = 0.0f;
    float    rmsL = 0.0f;         // linear RMS over the frame window
    float    rmsR = 0.0f;
    float    gainReductionDb = 0.0f;   // 0 = none (dynamics report negative)
    uint16_t flags = 0;           // bit 0: clipped, bit 1: truePeak over ceiling
    uint16_t seq = 0;             // per-uid wrap counter
};

static_assert(sizeof(MeterFrame) == 32);
static_assert(std::is_trivially_copyable_v<MeterFrame>);

inline constexpr uint16_t kMeterClipped      = 1u << 0;
inline constexpr uint16_t kMeterTruePeakOver = 1u << 1;

} // namespace daw
