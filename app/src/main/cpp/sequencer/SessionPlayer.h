#pragma once

#include <cmath>
#include <cstdint>

#include "../core/EngineConfig.h"
#include "../core/FixedVector.h"
#include "../core/NodeUid.h"
#include "TimelineSnapshot.h"

// Launch-minimal Session playback (blueprint sequencer/SessionPlayer +
// TrackPlaybackArbiter + LaunchQuantizer, M4 cut). Entirely [RT],
// allocation-free, owned by AudioEngine.
//
// Semantics (spec Part 2 §1.1 + researched Ableton launch model):
//   - The quantization grid is anchored to the ABSOLUTE song beat grid
//     (boundary = ceil(now / quantum) * quantum), never to when playback or
//     a clip started - launches after off-bar seeks stay musical.
//   - A track is either arrangement-played or SESSION-OWNED. Ownership
//     flips only AT a boundary (or immediately for return-to-arrangement /
//     stopped transport), so the arrangement keeps sounding until the
//     musical moment the session takes over (spec: launching stops the
//     arrangement clip on that track only).
//   - StopSlot silences the track's session playback at the boundary but
//     the track STAYS session-owned (silent) until ReturnTrack/ReturnAll -
//     the Ableton "Back to Arrangement" model.
//   - Launch while the transport is stopped activates immediately and asks
//     the engine to start playing (the op handler returns true).
//   - Loop wrap: pending boundaries beyond the loop end can never arrive;
//     they re-anchor to the wrap point (the strongest boundary available).
//     Anchor = activation beat; clip-local position is derived statelessly
//     as (beat - anchor) mod contentLength, so seeks need no fixup.
//
// The scheduler consults sourceFor() per track: owned + clip -> schedule
// that session clip (loop from anchor); owned + no clip -> silence; not
// owned -> arrangement. Activation flushes are handed back to the caller
// through a duck-typed callback (flush(trackUid, blockOffset)) so this
// header stays independent of MidiScheduler.

namespace daw {

struct SessionSource {
    bool    owned = false;      // session governs this track's playback
    NodeUid clipUid = 0;        // 0 while owned = silent slot
    double  anchorBeat = 0.0;   // absolute beat the active clip started
};

class SessionPlayer {
public:
    static constexpr int kMaxSplitsPerSpan = 4;   // boundary splits per span (guard)
    static constexpr double kBoundaryEps = 1e-9;

    enum class QuantumMode : uint32_t { None = 0, Bar = 1, FixedBeats = 2 };

    // ---- ops [RT, from the message drain] -----------------------------------

    // Returns true when the transport should start (launch while stopped).
    bool launch(NodeUid trackUid, NodeUid clipUid, double nowBeat,
                bool playing, double barBeats) noexcept {
        TrackState* row = rowFor(trackUid);
        if (row == nullptr) return false;                 // rows exhausted (counted)
        if (!playing) {
            row->owned = true;
            row->activeClip = clipUid;
            row->anchorBeat = nowBeat;
            row->pendingClip = 0;
            row->hasPending = false;
            row->stopPending = false;
            return true;
        }
        row->pendingClip = clipUid;
        row->stopPending = false;
        row->hasPending = true;
        row->boundaryBeat = nextBoundary(nowBeat, barBeats);
        return false;
    }

    void stopSlot(NodeUid trackUid, double nowBeat, bool playing,
                  double barBeats) noexcept {
        TrackState* row = rowFor(trackUid);
        if (row == nullptr) return;
        if (!playing) {
            row->owned = true;
            row->activeClip = 0;
            row->pendingClip = 0;
            row->hasPending = false;
            row->stopPending = false;
            return;
        }
        row->pendingClip = 0;
        row->stopPending = true;
        row->hasPending = true;
        row->boundaryBeat = nextBoundary(nowBeat, barBeats);
    }

    // Immediate back-to-arrangement; flush(trackUid, blockOffset) cuts the
    // session notes that were sounding.
    template <typename FlushFn>
    void returnTrack(NodeUid trackUid, int blockOffset, FlushFn&& flush) noexcept {
        for (size_t i = 0; i < rows_.size(); ++i) {
            if (rows_[i].trackUid != trackUid) continue;
            if (rows_[i].owned) flush(rows_[i].trackUid, blockOffset);
            rows_.eraseUnordered(i);
            return;
        }
    }

    template <typename FlushFn>
    void returnAll(int blockOffset, FlushFn&& flush) noexcept {
        for (TrackState& row : rows_)
            if (row.owned) flush(row.trackUid, blockOffset);
        rows_.clear();
    }

    void setQuantum(QuantumMode mode, double beats) noexcept {
        quantumMode_ = mode;
        quantumBeats_ = beats > 0.0 ? beats : 0.0;
    }

    // ---- per span [RT, AudioEngine's span loop] -----------------------------

    // Loop wrap: unreachable pending boundaries re-anchor to the wrap start.
    void onLoopWrap(double wrapStartBeat) noexcept {
        for (TrackState& row : rows_)
            if (row.hasPending && row.boundaryBeat > wrapStartBeat)
                row.boundaryBeat = wrapStartBeat;
    }

    // Activate every pending whose boundary is at-or-before `beat`. The
    // callback cuts the previous source's sounding notes at blockOffset
    // (a no-op when that track had nothing sounding).
    template <typename FlushFn>
    void activateDueAt(double beat, int blockOffset, bool playing,
                       FlushFn&& flush) noexcept {
        for (TrackState& row : rows_) {
            if (!row.hasPending || row.boundaryBeat > beat + kBoundaryEps)
                continue;
            if (playing) flush(row.trackUid, blockOffset);
            row.owned = true;
            row.activeClip = row.stopPending ? 0 : row.pendingClip;
            row.anchorBeat = row.boundaryBeat;
            row.pendingClip = 0;
            row.hasPending = false;
            row.stopPending = false;
        }
    }

    // Earliest pending boundary STRICTLY inside (fromBeat, toBeat), or a
    // value >= toBeat when none - the span-split cut point.
    double nextBoundaryWithin(double fromBeat, double toBeat) const noexcept {
        double best = toBeat;
        for (const TrackState& row : rows_)
            if (row.hasPending && row.boundaryBeat > fromBeat + kBoundaryEps &&
                row.boundaryBeat < best)
                best = row.boundaryBeat;
        return best;
    }

    // ---- arbiter [RT, MidiScheduler] ----------------------------------------

    SessionSource sourceFor(NodeUid trackUid) const noexcept {
        for (const TrackState& row : rows_)
            if (row.trackUid == trackUid && row.owned)
                return {true, row.activeClip, row.anchorBeat};
        return {};
    }

    // ---- housekeeping [RT, at timeline swap] --------------------------------

    // Drop rows whose track left the project so stale uids never pin the
    // fixed row pool. Sounding-note cleanup is the scheduler's swap
    // reconciliation, not ours.
    void pruneAgainst(const TimelineSnapshot* t) noexcept {
        if (t == nullptr) return;
        for (size_t i = 0; i < rows_.size();) {
            if (t->trackByUid(rows_[i].trackUid) != nullptr) ++i;
            else rows_.eraseUnordered(i);
        }
    }

    // ---- diagnostics --------------------------------------------------------
    uint32_t rowExhaustions() const noexcept { return rowExhaustions_; }
    bool anySessionOwned() const noexcept {
        for (const TrackState& row : rows_)
            if (row.owned) return true;
        return false;
    }

private:
    struct TrackState {
        NodeUid trackUid = 0;
        NodeUid activeClip = 0;
        NodeUid pendingClip = 0;
        double  anchorBeat = 0.0;
        double  boundaryBeat = 0.0;
        bool    owned = false;
        bool    hasPending = false;
        bool    stopPending = false;
    };

    TrackState* rowFor(NodeUid trackUid) noexcept {
        for (TrackState& row : rows_)
            if (row.trackUid == trackUid) return &row;
        if (rows_.full()) {
            ++rowExhaustions_;
            return nullptr;
        }
        TrackState fresh;
        fresh.trackUid = trackUid;
        rows_.push_back(fresh);
        return &rows_[rows_.size() - 1];
    }

    // Absolute-grid boundary at-or-after now (equality launches this block).
    double nextBoundary(double nowBeat, double barBeats) const noexcept {
        double q = 0.0;
        switch (quantumMode_) {
            case QuantumMode::None:       q = 0.0; break;
            case QuantumMode::Bar:        q = barBeats; break;
            case QuantumMode::FixedBeats: q = quantumBeats_; break;
        }
        if (q <= 0.0) return nowBeat;                     // unquantized: now
        return std::ceil((nowBeat - kBoundaryEps) / q) * q;
    }

    FixedVector<TrackState, kMaxTracks> rows_;
    QuantumMode quantumMode_ = QuantumMode::Bar;
    double quantumBeats_ = 4.0;
    uint32_t rowExhaustions_ = 0;
};

} // namespace daw
