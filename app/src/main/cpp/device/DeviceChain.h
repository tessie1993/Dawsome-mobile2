#pragma once

#include <cstring>
#include <vector>

#include "../core/EngineConfig.h"
#include "../core/SmoothedValue.h"
#include "../dsp/DelayLine.h"
#include "../dsp/DspMath.h"
#include "DeviceNode.h"

// Serial device chain (blueprint device/DeviceChain) - a composite
// DeviceNode, so the resolver, MigrationPlan and graph treat a whole chain
// as one node while its member devices stay individually addressable and
// individually migratable.
//
// THE BYPASS CONTRACT lives here, not in devices (blueprint 3.3): bypass is
// a parameter, therefore latency-preserving and click-free -
//   - the dry path is delayed by the device's reported latencySamples(),
//   - active<->bypassed blends over a ~10 ms equal-power crossfade,
//   - the device keeps processing exactly while the crossfade runs (the
//     "one crossfade-length after bypass engages" rule), then stops; the
//     chain's total latency NEVER changes with bypass state.
//
// Chain params: one "device.bypass" switch per slot (dense index = slot).
// The graph registers each under its DEVICE's uid, so the Kotlin side
// addresses bypass at the device it toggles. Chain state (bypass targets +
// crossfade positions) migrates; member devices migrate as their own
// adopt entries. configHash must cover slot count + per-slot latencies
// (the dry delays are topology) - computeConfigHash() below.

namespace daw {

class DeviceChain final : public DeviceNode {
public:
    static constexpr uint16_t kStateVersion = 1;
    static constexpr int kMaxSlots = kMaxDevicesPerChain;
    static constexpr float kBypassFadeMs = 10.0f;

    struct State {                        // seam-3 body, POD
        uint8_t bypassTarget[kMaxSlots];
        float   xfadeCurrent[kMaxSlots];  // 0 = active, 1 = bypassed
        int32_t slotCount;
    };

    // ---- builder assembly ---------------------------------------------------

    // Devices are owned by the graph's node store; the chain holds raw
    // pointers. Order of addition = processing order.
    bool addDevice(NodeUid uid, DeviceNode* device, bool startBypassed) {
        if (slotCount_ >= kMaxSlots || device == nullptr) return false;
        Slot& s = slots_[slotCount_++];
        s.uid = uid;
        s.dev = device;
        s.bypassTarget = startBypassed;
        return true;
    }

    int slotCount() const noexcept { return slotCount_; }
    NodeUid slotUid(int i) const noexcept { return slots_[i].uid; }

    // DSP-topology digest for seam-3 adoption: slot count + each slot's
    // device identity and latency (dry delay lengths are buffer topology).
    uint64_t computeConfigHash() const noexcept {
        uint64_t h = 0xcbf29ce484222325ull ^ uint64_t(slotCount_);
        for (int i = 0; i < slotCount_; ++i) {
            h ^= slots_[i].uid + 0x9e3779b97f4a7c15ull + (h << 6) + (h >> 2);
            h ^= uint64_t(uint32_t(slots_[i].latency)) * 0x100000001b3ull;
        }
        return h;
    }

    // ---- DeviceNode ---------------------------------------------------------

    void prepare(double sampleRate, int maxBlock) override {
        maxBlock_ = maxBlock;
        dryL_.assign(size_t(maxBlock), 0.0f);
        dryR_.assign(size_t(maxBlock), 0.0f);
        for (int i = 0; i < slotCount_; ++i) {
            Slot& s = slots_[i];
            s.dev->prepare(sampleRate, maxBlock);
            s.latency = s.dev->latencySamples();
            if (s.latency > 0) {
                s.delayL.prepare(s.latency + 1);
                s.delayR.prepare(s.latency + 1);
            }
            s.xfade.prepare(sampleRate, kBypassFadeMs);
            s.xfade.snap(s.bypassTarget ? 1.0f : 0.0f);
        }
    }

    void process(ProcessContext& ctx) override {
        float* l = ctx.outputs[0];
        float* r = ctx.numChannels > 1 ? ctx.outputs[1] : ctx.outputs[0];
        const int n = ctx.numFrames;

        for (int i = 0; i < slotCount_; ++i) {
            Slot& s = slots_[i];
            const bool ramping = s.xfade.isSmoothing();

            if (!ramping && !s.bypassTarget) {       // fully active: straight through
                if (s.latency > 0) {
                    // Keep the dry delay warm so a bypass engage crossfades
                    // against real history, never stale/zero samples.
                    for (int f = 0; f < n; ++f) {
                        s.delayL.write(l[f]);
                        s.delayR.write(r[f]);
                    }
                }
                s.dev->process(ctx);
                continue;
            }
            if (!ramping && s.bypassTarget) {        // fully bypassed: dry only,
                if (s.latency > 0) {                 // still latency-preserving
                    for (int f = 0; f < n; ++f) {
                        s.delayL.write(l[f]);
                        s.delayR.write(r[f]);
                        l[f] = s.delayL.read(s.latency);
                        r[f] = s.delayR.read(s.latency);
                    }
                }
                continue;                            // device rests (past the fade)
            }

            // Crossfading: capture + delay the dry path, run the device (the
            // contract's processing window), blend equal-power per sample.
            for (int f = 0; f < n; ++f) {
                float dl = l[f];
                float dr = r[f];
                if (s.latency > 0) {
                    s.delayL.write(dl);
                    s.delayR.write(dr);
                    dl = s.delayL.read(s.latency);
                    dr = s.delayR.read(s.latency);
                }
                dryL_[size_t(f)] = dl;
                dryR_[size_t(f)] = dr;
            }
            s.dev->process(ctx);                     // wet, in place
            for (int f = 0; f < n; ++f) {
                // xfade t: 0 = active (wet full) -> 1 = bypassed (dry full);
                // equalPower(t, a, b) gives a = cos (wet), b = sin (dry).
                float wetG, dryG;
                dsp::equalPower(s.xfade.getNext(), wetG, dryG);
                l[f] = l[f] * wetG + dryL_[size_t(f)] * dryG;
                r[f] = r[f] * wetG + dryR_[size_t(f)] * dryG;
            }
        }
    }

    void reset() override {
        for (int i = 0; i < slotCount_; ++i) {
            Slot& s = slots_[i];
            s.dev->reset();
            if (s.latency > 0) { s.delayL.clear(); s.delayR.clear(); }
            s.xfade.snap(s.bypassTarget ? 1.0f : 0.0f);
        }
    }

    // Bypass-independent by contract.
    int latencySamples() const override {
        int sum = 0;
        for (int i = 0; i < slotCount_; ++i) sum += slots_[i].latency;
        return sum;
    }

    int paramCount() const override { return slotCount_; }

    const ParamDescriptor& paramDescriptor(int i) const override {
        (void)i;
        return kBypassParam;
    }

    void setParamImmediate(int denseIndex, float plain) override {
        if (denseIndex < 0 || denseIndex >= slotCount_) return;
        Slot& s = slots_[denseIndex];
        const bool bypass = plain >= 0.5f;
        if (bypass == s.bypassTarget) return;
        s.bypassTarget = bypass;
        s.xfade.setTarget(bypass ? 1.0f : 0.0f);
    }

    size_t stateBytes() const override { return sizeof(State); }

    void saveState(NodeState& out) const override {
        out.hdr.version = kStateVersion;
        out.hdr.sizeBytes = sizeof(State);
        out.hdr.flags = 0;
        State st{};
        st.slotCount = slotCount_;
        for (int i = 0; i < slotCount_; ++i) {
            st.bypassTarget[i] = slots_[i].bypassTarget ? 1 : 0;
            st.xfadeCurrent[i] = slots_[i].xfade.current();
        }
        std::memcpy(out.body, &st, sizeof st);
    }

    bool loadState(const NodeState& in) override {
        if (in.hdr.version != kStateVersion || in.hdr.sizeBytes != sizeof(State) ||
            in.body == nullptr)
            return false;
        State st;
        std::memcpy(&st, in.body, sizeof st);
        if (st.slotCount != slotCount_) return false;   // configHash should prevent this
        for (int i = 0; i < slotCount_; ++i) {
            Slot& s = slots_[i];
            s.bypassTarget = st.bypassTarget[i] != 0;
            s.xfade.snap(st.xfadeCurrent[i]);
            s.xfade.setTarget(s.bypassTarget ? 1.0f : 0.0f);
        }
        return true;
    }

private:
    struct Slot {
        NodeUid uid = 0;
        DeviceNode* dev = nullptr;
        dsp::DelayLine delayL;
        dsp::DelayLine delayR;
        int latency = 0;
        SmoothedValue xfade;          // 0 = active, 1 = bypassed
        bool bypassTarget = false;
    };

    static constexpr ParamDescriptor kBypassParam = {
        "device.bypass", "Bypass", 0.0f, 1.0f, 0.0f,
        ParamDescriptor::Curve::Switch, "", 0.0f, true, true, false};

    Slot slots_[kMaxSlots];
    int slotCount_ = 0;
    int maxBlock_ = 0;
    std::vector<float> dryL_;         // crossfade dry scratch (prepare-sized)
    std::vector<float> dryR_;
};

} // namespace daw
