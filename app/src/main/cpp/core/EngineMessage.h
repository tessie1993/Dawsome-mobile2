#pragma once

#include <cstddef>
#include <cstdint>
#include <type_traits>

#include "NodeUid.h"

// The one realtime message POD (CONTRACTS.md seam 2, v1). Exactly 64 bytes,
// trivially copyable, layout frozen - any change bumps kMessageVersion and the
// contract first. Param addressing is always (nodeUid, paramKeyHash); dense
// indices never cross a thread boundary.

namespace daw {

inline constexpr uint16_t kMessageVersion = 1;

enum class MsgFamily : uint8_t {
    Transport = 0,
    Param     = 1,
    Note      = 2,
    Structure = 3,
    System    = 4,
};

enum class TransportOp : uint8_t {
    Play = 0, Stop, TogglePlay, RecordOn, RecordOff,
    SeekSample,        // samplePos
    SeekBeat,          // beat
    SetLoopRegion,     // v0 = startBeat, v1 = endBeat
    LoopOn, LoopOff,
    SetTempo,          // v0 = bpm (RT tempo-tail append)
    NudgeTempo,        // v0 = bpm delta
    SetTimeSig,        // a = (num << 16) | den
    MetronomeOn, MetronomeOff,
    SetTimebaseSource, // a = 0 internal, 1 link, 2 midi-clock-slave
};

enum class ParamOp : uint8_t {
    Move = 0,          // v0 = plain value (lands in ParamMoveTable)
    BlockSet,          // b = builder-owned bulk buffer, a = entry count
    Touch,             // automation touch begin (override latch)
    Release,           // return to automation
};

enum class NoteOp : uint8_t {
    On = 0,            // a = noteId, v0 = pitch (semitones, fractional ok), v1 = velocity 0..1
    Off,               // a = noteId, v0 = release velocity
    PolyPressure,      // a = noteId, v0 = pressure 0..1
    PitchBend,         // a = noteId (0 = channel-wide), v0 = semitones
    Slide,             // a = noteId, v0 = 0..1 (MPE timbre / CC74)
    AllNotesOff,       // targeted nodeUid, or 0 = every instrument
};

enum class StructureOp : uint8_t {
    // Reserved: structural edits flow Kotlin -> builder via StateCodec deltas,
    // not through realtime rings. Present so family numbering is stable.
    Reserved = 0,
};

enum class SystemOp : uint8_t {
    Panic = 0,         // ring overflow / stuck-note guard: all notes off everywhere
    RequestMeterFlush, // debugging aid
};

struct EngineMessage {
    MsgFamily family;        // offset 0
    uint8_t   op;            // 1   (cast of the per-family enum)
    uint16_t  flags;         // 2
    uint32_t  editSeq;       // 4   monotonic edit sequence (0 = not model-originated)
    NodeUid   nodeUid;       // 8   0 = engine-global
    ParamKeyHash paramKeyHash; // 16  0 = n/a
    uint32_t  a;             // 20  family-defined small payload
    int64_t   samplePos;     // 24  timeline sample position, -1 = n/a
    double    beat;          // 32  musical position, NaN = n/a
    double    v0;            // 40  primary value, plain units
    double    v1;            // 48  secondary value
    uint64_t  b;             // 56  family-defined / pointer-sized payload
};

static_assert(sizeof(EngineMessage) == 64, "seam-2 layout is frozen at 64 bytes");
static_assert(offsetof(EngineMessage, editSeq) == 4);
static_assert(offsetof(EngineMessage, nodeUid) == 8);
static_assert(offsetof(EngineMessage, paramKeyHash) == 16);
static_assert(offsetof(EngineMessage, samplePos) == 24);
static_assert(offsetof(EngineMessage, beat) == 32);
static_assert(offsetof(EngineMessage, v0) == 40);
static_assert(offsetof(EngineMessage, b) == 56);
static_assert(std::is_trivially_copyable_v<EngineMessage>);

} // namespace daw
