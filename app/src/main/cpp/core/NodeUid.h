#pragma once

#include <cstdint>
#include <string_view>

// Stable identity types shared by the edit model, builder and realtime graph.
//
// NodeUid: 64-bit identity of a graph node, derived deterministically from the
// edit-model entity id so a rebuilt graph re-derives the same uid and state
// migration can match old->new nodes (CONTRACTS.md seam 3).
//
// ParamKeyHash: FNV-1a-32 of a device's stable semantic parameter key
// ("filter.cutoff"). String keys are the persisted form; hashes travel in
// messages and tables (CONTRACTS.md seam 6). Collisions within one device
// type are rejected by a hostside assertion at registry construction.

namespace daw {

using NodeUid      = uint64_t;   // 0 = engine-global / none
using ConfigHash   = uint64_t;   // DSP-topology-relevant config digest
using ParamKeyHash = uint32_t;

inline constexpr uint32_t fnv1a32(std::string_view s) noexcept {
    uint32_t h = 0x811c9dc5u;
    for (const char c : s) {
        h ^= static_cast<uint8_t>(c);
        h *= 0x01000193u;
    }
    return h;
}

inline constexpr uint64_t fnv1a64(std::string_view s) noexcept {
    uint64_t h = 0xcbf29ce484222325ull;
    for (const char c : s) {
        h ^= static_cast<uint8_t>(c);
        h *= 0x100000001b3ull;
    }
    return h;
}

// Combine an entity-kind tag with the edit-model entity id string.
inline constexpr NodeUid makeNodeUid(std::string_view kind, std::string_view entityId) noexcept {
    uint64_t h = fnv1a64(kind);
    h ^= fnv1a64(entityId) + 0x9e3779b97f4a7c15ull + (h << 6) + (h >> 2);
    return h == 0 ? 1 : h;   // 0 is reserved for "engine-global"
}

inline constexpr ParamKeyHash paramKey(std::string_view key) noexcept {
    return fnv1a32(key);
}

} // namespace daw
