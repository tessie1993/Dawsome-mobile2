#pragma once

#include <cstdint>

#include "../core/NodeUid.h"

// Modulation matrix core (blueprint §5, device/ModMatrix): OFFSETS, never
// rewrites - modulation adds to the resolved base value at apply time and
// the base (model/automation) is never touched. This is the structural core
// (M3): slots + evaluation; instruments wire their internal sources (LFOs,
// envelopes, velocity, pressure, macros) from M4, and the full resolution
// layering (base <- automation <- clip envelopes <- modulation <- macros)
// assembles at the automation milestone.
//
// All [RT]-safe: fixed capacity, POD slots, no allocation. Source VALUES
// arrive per block as a caller-owned array indexed by ModSource.

namespace daw {

enum class ModSource : uint8_t {
    None = 0,
    Lfo1, Lfo2,
    ModEnv,
    Velocity,
    Pressure,        // channel/MPE
    Slide,           // MPE timbre
    PitchBend,
    Macro1, Macro2, Macro3, Macro4,
    Random,          // per-note random (seeded)
    Count,
};
inline constexpr int kModSourceCount = static_cast<int>(ModSource::Count);

struct ModSlot {
    ModSource    source = ModSource::None;
    ParamKeyHash targetKey = 0;   // semantic key within the OWNING device
    float        depth = 0.0f;    // -1..+1, scales the target's plain range
    bool         bipolar = false; // source treated as -1..1 instead of 0..1
};

class ModMatrix {
public:
    static constexpr int kMaxSlots = 32;

    // ---- builder / preset ----------------------------------------------------
    bool addSlot(const ModSlot& s) noexcept {
        if (count_ >= kMaxSlots || s.source == ModSource::None) return false;
        slots_[count_++] = s;
        return true;
    }
    void clear() noexcept { count_ = 0; }
    int  slotCount() const noexcept { return count_; }
    const ModSlot& slot(int i) const noexcept { return slots_[i]; }

    // ---- audio thread --------------------------------------------------------

    // Sum of offsets targeting `key`, in NORMALIZED units (-1..1 scale);
    // the device maps to plain range when applying. sources[] indexed by
    // ModSource, values 0..1 (bipolar slots remap to -1..1).
    float offsetFor(ParamKeyHash key, const float* sources) const noexcept {
        float sum = 0.0f;
        for (int i = 0; i < count_; ++i) {
            const ModSlot& s = slots_[i];
            if (s.targetKey != key) continue;
            float v = sources[static_cast<int>(s.source)];
            if (s.bipolar) v = v * 2.0f - 1.0f;
            sum += v * s.depth;
        }
        return sum;
    }

private:
    ModSlot slots_[kMaxSlots];
    int count_ = 0;
};

} // namespace daw
