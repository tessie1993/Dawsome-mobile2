#pragma once

#include "EngineMessage.h"
#include "SpscRing.h"

// Lossless event channel: one per producer thread, audio thread consumes
// (CONTRACTS.md seam 2). Wraps SpscRing<EngineMessage> with the stuck-note
// guarantee:
//
//   A note-ON is admitted only if a slot for its eventual note-OFF can be
//   guaranteed. Admission reserves that slot (producer-local reservation
//   counter); the matching OFF consumes the reservation and is therefore
//   never refused. Everything else is admitted against the unreserved
//   remainder, so ordinary traffic can never eat the space an OFF needs.
//
// Refusal of a note-on (or any message) under pressure is reported by the
// caller as a System/Panic to the engine, which executes all-notes-off -
// audible but safe, never a stuck note.

namespace daw {

template <size_t Capacity = kEventRingCap>
class EventRing {
public:
    // Producer thread only. Note-ONs (family Note, op On) reserve an OFF slot.
    // Note-OFFs consume a reservation and always succeed while the contract
    // is respected (an unmatched OFF falls back to a normal push).
    bool tryPush(const EngineMessage& m) noexcept {
        const bool isNoteOn  = m.family == MsgFamily::Note &&
                               m.op == static_cast<uint8_t>(NoteOp::On);
        const bool isNoteOff = m.family == MsgFamily::Note &&
                               m.op == static_cast<uint8_t>(NoteOp::Off);

        if (isNoteOff && reservedOffs_ > 0) {
            // Space was reserved at ON-admission: cannot legitimately fail.
            const bool ok = ring_.tryPush(m);
            DAW_RT_ASSERT(ok);
            if (ok) --reservedOffs_;
            return ok;
        }

        const size_t needed = isNoteOn ? 2u : 1u;   // self + reserved OFF
        if (ring_.freeSlots() < needed + reservedOffs_) return false;

        if (!ring_.tryPush(m)) return false;        // unreachable given the check
        if (isNoteOn) ++reservedOffs_;
        return true;
    }

    // Consumer (audio thread) only.
    bool tryPop(EngineMessage& out) noexcept { return ring_.tryPop(out); }
    size_t pending() const noexcept { return ring_.pending(); }

private:
    SpscRing<EngineMessage, Capacity> ring_;
    size_t reservedOffs_ = 0;   // producer-thread-local bookkeeping
};

} // namespace daw
