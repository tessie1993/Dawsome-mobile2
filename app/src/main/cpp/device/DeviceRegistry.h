#pragma once

#include <cstring>
#include <memory>

#include "../core/NodeUid.h"
#include "../core/RtAssert.h"
#include "DeviceNode.h"

// Device type registry (blueprint device/; M3 platform core).
//
// DeviceTypeId is the FROZEN wire numbering carried by
// DeviceDeltaPayload.deviceType and mirrored in Kotlin EngineSync's
// deviceTypeWire map. Append-only - values persist inside project deltas
// and must never be renumbered. (This replaces the fragile Kotlin-ordinal
// numbering used before M3.)
//
// The registry starts EMPTY: instrument/effect factories register at their
// milestones (M4 first sound onward) via registerType(); the GraphBuilder
// skips unregistered types and counts them, so a project referencing
// not-yet-built devices still compiles a working mixer.
//
// registerType() runs the seam-6 build-time rule hostside: FNV-1a-32 of
// every semantic key in the type's descriptor set must be unique within
// the type - a collision refuses registration (and trips the debug assert).
// Non-RT throughout; the audio thread never touches this class.

namespace daw {

enum class DeviceTypeId : uint8_t {
    SubtractiveSynth = 0,
    WavetableSynth   = 1,
    FmSynth          = 2,
    Sampler          = 3,
    ElectricPiano    = 4,
    StringPad        = 5,
    DrumRack         = 6,
    ParametricEq     = 7,
    Compressor       = 8,
    Reverb           = 9,
    Delay            = 10,
    Distortion       = 11,
    Chorus           = 12,
    Limiter          = 13,
    // Append only. Never renumber.
};

class DeviceRegistry {
public:
    using Factory = std::unique_ptr<DeviceNode> (*)();

    struct TypeInfo {
        const char* name = nullptr;
        Factory factory = nullptr;
        const ParamDescriptor* params = nullptr;   // canonical descriptor set
        int paramCount = 0;
        bool isInstrument = false;   // safe static_cast to InstrumentNode
    };

    static DeviceRegistry& instance() {
        static DeviceRegistry r;
        return r;
    }

    // [non-RT init] Registers a type; refuses (false) on re-registration or
    // a semantic-key hash collision within the descriptor set.
    bool registerType(DeviceTypeId id, const char* name, Factory factory,
                      const ParamDescriptor* params, int paramCount,
                      bool isInstrument = false) {
        const auto idx = static_cast<size_t>(id);
        if (present_[idx] || factory == nullptr) {
            DAW_RT_ASSERT(false);
            return false;
        }
        if (!keysCollisionFree(params, paramCount)) {
            DAW_RT_ASSERT(false);
            return false;
        }
        types_[idx] = {name, factory, params, paramCount, isInstrument};
        present_[idx] = true;
        return true;
    }

    // [builder] Null when the type's milestone hasn't landed yet.
    std::unique_ptr<DeviceNode> create(uint8_t typeId) const {
        if (!present_[typeId]) return nullptr;
        return types_[typeId].factory();
    }

    const TypeInfo* info(uint8_t typeId) const {
        return present_[typeId] ? &types_[typeId] : nullptr;
    }

    bool isRegistered(uint8_t typeId) const { return present_[typeId]; }

    // Seam-6 build-time rule, hostside-runnable: FNV-1a-32 uniqueness of the
    // semantic keys within one device type.
    static bool keysCollisionFree(const ParamDescriptor* params, int count) {
        for (int i = 0; i < count; ++i) {
            for (int j = i + 1; j < count; ++j) {
                if (fnv1a32(params[i].key) == fnv1a32(params[j].key) &&
                    std::strcmp(params[i].key, params[j].key) != 0)
                    return false;
                if (std::strcmp(params[i].key, params[j].key) == 0)
                    return false;                  // duplicate key is also a bug
            }
        }
        return true;
    }

private:
    DeviceRegistry() = default;
    TypeInfo types_[256]{};
    bool present_[256]{};
};

// Registers every built-in landed so far (RegisterBuiltins.cpp); idempotent,
// called once at engine construction.
void registerBuiltinDevices();

} // namespace daw
