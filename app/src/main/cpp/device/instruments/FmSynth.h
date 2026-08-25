#pragma once

#include <cmath>

#include "../../dsp/AdsrEnvelope.h"
#include "../../dsp/DspMath.h"
#include "../../dsp/Lfo.h"
#include "../PolyInstrument.h"

// 4-operator phase-modulation synth (blueprint device/instruments/FmSynth,
// DeviceTypeId 2). Eight classic algorithm topologies over four sine
// operators, each with ratio + level + its own audio-rate ADSR (FM timbre
// IS the envelope motion - no control-rate shortcut on envelopes); operator
// 3 has self-feedback. Phase modulation (the DX lineage's "FM"): carriers
// sum to the output, modulator outputs add into their targets' phases
// scaled by kModDepth.
//
// Topologies are encoded in kFmAlgorithms below: modSources[op] = bitmask
// of ops whose output phase-modulates `op`, carriers = bitmask of ops summed
// to the output. Every mask only references HIGHER ops, so the 3->0
// computation order in renderAdd always has a modulator ready before its
// target (op3's self-feedback is the one-sample-delayed exception).

namespace daw {

struct FmAlgorithm {
    uint8_t modSources[4];   // per-op bitmask of modulating ops
    uint8_t carriers;        // bitmask of carrier ops
};

// The classic eight (a practical subset of the DX topologies).
inline constexpr FmAlgorithm kFmAlgorithms[8] = {
    // 0: 3->2->1->0, carrier 0                       (deep stack)
    {{0x02, 0x04, 0x08, 0x00}, 0x01},
    // 1: 3->2, 2->0 and 1->0, carrier 0              (two mods into one)
    {{0x06, 0x00, 0x08, 0x00}, 0x01},
    // 2: 3->1, 2->0, carriers 0+1                    (two stacks)
    {{0x04, 0x08, 0x00, 0x00}, 0x03},
    // 3: 3->2->both 0 and 1, carriers 0+1
    {{0x04, 0x04, 0x08, 0x00}, 0x03},
    // 4: 3 mods everything, carriers 0+1+2           (bright bell)
    {{0x08, 0x08, 0x08, 0x00}, 0x07},
    // 5: 3->2, carriers 0+1+2                        (mostly additive)
    {{0x00, 0x00, 0x08, 0x00}, 0x07},
    // 6: 1->0, 3->2, carriers 0+2                    (two pairs)
    {{0x02, 0x00, 0x08, 0x00}, 0x05},
    // 7: all carriers                                 (organ/additive)
    {{0x00, 0x00, 0x00, 0x00}, 0x0F},
};

struct FmShared {                     // POD; migrating state body
    float algorithm = 2.0f;           // 0..7
    float feedback = 0.2f;            // op3 self-feedback 0..1
    float ratio[4] = {1.0f, 2.0f, 1.0f, 3.0f};
    float level[4] = {1.0f, 0.6f, 0.8f, 0.5f};
    float attackMs[4] = {2.0f, 2.0f, 2.0f, 2.0f};
    float decayMs[4] = {400.0f, 300.0f, 350.0f, 250.0f};
    float sustain[4] = {0.7f, 0.3f, 0.6f, 0.2f};
    float releaseMs[4] = {200.0f, 150.0f, 180.0f, 120.0f};
    float lfoRateHz = 5.0f;
    float lfoToPitchSemi = 0.0f;
    float velToAmp = 0.7f;
    float velToMod = 0.5f;            // velocity scales modulator levels
    float quality = 1.0f;
};

class FmVoice {
public:
    static constexpr int kCtrlInterval = 16;
    static constexpr float kModDepth = 6.2831853f;   // 2*pi phase-mod scale

    void prepare(double sampleRate) noexcept {
        rate_ = sampleRate;
        for (auto& e : env_) e.prepare(sampleRate);
        lfo_.prepare(sampleRate / kCtrlInterval);
        transientSamples_ = static_cast<int>(sampleRate * 0.03);
    }

    void start(uint16_t pitch, float velocity01, const FmShared& p) noexcept {
        pitch_ = pitch;
        releasing_ = false;
        ageSamples_ = 0;
        fbState_ = 0.0f;
        ampVel_ = 1.0f - p.velToAmp * (1.0f - velocity01);
        modVel_ = 1.0f - p.velToMod * (1.0f - velocity01);
        alg_ = static_cast<int>(p.algorithm + 0.5f) & 7;
        for (int op = 0; op < 4; ++op) {
            phase_[op] = 0.0;
            env_[op].setTimes(p.attackMs[op], p.decayMs[op], p.sustain[op],
                              p.releaseMs[op]);
            env_[op].noteOn();
        }
        lfo_.setFrequency(p.lfoRateHz);
    }

    // Carrier envelopes gate the voice's life.
    bool active() const noexcept {
        const uint8_t c = kFmAlgorithms[alg_].carriers;
        for (int op = 0; op < 4; ++op)
            if ((c >> op & 1) && env_[op].isActive()) return true;
        return false;
    }
    bool  releasing() const noexcept { return releasing_; }
    float level() const noexcept {
        const uint8_t c = kFmAlgorithms[alg_].carriers;
        float s = 0.0f;
        for (int op = 0; op < 4; ++op)
            if (c >> op & 1) s += env_[op].level();
        return s * ampVel_ * 0.25f;
    }
    bool inTransientWindow() const noexcept { return ageSamples_ < transientSamples_; }
    void beginRelease() noexcept {
        releasing_ = true;
        for (auto& e : env_) e.noteOff();
    }
    void fastRelease() noexcept {
        releasing_ = true;
        for (int op = 0; op < 4; ++op) {
            env_[op].setTimes(1.0f, 1.0f, 0.0f, 4.0f);
            env_[op].noteOff();
        }
    }
    void kill() noexcept {
        for (auto& e : env_) e.reset();
    }

    void renderAdd(float* l, float* r, int n, const FmShared& p) noexcept {
        const FmAlgorithm& alg = kFmAlgorithms[alg_];
        const float carrierNorm =
            1.0f / static_cast<float>(countBits(alg.carriers));

        for (int i = 0; i < n && active(); i += kCtrlInterval) {
            const int m = (n - i) < kCtrlInterval ? (n - i) : kCtrlInterval;
            const float lfoV = lfo_.process();
            const float note = static_cast<float>(pitch_) + lfoV * p.lfoToPitchSemi;
            const double baseInc =
                static_cast<double>(dsp::midiToHz(note)) / rate_;
            double inc[4];
            for (int op = 0; op < 4; ++op)
                inc[op] = baseInc * static_cast<double>(p.ratio[op]);

            for (int s = 0; s < m; ++s) {
                float out[4];
                // Highest op first: modulators are computed before targets.
                for (int op = 3; op >= 0; --op) {
                    float mod = 0.0f;
                    const uint8_t src = alg.modSources[op];
                    for (int j = op + 1; j < 4; ++j)
                        if (src >> j & 1) mod += out[j];
                    if (op == 3) mod += fbState_ * p.feedback;
                    const bool isCarrier = (alg.carriers >> op) & 1;
                    const float lvl = p.level[op] * (isCarrier ? 1.0f : modVel_);
                    out[op] = std::sin(static_cast<float>(
                                  phase_[op] * 6.2831853) + mod * kModDepth) *
                              env_[op].process() * lvl;
                    phase_[op] += inc[op];
                    phase_[op] -= std::floor(phase_[op]);
                }
                fbState_ = out[3];

                float v = 0.0f;
                for (int op = 0; op < 4; ++op)
                    if (alg.carriers >> op & 1) v += out[op];
                v *= carrierNorm * ampVel_;
                l[i + s] += v;
                r[i + s] += v;
            }
        }
        ageSamples_ += n;
    }

private:
    static int countBits(uint8_t b) noexcept {
        int c = 0;
        for (int i = 0; i < 4; ++i) c += (b >> i) & 1;
        return c == 0 ? 1 : c;
    }

    double rate_ = 48000.0;
    double phase_[4] = {0, 0, 0, 0};
    uint16_t pitch_ = 60;
    int alg_ = 2;
    bool releasing_ = false;
    int ageSamples_ = 0;
    int transientSamples_ = 1440;
    float ampVel_ = 1.0f;
    float modVel_ = 1.0f;
    float fbState_ = 0.0f;

    dsp::AdsrEnvelope env_[4];
    dsp::Lfo lfo_;
};

inline constexpr ParamDescriptor kFmParams[] = {
    {"fm.algorithm", "Algorithm", 0, 7, 2, ParamDescriptor::Curve::Switch, "", 0, true, false, false},
    {"fm.feedback", "Feedback", 0, 1, 0.2f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"op1.ratio", "Op1 Ratio", 0.25f, 12, 1, ParamDescriptor::Curve::Linear, "x", 0, true, false, false},
    {"op1.level", "Op1 Level", 0, 1, 1, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"op1.attack", "Op1 A", 0.1f, 5000, 2, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"op1.decay", "Op1 D", 1, 5000, 400, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"op1.sustain", "Op1 S", 0, 1, 0.7f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"op1.release", "Op1 R", 1, 8000, 200, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"op2.ratio", "Op2 Ratio", 0.25f, 12, 2, ParamDescriptor::Curve::Linear, "x", 0, true, false, false},
    {"op2.level", "Op2 Level", 0, 1, 0.6f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"op2.attack", "Op2 A", 0.1f, 5000, 2, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"op2.decay", "Op2 D", 1, 5000, 300, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"op2.sustain", "Op2 S", 0, 1, 0.3f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"op2.release", "Op2 R", 1, 8000, 150, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"op3.ratio", "Op3 Ratio", 0.25f, 12, 1, ParamDescriptor::Curve::Linear, "x", 0, true, false, false},
    {"op3.level", "Op3 Level", 0, 1, 0.8f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"op3.attack", "Op3 A", 0.1f, 5000, 2, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"op3.decay", "Op3 D", 1, 5000, 350, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"op3.sustain", "Op3 S", 0, 1, 0.6f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"op3.release", "Op3 R", 1, 8000, 180, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"op4.ratio", "Op4 Ratio", 0.25f, 12, 3, ParamDescriptor::Curve::Linear, "x", 0, true, false, false},
    {"op4.level", "Op4 Level", 0, 1, 0.5f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"op4.attack", "Op4 A", 0.1f, 5000, 2, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"op4.decay", "Op4 D", 1, 5000, 250, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"op4.sustain", "Op4 S", 0, 1, 0.2f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"op4.release", "Op4 R", 1, 8000, 120, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"lfo.rate", "LFO Rate", 0.05f, 30, 5, ParamDescriptor::Curve::Log, "Hz", 0, true, false, false},
    {"lfo.toPitch", "LFO>Pitch", 0, 12, 0, ParamDescriptor::Curve::Linear, "st", 0, true, false, false},
    {"velocity.toAmp", "Vel>Amp", 0, 1, 0.7f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"velocity.toMod", "Vel>Mod", 0, 1, 0.5f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"quality", "Quality", 0, 2, 1, ParamDescriptor::Curve::Switch, "", 0, true, true, true},
};

class FmSynth final
    : public PolyInstrument<FmVoice, FmShared,
                            /*StateVersion=*/1, /*PoolVoices=*/16,
                            /*DefaultPolyphony=*/8> {
public:
    static constexpr int kParamCount =
        static_cast<int>(sizeof(kFmParams) / sizeof(kFmParams[0]));

    int paramCount() const override { return kParamCount; }

    const ParamDescriptor& paramDescriptor(int i) const override {
        return kFmParams[i < 0 || i >= kParamCount ? 0 : i];
    }

    void setParamImmediate(int denseIndex, float plain) override {
        float* fields[kParamCount] = {
            &shared_.algorithm, &shared_.feedback,
            &shared_.ratio[0], &shared_.level[0], &shared_.attackMs[0],
            &shared_.decayMs[0], &shared_.sustain[0], &shared_.releaseMs[0],
            &shared_.ratio[1], &shared_.level[1], &shared_.attackMs[1],
            &shared_.decayMs[1], &shared_.sustain[1], &shared_.releaseMs[1],
            &shared_.ratio[2], &shared_.level[2], &shared_.attackMs[2],
            &shared_.decayMs[2], &shared_.sustain[2], &shared_.releaseMs[2],
            &shared_.ratio[3], &shared_.level[3], &shared_.attackMs[3],
            &shared_.decayMs[3], &shared_.sustain[3], &shared_.releaseMs[3],
            &shared_.lfoRateHz, &shared_.lfoToPitchSemi,
            &shared_.velToAmp, &shared_.velToMod, &shared_.quality,
        };
        if (denseIndex >= 0 && denseIndex < kParamCount)
            *fields[denseIndex] = plain;
    }
};

} // namespace daw
