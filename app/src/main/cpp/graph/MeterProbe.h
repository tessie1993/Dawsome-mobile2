#pragma once

#include <cmath>

#include "../core/MeterFrame.h"
#include "../core/NodeUid.h"

// Post-fader level metering (blueprint graph/MeterProbe; readback 2.5).
// Accumulates peak + RMS over a ~30 Hz window and emits one MeterFrame per
// window; the graph schedule pushes ready frames into the engine's MeterBus
// ring (this class stays pure and hostside-testable). Lossy by design -
// only the newest frames matter to the UI.

namespace daw {

class MeterProbe {
public:
    void prepare(NodeUid uid, double sampleRate) noexcept {
        uid_ = uid;
        windowFrames_ = sampleRate > 0.0 ? static_cast<int>(sampleRate / 30.0) : 1600;
        resetWindow();
    }

    // [RT] Accumulate one block; returns true when `out` holds a new frame.
    bool sample(const float* l, const float* r, int numFrames, MeterFrame& out) noexcept {
        for (int f = 0; f < numFrames; ++f) {
            const float al = l[f] < 0.0f ? -l[f] : l[f];
            const float ar = r[f] < 0.0f ? -r[f] : r[f];
            if (al > peakL_) peakL_ = al;
            if (ar > peakR_) peakR_ = ar;
            sumL2_ += al * al;
            sumR2_ += ar * ar;
        }
        frames_ += numFrames;
        if (frames_ < windowFrames_) return false;

        out.uid = uid_;
        out.peakL = peakL_;
        out.peakR = peakR_;
        const float inv = 1.0f / static_cast<float>(frames_);
        out.rmsL = std::sqrt(sumL2_ * inv);
        out.rmsR = std::sqrt(sumR2_ * inv);
        out.gainReductionDb = 0.0f;          // dynamics report through this later
        out.flags = (peakL_ > 1.0f || peakR_ > 1.0f) ? kMeterClipped : uint16_t(0);
        out.seq = ++seq_;
        resetWindow();
        return true;
    }

    NodeUid uid() const noexcept { return uid_; }

private:
    void resetWindow() noexcept {
        peakL_ = peakR_ = 0.0f;
        sumL2_ = sumR2_ = 0.0f;
        frames_ = 0;
    }

    NodeUid uid_ = 0;
    int windowFrames_ = 1600;
    int frames_ = 0;
    float peakL_ = 0.0f;
    float peakR_ = 0.0f;
    float sumL2_ = 0.0f;
    float sumR2_ = 0.0f;
    uint16_t seq_ = 0;
};

} // namespace daw
