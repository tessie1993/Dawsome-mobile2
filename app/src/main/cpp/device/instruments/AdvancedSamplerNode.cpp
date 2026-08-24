#include "AdvancedSamplerNode.h"
#include <cmath>
#include <algorithm>

AdvancedSamplerNode::AdvancedSamplerNode(std::string id)
    : InstrumentNode(std::move(id)) {
}

void AdvancedSamplerNode::prepareToPlay(double sampleRate, size_t /*maxBlockSize*/) {
    engineSampleRate_ = sampleRate;
    releaseResources();
}

void AdvancedSamplerNode::releaseResources() {
    for (auto& voice : voices_) {
        voice.reset();
    }
}

void AdvancedSamplerNode::loadSampleBuffer(const float* buffer, size_t numFrames, double sampleRate, int rootNote) {
    sampleSourceRate_ = sampleRate;
    rootNote_ = rootNote;
    sampleData_.assign(buffer, buffer + numFrames);
    loopStart_ = 0;
    loopEnd_ = numFrames;
}

void AdvancedSamplerNode::setLoopRegion(size_t startFrame, size_t endFrame, SamplerLoopMode mode) {
    loopStart_ = std::min(startFrame, sampleData_.size());
    loopEnd_ = std::clamp(endFrame, loopStart_, sampleData_.size());
    loopMode_ = mode;
}

void AdvancedSamplerNode::noteOn(int noteNumber, float velocity) {
    if (velocity <= 0.0f || sampleData_.empty()) {
        noteOff(noteNumber);
        return;
    }

    SamplerVoice* targetVoice = nullptr;
    for (auto& v : voices_) {
        if (!v.isPlaying) {
            targetVoice = &v;
            break;
        }
    }
    if (!targetVoice) targetVoice = &voices_[0];

    targetVoice->noteNumber = noteNumber;
    targetVoice->velocity = velocity;
    targetVoice->playheadPosition = 0.0;
    targetVoice->isPlaying = true;
    targetVoice->isLoopReversing = false;
    targetVoice->ampEnv.trigger();
}

void AdvancedSamplerNode::noteOff(int noteNumber) {
    for (auto& v : voices_) {
        if (v.isPlaying && v.noteNumber == noteNumber) {
            v.ampEnv.release();
        }
    }
}

void AdvancedSamplerNode::allNotesOff() {
    for (auto& v : voices_) {
        v.ampEnv.release();
    }
}

void AdvancedSamplerNode::setPitchBend(float bendSemitones) {
    pitchBendSemitones_ = std::clamp(bendSemitones, -24.0f, 24.0f);
}

void AdvancedSamplerNode::setModWheel(float modWheel) {
    modWheel_ = std::clamp(modWheel, 0.0f, 1.0f);
}

void AdvancedSamplerNode::process(const ProcessContext& ctx, float** /*inBuffers*/, float** outBuffers) {
    float* outL = outBuffers[0];
    float* outR = (ctx.numChannels > 1) ? outBuffers[1] : outBuffers[0];

    std::fill_n(outL, ctx.numFrames, 0.0f);
    std::fill_n(outR, ctx.numFrames, 0.0f);

    if (!isEnabled_ || sampleData_.empty()) return;

    const size_t totalFrames = sampleData_.size();

    for (auto& v : voices_) {
        if (!v.isPlaying) continue;

        double semitonesDiff = (v.noteNumber + pitchBendSemitones_) - rootNote_;
        double pitchRatio = std::pow(2.0, semitonesDiff / 12.0) * (sampleSourceRate_ / engineSampleRate_);

        for (size_t i = 0; i < ctx.numFrames; ++i) {
            float amp = v.ampEnv.getNext(engineSampleRate_);
            if (!v.ampEnv.isActive()) {
                v.isPlaying = false;
                break;
            }

            // Linear interpolation
            size_t idx0 = static_cast<size_t>(v.playheadPosition);
            size_t idx1 = std::min(idx0 + 1, totalFrames - 1);
            float frac = static_cast<float>(v.playheadPosition - idx0);

            float sample = sampleData_[idx0] + frac * (sampleData_[idx1] - sampleData_[idx0]);

            // Advance playhead based on loop mode
            if (!v.isLoopReversing) {
                v.playheadPosition += pitchRatio;
                if (loopMode_ == SamplerLoopMode::FORWARD_LOOP && v.playheadPosition >= loopEnd_) {
                    v.playheadPosition = loopStart_ + (v.playheadPosition - loopEnd_);
                } else if (loopMode_ == SamplerLoopMode::PING_PONG_LOOP && v.playheadPosition >= loopEnd_) {
                    v.isLoopReversing = true;
                    v.playheadPosition = loopEnd_ - 1.0;
                } else if (v.playheadPosition >= totalFrames) {
                    v.isPlaying = false;
                    break;
                }
            } else {
                v.playheadPosition -= pitchRatio;
                if (v.playheadPosition <= loopStart_) {
                    v.isLoopReversing = false;
                    v.playheadPosition = loopStart_;
                }
            }

            float voiceOut = sample * amp * v.velocity * 0.35f;
            outL[i] += voiceOut;
            outR[i] += voiceOut;
        }
    }
}

void AdvancedSamplerNode::setParameter(const std::string& paramName, float value) {
    if (paramName == "amp_attack") for (auto& v : voices_) v.ampEnv.attackSec = std::max(value, 0.001f);
    else if (paramName == "amp_decay") for (auto& v : voices_) v.ampEnv.decaySec = std::max(value, 0.01f);
    else if (paramName == "amp_sustain") for (auto& v : voices_) v.ampEnv.sustainLevel = std::clamp(value, 0.0f, 1.0f);
    else if (paramName == "amp_release") for (auto& v : voices_) v.ampEnv.releaseSec = std::max(value, 0.01f);
}

float AdvancedSamplerNode::getParameter(const std::string& paramName) const {
    if (paramName == "amp_attack") return voices_[0].ampEnv.attackSec;
    if (paramName == "amp_decay") return voices_[0].ampEnv.decaySec;
    if (paramName == "amp_sustain") return voices_[0].ampEnv.sustainLevel;
    if (paramName == "amp_release") return voices_[0].ampEnv.releaseSec;
    return 0.0f;
}
