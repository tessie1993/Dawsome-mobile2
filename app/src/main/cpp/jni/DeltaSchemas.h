#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>

// Per-entityKind payload layouts for StateCodec model deltas (seam 5).
// Kotlin EngineSync writes these; the GraphBuilder's EngineModel reads them.
// Same generation as the state wire (StateCodec kWireVersionState = 1);
// layouts are little-endian, natural-aligned, read via memcpy.
//
// Conventions:
//   - entityId in the delta header is the SAME makeNodeUid(kindString,
//     editModelId) used for param addressing and graph identity - one
//     identity end-to-end (blueprint §6). Kind strings: "track", "clip",
//     "content", "device", "scene" (WireProtocol mirrors them).
//   - byteLen == 0  =>  REMOVE the entity (idempotent, like upserts).
//   - Variable-length payloads are a fixed head + packed record arrays,
//     counts in the head. Records are themselves natural-aligned.
//   - Kinds without a schema yet (Rack, Routing, LaneGroup, Groove) are
//     counted-deferred by the model until their milestone (M2/M3).

namespace daw {

// ---- ModelDelta envelope ---------------------------------------------------
// A CommandCodec ModelDelta frame's payload is this 8-byte envelope followed
// by StateCodec entity-delta frames. editSeq stamps the whole bundle (one
// edit-model action => one bundle); the builder applies bundles in arrival
// order (the single engine-io producer preserves dispatch order) and stamps
// compiled artifacts with the highest editSeq seen (blueprint 2.2 ordering).
struct ModelDeltaEnvelope {
    uint32_t editSeq;
    uint32_t flags;          // reserved
};
inline constexpr size_t kModelDeltaEnvelopeBytes = 8;
static_assert(sizeof(ModelDeltaEnvelope) == kModelDeltaEnvelopeBytes);

// ---- Track (kind = Track, 20 bytes) ----------------------------------------
struct TrackDeltaPayload {
    uint8_t  trackType;      // 0 midi, 1 audio, 2 drum, 3 return, 4 master, 5 group
    uint8_t  flags;          // bit0 muted, bit1 soloed, bit2 armed, bit3 sessionOverride
    uint16_t order;          // arranger/mixer position
    float    volumeDb;
    float    pan;            // -1..+1
    float    sendA;          // 0..1
    float    sendB;
};
static_assert(sizeof(TrackDeltaPayload) == 20);

inline constexpr uint8_t kTrackFlagMuted           = 1u << 0;
inline constexpr uint8_t kTrackFlagSoloed          = 1u << 1;
inline constexpr uint8_t kTrackFlagArmed           = 1u << 2;
inline constexpr uint8_t kTrackFlagSessionOverride = 1u << 3;

// ---- Clip (kind = Clip, 40 bytes) ------------------------------------------
// One PLACEMENT (arrangement position or session slot) referencing shared
// ClipContent - the linked-clip split (spec P2 §1.2).
struct ClipDeltaPayload {
    uint64_t trackUid;
    uint64_t contentUid;
    double   startBeat;      // arrangement position; ignored for session clips
    double   lengthBeats;    // placed length (content loops underneath)
    int32_t  slotIndex;      // -1 = arrangement clip, >= 0 = session slot
    uint8_t  flags;          // bit0 looping
    uint8_t  pad0;
    uint16_t pad1;
};
static_assert(sizeof(ClipDeltaPayload) == 40);

inline constexpr uint8_t kClipFlagLooping = 1u << 0;

// ---- ClipContent (kind = ClipContent, 16-byte head + notes) ----------------
struct ClipContentDeltaHead {
    double   lengthBeats;    // content loop length
    uint32_t noteCount;
    uint32_t pad0;
};
static_assert(sizeof(ClipContentDeltaHead) == 16);

// Drum steps flatten to notes Kotlin-side (pad -> pitch table); the engine
// schedules one uniform record type.
struct NoteRecord {
    uint32_t id;             // fnv1a32 of the edit-model note id
    uint16_t pitch;          // MIDI 0..127
    uint16_t velocity;       // 0..127
    double   startBeat;      // content-local
    double   lengthBeats;
};
static_assert(sizeof(NoteRecord) == 24);

// ---- Device (kind = Device, 16-byte head + params) -------------------------
// Length-driven extension (backward-readable, no version bump): the head may
// be followed by N ParamValueRecords, N = (byteLen - 16) / 8. These are the
// device's current plain param values by semantic key hash - the model
// residency that lets rebuilt graphs bake params in (a table-only value can
// be reclaimed as "baked" without ever reaching the model).
struct DeviceDeltaPayload {
    uint64_t trackUid;
    uint8_t  deviceType;     // DeviceTypeId (frozen wire numbering)
    uint8_t  flags;          // bit0 enabled
    uint16_t order;          // chain position
    uint32_t pad0;           // keeps sizeof == 16 under 8-byte struct alignment
};
static_assert(sizeof(DeviceDeltaPayload) == 16);

inline constexpr uint8_t kDeviceFlagEnabled = 1u << 0;

struct ParamValueRecord {
    uint32_t keyHash;        // FNV-1a-32 of the semantic key
    float    plain;
};
static_assert(sizeof(ParamValueRecord) == 8);

// ---- ParamBlockSet (CommandCodec kind 1) -----------------------------------
// Payload = ModelDeltaEnvelope (editSeq stamps the whole set) followed by N
// ParamBlockEntry triples. Bulk atomic-intent sets (preset load, variation
// recall, full reconcile). The full table generation barrier lands with the
// presets milestone; until then entries apply sequentially (per-slot seqlock
// keeps each entry internally consistent - documented deferral).
struct ParamBlockEntry {
    uint64_t nodeUid;
    uint32_t keyHash;
    float    plain;
};
static_assert(sizeof(ParamBlockEntry) == 16);

// ---- Scene (kind = Scene, 8 bytes) -----------------------------------------
struct SceneDeltaPayload {
    int32_t  index;
    uint32_t flags;          // reserved
};
static_assert(sizeof(SceneDeltaPayload) == 8);

// ---- TempoMap (kind = TempoMap, entityId = 0, 8-byte head + events) --------
// The project's canonical tempo/meter lists; the builder compiles them into
// a TempoMapBase (densifying ramps when those arrive) and offers it.
struct TempoMapDeltaHead {
    uint32_t tempoCount;
    uint32_t sigCount;
};
static_assert(sizeof(TempoMapDeltaHead) == 8);

struct TempoEventRecord {
    double beat;
    double bpm;              // constant until the next event (ramps: M13 densify)
};
static_assert(sizeof(TempoEventRecord) == 16);

struct SigEventRecord {
    double   beat;           // bar-aligned
    uint16_t numerator;
    uint16_t denominator;
    uint32_t pad0;
};
static_assert(sizeof(SigEventRecord) == 16);

// ---- SampleRef (kind = SampleRef, 16-byte head + UTF-8 path) ---------------
// v1.2: entityId = the DEVICE uid the sample is assigned to. slot 0 for
// single-sample devices (SimpleSampler); the pad index for DrumRack. The
// path tail (byteLen - 16 bytes, not NUL-terminated) lets the builder load
// on a cache miss; fileId is the durable identity (Kotlin fnv1a64). One
// frame upserts ONE slot; fileId == 0 clears that slot; byteLen == 0
// removes every ref of the device (the uniform remove convention).
struct SampleRefDeltaHead {
    uint32_t slot;
    uint32_t pad0;
    uint64_t fileId;
};
static_assert(sizeof(SampleRefDeltaHead) == 16);

// ---- Preview (kind = Preview) ----------------------------------------------
// v1.2: entityId = fileId, payload = the UTF-8 path (no head struct). A
// fresh frame retriggers the audition from the top; entityId == 0 or
// byteLen == 0 stops it. Preview is a transient builder fact - it never
// touches the graph; the builder pins a handle and offers a PreviewClip
// to the RT PreviewPlayer (post-graph, never recorded, never metered).

// ---- helpers ---------------------------------------------------------------

// Safe unaligned-tolerant read of one record at byte offset; false if the
// span cannot hold it.
template <typename T>
inline bool readRecord(const uint8_t* payload, uint32_t byteLen,
                       size_t offset, T& out) noexcept {
    if (offset + sizeof(T) > byteLen) return false;
    std::memcpy(&out, payload + offset, sizeof(T));
    return true;
}

} // namespace daw
