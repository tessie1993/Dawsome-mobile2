#include "PlaybackGraph.h"

#include <cstring>

namespace daw {

namespace {

inline void zeroBuf(float* b, int n) noexcept {
    std::memset(b, 0, size_t(n) * sizeof(float));
}

inline void mixInto(float* dstL, float* dstR,
                    const float* srcL, const float* srcR, int n) noexcept {
    for (int f = 0; f < n; ++f) {
        dstL[f] += srcL[f];
        dstR[f] += srcR[f];
    }
}

inline ProcessContext contextFor(float* const* bufs, int n,
                                 const RenderFacts& facts) noexcept {
    ProcessContext ctx;
    ctx.inputs = bufs;
    ctx.outputs = const_cast<float* const*>(bufs);
    ctx.numChannels = 2;
    ctx.numFrames = n;
    ctx.sampleRate = facts.sampleRate;
    ctx.blockStartSample = facts.blockStartSample;
    ctx.blockStartBeat = facts.blockStartBeat;
    ctx.bpm = facts.bpm;
    ctx.isPlaying = facts.playing;
    ctx.isRecording = facts.recording;
    return ctx;
}

} // namespace

void PlaybackGraph::processBlock(int numFrames, const RenderFacts& facts) noexcept {
    pendingMeters.clear();
    const int n = numFrames > kMaxBlock ? kMaxBlock : numFrames;
    if (n <= 0) return;

    // Bus accumulators first: master mix + return inputs collect += taps.
    zeroBuf(mixL, n);
    zeroBuf(mixR, n);
    for (TrackUnit& r : returns) {
        zeroBuf(r.bufL, n);
        zeroBuf(r.bufR, n);
    }

    // Tracks: zeroed buffer = the silence source (instruments write here
    // from M4, clip players from M5/M6) -> device chain -> strip ->
    // post-fader send taps -> comp -> master join.
    for (TrackUnit& t : tracks) {
        zeroBuf(t.bufL, n);
        zeroBuf(t.bufR, n);

        float* bufs[2] = {t.bufL, t.bufR};
        ProcessContext ctx = contextFor(bufs, n, facts);
        if (t.chain != nullptr) t.chain->process(ctx);
        t.strip->process(ctx);

        if (t.sendA != nullptr && !returns.empty()) {
            float* dst[2] = {returns[0].bufL, returns[0].bufR};
            ProcessContext sctx = contextFor(bufs, n, facts);
            sctx.outputs = dst;
            t.sendA->process(sctx);          // += accumulate (SendNode contract)
        }
        if (t.sendB != nullptr && returns.size() > 1) {
            float* dst[2] = {returns[1].bufL, returns[1].bufR};
            ProcessContext sctx = contextFor(bufs, n, facts);
            sctx.outputs = dst;
            t.sendB->process(sctx);
        }

        MeterFrame mf;
        if (t.meter.sample(t.bufL, t.bufR, n, mf)) pendingMeters.push_back(mf);

        if (t.comp != nullptr) t.comp->process(t.bufL, t.bufR, n);
        mixInto(mixL, mixR, t.bufL, t.bufR, n);
    }

    // Returns: accumulated sends -> device chain -> strip -> comp -> master.
    for (TrackUnit& r : returns) {
        float* bufs[2] = {r.bufL, r.bufR};
        ProcessContext ctx = contextFor(bufs, n, facts);
        if (r.chain != nullptr) r.chain->process(ctx);
        r.strip->process(ctx);

        MeterFrame mf;
        if (r.meter.sample(r.bufL, r.bufR, n, mf)) pendingMeters.push_back(mf);

        if (r.comp != nullptr) r.comp->process(r.bufL, r.bufR, n);
        mixInto(mixL, mixR, r.bufL, r.bufR, n);
    }

    // Master: mastering chain -> strip in place on the mix -> Main bus.
    {
        float* bufs[2] = {master.bufL, master.bufR};
        ProcessContext ctx = contextFor(bufs, n, facts);
        if (master.chain != nullptr) master.chain->process(ctx);
        master.strip->process(ctx);

        MeterFrame mf;
        if (master.meter.sample(master.bufL, master.bufR, n, mf))
            pendingMeters.push_back(mf);
    }
    // mainL/mainR alias the master buffer (zero-copy); the engine copies the
    // Main bus to the driver's output staging.
}

} // namespace daw
