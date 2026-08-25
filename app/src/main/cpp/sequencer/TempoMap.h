#pragma once

#include <cstdint>
#include <mutex>

#include "../core/EngineConfig.h"
#include "../core/FixedVector.h"
#include "../core/OfferSlot.h"
#include "../core/Seqlock.h"

// Musical time authority (CONTRACTS.md "TempoMap contract"; blueprint 2.5).
//
// Two layers, one precedence rule:
//
//   BASE  - immutable, builder-compiled piecewise-linear segments
//           {startBeat, startSample, samplesPerBeat}. All RT math is a
//           segment lookup + linear interpolation; smooth tempo ramps and
//           tempo-automation curves are DENSIFIED INTO SEGMENTS by the
//           builder offline (the Ardour lesson: keep the hot path
//           piecewise-constant, let the compiler of the map spend the
//           effort). Swapped whole via OfferSlot epoch mechanics on every
//           structure-shaped tempo edit.
//   TAIL  - fixed-capacity RT-appended events (live SetTempo, nudge, scene
//           tempo at quantized launch), each anchored {startBeat,
//           startSample} at the exact transport position where it took
//           effect - so the mapping is continuous at the splice by
//           construction. The tail SUPERSEDES the base from its first
//           event's position until a base rebuild consolidates it.
//
// Revision protocol: `rev` bumps on every tail append and every base claim;
// background readers snapshot {base, tail, rev} and stamp everything they
// derive with that rev - stale prefetches/proxies are detectable, never
// silently wrong. Base-claim retention rule: claiming a consolidated base
// re-appends tail events with rev > base.foldRev; claimBase never clears
// the whole tail. A full tail forces (builder-side) consolidation; until it
// lands, the newest event overwrites the last slot - two rapid-fire tempo
// sets within one consolidation window merge, mapping history stays intact.
//
// Threading:
//   [RT]      rtSetTempo / rtNudgeTempo / rtClaimOfferedBase / conversions -
//             audio thread only; lock-free, allocation-free.
//   [builder] offerBase / retiredBaseAcked / publishBackgroundBase - the
//             builder owns base allocations and frees a retired base only
//             after BOTH the RT epoch ack and replacing the background
//             pointer (background readers hold Snapshot::base only while
//             deriving one artifact - the builder defers frees one build
//             generation, which bounds that window).
//   [any]     snapshot() - mutex + seqlock; the RT thread never touches the
//             mutex, so no reader can ever block it.

namespace daw {

inline constexpr int kTempoBaseSegmentCap = 1024;  // densified curve headroom
inline constexpr int kTimeSigEventCap     = 128;

struct TempoSegment {
    double  startBeat = 0.0;        // beats are quarter notes throughout
    int64_t startSample = 0;
    double  samplesPerBeat = 0.0;   // > 0 always
};

struct TimeSigEvent {
    double   startBeat = 0.0;       // always on a bar boundary
    int32_t  barAtStart = 0;        // bar index (0-based) at startBeat
    uint16_t numerator = 4;
    uint16_t denominator = 4;
};

struct TempoMapBase {
    uint64_t epoch = 0;             // OfferSlot contract; 0 = the immortal default
    uint32_t foldRev = 0;           // highest tail rev folded into this base
    FixedVector<TempoSegment, kTempoBaseSegmentCap> segments;  // >= 1, ascending
    FixedVector<TimeSigEvent, kTimeSigEventCap>     timeSigs;  // >= 1
};

struct TempoTailEvent {
    double  startBeat = 0.0;
    int64_t startSample = 0;
    double  samplesPerBeat = 0.0;
    uint32_t rev = 0;               // for base-claim retention
    uint32_t pad = 0;
};

// Seqlock-published POD view of the tail (background readers).
struct TempoTailBlock {
    uint32_t count = 0;
    uint32_t rev = 0;               // the map revision as of this publish
    TempoTailEvent events[kTempoTailCap]{};
};

class TempoMap {
public:
    // ---- lifecycle [non-RT, before streams start] ---------------------------

    void prepare(double sampleRate, double defaultBpm = 120.0) noexcept {
        sampleRate_ = sampleRate;
        defaultBase_.segments.clear();
        defaultBase_.segments.push_back(
            {0.0, 0, sampleRate > 0.0 ? sampleRate * 60.0 / defaultBpm : 0.0});
        defaultBase_.timeSigs.clear();
        defaultBase_.timeSigs.push_back({0.0, 0, 4, 4});
        rtBase_ = &defaultBase_;
        tailCount_ = 0;
        rev_ = 0;
        publishTail();
        {
            std::lock_guard<std::mutex> lock(bgMutex_);
            bgBase_ = &defaultBase_;
        }
    }

    double sampleRate() const noexcept { return sampleRate_; }

    // ---- audio thread -------------------------------------------------------

    // Live tempo event anchored at the exact transport position it takes
    // effect. Continuity at the splice holds by construction: the anchor is
    // the position pair the transport is actually at.
    void rtSetTempo(double bpm, int64_t atSample, double atBeat) noexcept {
        if (bpm <= 0.0 || sampleRate_ <= 0.0) return;
        TempoTailEvent e;
        e.startBeat = atBeat;
        e.startSample = atSample;
        e.samplesPerBeat = sampleRate_ * 60.0 / bpm;
        e.rev = ++rev_;
        if (tailCount_ < kTempoTailCap) {
            tail_[tailCount_++] = e;
        } else {
            tail_[kTempoTailCap - 1] = e;   // merge until consolidation lands
        }
        publishTail();
    }

    void rtNudgeTempo(double bpmDelta, int64_t atSample, double atBeat) noexcept {
        rtSetTempo(bpmAt(atBeat) + bpmDelta, atSample, atBeat);
    }

    // Builder consolidation asked-for signal (feature 2 wires the nudge).
    bool rtTailNeedsConsolidation() const noexcept {
        return tailCount_ >= (kTempoTailCap * 3) / 4;
    }

    // Claim a consolidated base at a block boundary. Retains tail events
    // newer than the base's foldRev, then acks the retired base's epoch.
    bool rtClaimOfferedBase() noexcept {
        TempoMapBase* nb = baseOffer_.claim();
        if (nb == nullptr) return false;
        uint32_t kept = 0;
        for (uint32_t i = 0; i < tailCount_; ++i)
            if (tail_[i].rev > nb->foldRev) tail_[kept++] = tail_[i];
        tailCount_ = kept;
        const uint64_t retired = rtBase_->epoch;
        rtBase_ = nb;
        if (retired != 0) baseOffer_.ackRetired(retired);   // default base is immortal
        ++rev_;
        publishTail();
        return true;
    }

    // ---- conversions [RT; snapshots share the same math] --------------------

    double beatAt(int64_t sample) const noexcept {
        return beatAtImpl(*rtBase_, tail_, tailCount_, sample);
    }
    int64_t sampleAt(double beat) const noexcept {
        return sampleAtImpl(*rtBase_, tail_, tailCount_, beat);
    }
    double bpmAt(double beat) const noexcept {
        const double spb = samplesPerBeatAtImpl(*rtBase_, tail_, tailCount_, beat);
        return spb > 0.0 ? sampleRate_ * 60.0 / spb : 0.0;
    }

    // Bar|beat display + metronome accents + launch grids. Uses the base
    // time-sig list (live SetTimeSig rides TransportEngine until a base
    // rebuild makes it canonical).
    struct BarBeat { int32_t bar; double beatInBar; uint16_t num; uint16_t den; };
    BarBeat barBeatAt(double beat) const noexcept {
        const TempoMapBase& b = *rtBase_;
        const TimeSigEvent* sig = &b.timeSigs[0];
        for (size_t i = b.timeSigs.size(); i-- > 0;) {
            if (b.timeSigs[i].startBeat <= beat) { sig = &b.timeSigs[i]; break; }
        }
        const double beatsPerBar = 4.0 * double(sig->numerator) / double(sig->denominator);
        const double rel = (beat - sig->startBeat) / beatsPerBar;
        const double barF = rel < 0.0 ? 0.0 : rel;   // pre-song clamps to bar 0
        const int32_t barsIn = static_cast<int32_t>(barF);
        return {sig->barAtStart + barsIn,
                (barF - barsIn) * beatsPerBar,
                sig->numerator, sig->denominator};
    }

    uint32_t rev() const noexcept { return rev_; }

    // ---- builder thread -----------------------------------------------------

    // Offer a consolidated base; returns a replaced never-claimed offer for
    // immediate GC. The builder assigns epoch (monotonic, > 0) and foldRev.
    TempoMapBase* offerBase(TempoMapBase* built) noexcept {
        return baseOffer_.offer(built);
    }
    bool retiredBaseAcked(uint64_t epoch) const noexcept {
        return baseOffer_.retiredAcked(epoch);
    }
    // After observing the claim ack, point background readers at the new
    // base. The builder frees the retired base only after this call returns.
    void publishBackgroundBase(const TempoMapBase* base) noexcept {
        std::lock_guard<std::mutex> lock(bgMutex_);
        bgBase_ = base;
    }

    // ---- background readers [any non-RT thread] -----------------------------

    struct Snapshot {
        const TempoMapBase* base = nullptr;  // valid for one derivation (see header)
        TempoTailBlock tail;
        uint32_t rev = 0;                    // stamp derived artifacts with this
        double sampleRate = 0.0;

        double beatAt(int64_t sample) const noexcept {
            return beatAtImpl(*base, tail.events, tail.count, sample);
        }
        int64_t sampleAt(double beat) const noexcept {
            return sampleAtImpl(*base, tail.events, tail.count, beat);
        }
        double bpmAt(double beat) const noexcept {
            const double spb = samplesPerBeatAtImpl(*base, tail.events, tail.count, beat);
            return spb > 0.0 ? sampleRate * 60.0 / spb : 0.0;
        }
    };

    Snapshot snapshot() const noexcept {
        Snapshot s;
        {
            std::lock_guard<std::mutex> lock(bgMutex_);
            s.base = bgBase_;
        }
        tailPub_.read(s.tail);
        s.rev = s.tail.rev;
        s.sampleRate = sampleRate_;
        return s;
    }

private:
    // Governing rule: the NEWEST tail event whose anchor is at-or-before the
    // position governs (insertion order = edit recency; anchors need not be
    // monotonic - a seek-back followed by a live tempo set anchors earlier
    // than older events, and that newer edit rules from its anchor on).
    // No governing tail event -> the base segment at-or-before governs.
    // Either way the governing entry extends linearly.
    static double beatAtImpl(const TempoMapBase& base, const TempoTailEvent* tail,
                             uint32_t tailCount, int64_t sample) noexcept {
        for (uint32_t i = tailCount; i-- > 0;) {
            if (tail[i].startSample <= sample) {
                return tail[i].startBeat +
                       double(sample - tail[i].startSample) / tail[i].samplesPerBeat;
            }
        }
        const TempoSegment& s = segmentForSample(base, sample);
        return s.startBeat + double(sample - s.startSample) / s.samplesPerBeat;
    }

    static int64_t sampleAtImpl(const TempoMapBase& base, const TempoTailEvent* tail,
                                uint32_t tailCount, double beat) noexcept {
        for (uint32_t i = tailCount; i-- > 0;) {
            if (tail[i].startBeat <= beat) {
                return tail[i].startSample + roundToSample(
                    (beat - tail[i].startBeat) * tail[i].samplesPerBeat);
            }
        }
        const TempoSegment& s = segmentForBeat(base, beat);
        return s.startSample + roundToSample((beat - s.startBeat) * s.samplesPerBeat);
    }

    static double samplesPerBeatAtImpl(const TempoMapBase& base, const TempoTailEvent* tail,
                                       uint32_t tailCount, double beat) noexcept {
        for (uint32_t i = tailCount; i-- > 0;)
            if (tail[i].startBeat <= beat) return tail[i].samplesPerBeat;
        return segmentForBeat(base, beat).samplesPerBeat;
    }

    static const TempoSegment& segmentForBeat(const TempoMapBase& b, double beat) noexcept {
        size_t lo = 0, hi = b.segments.size();          // last startBeat <= beat
        while (hi - lo > 1) {
            const size_t mid = (lo + hi) / 2;
            if (b.segments[mid].startBeat <= beat) lo = mid; else hi = mid;
        }
        return b.segments[lo];
    }

    static const TempoSegment& segmentForSample(const TempoMapBase& b, int64_t sample) noexcept {
        size_t lo = 0, hi = b.segments.size();
        while (hi - lo > 1) {
            const size_t mid = (lo + hi) / 2;
            if (b.segments[mid].startSample <= sample) lo = mid; else hi = mid;
        }
        return b.segments[lo];
    }

    static int64_t roundToSample(double x) noexcept {
        return static_cast<int64_t>(x + (x >= 0.0 ? 0.5 : -0.5));
    }

    void publishTail() noexcept {
        TempoTailBlock block;
        block.count = tailCount_;
        block.rev = rev_;
        for (uint32_t i = 0; i < tailCount_; ++i) block.events[i] = tail_[i];
        tailPub_.publish(block);
    }

    double sampleRate_ = 0.0;

    // RT-owned state.
    TempoMapBase defaultBase_;                   // immortal fallback (epoch 0)
    const TempoMapBase* rtBase_ = &defaultBase_;
    TempoTailEvent tail_[kTempoTailCap]{};
    uint32_t tailCount_ = 0;
    uint32_t rev_ = 0;

    // Cross-thread machinery.
    Seqlock<TempoTailBlock> tailPub_;
    OfferSlot<TempoMapBase> baseOffer_;
    mutable std::mutex bgMutex_;                 // never touched by RT
    const TempoMapBase* bgBase_ = &defaultBase_;
};

} // namespace daw
