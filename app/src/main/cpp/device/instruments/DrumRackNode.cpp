#include "DrumRackNode.h"
#include <cmath>
#include <algorithm>

DrumRackNode::DrumRackNode(std::string id)
    : InstrumentNode(std::move(id)) {
    // Set up standard pad choke groups (e.g. Pad 3 Closed Hat and Pad 4 Open Hat in Choke Group 1)
    pads_[3].chokeGroup = 1;
    pads_[4].chokeGroup = 1;
}

void DrumRackNode::prepareToPlay(double sampleRate, size_t /*maxBlockSize*/) {
    sampleRate_ = sampleRate;
    releaseResources();
}

void DrumRackNode::releaseResources() {
    for (auto& pad : pads_) {
        pad.choke();
    }
}

void DrumRackNode::setPadSample(size_t padIndex, const float* buffer, size_t numFrames) {
    if (padIndex < NUM_PADS) {
        pads_[padIndex].sampleData.assign(buffer, buffer + numFrames);
    }
}

void DrumRackNode::setPadChokeGroup(size_t padIndex, int chokeGroup) {
    if (padIndex < NUM_PADS) pads_[padIndex].chokeGroup = chokeGroup;
}

void DrumRackNode::setPadTune(size_t padIndex, float semitones) {
    if (padIndex < NUM_PADS) pads_[padIndex].tuneSemitones = std::clamp(semitones, -24.0f, 24.0f);
}

void DrumRackNode::setPadDecay(size_t padIndex, float decaySec) {
    if (padIndex < NUM_PADS) pads_[padIndex].decaySec = std::clamp(decaySec, 0.01f, 5.0f);
}

void DrumRackNode::triggerPad(size_t padIndex, float velocity) {
    if (padIndex >= NUM_PADS || velocity <= 0.0f) return;

    // Choke any active pad in the same choke group
    int cg = pads_[padIndex].chokeGroup;
    if (cg > 0) {
        for (size_t p = 0; p < NUM_PADS; ++p) {
            if (p != padIndex && pads_[p].chokeGroup == cg) {
                pads_[p].choke();
            }
        }
    }

    pads_[padIndex].trigger(velocity);
}

void DrumRackNode::noteOn(int noteNumber, float velocity) {
    // Map standard General MIDI drum notes (36=Kick, 38=Snare, 42=ClosedHat, etc.) or 0..15
    size_t padIndex = 0;
    if (noteNumber >= 36 && noteNumber < 36 + static_cast<int>(NUM_PADS)) {
        padIndex = static_cast<size_t>(noteNumber - 36);
    } else if (noteNumber >= 0 && noteNumber < static_cast<int>(NUM_PADS)) {
        padIndex = static_cast<size_t>(noteNumber);
    } else {
        padIndex = static_cast<size_t>(noteNumber % NUM_PADS);
    }
    triggerPad(padIndex, velocity);
}

void DrumRackNode::noteOff(int /*noteNumber*/) {}
void DrumRackNode::allNotesOff() { releaseResources(); }
void DrumRackNode::setPitchBend(float /*bendSemitones*/) {}
void DrumRackNode::setModWheel(float /*modWheel*/) {}

void DrumRackNode::process(const ProcessContext& ctx, float** /*inBuffers*/, float** outBuffers) {
    float* outL = outBuffers[0];
    float* outR = (ctx.numChannels > 1) ? outBuffers[1] : outBuffers[0];

    std::fill_n(outL, ctx.numFrames, 0.0f);
    std::fill_n(outR, ctx.numFrames, 0.0f);

    if (!isEnabled_) return;

    for (size_t p = 0; p < NUM_PADS; ++p) {
        auto& pad = pads_[p];
        if (!pad.isPlaying) continue;

        float decayRate = 1.0f / (pad.decaySec * static_cast<float>(sampleRate_));
        float pitchRatio = std::pow(2.0f, pad.tuneSemitones / 12.0f);

        for (size_t i = 0; i < ctx.numFrames; ++i) {
            float sample = 0.0f;

            if (!pad.sampleData.empty()) {
                // Play sample from memory
                size_t sIdx = static_cast<size_t>(pad.currentPlayhead);
                if (sIdx < pad.sampleData.size()) {
                    sample = pad.sampleData[sIdx];
                    pad.currentPlayhead += pitchRatio;
                } else {
                    pad.isPlaying = false;
                    break;
                }
            } else {
                // Synthetic Drum Model Fallback
                pad.envLevel -= decayRate;
                if (pad.envLevel <= 0.0f) {
                    pad.isPlaying = false;
                    break;
                }

                if (p == 0) {
                    // Kick 808 pitch sweep
                    float pitchEnv = pad.envLevel * pad.envLevel;
                    float freq = (50.0f + pitchEnv * 120.0f) * pitchRatio;
                    pad.synthPhase += freq / static_cast<float>(sampleRate_);
                    if (pad.synthPhase >= 1.0f) pad.synthPhase -= 1.0f;
                    sample = std::sin(pad.synthPhase * 6.2831853f) * pad.envLevel;
                } else if (p == 1 || p == 2) {
                    // Snare / Clap noise + tonal body
                    float noise = ((static_cast<float>(rand()) / RAND_MAX) * 2.0f) - 1.0f;
                    float tone = std::sin(pad.synthPhase * 6.2831853f);
                    pad.synthPhase += (200.0f * pitchRatio) / static_cast<float>(sampleRate_);
                    if (pad.synthPhase >= 1.0f) pad.synthPhase -= 1.0f;
                    sample = (noise * 0.7f + tone * 0.3f) * pad.envLevel;
                } else {
                    // Hi-Hat / Percussion metallic chirp
                    float noise = ((static_cast<float>(rand()) / RAND_MAX) * 2.0f) - 1.0f;
                    sample = noise * pad.envLevel;
                }
            }

            float voiceOut = sample * pad.velocity * pad.volumeLinear * 0.3f;
            outL[i] += voiceOut;
            outR[i] += voiceOut;
        }
    }
}

void DrumRackNode::setParameter(const std::string& paramName, float value) {
    if (paramName.rfind("pad_", 0) == 0) {
        // e.g. "pad_0_tune"
        size_t padIdx = static_cast<size_t>(paramName[4] - '0');
        if (padIdx < NUM_PADS) {
            if (paramName.find("tune") != std::string::npos) setPadTune(padIdx, value);
            else if (paramName.find("decay") != std::string::npos) setPadDecay(padIdx, value);
        }
    }
}

float DrumRackNode::getParameter(const std::string& paramName) const {
    if (paramName.rfind("pad_", 0) == 0) {
        size_t padIdx = static_cast<size_t>(paramName[4] - '0');
        if (padIdx < NUM_PADS) {
            if (paramName.find("tune") != std::string::npos) return pads_[padIdx].tuneSemitones;
            if (paramName.find("decay") != std::string::npos) return pads_[padIdx].decaySec;
        }
    }
    return 0.0f;
}
