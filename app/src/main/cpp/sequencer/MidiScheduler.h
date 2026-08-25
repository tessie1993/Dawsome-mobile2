#pragma once

#include <algorithm>
#include <cmath>
#include <cstdint>

#include "../core/EngineConfig.h"
#include "../core/FixedVector.h"
#include "../core/MidiEvent.h"
#include "TempoMap.h"
#include "TimelineSnapshot.h"
#include "TransportEngine.h"

// Sample-accurate MIDI scheduling from the installed TimelineSnapshot
// (blueprint sequencer/MidiScheduler). Entirely [RT], allocation-free.
//
// Statelessness rule: everything positional - which notes start in a span,
// the loop-pass index, (later) the probability seed (clipUid, quantizedPos,
// loopPassIndex) - is DERIVED from the TransportSpan and the clip geometry,
// never accumulated. The only state is the sounding-note table, which owes
// the stuck-note guarantees:
//   - note ends inside a span      -> OFF at its mapped sample offset
//   - stop / seek / loop wrap      -> OFFs at the span boundary
//   - timeline snapshot swap       -> synthetic OFFs for sounding notes
//                                     absent from the new snapshot (seam 4)
//   - sounding table full          -> the ON is refused + counted (never an
//                                     untracked voice)
// Loop passes give notes fresh INSTANCE ids (contentId ^ pass hash) so a
// retrigger never collides with its own previous pass downstream; synthetic
// reconciliation still matches by content id.
//
// Per block: beginBlock() -> onTimelineSwap (if a claim happened) ->
// scheduleSpan per transport span. Output is one flat event pool plus
// per-track segments, each segment sorted by (offset, OFF-before-ON);
// instruments consume them from M4 (until then AudioEngine just counts).
// MidiClipPlayer (comp regions, MPE state, probability) grows out of this
// at M7 - the stateful parts belong there, not here.

namespace daw {

class MidiScheduler {
public:
    struct TrackEvents {
        NodeUid  trackUid = 0;
        uint32_t first = 0;      // index into events()
        uint32_t count = 0;
    };

    static constexpr int kEventCap    = 2048;
    static constexpr int kSoundingCap = 512;
    static constexpr int kSegmentCap  = kMaxTracks * 6;

    // ---- per block [RT] -----------------------------------------------------

    void beginBlock() noexcept {
        pool_.clear();
        segments_.clear();
    }

    // Synthetic OFFs (offset 0) for sounding notes whose clip or content
    // note vanished from the newly installed snapshot.
    void onTimelineSwap(const TimelineSnapshot* t) noexcept {
        const size_t rangeStart = pool_.size();
        for (size_t i = 0; i < sounding_.size();) {
            const Sounding& s = sounding_[i];
            if (noteAlive(t, s)) {
                ++i;
                continue;
            }
            emitOff(s, 0);
            ++syntheticOffs_;
            sounding_.eraseUnordered(i);
        }
        closeRange(rangeStart);
    }

    void scheduleSpan(const TimelineSnapshot* timeline, const TransportSpan& span,
                      const TempoMap& map, bool playing) noexcept {
        const size_t rangeStart = pool_.size();

        if (!playing) {
            flushSounding(span.offsetFrames);
            expectedNextSample_ = -1;
            closeRange(rangeStart);
            return;
        }
        // Loop wrap or seek discontinuity: cut everything at the boundary.
        if (span.wrapped ||
            (expectedNextSample_ >= 0 && span.startSample != expectedNextSample_)) {
            flushSounding(span.offsetFrames);
        }
        expectedNextSample_ = span.startSample + span.frames;

        if (span.endBeat > span.startBeat) {
            scheduleOffs(span, map);
            if (timeline != nullptr) scheduleOns(*timeline, span, map);
        }
        closeRange(rangeStart);
    }

    // Cut every sounding note at the given block offset (panic path).
    void allNotesOff(int sampleOffset) noexcept {
        const size_t rangeStart = pool_.size();
        flushSounding(sampleOffset);
        closeRange(rangeStart);
    }

    // ---- output [RT, valid until the next beginBlock] -----------------------

    const FixedVector<MidiEvent, kEventCap>& events() const noexcept { return pool_; }
    const FixedVector<TrackEvents, kSegmentCap>& segments() const noexcept { return segments_; }

    // ---- diagnostics [RT-owned] ---------------------------------------------
    uint32_t scheduledOns() const noexcept { return scheduledOns_; }
    uint32_t scheduledOffs() const noexcept { return scheduledOffs_; }
    uint32_t syntheticOffs() const noexcept { return syntheticOffs_; }
    uint32_t overflowDrops() const noexcept { return overflowDrops_; }
    size_t   soundingCount() const noexcept { return sounding_.size(); }

private:
    struct Sounding {
        NodeUid  trackUid = 0;
        NodeUid  clipUid = 0;
        uint32_t instanceId = 0;   // what downstream voices key on
        uint32_t contentId = 0;    // what snapshot reconciliation keys on
        uint16_t pitch = 60;
        double   endBeat = 0.0;    // absolute timeline beat
    };

    static uint32_t instanceIdFor(uint32_t contentId, int64_t pass) noexcept {
        return contentId ^ static_cast<uint32_t>(
            static_cast<uint64_t>(pass) * 0x9E3779B9ull);
    }

    static bool noteAlive(const TimelineSnapshot* t, const Sounding& s) noexcept {
        if (t == nullptr) return false;
        for (const TrackTimeline& track : t->tracks) {
            for (uint32_t c = 0; c < track.clipCount; ++c) {
                const ClipView& clip = track.clips[c];
                if (clip.clipUid != s.clipUid) continue;
                for (uint32_t n = 0; n < clip.noteCount; ++n)
                    if (clip.notes[n].id == s.contentId) return true;
                return false;      // clip found, note gone
            }
        }
        return false;              // clip gone
    }

    static int32_t offsetFor(int64_t absSample, const TransportSpan& span) noexcept {
        int64_t rel = absSample - span.startSample;
        if (rel < 0) rel = 0;
        if (rel >= span.frames) rel = span.frames > 0 ? span.frames - 1 : 0;
        return static_cast<int32_t>(rel) + span.offsetFrames;
    }

    void scheduleOffs(const TransportSpan& span, const TempoMap& map) noexcept {
        for (size_t i = 0; i < sounding_.size();) {
            const Sounding& s = sounding_[i];
            if (s.endBeat >= span.startBeat && s.endBeat < span.endBeat) {
                emitOff(s, offsetFor(map.sampleAt(s.endBeat), span));
                ++scheduledOffs_;
                sounding_.eraseUnordered(i);
            } else {
                ++i;
            }
        }
    }

    void scheduleOns(const TimelineSnapshot& timeline, const TransportSpan& span,
                     const TempoMap& map) noexcept {
        for (const TrackTimeline& track : timeline.tracks) {
            for (uint32_t c = 0; c < track.clipCount; ++c) {
                const ClipView& clip = track.clips[c];
                if (clip.startBeat >= span.endBeat) break;   // sorted by placement
                const double clipEnd = clip.startBeat + clip.lengthBeats;
                if (clipEnd <= span.startBeat) continue;

                const double winFrom = span.startBeat > clip.startBeat
                                           ? span.startBeat : clip.startBeat;
                const double winTo = span.endBeat < clipEnd ? span.endBeat : clipEnd;
                if (winTo <= winFrom) continue;

                const double len =
                    clip.contentLengthBeats > 0.0 ? clip.contentLengthBeats : 4.0;
                const double localFrom = winFrom - clip.startBeat;
                const double localTo = winTo - clip.startBeat;

                if (!clip.looping) {
                    if (localFrom >= len) continue;          // past one-shot content
                    scheduleWindow(track, clip, localFrom,
                                   localTo < len ? localTo : len,
                                   clip.startBeat, 0, clipEnd, span, map);
                } else {
                    int64_t pass = static_cast<int64_t>(std::floor(localFrom / len));
                    if (pass < 0) pass = 0;
                    for (; static_cast<double>(pass) * len < localTo; ++pass) {
                        const double passStart = static_cast<double>(pass) * len;
                        const double pf = localFrom > passStart ? localFrom - passStart : 0.0;
                        const double pt = (localTo - passStart) < len ? (localTo - passStart) : len;
                        if (pt <= pf) continue;
                        scheduleWindow(track, clip, pf, pt,
                                       clip.startBeat + passStart, pass, clipEnd,
                                       span, map);
                    }
                }
            }
        }
    }

    void scheduleWindow(const TrackTimeline& track, const ClipView& clip,
                        double localFrom, double localTo, double absBase,
                        int64_t pass, double clipEnd,
                        const TransportSpan& span, const TempoMap& map) noexcept {
        const NoteSpan notes = clip.notesInRange(localFrom, localTo);
        for (const SnapshotNote& n : notes) {
            if (n.lengthBeats <= 0.0) continue;
            const double absStart = absBase + n.startBeat;
            double absEnd = absStart + n.lengthBeats;
            if (absEnd > clipEnd) absEnd = clipEnd;          // placement cuts the tail
            if (absEnd <= absStart) continue;
            // Admission reserves pool space for this note's ON *and* one OFF
            // slot per sounding note (this one included) - the invariant
            // `capacity - poolSize >= soundingCount` makes emitOff() below
            // infallible, the pool-level mirror of EventRing's reserved-OFF
            // rule. Refused = counted, never an untracked voice.
            if (sounding_.full() ||
                pool_.size() + 2 + sounding_.size() > size_t(kEventCap)) {
                ++overflowDrops_;
                continue;
            }
            const uint32_t instanceId = instanceIdFor(n.id, pass);
            MidiEvent e;
            e.trackUid = track.trackUid;
            e.noteId = instanceId;
            e.sampleOffset = offsetFor(map.sampleAt(absStart), span);
            e.pitch = n.pitch;
            e.type = static_cast<uint8_t>(MidiEventType::NoteOn);
            e.velocity = static_cast<uint8_t>(n.velocity);
            pool_.push_back(e);
            ++scheduledOns_;

            Sounding s;
            s.trackUid = track.trackUid;
            s.clipUid = clip.clipUid;
            s.instanceId = instanceId;
            s.contentId = n.id;
            s.pitch = n.pitch;
            s.endBeat = absEnd;
            sounding_.push_back(s);
        }
    }

    void emitOff(const Sounding& s, int32_t offset) noexcept {
        // Infallible while the admission invariant holds (see scheduleWindow);
        // the guard is release-build belt only.
        DAW_RT_ASSERT(!pool_.full());
        if (pool_.full()) return;
        MidiEvent e;
        e.trackUid = s.trackUid;
        e.noteId = s.instanceId;
        e.sampleOffset = offset;
        e.pitch = s.pitch;
        e.type = static_cast<uint8_t>(MidiEventType::NoteOff);
        e.velocity = 0;
        pool_.push_back(e);
    }

    void flushSounding(int sampleOffset) noexcept {
        for (const Sounding& s : sounding_) {
            emitOff(s, sampleOffset);
            ++scheduledOffs_;
        }
        sounding_.clear();
    }

    // Sort the just-appended range by (track, offset, OFF-before-ON) and cut
    // it into per-track segments.
    void closeRange(size_t rangeStart) noexcept {
        if (pool_.size() <= rangeStart) return;
        MidiEvent* first = pool_.begin() + rangeStart;
        MidiEvent* last = pool_.end();
        std::sort(first, last, [](const MidiEvent& a, const MidiEvent& b) {
            if (a.trackUid != b.trackUid) return a.trackUid < b.trackUid;
            if (a.sampleOffset != b.sampleOffset) return a.sampleOffset < b.sampleOffset;
            return a.type < b.type;                          // Off (0) before On (1)
        });
        size_t runStart = rangeStart;
        for (size_t i = rangeStart + 1; i <= pool_.size(); ++i) {
            if (i == pool_.size() || pool_[i].trackUid != pool_[runStart].trackUid) {
                if (!segments_.full()) {
                    segments_.push_back({pool_[runStart].trackUid,
                                         static_cast<uint32_t>(runStart),
                                         static_cast<uint32_t>(i - runStart)});
                }
                runStart = i;
            }
        }
    }

    FixedVector<MidiEvent, kEventCap>       pool_;
    FixedVector<TrackEvents, kSegmentCap>   segments_;
    FixedVector<Sounding, kSoundingCap>     sounding_;
    int64_t expectedNextSample_ = -1;

    uint32_t scheduledOns_ = 0;
    uint32_t scheduledOffs_ = 0;
    uint32_t syntheticOffs_ = 0;
    uint32_t overflowDrops_ = 0;
};

} // namespace daw
