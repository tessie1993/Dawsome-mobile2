#pragma once

#include <cstdint>

#include "Seqlock.h"

// Cross-clock-domain anchor (CONTRACTS.md; blueprint 2.5, 3.5). The audio
// callback publishes one of these per callback from the Oboe stream
// timestamp; every conversion between wall-clock event time (MIDI input,
// Link phase) and engine frame position goes through the latest anchor.

namespace daw {

struct TimeAnchor {
    int64_t framePosition  = 0;   // engine output frame position at the anchor
    int64_t monotonicNanos = 0;   // CLOCK_MONOTONIC time of that frame at the DAC
    double  sampleRate     = 0.0;

    // Convert a monotonic timestamp to an engine frame position.
    int64_t framesAt(int64_t nanos) const noexcept {
        if (sampleRate <= 0.0) return framePosition;
        const double dt = static_cast<double>(nanos - monotonicNanos) * 1.0e-9;
        return framePosition + static_cast<int64_t>(dt * sampleRate);
    }
};

// RT publishes, MIDI thread / RecordingAligner / SyncAdapter read.
using TimeAnchorPublisher = Seqlock<TimeAnchor>;

} // namespace daw
