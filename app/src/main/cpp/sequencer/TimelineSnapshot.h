#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

#include "../core/NodeUid.h"

// Immutable, RT-readable compile of the timeline (CONTRACTS.md seam 4;
// blueprint 2.3): built whole by the GraphBuilder from the EngineModel,
// offered through an OfferSlot, claimed by the audio thread at a block
// boundary, freed by the builder only after the epoch ack. The audio thread
// only ever READS this object - the heap it lives on was allocated
// builder-side and is frozen from offer() on.
//
// M1 scope: arrangement clip placements + note content, read through
// pointer+count spans. M4 adds per-track SESSION clip views (same ClipView
// shape; placement fields idle - the SessionPlayer's anchor provides the
// timeline position, contentLengthBeats the loop). The bounded scene
// window (D4) narrows this list when projects grow; automation lane groups
// and markers join with their evaluators. Compiles skip placements whose
// track or content is missing from the model - the seam-4 skew rule (skips
// are counted, convergence at the next swap).
//
// Storage discipline: all views point into flat stores owned by the same
// snapshot; stores are sized exactly and filled BEFORE views are built, so
// every pointer is stable for the artifact's lifetime.

namespace daw {

struct SnapshotNote {
    uint32_t id = 0;
    uint16_t pitch = 60;
    uint16_t velocity = 100;
    double   startBeat = 0.0;      // content-local
    double   lengthBeats = 0.0;
};

struct NoteSpan {
    const SnapshotNote* data = nullptr;
    size_t count = 0;
    const SnapshotNote* begin() const noexcept { return data; }
    const SnapshotNote* end() const noexcept { return data + count; }
    bool empty() const noexcept { return count == 0; }
};

// One placed arrangement clip with its (shared) content resolved.
struct ClipView {
    NodeUid clipUid = 0;
    NodeUid trackUid = 0;
    NodeUid contentUid = 0;
    double  startBeat = 0.0;        // timeline placement
    double  lengthBeats = 0.0;      // placed length
    double  contentLengthBeats = 4.0;  // content loop length
    bool    looping = true;
    const SnapshotNote* notes = nullptr;   // sorted by startBeat, content-local
    uint32_t noteCount = 0;

    // Notes STARTING in [fromBeat, toBeat), content-local. Note-off timing is
    // the scheduler's job (sounding-note table), not the span's.
    NoteSpan notesInRange(double fromBeat, double toBeat) const noexcept {
        if (notes == nullptr || noteCount == 0 || toBeat <= fromBeat) return {};
        size_t lo = lowerBound(fromBeat);
        size_t hi = lowerBound(toBeat);
        return {notes + lo, hi - lo};
    }

private:
    // First index with startBeat >= b.
    size_t lowerBound(double b) const noexcept {
        size_t lo = 0, hi = noteCount;
        while (lo < hi) {
            const size_t mid = (lo + hi) / 2;
            if (notes[mid].startBeat < b) lo = mid + 1; else hi = mid;
        }
        return lo;
    }
};

struct TrackTimeline {
    NodeUid trackUid = 0;
    uint8_t trackType = 0;
    const ClipView* clips = nullptr;   // arrangement clips sorted by startBeat
    uint32_t clipCount = 0;
    const ClipView* sessionClips = nullptr;   // session slots, slot order
    uint32_t sessionClipCount = 0;

    const ClipView* sessionClipByUid(NodeUid uid) const noexcept {
        for (uint32_t i = 0; i < sessionClipCount; ++i)
            if (sessionClips[i].clipUid == uid) return &sessionClips[i];
        return nullptr;
    }
};

struct TimelineSnapshot {
    uint64_t epoch = 0;                // OfferSlot contract (builder-assigned)
    uint32_t builtFromEditSeq = 0;     // ordering rule (blueprint 2.2)
    uint32_t tempoMapRev = 0;          // map revision at build (stale detect)

    std::vector<SnapshotNote> noteStore;
    std::vector<ClipView>     clipStore;
    std::vector<TrackTimeline> tracks; // ordered by the model's track order

    const TrackTimeline* trackByUid(NodeUid uid) const noexcept {
        for (const TrackTimeline& t : tracks)
            if (t.trackUid == uid) return &t;
        return nullptr;
    }
};

} // namespace daw
