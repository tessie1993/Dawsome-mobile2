#pragma once

#include <cstddef>
#include <cstdint>

#include "NodeUid.h"

// Block-local MIDI event stream types (the seam-1 `MidiEventSpan` that
// ProcessContext carries). Events are sample-accurate within the render
// block: `sampleOffset` indexes the block's buffers directly (the span's
// offsetFrames is already folded in). Streams are sorted by
// (sampleOffset, type) with OFF ordered before ON at the same offset so
// same-sample retriggers release the old voice first.

namespace daw {

enum class MidiEventType : uint8_t {
    NoteOff = 0,     // sorts before NoteOn at equal offsets
    NoteOn  = 1,
    // PolyPressure / PitchBend / Slide (MPE) join with the live-input path.
};

struct MidiEvent {
    NodeUid  trackUid = 0;
    uint32_t noteId = 0;         // stable content note id (or live-input id)
    int32_t  sampleOffset = 0;   // within the current render block
    uint16_t pitch = 60;         // MIDI 0..127
    uint8_t  type = 0;           // MidiEventType
    uint8_t  velocity = 100;     // 0..127 (release velocity for offs)
};
static_assert(sizeof(MidiEvent) == 24);

class MidiEventSpan {
public:
    MidiEventSpan() = default;
    MidiEventSpan(const MidiEvent* data, size_t count) : data_(data), count_(count) {}
    const MidiEvent* begin() const noexcept { return data_; }
    const MidiEvent* end() const noexcept { return data_ + count_; }
    size_t size() const noexcept { return count_; }
    bool empty() const noexcept { return count_ == 0; }

private:
    const MidiEvent* data_ = nullptr;
    size_t count_ = 0;
};

// One track's contiguous, (offset, OFF-before-ON)-sorted run inside a
// block's event pool - the scheduler's finalizeBlock() product and the
// graph's per-lane midiIn source. Lives in core so graph and sequencer
// share it without a sibling include.
struct MidiTrackRun {
    NodeUid  trackUid = 0;
    uint32_t first = 0;      // index into the block's event pool
    uint32_t count = 0;
};

} // namespace daw
