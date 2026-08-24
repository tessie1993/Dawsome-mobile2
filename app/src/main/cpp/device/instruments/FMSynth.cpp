#include "FMSynth.h"
#include <cmath>
#include <algorithm>

FMSynth::FMSynth(std::string id)
    : InstrumentNode(std::move(id)) {
    // Default Operator settings
    opConfigs_[0] = {1.0f, 1.0f, 0.0f, 0.005f, 0.4f, 0.8f, 0.3f}; // Carrier 1
    opConfigs_[1] = {2.0f, 0.8f, 0.2f, 0.005f, 0.3f, 0.4f, 0.2f}; // Modulator 1
    opConfigs_[2] = {1.0f, 0.6f, 0.0f, 0.01f, 0.5f, 0.0f, 0.2f};  // Modulator 2
    opConfigs_[3] = {4.0f, 0.4f, 0.0f, 0.005f, 0.2f, 0.0f, 0.1f};  // Modulator 3
}

void FMSynth::prepareToPlay(double sampleRate, size_t /*maxBlockSize*/) {
    sampleRate_ = sampleRate;
    releaseResources();
}

void FMSynth::releaseResources() {
    for (auto& voice : voices_) {
        voice.reset();
    }
}

void FMSynth::setAlgorithm(int algorithm) {
    algorithm_ = std::clamp(algorithm, 0, 7);
}

void FMSynth::setOperatorRatio(size_t op, float ratio) {
    if (op < NUM_OPS) opConfigs_[op].ratio = std::clamp(ratio, 0.25f, 32.0f);
}

void FMSynth::setOperatorLevel(size_t op, float level) {
    if (op < NUM_OPS) opConfigs_[op].level = std::clamp(level, 0.0f, 1.0f);
}

void FMSynth::setOperatorFeedback(size_t op, float fb) {
    if (op < NUM_OPS) opConfigs_[op].feedback = std::clamp(fb, 0.0f, 1.0f);
}

void FMSynth::noteOn(int noteNumber, float velocity) {
    if (velocity <= 0.0f) {
        noteOff(noteNumber);
        return;
    }

    FMVoice* targetVoice = nullptr;
    for (auto& v : voices_) {
        if (!v.active) {
            targetVoice = &v;
            break;
        }
    }
    if (!targetVoice) targetVoice = &voices_[0];

    targetVoice->noteNumber = noteNumber;
    targetVoice->velocity = velocity;
    targetVoice->phases.fill(0.0f);
    targetVoice->lastOutputs.fill(0.0f);
    targetVoice->active = true;

    for (size_t op = 0; op < NUM_OPS; ++op) {
        targetVoice->envs[op].attackSec = opConfigs_[op].attackSec;
        targetVoice->envs[op].decaySec = opConfigs_[op].decaySec;
        targetVoice->envs[op].sustainLevel = opConfigs_[op].sustainLevel;
        targetVoice->envs[op].releaseSec = opConfigs_[op].releaseSec;
        targetVoice->envs[op].trigger();
    }
}

void FMSynth::noteOff(int noteNumber) {
    for (auto& v : voices_) {
        if (v.active && v.noteNumber == noteNumber) {
            for (auto& env : v.envs) env.release();
        }
    }
}

void FMSynth::allNotesOff() {
    for (auto& v : voices_) {
        for (auto& env : v.envs) env.release();
    }
}

void FMSynth::setPitchBend(float bendSemitones) {
    pitchBendSemitones_ = std::clamp(bendSemitones, -24.0f, 24.0f);
}

void FMSynth::setModWheel(float modWheel) {
    modWheel_ = std::clamp(modWheel, 0.0f, 1.0f);
}

void FMSynth::process(const ProcessContext& ctx, float** /*inBuffers*/, float** outBuffers) {
    float* outL = outBuffers[0];
    float* outR = (ctx.numChannels > 1) ? outBuffers[1] : outBuffers[0];

    std::fill_n(outL, ctx.numFrames, 0.0f);
    std::fill_n(outR, ctx.numFrames, 0.0f);

    if (!isEnabled_) return;

    constexpr float TWO_PI = 6.283185307179586f;

    for (auto& v : voices_) {
        if (!v.active) continue;

        float baseFreq = 440.0f * std::pow(2.0f, (v.noteNumber + pitchBendSemitones_ - 69.0f) / 12.0f);

        for (size_t i = 0; i < ctx.numFrames; ++i) {
            float carrierEnv = v.envs[0].getNext(sampleRate_);
            if (!v.envs[0].isActive()) {
                v.active = false;
                break;
            }

            std::array<float, NUM_OPS> opOutputs;

            // Compute operators based on algorithm
            // Algorithm 0: Op3 -> Op2 -> Op1 -> Op0 (Classic Serial Cascade)
            float env3 = v.envs[3].getNext(sampleRate_) * opConfigs_[3].level;
            float freq3 = baseFreq * opConfigs_[3].ratio;
            v.phases[3] += freq3 / static_cast<float>(sampleRate_);
            if (v.phases[3] >= 1.0f) v.phases[3] -= 1.0f;
            opOutputs[3] = std::sin(v.phases[3] * TWO_PI) * env3;

            float env2 = v.envs[2].getNext(sampleRate_) * opConfigs_[2].level;
            float freq2 = baseFreq * opConfigs_[2].ratio;
            v.phases[2] += freq2 / static_cast<float>(sampleRate_);
            if (v.phases[2] >= 1.0f) v.phases[2] -= 1.0f;
            opOutputs[2] = std::sin((v.phases[2] * TWO_PI) + (opOutputs[3] * 3.0f)) * env2;

            float env1 = v.envs[1].getNext(sampleRate_) * opConfigs_[1].level;
            float freq1 = baseFreq * opConfigs_[1].ratio;
            v.phases[1] += freq1 / static_cast<float>(sampleRate_);
            if (v.phases[1] >= 1.0f) v.phases[1] -= 1.0f;
            float modAmount = (opOutputs[2] * 3.0f) + (modWheel_ * 2.0f);
            opOutputs[1] = std::sin((v.phases[1] * TWO_PI) + modAmount) * env1;

            float freq0 = baseFreq * opConfigs_[0].ratio;
            v.phases[0] += freq0 / static_cast<float>(sampleRate_);
            if (v.phases[0] >= 1.0f) v.phases[0] -= 1.0f;
            opOutputs[0] = std::sin((v.phases[0] * TWO_PI) + (opOutputs[1] * 4.0f)) * carrierEnv * opConfigs_[0].level;

            float voiceOut = opOutputs[0] * v.velocity * 0.25f;
            outL[i] += voiceOut;
            outR[i] += voiceOut;
        }
    }
}

void FMSynth::setParameter(const std::string& paramName, float value) {
    if (paramName == "algorithm") setAlgorithm(static_cast<int>(value));
    else if (paramName == "op1_level") setOperatorLevel(1, value);
    else if (paramName == "op2_level") setOperatorLevel(2, value);
    else if (paramName == "op3_level") setOperatorLevel(3, value);
    else if (paramName == "op1_ratio") setOperatorRatio(1, value);
    else if (paramName == "op2_ratio") setOperatorRatio(2, value);
    else if (paramName == "op3_ratio") setOperatorRatio(3, value);
}

float FMSynth::getParameter(const std::string& paramName) const {
    if (paramName == "algorithm") return static_cast<float>(algorithm_);
    if (paramName == "op1_level") return opConfigs_[1].level;
    if (paramName == "op2_level") return opConfigs_[2].level;
    if (paramName == "op3_level") return opConfigs_[3].level;
    if (paramName == "op1_ratio") return opConfigs_[1].ratio;
    if (paramName == "op2_ratio") return opConfigs_[2].ratio;
    if (paramName == "op3_ratio") return opConfigs_[3].ratio;
    return 0.0f;
}
