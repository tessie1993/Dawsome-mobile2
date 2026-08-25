#pragma once

#include <cmath>

#include "../core/EngineConfig.h"
#include "../core/FixedVector.h"
#include "TempoMap.h"
#include "TransportEngine.h"

// The click (blueprint sequencer/MetronomeNode; §3.2 bus-routable). Engine-
// owned, rendered AFTER the graph into the output buses so it works with or
// without an installed graph and never enters the mix path (not recorded,
// not metered, not affected by master volume - the standard cue-monitor
// behaviour). Routing today: Main (Cue folds into Main until unfolded
// hardware routing exists); the route enum is already in place.
//
// Sound: two short sine bursts - accented bar click (higher pitch, louder)
// and beat click - with an instant attack and exponential decay. Beat
// boundaries come from the SAME TransportSpans the schedulers use, and each
// crossing converts through the TempoMap, so clicks stay sample-locked to
// scheduled notes through tempo changes and loop wraps.
//
// All [RT]: fixed click pool, no allocation; clicks ring across block
// boundaries until their envelope dies.

namespace daw {

class MetronomeNode {
public:
    enum class Route : uint8_t { Cue = 0, Main = 1, Both = 2 };

    static constexpr float kBarHz = 1600.0f;
    static constexpr float kBeatHz = 1100.0f;
    static constexpr float kBarGain = 0.5f;
    static constexpr float kBeatGain = 0.35f;
    static constexpr float kDecaySeconds = 0.045f;

    void prepare(double sampleRate) noexcept {
        rate_ = sampleRate;
        // Exponential decay reaching -80 dB over kDecaySeconds.
        decayCoef_ = sampleRate > 0.0
            ? std::exp(-9.21034 / (sampleRate * kDecaySeconds))
            : 0.999;
        clicks_.clear();
    }

    void setRoute(Route r) noexcept { route_ = r; }
    Route route() const noexcept { return route_; }

    // Queue clicks for one transport span (call per span, before render).
    void scheduleSpan(const TransportSpan& span, const TempoMap& map,
                      bool playing, bool enabled) noexcept {
        if (!playing || !enabled || span.endBeat <= span.startBeat) return;
        // Integer beats crossing [startBeat, endBeat) - a beat exactly at
        // the span start belongs to this span (half-open convention).
        for (double b = std::ceil(span.startBeat); b < span.endBeat; b += 1.0) {
            const int64_t abs = map.sampleAt(b);
            int64_t rel = abs - span.startSample;
            if (rel < 0) rel = 0;
            if (rel >= span.frames) continue;
            if (clicks_.full()) break;
            const TempoMap::BarBeat bb = map.barBeatAt(b + 1e-6);
            const bool accent = bb.beatInBar < 0.5;
            Click c;
            c.startOffset = static_cast<int>(rel) + span.offsetFrames;
            c.phaseInc = static_cast<float>(
                (accent ? kBarHz : kBeatHz) * 2.0 * 3.14159265358979 / rate_);
            c.amp = accent ? kBarGain : kBeatGain;
            clicks_.push_back(c);
        }
    }

    // Add ringing + newly scheduled clicks into the bus buffer(s).
    void render(float* l, float* r, int numFrames) noexcept {
        for (size_t i = 0; i < clicks_.size();) {
            Click& c = clicks_[i];
            const int from = c.startOffset > 0 ? c.startOffset : 0;
            for (int f = from; f < numFrames; ++f) {
                const float v = std::sin(c.phase) * c.amp;
                c.phase += c.phaseInc;
                c.amp *= static_cast<float>(decayCoef_);
                l[f] += v;
                r[f] += v;
            }
            c.startOffset -= numFrames;
            if (c.startOffset < 0) c.startOffset = 0;
            if (c.amp < 0.0005f) {
                clicks_.eraseUnordered(i);
            } else {
                ++i;
            }
        }
    }

    void reset() noexcept { clicks_.clear(); }

private:
    struct Click {
        int   startOffset = 0;   // within the current block; 0 once ringing
        float phase = 0.0f;
        float phaseInc = 0.1f;
        float amp = 0.5f;
    };

    double rate_ = 48000.0;
    double decayCoef_ = 0.999;
    Route route_ = Route::Main;
    FixedVector<Click, 8> clicks_;
};

} // namespace daw
