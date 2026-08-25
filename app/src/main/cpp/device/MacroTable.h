#pragma once

#include <cstdint>

#include "../core/EngineConfig.h"
#include "../core/NodeUid.h"
#include "../core/SmoothedValue.h"

// Macro system core (blueprint device/racks MacroTable; §5 layering - macros
// are the TOP layer of param resolution). A rack owns up to kMaxMacros
// knobs; each knob maps to any number of member-device params with a plain
// range per mapping. Setting a macro (its own "macro.N" param on the rack)
// expands through an apply callback - in the graph that callback is the
// installed resolver, so macro moves ride the exact same machinery as
// direct param moves and land post-smoothing on each target device.
//
// [RT]: fixed capacity, no allocation; expansion is bounded by mapping
// count. Mapping edits are builder-side (rack rebuild or preset load).

namespace daw {

struct MacroMapping {
    NodeUid      targetUid = 0;     // member device
    ParamKeyHash targetKey = 0;
    float        minPlain = 0.0f;   // macro 0 -> this
    float        maxPlain = 1.0f;   // macro 1 -> this (inverted ranges legal)
};

class MacroTable {
public:
    static constexpr int kMappingCap = 64;   // total across all macros

    // ---- builder / preset ---------------------------------------------------
    bool addMapping(int macroIndex, const MacroMapping& m) noexcept {
        if (macroIndex < 0 || macroIndex >= kMaxMacros) return false;
        if (count_ >= kMappingCap || m.targetUid == 0) return false;
        macroOf_[count_] = static_cast<uint8_t>(macroIndex);
        mappings_[count_] = m;
        ++count_;
        return true;
    }
    void clearMappings() noexcept { count_ = 0; }
    int  mappingCount() const noexcept { return count_; }

    // ---- audio thread -------------------------------------------------------

    void setMacro(int macroIndex, float value01) noexcept {
        if (macroIndex < 0 || macroIndex >= kMaxMacros) return;
        values_[macroIndex] = value01 < 0.0f ? 0.0f : (value01 > 1.0f ? 1.0f : value01);
    }

    float macroValue(int macroIndex) const noexcept {
        return macroIndex >= 0 && macroIndex < kMaxMacros ? values_[macroIndex] : 0.0f;
    }

    // Expand one macro's mappings through `apply(uid, key, plain)`. In the
    // graph, apply = ParamResolver::apply, so targets smooth normally.
    template <typename ApplyFn>
    void expandMacro(int macroIndex, ApplyFn&& apply) const noexcept {
        if (macroIndex < 0 || macroIndex >= kMaxMacros) return;
        const float t = values_[macroIndex];
        for (int i = 0; i < count_; ++i) {
            if (macroOf_[i] != macroIndex) continue;
            const MacroMapping& m = mappings_[i];
            apply(m.targetUid, m.targetKey, m.minPlain + (m.maxPlain - m.minPlain) * t);
        }
    }

private:
    MacroMapping mappings_[kMappingCap];
    uint8_t      macroOf_[kMappingCap]{};
    int          count_ = 0;
    float        values_[kMaxMacros]{};
};

} // namespace daw
