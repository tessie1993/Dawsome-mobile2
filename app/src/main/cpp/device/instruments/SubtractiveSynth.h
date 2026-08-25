#pragma once

#include "../../dsp/AdsrEnvelope.h"
#include "../../dsp/DspMath.h"
#include "../../dsp/Lfo.h"
#include "../../dsp/NoiseGen.h"
#include "../../dsp/Oscillator.h"
#include "../../dsp/SvfFilter.h"
#include "../PolyInstrument.h"

// The first instrument (M4 "first sound"; blueprint device/instruments/
// SubtractiveSynth). Classic virtual-analog voice, per the researched
// convention: two polyBLEP oscillators (osc2 detune/semitones, mix knob) +
// noise -> Simper SVF lowpass with envelope amount (octaves), keyboard
// tracking and LFO -> analog-curve amp ADSR; velocity scales amplitude and
// filter-envelope depth.
//
// Voices are heap-free and trivially copyable; filter/LFO/pitch update at
// control rate (kCtrlInterval samples - their DSP objects are PREPARED at
// the control rate), the amp envelope and oscillators at audio rate.
//
// Note routing: process() consumes ctx.midiIn (the scheduler's per-track
// run) sample-accurately, splitting the block at event offsets. The
// scheduler's per-pass instance noteIds key the allocator; the seam-1
// VoiceInterface (live input) maps note number -> id. Voice admission asks
// the graph's VoiceBudgetLedger through the InstrumentNode hook.
//
// Migration: shared params migrate (State below); SOUNDING VOICES reset on
// structural rebuilds for now - full voice-state adoption is deferred and
// tracked in BUILD_LOG (rebuilds are rare edit-time events; a cut sustain
// at swap is the accepted M4 cost).

namespace daw {

struct SubtractiveShared {          // POD; also the migrating state body
    float osc1Wave = 1.0f;          // 0 sine, 1 saw, 2 pulse, 3 tri
    float osc2Wave = 1.0f;
    float osc2DetuneCents = 7.0f;
    float osc2Semitones = 0.0f;
    float oscMix = 0.35f;           // 0 = osc1 only
    float noiseLevel = 0.0f;
    float cutoffHz = 2500.0f;
    float resonanceQ = 0.707f;
    float filterEnvOct = 2.0f;      // octaves at env = 1
    float keyTrack = 0.5f;          // 0..1 of semitone-proportional tracking
    float ampAttackMs = 5.0f, ampDecayMs = 120.0f, ampSustain = 0.8f, ampReleaseMs = 150.0f;
    float filtAttackMs = 2.0f, filtDecayMs = 180.0f, filtSustain = 0.2f, filtReleaseMs = 200.0f;
    float lfoRateHz = 5.0f;
    float lfoToPitchSemi = 0.0f;
    float lfoToCutoffOct = 0.0f;
    float velToAmp = 0.6f;          // 0 = velocity ignored
    float velToFilter = 0.3f;
    float quality = 1.0f;           // QualityMode plain
};

class SubtractiveVoice {
public:
    static constexpr int kCtrlInterval = 16;

    void prepare(double sampleRate) noexcept {
        rate_ = sampleRate;
        osc1_.prepare(sampleRate);
        osc2_.prepare(sampleRate);
        filter_.prepare(sampleRate);
        filter_.setMode(dsp::SvfFilter::Mode::Lowpass);
        ampEnv_.prepare(sampleRate);
        const double ctrlRate = sampleRate / kCtrlInterval;
        filtEnv_.prepare(ctrlRate);
        lfo_.prepare(ctrlRate);
        transientSamples_ = static_cast<int>(sampleRate * 0.03);   // 30 ms window
    }

    void start(uint16_t pitch, float velocity01, const SubtractiveShared& p) noexcept {
        pitch_ = pitch;
        releasing_ = false;
        ageSamples_ = 0;
        ampVel_ = 1.0f - p.velToAmp * (1.0f - velocity01);
        filtVel_ = 1.0f - p.velToFilter * (1.0f - velocity01);
        osc1_.setWave(waveOf(p.osc1Wave));
        osc2_.setWave(waveOf(p.osc2Wave));
        osc1_.reset();
        osc2_.reset(0.13);                       // slight offset widens the stack
        filter_.reset();
        ampEnv_.setTimes(p.ampAttackMs, p.ampDecayMs, p.ampSustain, p.ampReleaseMs);
        filtEnv_.setTimes(p.filtAttackMs, p.filtDecayMs, p.filtSustain, p.filtReleaseMs);
        lfo_.setFrequency(p.lfoRateHz);
        ampEnv_.noteOn();
        filtEnv_.noteOn();
    }

    // ---- VoiceAllocator VoiceT contract -------------------------------------
    bool  active() const noexcept { return ampEnv_.isActive(); }
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
        ampEnv_.setTimes(1.0f, 1.0f, 0.0f, 4.0f);   // steal fade
        ampEnv_.noteOff();
        filtEnv_.noteOff();
    }
    void  kill() noexcept { ampEnv_.reset(); filtEnv_.reset(); }

    // ---- render [RT]: adds this voice into l/r ------------------------------
    void renderAdd(float* l, float* r, int n, const SubtractiveShared& p) noexcept {
        const float mix = dsp::clamp01(p.oscMix);
        for (int i = 0; i < n && ampEnv_.isActive(); i += kCtrlInterval) {
            const int m = (n - i) < kCtrlInterval ? (n - i) : kCtrlInterval;

            // Control-rate: pitch, filter, LFO, filter envelope.
            const float lfoV = lfo_.process();
            const float fEnv = filtEnv_.process();
            const float baseNote = static_cast<float>(pitch_) +
                                   lfoV * p.lfoToPitchSemi;
            osc1_.setFrequency(dsp::midiToHz(baseNote));
            osc2_.setFrequency(dsp::midiToHz(baseNote + p.osc2Semitones +
                                             p.osc2DetuneCents * 0.01f));
            const float octaves =
                p.keyTrack * (static_cast<float>(pitch_) - 60.0f) / 12.0f +
                p.filterEnvOct * fEnv * filtVel_ +
                p.lfoToCutoffOct * lfoV;
            float cutoff = p.cutoffHz * std::exp2(octaves);
            const float maxHz = static_cast<float>(rate_) * 0.45f;
            cutoff = dsp::clamp(cutoff, 20.0f, maxHz);
            filter_.setParams(cutoff, dsp::clamp(p.resonanceQ, 0.5f, 12.0f));

            for (int s = 0; s < m; ++s) {
                const float a = ampEnv_.process() * ampVel_;
                float o = osc1_.process() * (1.0f - mix) + osc2_.process() * mix;
                if (p.noiseLevel > 0.0f) o += noise_.white() * p.noiseLevel;
                const float v = filter_.process(o) * a;
                l[i + s] += v;
                r[i + s] += v;
            }
        }
        ageSamples_ += n;
    }

private:
    static dsp::Oscillator::Wave waveOf(float plain) noexcept {
        const int w = static_cast<int>(plain + 0.5f);
        switch (w) {
            case 0: return dsp::Oscillator::Wave::Sine;
            case 2: return dsp::Oscillator::Wave::Pulse;
            case 3: return dsp::Oscillator::Wave::Triangle;
            default: return dsp::Oscillator::Wave::Saw;
        }
    }

    double rate_ = 48000.0;
    uint16_t pitch_ = 60;
    bool releasing_ = false;
    int ageSamples_ = 0;
    int transientSamples_ = 1440;
    float ampVel_ = 1.0f;
    float filtVel_ = 1.0f;

    dsp::Oscillator osc1_;
    dsp::Oscillator osc2_;
    dsp::NoiseGen noise_;
    dsp::SvfFilter filter_;
    dsp::AdsrEnvelope ampEnv_;
    dsp::AdsrEnvelope filtEnv_;
    dsp::Lfo lfo_;
};

// The canonical descriptor set (registry-registered; hostside collision
// checked at registration).
inline constexpr ParamDescriptor kSubtractiveParams[] = {
    {"osc1.wave", "Osc 1 Wave", 0, 3, 1, ParamDescriptor::Curve::Switch, "", 0, true, false, false},
    {"osc2.wave", "Osc 2 Wave", 0, 3, 1, ParamDescriptor::Curve::Switch, "", 0, true, false, false},
    {"osc2.detune", "Osc 2 Detune", -50, 50, 7, ParamDescriptor::Curve::Linear, "ct", 0, true, false, false},
    {"osc2.semi", "Osc 2 Semi", -24, 24, 0, ParamDescriptor::Curve::Linear, "st", 0, true, false, false},
    {"osc.mix", "Osc Mix", 0, 1, 0.35f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"noise.level", "Noise", 0, 1, 0, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"filter.cutoff", "Cutoff", 20, 16000, 2500, ParamDescriptor::Curve::Log, "Hz", 0, true, false, false},
    {"filter.resonance", "Resonance", 0.5f, 12, 0.707f, ParamDescriptor::Curve::Linear, "Q", 0, true, false, false},
    {"filter.envAmount", "Filter Env", -5, 5, 2, ParamDescriptor::Curve::Linear, "oct", 0, true, false, false},
    {"filter.keyTrack", "Key Track", 0, 1, 0.5f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"ampenv.attack", "Amp Attack", 0.1f, 5000, 5, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"ampenv.decay", "Amp Decay", 1, 5000, 120, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"ampenv.sustain", "Amp Sustain", 0, 1, 0.8f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"ampenv.release", "Amp Release", 1, 8000, 150, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"filtenv.attack", "Filt Attack", 0.1f, 5000, 2, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"filtenv.decay", "Filt Decay", 1, 5000, 180, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"filtenv.sustain", "Filt Sustain", 0, 1, 0.2f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"filtenv.release", "Filt Release", 1, 8000, 200, ParamDescriptor::Curve::Log, "ms", 0, true, false, false},
    {"lfo.rate", "LFO Rate", 0.05f, 30, 5, ParamDescriptor::Curve::Log, "Hz", 0, true, false, false},
    {"lfo.toPitch", "LFO>Pitch", 0, 12, 0, ParamDescriptor::Curve::Linear, "st", 0, true, false, false},
    {"lfo.toCutoff", "LFO>Cutoff", -4, 4, 0, ParamDescriptor::Curve::Linear, "oct", 0, true, false, false},
    {"velocity.toAmp", "Vel>Amp", 0, 1, 0.6f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"velocity.toFilter", "Vel>Filter", 0, 1, 0.3f, ParamDescriptor::Curve::Linear, "", 0, true, false, false},
    {"quality", "Quality", 0, 2, 1, ParamDescriptor::Curve::Switch, "", 0, true, true, true},
};

class SubtractiveSynth final
    : public PolyInstrument<SubtractiveVoice, SubtractiveShared,
                            /*StateVersion=*/1, /*PoolVoices=*/16,
                            /*DefaultPolyphony=*/8> {
public:
    static constexpr int kParamCount =
        static_cast<int>(sizeof(kSubtractiveParams) / sizeof(kSubtractiveParams[0]));

    int paramCount() const override { return kParamCount; }

    const ParamDescriptor& paramDescriptor(int i) const override {
        return kSubtractiveParams[i < 0 || i >= kParamCount ? 0 : i];
    }

    void setParamImmediate(int denseIndex, float plain) override {
        float* fields[] = {
            &shared_.osc1Wave, &shared_.osc2Wave, &shared_.osc2DetuneCents,
            &shared_.osc2Semitones, &shared_.oscMix, &shared_.noiseLevel,
            &shared_.cutoffHz, &shared_.resonanceQ, &shared_.filterEnvOct,
            &shared_.keyTrack,
            &shared_.ampAttackMs, &shared_.ampDecayMs, &shared_.ampSustain,
            &shared_.ampReleaseMs,
            &shared_.filtAttackMs, &shared_.filtDecayMs, &shared_.filtSustain,
            &shared_.filtReleaseMs,
            &shared_.lfoRateHz, &shared_.lfoToPitchSemi, &shared_.lfoToCutoffOct,
            &shared_.velToAmp, &shared_.velToFilter, &shared_.quality,
        };
        static_assert(sizeof(fields) / sizeof(fields[0]) == size_t(kParamCount));
        if (denseIndex >= 0 && denseIndex < kParamCount)
            *fields[denseIndex] = plain;
    }
};

} // namespace daw
