#pragma once

#include <cmath>

#include "../../dsp/AdsrEnvelope.h"
#include "../../dsp/DspMath.h"
#include "../../dsp/SvfFilter.h"
#include "../../media/SampleBuffer.h"
#include "../PolyInstrument.h"

// Melodic one-sample player (blueprint device/instruments/SimpleSampler,
// DeviceTypeId 3 "Sampler"). Zones/velocity layers/SFZ import are the
// MultiSampler's milestone; this is the single-sample workhorse: pitched
// stereo playback -> Simper SVF lowpass -> amp ADSR.
//
// Sample residency protocol (the seam decision of this feature):
//   - The MODEL owns the assignment (fileId); when the media-library wire
//     lands, the GraphBuilder resolves it and calls setSample() with a
//     cache-pinned handle at COMPILE time, per node instance. Handles are
//     normal refcounted members - they never ride the POD migration
//     (memcpy would bypass the refcount); SamplerShared carries the fileId
//     as bookkeeping only. RT never touches the SampleCache.
//   - Voices read a raw const SampleBuffer* distributed by setSample();
//     the instrument's handle outlives its voices, and the cache never
//     evicts pinned entries, so the pointer is RT-safe by construction.
//   - The cache conforms samples to the DEVICE rate at load (D5), so the
//     playback ratio is purely musical: 2^((note - root + tune)/12).
//
// Until assignment arrives the sampler is a silent, fully-parameterized
// instrument (same posture as DrumPad's Sample mode).

namespace daw {

struct SamplerShared {                 // POD; migrating state body
    uint64_t fileId = 0;               // bookkeeping mirror of the model
    float rootNote = 60.0f;            // sample's recorded pitch
    float tuneSemi = 0.0f;
    float tuneCents = 0.0f;
    float startNorm = 0.0f;            // playback start, 0..1 of length
    float loopMode = 0.0f;             // 0 one-shot, 1 forward loop
    float loopStartNorm = 0.25f;       // 0..1, clamped sane at voice start
    float loopEndNorm = 1.0f;
    float cutoffHz = 12000.0f;
    float resonanceQ = 0.707f;
    float filterEnvOct = 0.0f;
    float keyTrack = 0.3f;
    float ampAttackMs = 2.0f, ampDecayMs = 400.0f, ampSustain = 1.0f, ampReleaseMs = 250.0f;
    float filtAttackMs = 2.0f, filtDecayMs = 300.0f, filtSustain = 1.0f, filtReleaseMs = 250.0f;
    float velToAmp = 0.7f;
    float velToFilter = 0.2f;
    float quality = 1.0f;
};

class SamplerVoice {
public:
    static constexpr int kCtrlInterval = 16;

    void prepare(double sampleRate) noexcept {
        rate_ = sampleRate;
        filterL_.prepare(sampleRate);
        filterL_.setMode(dsp::SvfFilter::Mode::Lowpass);
        filterR_.prepare(sampleRate);
        filterR_.setMode(dsp::SvfFilter::Mode::Lowpass);
        ampEnv_.prepare(sampleRate);
        filtEnv_.prepare(sampleRate / kCtrlInterval);
        transientSamples_ = static_cast<int>(sampleRate * 0.03);
    }

    void setBuffer(const SampleBuffer* b) noexcept { buf_ = b; }

    void start(uint16_t pitch, float velocity01, const SamplerShared& p) noexcept {
        pitch_ = pitch;
        releasing_ = false;
        ageSamples_ = 0;
        ampVel_ = 1.0f - p.velToAmp * (1.0f - velocity01);
        filtVel_ = 1.0f - p.velToFilter * (1.0f - velocity01);

        const double frames = buf_ != nullptr ? double(buf_->frames) : 0.0;
        pos_ = dsp::clamp01(p.startNorm) * frames;
        // Loop points clamped to a sane, ordered window at note start; a
        // degenerate window (< 32 frames) disables looping for this note.
        loopStart_ = dsp::clamp01(p.loopStartNorm) * frames;
        loopEnd_ = dsp::clamp01(p.loopEndNorm) * frames;
        looping_ = p.loopMode >= 0.5f && (loopEnd_ - loopStart_) >= 32.0;
        const double semis = double(pitch) - double(p.rootNote) +
                             double(p.tuneSemi) + double(p.tuneCents) * 0.01;
        ratio_ = std::exp2(semis / 12.0);

        filterL_.reset();
        filterR_.reset();
        ampEnv_.setTimes(p.ampAttackMs, p.ampDecayMs, p.ampSustain, p.ampReleaseMs);
        filtEnv_.setTimes(p.filtAttackMs, p.filtDecayMs, p.filtSustain, p.filtReleaseMs);
        ampEnv_.noteOn();
        filtEnv_.noteOn();
        active_ = buf_ != nullptr && buf_->frames > 1;
    }

    bool  active() const noexcept { return active_ && ampEnv_.isActive(); }
    bool  releasing() const noexcept { return releasing_; }
    float level() const noexcept { return ampEnv_.level() * ampVel_; }
    bool  inTransientWindow() const noexcept { return ageSamples_ < transientSamples_; }
    void  beginRelease() noexcept {
        releasing_ = true;
        ampEnv_.noteOff();
        filtEnv_.noteOff();
    }
    void  fastRelease() noexcept {
        releasing_ = true;
        ampEnv_.setTimes(1.0f, 1.0f, 0.0f, 4.0f);
        ampEnv_.noteOff();
    }
    void  kill() noexcept { active_ = false; ampEnv_.reset(); filtEnv_.reset(); }

    void renderAdd(float* l, float* r, int n, const SamplerShared& p) noexcept {
        if (!active_ || buf_ == nullptr) return;
        const double lastFrame = double(buf_->frames - 1);
        const float* chL = buf_->channel(0);
        const float* chR = buf_->channel(1);

        for (int i = 0; i < n && active_ && ampEnv_.isActive(); i += kCtrlInterval) {
            const int m = (n - i) < kCtrlInterval ? (n - i) : kCtrlInterval;

            const float fEnv = filtEnv_.process();
            const float octaves =
                p.keyTrack * (float(pitch_) - 60.0f) / 12.0f +
                p.filterEnvOct * fEnv * filtVel_;
            float cutoff = p.cutoffHz * std::exp2(octaves);
            cutoff = dsp::clamp(cutoff, 20.0f, float(rate_) * 0.45f);
            const float q = dsp::clamp(p.resonanceQ, 0.5f, 12.0f);
            filterL_.setParams(cutoff, q);
            filterR_.setParams(cutoff, q);

            for (int s = 0; s < m; ++s) {
                if (pos_ >= lastFrame) {
                    if (looping_) {
                        pos_ = loopStart_ + (pos_ - loopEnd_);
                        if (pos_ < loopStart_ || pos_ >= loopEnd_) pos_ = loopStart_;
                    } else {
                        active_ = false;
                        break;
                    }
                }
                if (looping_ && pos_ >= loopEnd_) {
                    pos_ = loopStart_ + (pos_ - loopEnd_);
                    if (pos_ < loopStart_ || pos_ >= loopEnd_) pos_ = loopStart_;
                }
                const int64_t i0 = static_cast<int64_t>(pos_);
                const float fr = static_cast<float>(pos_ - double(i0));
                const float a = ampEnv_.process() * ampVel_;
                const float sl = chL[i0] + (chL[i0 + 1] - chL[i0]) * fr;
                const float sr = chR[i0] + (chR[i0 + 1] - chR[i0]) * fr;
                l[i + s] += filterL_.process(sl) * a;
                r[i + s] += filterR_.process(sr) * a;
                pos_ += ratio_;
            }
        }
        ageSamples_ += n;
    }

private:
    double rate_ = 48000.0;
    const SampleBuffer* buf_ = nullptr;
    double pos_ = 0.0;
    double ratio_ = 1.0;
    double loopStart_ = 0.0;
    double loopEnd_ = 0.0;
    bool looping_ = false;
    bool active_ = false;
    bool releasing_ = false;
    uint16_t pitch_ = 60;
    int ageSamples_ = 0;
    int transientSamples_ = 1440;
    float ampVel_ = 1.0f;
    float filtVel_ = 1.0f;

    dsp::SvfFilter filterL_;
    dsp::SvfFilter filterR_;
    dsp::AdsrEnvelope ampEnv_;
    dsp::AdsrEnvelope filtEnv_;
};

inline constexpr ParamDescriptor kSamplerParams[] = {
    {"sample.root", "Root Note", 0, 127, 60, ParamDescriptor::Curve::Linear, "st", 0, true, true, false},
    {"sample.tune", "Tune", -24, 24, 0, ParamDescriptor::Curve::Linear, "st", 0, true, false, false},
    {"sample.fine", "Fine", -100, 100, 0, ParamDescriptor::Curve::Linear, "ct", 0, true, false, false},
    {"sample.start", "Start", 0, 1, 0, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"loop.mode", "Loop", 0, 1, 0, ParamDescriptor::Curve::Switch, "", 0, true, true, false},
    {"loop.start", "Loop Start", 0, 1, 0.25f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"loop.end", "Loop End", 0, 1, 1, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"filter.cutoff", "Cutoff", 20, 16000, 12000, ParamDescriptor::Curve::Log, "Hz", 0, true, false, false},
    {"filter.resonance", "Resonance", 0.5f, 12, 0.707f, ParamDescriptor::Curve::Linear, "Q", 0, true, false, false},
    {"filter.envAmount", "Filter Env", -5, 5, 0, ParamDescriptor::Curve::Linear, "oct", 0, true, false, false},
    {"filter.keyTrack", "Key Track", 0, 1, 0.3f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"ampenv.attack", "Amp Attack", 0.1f, 5000, 2, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"ampenv.decay", "Amp Decay", 1, 5000, 400, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"ampenv.sustain", "Amp Sustain", 0, 1, 1, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"ampenv.release", "Amp Release", 1, 8000, 250, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"filtenv.attack", "Filt Attack", 0.1f, 5000, 2, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"filtenv.decay", "Filt Decay", 1, 5000, 300, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"filtenv.sustain", "Filt Sustain", 0, 1, 1, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"filtenv.release", "Filt Release", 1, 8000, 250, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"velocity.toAmp", "Vel>Amp", 0, 1, 0.7f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"velocity.toFilter", "Vel>Filter", 0, 1, 0.2f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"quality", "Quality", 0, 2, 1, ParamDescriptor::Curve::Switch, "", 0, true, true, true},
};

class SimpleSampler final
    : public PolyInstrument<SamplerVoice, SamplerShared,
                            /*StateVersion=*/1, /*PoolVoices=*/16,
                            /*DefaultPolyphony=*/8> {
public:
    static constexpr int kParamCount =
        static_cast<int>(sizeof(kSamplerParams) / sizeof(kSamplerParams[0]));

    // [builder, pre-install] Model-resolved assignment: pin + distribute.
    void setSample(FileId id, SampleHandle handle) noexcept {
        shared_.fileId = id;
        sample_ = static_cast<SampleHandle&&>(handle);
        const SampleBuffer* raw = sample_.get();
        for (auto& slot : voices_) slot.voice.setBuffer(raw);
    }

    int paramCount() const override { return kParamCount; }

    const ParamDescriptor& paramDescriptor(int i) const override {
        return kSamplerParams[i < 0 || i >= kParamCount ? 0 : i];
    }

    void setParamImmediate(int denseIndex, float plain) override {
        float* fields[kParamCount] = {
            &shared_.rootNote, &shared_.tuneSemi, &shared_.tuneCents,
            &shared_.startNorm,
            &shared_.loopMode, &shared_.loopStartNorm, &shared_.loopEndNorm,
            &shared_.cutoffHz, &shared_.resonanceQ, &shared_.filterEnvOct,
            &shared_.keyTrack,
            &shared_.ampAttackMs, &shared_.ampDecayMs, &shared_.ampSustain,
            &shared_.ampReleaseMs,
            &shared_.filtAttackMs, &shared_.filtDecayMs, &shared_.filtSustain,
            &shared_.filtReleaseMs,
            &shared_.velToAmp, &shared_.velToFilter, &shared_.quality,
        };
        if (denseIndex >= 0 && denseIndex < kParamCount)
            *fields[denseIndex] = plain;
    }

private:
    SampleHandle sample_;
};

} // namespace daw
