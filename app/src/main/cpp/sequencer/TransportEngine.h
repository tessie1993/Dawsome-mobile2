#pragma once

#include <cstdint>

#include "TempoMap.h"

// Transport state machine over the TempoMap (blueprint sequencer/, workflow
// area 2). Owns play/stop/record/loop/seek and the per-block advance that
// every player consumes; all beat<->sample authority delegates to the map.
//
// TimebaseSource is a day-one seam (blueprint 2.5): Internal is implemented;
// AbletonLink / MidiClockSlave arrive with SyncAdapter (M16) and chase via
// PLL there. Tempo authority rule: under an external timebase, tempo
// messages are rejected and counted (the UI reports the suspension).
//
// advance() returns 1-2 contiguous spans per block: crossing the loop end
// splits the block at the boundary and the second span starts at loop start
// (wrapped = true, players retrigger there). One wrap per block: a loop
// shorter than a render block plays through past its end for the remainder
// (degenerate sub-1024-sample loops trade wrap fidelity for bounded work).
// Span consumers needing beat positions INSIDE a span must convert through
// the map per event (the beat function is piecewise-linear; span endpoints
// alone cannot see interior tempo kinks).
//
// Threading: everything here is [RT] (message application + advance on the
// audio thread). prepare() runs before streams start.

namespace daw {

enum class TimebaseSource : uint8_t {
    Internal      = 0,
    AbletonLink   = 1,
    MidiClockSlave = 2,
};

struct TransportSpan {
    int64_t startSample = 0;   // timeline position at span start
    double  startBeat = 0.0;
    double  endBeat = 0.0;     // beat at span end (exclusive)
    int     offsetFrames = 0;  // offset within the render block
    int     frames = 0;
    bool    wrapped = false;   // this span begins at loop start after a wrap
};

class TransportEngine {
public:
    // ---- lifecycle [non-RT, before streams] ---------------------------------

    void prepare(double sampleRate, double defaultBpm = 120.0) noexcept {
        map_.prepare(sampleRate, defaultBpm);
        recomputeLoopAnchors();
    }

    TempoMap&       tempoMap() noexcept { return map_; }
    const TempoMap& tempoMap() const noexcept { return map_; }

    // ---- message application [RT] -------------------------------------------

    void play() noexcept { playing_ = true; }

    // Matches the edit-model reducer: Stop parks the playhead at zero.
    void stop() noexcept {
        playing_ = false;
        posSample_ = 0;
        posBeat_ = 0.0;
    }

    // Pause semantics: position is kept (only Stop parks at zero).
    void togglePlay() noexcept { playing_ = !playing_; }

    void setRecording(bool on) noexcept { recording_ = on; }   // pipeline lands M6
    void setLooping(bool on) noexcept { looping_ = on; }
    void setMetronome(bool on) noexcept { metronome_ = on; }   // audible node lands M2+

    void seekSample(int64_t sample) noexcept {
        posSample_ = sample < 0 ? 0 : sample;
        posBeat_ = map_.beatAt(posSample_);
    }

    void seekBeat(double beat) noexcept {
        posBeat_ = beat < 0.0 ? 0.0 : beat;
        posSample_ = map_.sampleAt(posBeat_);
    }

    void setLoopRegion(double startBeat, double endBeat) noexcept {
        loopStartBeat_ = startBeat;
        loopEndBeat_ = endBeat > startBeat ? endBeat : startBeat;
        recomputeLoopAnchors();
    }

    void setTempo(double bpm) noexcept {
        if (timebase_ != TimebaseSource::Internal) { ++tempoAuthorityRejections_; return; }
        map_.rtSetTempo(bpm, posSample_, posBeat_);
        recomputeLoopAnchors();                    // loop sample anchors move with tempo
    }

    void nudgeTempo(double bpmDelta) noexcept {
        if (timebase_ != TimebaseSource::Internal) { ++tempoAuthorityRejections_; return; }
        map_.rtNudgeTempo(bpmDelta, posSample_, posBeat_);
        recomputeLoopAnchors();
    }

    // Display/metronome authority until a structure-shaped base rebuild makes
    // it canonical in the map's time-sig list (builder, M2+).
    void setTimeSig(int numerator, int denominator) noexcept {
        if (numerator >= 1 && numerator <= 99 &&
            (denominator == 1 || denominator == 2 || denominator == 4 ||
             denominator == 8 || denominator == 16 || denominator == 32)) {
            sigNum_ = static_cast<uint16_t>(numerator);
            sigDen_ = static_cast<uint16_t>(denominator);
        }
    }

    void setTimebaseSource(TimebaseSource src) noexcept { timebase_ = src; }

    // ---- per block [RT] -----------------------------------------------------

    // Claims any offered tempo base, then advances. Returns the span count
    // (1, or 2 when the block wraps the loop). Spans are written to out[].
    int advance(int numFrames, TransportSpan out[2]) noexcept {
        if (map_.rtClaimOfferedBase()) {
            posBeat_ = map_.beatAt(posSample_);    // re-derive under the new base
            recomputeLoopAnchors();
        }
        if (!playing_ || numFrames <= 0) {
            out[0] = {posSample_, posBeat_, posBeat_, 0, numFrames, false};
            return 1;
        }

        int produced = 0;
        int remaining = numFrames;
        const bool crossesLoopEnd =
            looping_ && loopEndSample_ > loopStartSample_ &&
            posSample_ < loopEndSample_ &&
            posSample_ + remaining > loopEndSample_;

        if (crossesLoopEnd) {
            const int pre = static_cast<int>(loopEndSample_ - posSample_);
            out[produced++] = {posSample_, posBeat_, loopEndBeat_, 0, pre, false};
            remaining -= pre;
            posSample_ = loopStartSample_;
            posBeat_ = loopStartBeat_;
        }

        const int offset = numFrames - remaining;
        const double endBeat = map_.beatAt(posSample_ + remaining);
        out[produced++] = {posSample_, posBeat_, endBeat, offset, remaining, crossesLoopEnd};
        posSample_ += remaining;
        posBeat_ = endBeat;
        return produced;
    }

    // ---- facts [RT; non-RT reads go through the published clock] ------------

    bool playing() const noexcept { return playing_; }
    bool recording() const noexcept { return recording_; }
    bool looping() const noexcept { return looping_; }
    bool metronome() const noexcept { return metronome_; }
    int64_t positionSamples() const noexcept { return posSample_; }
    double positionBeat() const noexcept { return posBeat_; }
    double bpm() const noexcept { return map_.bpmAt(posBeat_); }
    uint16_t timeSigNumerator() const noexcept { return sigNum_; }
    uint16_t timeSigDenominator() const noexcept { return sigDen_; }
    TimebaseSource timebase() const noexcept { return timebase_; }
    uint32_t tempoAuthorityRejections() const noexcept { return tempoAuthorityRejections_; }
    double loopStartBeat() const noexcept { return loopStartBeat_; }
    double loopEndBeat() const noexcept { return loopEndBeat_; }

private:
    void recomputeLoopAnchors() noexcept {
        loopStartSample_ = map_.sampleAt(loopStartBeat_);
        loopEndSample_ = map_.sampleAt(loopEndBeat_);
    }

    TempoMap map_;

    bool playing_ = false;
    bool recording_ = false;
    bool looping_ = true;          // matches the edit model's default
    bool metronome_ = false;

    int64_t posSample_ = 0;
    double  posBeat_ = 0.0;

    double  loopStartBeat_ = 0.0;
    double  loopEndBeat_ = 16.0;   // edit-model default region
    int64_t loopStartSample_ = 0;
    int64_t loopEndSample_ = 0;

    uint16_t sigNum_ = 4;
    uint16_t sigDen_ = 4;

    TimebaseSource timebase_ = TimebaseSource::Internal;
    uint32_t tempoAuthorityRejections_ = 0;
};

} // namespace daw
