#pragma once

#include <cstring>

#include "../core/SmoothedValue.h"
#include "../device/DeviceNode.h"

// Per-(track, return-bus) send tap (blueprint graph/SendNode). Implements
// DeviceNode so the resolver / MigrationPlan / state registry stay uniform,
// with one graph-internal semantic deviation, documented loudly:
//
//   process() ACCUMULATES: outputs += inputs * level. The graph always calls
//   it with the post-strip track buffer as inputs and the return bus
//   accumulator as outputs (never aliased). Ordinary devices overwrite;
//   sends sum - that IS their job.
//
// The resolver maps (trackUid, "mixer.sendA"/"mixer.sendB") to this node's
// dense param 0, so the Kotlin side keeps addressing sends on the TRACK uid
// exactly as it already does. Post-fader sends (tap after the strip);
// pre-fader and cue sends join at their milestones.

namespace daw {

class SendNode final : public DeviceNode {
public:
    static constexpr uint16_t kStateVersion = 1;

    struct State {                    // seam-3 body, POD
        float level;                  // plain target
        float curLevel;               // smoothed current
    };

    explicit SendNode(int busIndex = 0) : bus_(busIndex == 0 ? 0 : 1) {}

    void prepare(double sampleRate, int maxBlock) override {
        (void)maxBlock;
        level_.prepare(sampleRate, kParams[bus_].smoothingMs);
        level_.snap(plain_);
    }

    // outputs += inputs * level  (see header).
    void process(ProcessContext& ctx) override {
        const float* inL = ctx.inputs[0];
        const float* inR = ctx.numChannels > 1 ? ctx.inputs[1] : ctx.inputs[0];
        float* outL = ctx.outputs[0];
        float* outR = ctx.numChannels > 1 ? ctx.outputs[1] : ctx.outputs[0];
        if (!level_.isSmoothing()) {
            const float g = level_.current();
            if (g <= 0.0f) return;
            for (int f = 0; f < ctx.numFrames; ++f) {
                outL[f] += inL[f] * g;
                outR[f] += inR[f] * g;
            }
            return;
        }
        for (int f = 0; f < ctx.numFrames; ++f) {
            const float g = level_.getNext();
            outL[f] += inL[f] * g;
            outR[f] += inR[f] * g;
        }
    }

    void reset() override {}

    int latencySamples() const override { return 0; }

    int paramCount() const override { return 1; }

    const ParamDescriptor& paramDescriptor(int i) const override {
        (void)i;
        return kParams[bus_];
    }

    void setParamImmediate(int denseIndex, float plain) override {
        if (denseIndex != 0) return;
        plain_ = plain < 0.0f ? 0.0f : (plain > 1.0f ? 1.0f : plain);
        level_.setTarget(plain_);
    }

    size_t stateBytes() const override { return sizeof(State); }

    void saveState(NodeState& out) const override {
        out.hdr.version = kStateVersion;
        out.hdr.sizeBytes = sizeof(State);
        out.hdr.flags = 0;
        const State s{plain_, level_.current()};
        std::memcpy(out.body, &s, sizeof s);
    }

    bool loadState(const NodeState& in) override {
        if (in.hdr.version != kStateVersion || in.hdr.sizeBytes != sizeof(State) ||
            in.body == nullptr)
            return false;
        State s;
        std::memcpy(&s, in.body, sizeof s);
        level_.snap(s.curLevel);
        plain_ = s.level;
        level_.setTarget(plain_);
        return true;
    }

    int busIndex() const noexcept { return bus_; }

private:
    static constexpr ParamDescriptor kParams[2] = {
        {"mixer.sendA", "Send A", 0.0f, 1.0f, 0.0f,
         ParamDescriptor::Curve::Linear, "", 10.0f, true, false, false},
        {"mixer.sendB", "Send B", 0.0f, 1.0f, 0.0f,
         ParamDescriptor::Curve::Linear, "", 10.0f, true, false, false},
    };

    int bus_ = 0;
    float plain_ = 0.0f;
    SmoothedValue level_;
};

} // namespace daw
