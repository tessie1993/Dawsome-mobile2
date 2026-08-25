#pragma once

#include <cstdint>

#include "../core/OfferSlot.h"
#include "../media/SampleBuffer.h"

// Browser audition path (spec P1 §12 "preview sounds before loading";
// CONTRACTS v1.2 Preview). The GraphBuilder resolves a Preview delta to a
// cache-pinned handle and offers a PreviewClip; this player claims it at the
// block boundary and renders POST-GRAPH straight onto the output bus - the
// same cue-fold lane as the metronome (§3.2): never recorded, never metered,
// unaffected by the mix. Tempo-synced loop preview arrives with the
// time-stretch milestone; this plays one-shots 1:1 (the cache conformed the
// file to the engine rate at load, D5).
//
// Click discipline (the known failure mode of browser previews): 5 ms
// fade-in on start, a 5 ms tail fade INTO the buffer end (truncated files
// end hot), and a replaced preview fades OUT while the new one fades in.
// The retiring artifact's retirement is acked only after its fade
// completes - until then the builder must not free it (OfferSlot protocol);
// a second replacement inside one fade hard-drops the near-silent oldest.
//
// Threading: process() is the audio thread, everything noexcept and
// allocation-free; playback state never leaves this object. The handles
// inside claimed artifacts are builder-pinned; this class only reads.

namespace daw {

struct PreviewClip {
    uint64_t     epoch = 0;
    uint64_t     fileId = 0;   // 0 = a stop request (handle empty)
    SampleHandle handle;       // empty on stop or decode failure
};

class PreviewPlayer {
public:
    static constexpr float kGain = 0.85f;   // headroom under a hot mix

    // [non-RT, streams closed] Rate changes invalidate conformed playback,
    // so any held claims are released HERE - acking while RT is quiescent is
    // the one legal non-RT ack, and silently nulling them would strand the
    // artifacts in the builder's GC list (frees wait on the ack) forever.
    void prepare(double sampleRate, OfferSlot<PreviewClip>& slot) noexcept {
        fadeFrames_ = sampleRate > 0.0 ? int(sampleRate * 0.005) : 240;
        if (fadeFrames_ < 1) fadeFrames_ = 1;
        if (retiring_ != nullptr) { slot.ackRetired(retiring_->epoch); retiring_ = nullptr; }
        if (current_ != nullptr) { slot.ackRetired(current_->epoch); current_ = nullptr; }
        playing_ = false;
    }

    // [RT, once per block] Claim any pending offer, then add the audition
    // into the output bus. `slot` is the engine's preview OfferSlot - passed
    // in so ack timing can follow the fades (see the header note).
    void process(OfferSlot<PreviewClip>& slot, float* l, float* r, int n) noexcept {
        if (PreviewClip* nc = slot.claim()) {
            // A still-fading older artifact: drop it now (it is >= 1 block
            // into a 5 ms fade - inaudible) so at most two clips are live.
            if (retiring_ != nullptr) {
                slot.ackRetired(retiring_->epoch);
                retiring_ = nullptr;
            }
            if (current_ != nullptr) {
                if (playing_) {                    // audible: fade it out
                    retiring_ = current_;
                    retirePos_ = pos_;
                    retireGain_ = fadeGain_;
                } else {                           // already silent: release
                    slot.ackRetired(current_->epoch);
                }
            } else {
                // First-ever claim: ack the predecessor epoch so any older
                // claimed-and-finished chain releases (graph-claim pattern;
                // harmless when nothing older exists).
                slot.ackRetired(nc->epoch - 1);
            }
            current_ = nc;
            pos_ = 0;
            fadeGain_ = 0.0f;
            playing_ = current_->handle && current_->handle->frames > 0;
        }

        if (retiring_ != nullptr) renderRetiring(slot, l, r, n);
        if (playing_) renderCurrent(l, r, n);
    }

    bool auditioning() const noexcept { return playing_; }

private:
    void renderRetiring(OfferSlot<PreviewClip>& slot, float* l, float* r, int n) noexcept {
        const SampleBuffer* buf = retiring_->handle.get();
        const float step = 1.0f / float(fadeFrames_);
        if (buf != nullptr) {
            const float* chL = buf->channel(0);
            const float* chR = buf->channel(1);
            for (int i = 0; i < n && retireGain_ > 0.0f && retirePos_ < buf->frames; ++i) {
                l[i] += chL[retirePos_] * retireGain_ * kGain;
                r[i] += chR[retirePos_] * retireGain_ * kGain;
                retireGain_ -= step;
                ++retirePos_;
            }
        } else {
            retireGain_ = 0.0f;
        }
        if (retireGain_ <= 0.0f ||
            (buf != nullptr && retirePos_ >= buf->frames)) {
            slot.ackRetired(retiring_->epoch);     // fade done: builder may free
            retiring_ = nullptr;
        }
    }

    void renderCurrent(float* l, float* r, int n) noexcept {
        const SampleBuffer* buf = current_->handle.get();
        const float step = 1.0f / float(fadeFrames_);
        const float* chL = buf->channel(0);
        const float* chR = buf->channel(1);
        const int64_t frames = buf->frames;
        for (int i = 0; i < n; ++i) {
            if (pos_ >= frames) { playing_ = false; break; }
            if (fadeGain_ < 1.0f) {
                fadeGain_ += step;
                if (fadeGain_ > 1.0f) fadeGain_ = 1.0f;
            }
            // Tail window: ramp into the end so truncated files close clean.
            const int64_t remaining = frames - pos_;
            const float tail = remaining < int64_t(fadeFrames_)
                ? float(remaining) * step : 1.0f;
            const float g = fadeGain_ * tail * kGain;
            l[i] += chL[pos_] * g;
            r[i] += chR[pos_] * g;
            ++pos_;
        }
    }

    PreviewClip* current_ = nullptr;    // claimed; held until replaced
    PreviewClip* retiring_ = nullptr;   // claimed; fading out, ack deferred
    int64_t pos_ = 0;
    int64_t retirePos_ = 0;
    float   fadeGain_ = 0.0f;
    float   retireGain_ = 0.0f;
    int     fadeFrames_ = 240;
    bool    playing_ = false;
};

} // namespace daw
