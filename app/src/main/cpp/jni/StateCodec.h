#pragma once

#include <bit>
#include <cstddef>
#include <cstdint>
#include <cstring>

// Model-delta wire format (CONTRACTS.md seam 5, v1): the structural half of
// the Kotlin -> C++ seam. EngineSync serializes edit-model changes as framed
// entity deltas; the GraphBuilder thread applies them (ordered by editSeq) to
// its private EngineModel and rebuilds. Deltas are idempotent upserts /
// removes - replaying one is harmless.
//
// Frame layout is CONTRACT-ORDERED and therefore byte-packed - the u64 sits
// at offset 4, so headers are read/written field-wise, never as a struct
// overlay:
//
//     offset 0   uint16  version     (= kWireVersionState below; 1 today)
//     offset 2   uint16  entityKind  (EntityKind)
//     offset 4   uint64  entityId    (edit-model entity uid; 0 = project-global)
//     offset 12  uint32  byteLen     (payload bytes after this 16-byte header)
//     offset 16  payload             (per-entityKind POD, versioned with the wire)
//
// These frames normally travel inside a CommandCodec ModelDelta frame; the
// C++ -> Kotlin EngineEventBus (mustDeliver events + sequence numbers,
// arriving with the recording/export milestones) frames identically.
// Payload layouts per entityKind live in DeltaSchemas.h; byteLen == 0 means
// REMOVE the entity (idempotent, like the upserts).
//
// Version discipline matches CommandCodec: unknown version refuses the rest
// of the buffer (count + surface, never crash); unknown entityKind within a
// known version is skipped and counted - frames are length-delimited.
//
// Pure and host-compilable: no JNI, memcpy-only access.

namespace daw {

inline constexpr size_t kDeltaHeaderBytes = 16;

enum class EntityKind : uint16_t {
    Track       = 0,
    Clip        = 1,
    ClipContent = 2,
    Device      = 3,
    Rack        = 4,
    Routing     = 5,
    Scene       = 6,
    TempoMap    = 7,
    LaneGroup   = 8,
    Groove      = 9,
};

struct EntityDelta {
    uint16_t       entityKind;   // raw EntityKind value (always a known kind here)
    uint64_t       entityId;
    const uint8_t* payload;
    uint32_t       byteLen;
};

class StateCodec {
public:
    enum class Status : int32_t {
        Ok         = 0,
        BadVersion = 2,   // shares CommandCodec::Status numbering
        Truncated  = 3,
    };

    struct Result {
        Status   status = Status::Ok;
        uint32_t deltasConsumed = 0;
        uint32_t unknownKinds = 0;    // skipped forward-compatibly
        size_t   stopOffset = 0;
    };

    // Visitor: void onDelta(const EntityDelta&). Known kinds only; unknown
    // kinds are skipped and counted. Deltas are idempotent, so the visitor
    // never refuses - there is no backpressure on the builder path (the
    // builder owns application order by editSeq).
    template <typename Visitor>
    static Result decode(const uint8_t* data, size_t len, Visitor&& v) noexcept {
        Result r;
        size_t off = 0;
        while (off < len) {
            if (len - off < kDeltaHeaderBytes) {
                r.status = Status::Truncated;
                r.stopOffset = off;
                return r;
            }
            uint16_t version, kind;
            uint64_t entityId;
            uint32_t byteLen;
            std::memcpy(&version,  data + off + 0,  sizeof version);
            std::memcpy(&kind,     data + off + 2,  sizeof kind);
            std::memcpy(&entityId, data + off + 4,  sizeof entityId);
            std::memcpy(&byteLen,  data + off + 12, sizeof byteLen);

            if (version != kWireVersionState) {
                r.status = Status::BadVersion;
                r.stopOffset = off;
                return r;
            }
            const size_t payloadOff = off + kDeltaHeaderBytes;
            if (byteLen > len - payloadOff) {
                r.status = Status::Truncated;
                r.stopOffset = off;
                return r;
            }

            if (kind <= static_cast<uint16_t>(EntityKind::Groove)) {
                v.onDelta(EntityDelta{kind, entityId, data + payloadOff, byteLen});
                ++r.deltasConsumed;
            } else {
                ++r.unknownKinds;
            }

            off = payloadOff + byteLen;
            r.stopOffset = off;
        }
        return r;
    }

    // Encode-side helper (EngineSync's serializer mirrors this in Kotlin; the
    // C++ user is the outbound EngineEventBus). Returns bytes written, 0 if
    // the destination cannot hold the header.
    static size_t writeDeltaHeader(uint8_t* dst, size_t dstLen, EntityKind kind,
                                   uint64_t entityId, uint32_t payloadLen) noexcept {
        if (dstLen < kDeltaHeaderBytes) return 0;
        const uint16_t version = kWireVersionState;
        const uint16_t k = static_cast<uint16_t>(kind);
        std::memcpy(dst + 0,  &version,    sizeof version);
        std::memcpy(dst + 2,  &k,          sizeof k);
        std::memcpy(dst + 4,  &entityId,   sizeof entityId);
        std::memcpy(dst + 12, &payloadLen, sizeof payloadLen);
        return kDeltaHeaderBytes;
    }

private:
    // Same wire generation as the command side today; tracked separately so
    // either seam can rev without dragging the other.
    static constexpr uint16_t kWireVersionState = 1;
};

} // namespace daw
