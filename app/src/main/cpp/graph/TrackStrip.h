#pragma once

#include <cstring>

#include "../core/SmoothedValue.h"
#include "../device/DeviceNode.h"
#include "../dsp/DspMath.h"

// The channel strip (blueprint graph/TrackStrip; conventions §3.4): volume,
// constant-power pan (-3 dB center) and click-free mute, applied in-place
// post-chain. Implemented AS a DeviceNode so the resolver, MigrationPlan and
// state registry treat it exactly like any device. Send LEVELS live on
// SendNodes (the graph taps the post-strip buffer); crossfader assign and
// cue sends join at their milestones.
//
// Param keys are the seam-6 contract strings the Kotlin side already
// addresses (ParamKeys.kt): mixer.volume / mixer.pan / mixer.mute.
//
// Smoothing runs in the GAIN domain: dB converts at set-time, pan converts
// to per-channel equal-power gains at set-time, and the three SmoothedValues
// ramp linearly - linear interpolation between equal-power points is the
// industry-standard approximation, and current+target migrate across swaps
// so a ridden fader never jumps.

namespace daw {

class TrackStrip final : public DeviceNode {
public:
    static constexpr uint16_t kStateVersion = 1;

    struct State {                       // seam-3 body, POD
        float volumeDb;
        float pan;
        float mute;                      // plain 0/1
        float curGain;                   // smoothed currents
        float curPanL;
        float curPanR;
        float curMuteGain;
    };

    // ---- DeviceNode ---------------------------------------------------------

    void prepare(double sampleRate, int maxBlock) override {
        (void)maxBlock;
        gain_.prepare(sampleRate, kParams[0].smoothingMs);
        panL_.prepare(sampleRate, kParams[1].smoothingMs);
        panR_.prepare(sampleRate, kParams[1].smoothingMs);
        muteGain_.prepare(sampleRate, kParams[2].smoothingMs);
        applyVolume(volumeDb_, true);
        applyPan(pan_, true);
        applyMute(mute_, true);
    }

    void process(ProcessContext& ctx) override {
        float* l = ctx.outputs[0];
        float* r = ctx.numChannels > 1 ? ctx.outputs[1] : ctx.outputs[0];
        const bool ramping = gain_.isSmoothing() || panL_.isSmoothing() ||
                             panR_.isSmoothing() || muteGain_.isSmoothing();
        if (!ramping) {
            const float gl = gain_.current() * muteGain_.current() * panL_.current();
            const float gr = gain_.current() * muteGain_.current() * panR_.current();
            for (int f = 0; f < ctx.numFrames; ++f) { l[f] *= gl; r[f] *= gr; }
            return;
        }
        for (int f = 0; f < ctx.numFrames; ++f) {
            const float g = gain_.getNext() * muteGain_.getNext();
            l[f] *= g * panL_.getNext();
            r[f] *= g * panR_.getNext();
        }
    }

    void reset() override {}             // stateless besides params: nothing tails

    int latencySamples() const override { return 0; }

    int paramCount() const override { return kParamCount; }

    const ParamDescriptor& paramDescriptor(int i) const override {
        return kParams[i < 0 || i >= kParamCount ? 0 : i];
    }

    void setParamImmediate(int denseIndex, float plain) override {
        switch (denseIndex) {
            case 0: applyVolume(plain, false); break;
            case 1: applyPan(plain, false); break;
            case 2: applyMute(plain, false); break;
            default: break;
        }
    }

    size_t stateBytes() const override { return sizeof(State); }

    // Fills version/size/flags + body; nodeUid/configHash belong to the
    // caller (NodeStateRegistry) - the strip's configHash is 0 (no
    // topology-shaped config), so uid+rate match always adopts.
    void saveState(NodeState& out) const override {
        out.hdr.version = kStateVersion;
        out.hdr.sizeBytes = sizeof(State);
        out.hdr.flags = 0;
        State s;
        s.volumeDb = volumeDb_;
        s.pan = pan_;
        s.mute = mute_;
        s.curGain = gain_.current();
        s.curPanL = panL_.current();
        s.curPanR = panR_.current();
        s.curMuteGain = muteGain_.current();
        std::memcpy(out.body, &s, sizeof s);
    }

    bool loadState(const NodeState& in) override {
        if (in.hdr.version != kStateVersion || in.hdr.sizeBytes != sizeof(State) ||
            in.body == nullptr)
            return false;
        State s;
        std::memcpy(&s, in.body, sizeof s);
        // Resume from the migrated currents, ramp to the (possibly newer)
        // targets - the never-jumps rule.
        gain_.snap(s.curGain);
        panL_.snap(s.curPanL);
        panR_.snap(s.curPanR);
        muteGain_.snap(s.curMuteGain);
        applyVolume(s.volumeDb, false);
        applyPan(s.pan, false);
        applyMute(s.mute, false);
        return true;
    }

    // ---- facts --------------------------------------------------------------

    float volumeDb() const noexcept { return volumeDb_; }
    float pan() const noexcept { return pan_; }
    bool  muted() const noexcept { return mute_ >= 0.5f; }

private:
    static constexpr int kParamCount = 3;
    static constexpr ParamDescriptor kParams[kParamCount] = {
        {"mixer.volume", "Volume", -72.0f, 6.0f, 0.0f,
         ParamDescriptor::Curve::Db, "dB", 10.0f, true, false, false},
        {"mixer.pan", "Pan", -1.0f, 1.0f, 0.0f,
         ParamDescriptor::Curve::Linear, "", 10.0f, true, false, false},
        {"mixer.mute", "Mute", 0.0f, 1.0f, 0.0f,
         ParamDescriptor::Curve::Switch, "", 5.0f, true, false, false},
    };

    void applyVolume(float db, bool snap) noexcept {
        volumeDb_ = db;
        const float g = dsp::dbToGain(db);
        if (snap) gain_.snap(g); else gain_.setTarget(g);
    }

    void applyPan(float p, bool snap) noexcept {
        pan_ = p < -1.0f ? -1.0f : (p > 1.0f ? 1.0f : p);
        float gl, gr;
        dsp::panGains(pan_, gl, gr);
        if (snap) { panL_.snap(gl); panR_.snap(gr); }
        else      { panL_.setTarget(gl); panR_.setTarget(gr); }
    }

    void applyMute(float m, bool snap) noexcept {
        mute_ = m;
        const float g = m >= 0.5f ? 0.0f : 1.0f;
        if (snap) muteGain_.snap(g); else muteGain_.setTarget(g);
    }

    float volumeDb_ = 0.0f;
    float pan_ = 0.0f;
    float mute_ = 0.0f;

    SmoothedValue gain_;
    SmoothedValue panL_;
    SmoothedValue panR_;
    SmoothedValue muteGain_;
};

} // namespace daw
