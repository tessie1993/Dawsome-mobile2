#pragma once

#include <cstring>
#include <vector>

#include "../core/EngineConfig.h"
#include "../core/SmoothedValue.h"
#include "DelayComp.h"
#include "DeviceChain.h"
#include "DeviceNode.h"
#include "MacroTable.h"

// Parallel-chain rack (blueprint device/racks: RackDevice + ChainMixer +
// MacroTable; M3 platform core). A composite DeviceNode:
//
//   input -> [copy to each chain scratch] -> chain N -> [internal PDC to the
//   slowest chain] -> * chainGain N -> sum -> output
//
// Latency = max member-chain latency (parallel paths balanced INSIDE, so
// the enclosing chain/graph sees one number - PDC composes). Macros are the
// rack's own "macro.N" params; setting one expands through the apply hook
// the builder wires to the installed graph resolver, so macro targets
// smooth exactly like direct moves (§5: macros are the top layer; offsets
// and automation slot underneath at their milestones).
//
// Chain zones (key/velocity/selector/frequency-band via LR crossovers,
// crossfaded) and VariationStore (snapshots/morph) join at the racks
// workflow milestone; model Rack deltas wire construction then. Member
// chains and their devices are graph-owned and migrate as their own
// entries; rack state = macro values + mixer gains.

namespace daw {

class RackDevice final : public DeviceNode {
public:
    static constexpr uint16_t kStateVersion = 1;
    static constexpr int kMaxChains = kMaxChainsPerRack;

    struct State {                        // seam-3 body, POD
        float macros[kMaxMacros];
        float gainTarget[kMaxChains];
        float gainCurrent[kMaxChains];
        int32_t chainCount;
    };

    using ApplyFn = bool (*)(void* ctx, NodeUid uid, ParamKeyHash key, float plain);

    // ---- builder assembly ---------------------------------------------------

    bool addChain(DeviceChain* chain) noexcept {
        if (chainCount_ >= kMaxChains || chain == nullptr) return false;
        chains_[chainCount_].chain = chain;
        ++chainCount_;
        return true;
    }

    MacroTable& macros() noexcept { return macros_; }

    // Builder wires this to the installed graph's resolver at compile.
    void setApplyHook(ApplyFn fn, void* ctx) noexcept {
        applyFn_ = fn;
        applyCtx_ = ctx;
    }

    uint64_t computeConfigHash() const noexcept {
        uint64_t h = 0xcbf29ce484222325ull ^ uint64_t(chainCount_);
        for (int i = 0; i < chainCount_; ++i)
            h ^= chains_[i].chain->computeConfigHash() +
                 0x9e3779b97f4a7c15ull + (h << 6) + (h >> 2);
        return h;
    }

    // ---- DeviceNode ---------------------------------------------------------

    void prepare(double sampleRate, int maxBlock) override {
        maxBlock_ = maxBlock;
        scratchL_.assign(size_t(maxBlock) * kMaxChains, 0.0f);
        scratchR_.assign(size_t(maxBlock) * kMaxChains, 0.0f);
        int maxLat = 0;
        for (int i = 0; i < chainCount_; ++i) {
            chains_[i].chain->prepare(sampleRate, maxBlock);
            chains_[i].latency = chains_[i].chain->latencySamples();
            if (chains_[i].latency > maxLat) maxLat = chains_[i].latency;
        }
        for (int i = 0; i < chainCount_; ++i) {
            chains_[i].comp.prepare(maxLat - chains_[i].latency, maxBlock);
            chains_[i].gain.prepare(sampleRate, 10.0f);
            chains_[i].gain.snap(chains_[i].gainPlain);
        }
        latency_ = maxLat;
    }

    void process(ProcessContext& ctx) override {
        if (chainCount_ == 0) return;
        float* l = ctx.outputs[0];
        float* r = ctx.numChannels > 1 ? ctx.outputs[1] : ctx.outputs[0];
        const int n = ctx.numFrames;

        // Fan out: each chain works on its own copy of the input.
        for (int i = 0; i < chainCount_; ++i) {
            float* cl = chainL(i);
            float* cr = chainR(i);
            std::memcpy(cl, l, size_t(n) * sizeof(float));
            std::memcpy(cr, r, size_t(n) * sizeof(float));
            float* bufs[2] = {cl, cr};
            ProcessContext cctx = ctx;
            cctx.inputs = bufs;
            cctx.outputs = bufs;
            chains_[i].chain->process(cctx);
            chains_[i].comp.process(cl, cr, n);   // balance to the slowest chain
        }

        // Sum through the chain mixer.
        for (int f = 0; f < n; ++f) { l[f] = 0.0f; r[f] = 0.0f; }
        for (int i = 0; i < chainCount_; ++i) {
            ChainSlot& c = chains_[i];
            const float* cl = chainL(i);
            const float* cr = chainR(i);
            if (!c.gain.isSmoothing()) {
                const float g = c.gain.current();
                for (int f = 0; f < n; ++f) { l[f] += cl[f] * g; r[f] += cr[f] * g; }
            } else {
                for (int f = 0; f < n; ++f) {
                    const float g = c.gain.getNext();
                    l[f] += cl[f] * g;
                    r[f] += cr[f] * g;
                }
            }
        }
    }

    void reset() override {
        for (int i = 0; i < chainCount_; ++i) {
            chains_[i].chain->reset();
            chains_[i].comp.reset();
        }
    }

    int latencySamples() const override { return latency_; }

    int paramCount() const override { return kMaxMacros + chainCount_; }

    const ParamDescriptor& paramDescriptor(int i) const override {
        if (i >= 0 && i < kMaxMacros) return kMacroParams[i];
        const int c = i - kMaxMacros;
        return kChainGainParams[c < 0 || c >= kMaxChains ? 0 : c];
    }

    void setParamImmediate(int denseIndex, float plain) override {
        if (denseIndex >= 0 && denseIndex < kMaxMacros) {
            macros_.setMacro(denseIndex, plain);
            if (applyFn_ != nullptr) {
                macros_.expandMacro(denseIndex,
                    [this](NodeUid uid, ParamKeyHash key, float p) {
                        applyFn_(applyCtx_, uid, key, p);
                    });
            }
            return;
        }
        const int c = denseIndex - kMaxMacros;
        if (c >= 0 && c < chainCount_) {
            ChainSlot& s = chains_[c];
            s.gainPlain = plain < 0.0f ? 0.0f : (plain > 2.0f ? 2.0f : plain);
            s.gain.setTarget(s.gainPlain);
        }
    }

    size_t stateBytes() const override { return sizeof(State); }

    void saveState(NodeState& out) const override {
        out.hdr.version = kStateVersion;
        out.hdr.sizeBytes = sizeof(State);
        out.hdr.flags = 0;
        State st{};
        st.chainCount = chainCount_;
        for (int m = 0; m < kMaxMacros; ++m) st.macros[m] = macros_.macroValue(m);
        for (int i = 0; i < chainCount_; ++i) {
            st.gainTarget[i] = chains_[i].gainPlain;
            st.gainCurrent[i] = chains_[i].gain.current();
        }
        std::memcpy(out.body, &st, sizeof st);
    }

    bool loadState(const NodeState& in) override {
        if (in.hdr.version != kStateVersion || in.hdr.sizeBytes != sizeof(State) ||
            in.body == nullptr)
            return false;
        State st;
        std::memcpy(&st, in.body, sizeof st);
        if (st.chainCount != chainCount_) return false;
        for (int m = 0; m < kMaxMacros; ++m) macros_.setMacro(m, st.macros[m]);
        for (int i = 0; i < chainCount_; ++i) {
            ChainSlot& s = chains_[i];
            s.gainPlain = st.gainTarget[i];
            s.gain.snap(st.gainCurrent[i]);
            s.gain.setTarget(s.gainPlain);
        }
        return true;
    }

private:
    struct ChainSlot {
        DeviceChain* chain = nullptr;
        DelayCompNode comp;
        SmoothedValue gain;
        float gainPlain = 1.0f;
        int latency = 0;
    };

    float* chainL(int i) noexcept { return scratchL_.data() + size_t(i) * size_t(maxBlock_); }
    float* chainR(int i) noexcept { return scratchR_.data() + size_t(i) * size_t(maxBlock_); }

    static constexpr ParamDescriptor kMacroParams[kMaxMacros] = {
        {"macro.1", "Macro 1", 0, 1, 0, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
        {"macro.2", "Macro 2", 0, 1, 0, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
        {"macro.3", "Macro 3", 0, 1, 0, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
        {"macro.4", "Macro 4", 0, 1, 0, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
        {"macro.5", "Macro 5", 0, 1, 0, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
        {"macro.6", "Macro 6", 0, 1, 0, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
        {"macro.7", "Macro 7", 0, 1, 0, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
        {"macro.8", "Macro 8", 0, 1, 0, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
        {"macro.9", "Macro 9", 0, 1, 0, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
        {"macro.10", "Macro 10", 0, 1, 0, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
        {"macro.11", "Macro 11", 0, 1, 0, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
        {"macro.12", "Macro 12", 0, 1, 0, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
        {"macro.13", "Macro 13", 0, 1, 0, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
        {"macro.14", "Macro 14", 0, 1, 0, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
        {"macro.15", "Macro 15", 0, 1, 0, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
        {"macro.16", "Macro 16", 0, 1, 0, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    };
    static constexpr ParamDescriptor kChainGainParams[kMaxChains] = {
        {"rack.chainGain.1", "Chain 1", 0, 2, 1, ParamDescriptor::Curve::Linear, "", 10, true, false, false},
        {"rack.chainGain.2", "Chain 2", 0, 2, 1, ParamDescriptor::Curve::Linear, "", 10, true, false, false},
        {"rack.chainGain.3", "Chain 3", 0, 2, 1, ParamDescriptor::Curve::Linear, "", 10, true, false, false},
        {"rack.chainGain.4", "Chain 4", 0, 2, 1, ParamDescriptor::Curve::Linear, "", 10, true, false, false},
        {"rack.chainGain.5", "Chain 5", 0, 2, 1, ParamDescriptor::Curve::Linear, "", 10, true, false, false},
        {"rack.chainGain.6", "Chain 6", 0, 2, 1, ParamDescriptor::Curve::Linear, "", 10, true, false, false},
        {"rack.chainGain.7", "Chain 7", 0, 2, 1, ParamDescriptor::Curve::Linear, "", 10, true, false, false},
        {"rack.chainGain.8", "Chain 8", 0, 2, 1, ParamDescriptor::Curve::Linear, "", 10, true, false, false},
    };

    ChainSlot chains_[kMaxChains];
    int chainCount_ = 0;
    int latency_ = 0;
    int maxBlock_ = 0;
    MacroTable macros_;
    ApplyFn applyFn_ = nullptr;
    void* applyCtx_ = nullptr;
    std::vector<float> scratchL_;
    std::vector<float> scratchR_;
};

} // namespace daw
