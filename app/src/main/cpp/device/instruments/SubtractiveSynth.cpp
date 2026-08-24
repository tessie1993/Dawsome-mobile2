#include "SubtractiveSynth.h"
#include <cmath>
#include <algorithm>

SubtractiveSynth::SubtractiveSynth(std::string id)
    : InstrumentNode(std::move(id)) {
}

void SubtractiveSynth::prepareToPlay(double sampleRate, size_t /*maxBlockSize*/) {
    sampleRate_ = sampleRate;
    releaseResources();
}

void SubtractiveSynth::releaseResources() {
    for (auto& voice : voices_) {
        voice.reset();
    }
}

void SubtractiveSynth::noteOn(int noteNumber, float velocity) {
    if (velocity <= 0.0f) {
        noteOff(noteNumber);
        return;
    }

    // Find free voice or steal oldest
    SubtractiveVoice* targetVoice = nullptr;
    for (auto& v : voices_) {
        if (!v.active) {
            targetVoice = &v;
            break;
        }
    }
    if (!targetVoice) {
        targetVoice = &voices_[0]; // Voice stealing fallback
    }

    targetVoice->noteNumber = noteNumber;
    targetVoice->velocity = velocity;
    targetVoice->phase1 = 0.0f;
    targetVoice->phase2 = 0.0f;
    targetVoice->active = true;
    targetVoice->ampEnv.trigger();
    targetVoice->filterEnv.trigger();
    targetVoice->filter.reset();
}

void SubtractiveSynth::noteOff(int noteNumber) {
    for (auto& v : voices_) {
        if (v.active && v.noteNumber == noteNumber) {
            v.ampEnv.release();
            v.filterEnv.release();
        }
    }
}

void SubtractiveSynth::allNotesOff() {
    for (auto& v : voices_) {
        v.ampEnv.release();
        v.filterEnv.release();
    }
}

void SubtractiveSynth::setPitchBend(float bendSemitones) {
    pitchBendSemitones_ = std::clamp(bendSemitones, -24.0f, 24.0f);
}

void SubtractiveSynth::setModWheel(float modWheel) {
    modWheel_ = std::clamp(modWheel, 0.0f, 1.0f);
}

void SubtractiveSynth::setFilterCutoff(float hz) {
    filterCutoffHz_ = std::clamp(hz, 20.0f, 20000.0f);
}

void SubtractiveSynth::setFilterResonance(float reso) {
    filterResonance_ = std::clamp(reso, 0.0f, 0.98f);
}

static inline float renderWave(SynthWaveform wave, float phase) {
    switch (wave) {
        case SynthWaveform::SAW:
            return 2.0f * phase - 1.0f;
        case SynthWaveform::PULSE:
            return (phase < 0.5f) ? 1.0f : -1.0f;
        case SynthWaveform::TRIANGLE:
            return 4.0f * std::abs(phase - 0.5f) - 1.0f;
        case SynthWaveform::SINE:
            return std::sin(phase * 2.0f * static_cast<float>(M_PI));
        case SynthWaveform::NOISE:
            return ((static_cast<float>(rand()) / RAND_MAX) * 2.0f) - 1.0f;
    }
    return 0.0f;
}

void SubtractiveSynth::process(const ProcessContext& ctx, float** /*inBuffers*/, float** outBuffers) {
    float* outL = outBuffers[0];
    float* outR = (ctx.numChannels > 1) ? outBuffers[1] : outBuffers[0];

    std::fill_n(outL, ctx.numFrames, 0.0f);
    std::fill_n(outR, ctx.numFrames, 0.0f);

    if (!isEnabled_) return;

    for (auto& v : voices_) {
        if (!v.active) continue;

        float baseFreq = 440.0f * std::pow(2.0f, (v.noteNumber + pitchBendSemitones_ - 69.0f) / 12.0f);
        float osc2Freq = 440.0f * std::pow(2.0f, (v.noteNumber + pitchBendSemitones_ + osc2DetuneSemitones_ - 69.0f) / 12.0f);

        float phaseInc1 = baseFreq / static_cast<float>(sampleRate_);
        float phaseInc2 = osc2Freq / static_cast<float>(sampleRate_);

        for (size_t i = 0; i < ctx.numFrames; ++i) {
            float amp = v.ampEnv.getNext(sampleRate_);
            if (!v.ampEnv.isActive()) {
                v.active = false;
                break;
            }

            float osc1 = renderWave(osc1Wave_, v.phase1);
            float osc2 = renderWave(osc2Wave_, v.phase2);

            v.phase1 += phaseInc1;
            if (v.phase1 >= 1.0f) v.phase1 -= 1.0f;

            v.phase2 += phaseInc2;
            if (v.phase2 >= 1.0f) v.phase2 -= 1.0f;

            float rawSignal = (osc1 * (1.0f - oscMix_)) + (osc2 * oscMix_);

            // Filter Modulation
            float fEnv = v.filterEnv.getNext(sampleRate_);
            float cutoff = filterCutoffHz_ + (fEnv * filterEnvAmount_) + (modWheel_ * 2000.0f);
            cutoff = std::clamp(cutoff, 20.0f, static_cast<float>(sampleRate_ * 0.45));

            float filtered = v.filter.process(rawSignal, cutoff, filterResonance_, sampleRate_);

            // Soft saturation clip
            filtered = std::tanh(filtered * 1.2f);

            float voiceSample = filtered * amp * v.velocity * 0.25f;

            outL[i] += voiceSample;
            outR[i] += voiceSample;
        }
    }
}

void SubtractiveSynth::setParameter(const std::string& paramName, float value) {
    if (paramName == "cutoff") setFilterCutoff(value);
    else if (paramName == "resonance") setFilterResonance(value);
    else if (paramName == "filter_env") filterEnvAmount_ = value;
    else if (paramName == "osc_mix") oscMix_ = std::clamp(value, 0.0f, 1.0f);
    else if (paramName == "osc2_detune") osc2DetuneSemitones_ = value;
    else if (paramName == "amp_attack") for (auto& v : voices_) v.ampEnv.attackSec = std::max(value, 0.001f);
    else if (paramName == "amp_decay") for (auto& v : voices_) v.ampEnv.decaySec = std::max(value, 0.01f);
    else if (paramName == "amp_sustain") for (auto& v : voices_) v.ampEnv.sustainLevel = std::clamp(value, 0.0f, 1.0f);
    else if (paramName == "amp_release") for (auto& v : voices_) v.ampEnv.releaseSec = std::max(value, 0.01f);
}

float SubtractiveSynth::getParameter(const std::string& paramName) const {
    if (paramName == "cutoff") return filterCutoffHz_;
    if (paramName == "resonance") return filterResonance_;
    if (paramName == "filter_env") return filterEnvAmount_;
    if (paramName == "osc_mix") return oscMix_;
    if (paramName == "osc2_detune") return osc2DetuneSemitones_;
    if (paramName == "amp_attack") return voices_[0].ampEnv.attackSec;
    if (paramName == "amp_decay") return voices_[0].ampEnv.decaySec;
    if (paramName == "amp_sustain") return voices_[0].ampEnv.sustainLevel;
    if (paramName == "amp_release") return voices_[0].ampEnv.releaseSec;
    return 0.0f;
}
