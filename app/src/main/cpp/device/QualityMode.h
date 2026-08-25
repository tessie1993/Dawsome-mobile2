#pragma once

#include <cstdint>

// QualityMode convention (blueprint D-quality; spec P1 §15): an ORDINARY
// enum param on devices that declare it - key "quality", descriptor flag
// isQualityMode = true - so preset recall, automation exclusion and the
// DegradationGovernor's forced-Eco ladder (M15) all ride the existing param
// machinery. Devices read their mode inside process() and choose algorithm
// variants; changing it is rt-safe by contract (no topology change).

namespace daw {

enum class QualityMode : uint8_t {
    Eco      = 0,
    Standard = 1,
    High     = 2,
};

inline constexpr const char* kQualityParamKey = "quality";

inline QualityMode qualityFromPlain(float plain) noexcept {
    if (plain < 0.5f) return QualityMode::Eco;
    if (plain < 1.5f) return QualityMode::Standard;
    return QualityMode::High;
}

} // namespace daw
