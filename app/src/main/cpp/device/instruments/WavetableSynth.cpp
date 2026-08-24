#include "WavetableSynth.h"
#include <cmath>
#include <algorithm>

WavetableSynth::WavetableSynth(std::string id)
    : InstrumentNode(std::move(id)) {
    generateDefaultWavetables();
}

void WavetableSynth::generateDefaultWavetables() {
    wavetableData_.assign(NUM_TABLE_FRAMES * TABLE_FRAME_SIZE, 0.0f);

    for (size_t f = 0; f < NUM_TABLE_FRAMES; ++f) {
        float morph = static_cast<float>(f) / static_cast<float>(NUM_TABLE_FRAMES - 1);
        for (size_t s = 0; s < TABLE_FRAME_SIZE; ++s) {
            float phase = static_cast<float>(s) / static_cast<float>(TABLE_FRAME_SIZE);

            // Morph from Sine -> Triangle -> Saw -> Pulse across 64 frames
            float sample = 0.0f;
            if (morph < 0.33f) {
                float localMorph = morph / 0.33f;
                float sine = std::sin(phase * 2.0f * static_cast<float>(M_PI));
                float tri = 4.0f * std::abs(phase - 0.5f) - 1.0f;
                sample = (sine * (1.0f - localMorph)) + (tri * localMorph);
            } else if (morph < 0.66f) {
                float localMorph = (morph - 0.33f) / 0.33f;
                float tri = 4.0f * std::abs(phase - 0.5f) - 1.0f;
                float saw = 2.0f * phase - 1.0f;
                sample = (tri * (1.0f - localMorph)) + (saw * localMorph);
            } else {
                float localMorph = (morph - 0.66f) / 0.34f;
                float saw = 2.0f * phase - 1.0f;
                float pulse = (phase < 0.5f) ? 1.0f : -1.0f;
                sample = (saw * (1.0f - localMorph)) + (pulse * localMorph);
            }

            wavetableData_[f * TABLE_FRAME_SIZE + s] = sample;
        }
    }
}

void WavetableSynth::prepareToPlay(double sampleRate, size_t /*maxBlockSize*/) {
    sampleRate_ = sampleRate;
    releaseResources();
}

void WavetableSynth::releaseResources() {
    for (auto& voice : voices_) {
        voice.reset();
    }
}

void WavetableSynth::noteOn(int noteNumber, float velocity) {
    if (velocity <= 0.0f) {
        noteOff(noteNumber);
        return;
    }

    WavetableVoice* targetVoice = nullptr;
    for (auto& v : voices_) {
        if (!v.active) {
            targetVoice = &v;
            break;
        }
    }
    if (!targetVoice) targetVoice = &voices_[0];

    targetVoice->noteNumber = noteNumber;
    targetVoice->velocity = velocity;
    targetVoice->phase = 0.0f;
    targetVoice->active = true;
    targetVoice->ampEnv.trigger();
}

void WavetableSynth::noteOff(int noteNumber) {
    for (auto& v : voices_) {
        if (v.active && v.noteNumber == noteNumber) {
            v.ampEnv.release();
        }
    }
}

void WavetableSynth::allNotesOff() {
    for (auto& v : voices_) {
        v.ampEnv.release();
    }
}

void WavetableSynth::setPitchBend(float bendSemitones) {
    pitchBendSemitones_ = std::clamp(bendSemitones, -24.0f, 24.0f);
}

void WavetableSynth::setModWheel(float modWheel) {
    modWheel_ = std::clamp(modWheel, 0.0f, 1.0f);
}

void WavetableSynth::setTablePosition(float pos) {
    tablePosition_ = std::clamp(pos, 0.0f, 1.0f);
}

void WavetableSynth::setWarpAmount(float warp) {
    warpAmount_ = std::clamp(warp, 0.0f, 1.0f);
}

float WavetableSynth::sampleWavetable(float phase, float tablePos) {
    float frameFloat = tablePos * static_cast<float>(NUM_TABLE_FRAMES - 1);
    size_t frame0 = static_cast<size_t>(frameFloat);
    size_t frame1 = std::min(frame0 + 1, NUM_TABLE_FRAMES - 1);
    float frameFrac = frameFloat - static_cast<float>(frame0);

    float samplePos = phase * static_cast<float>(TABLE_FRAME_SIZE);
    size_t s0 = static_cast<size_t>(samplePos) % TABLE_FRAME_SIZE;
    size_t s1 = (s0 + 1) % TABLE_FRAME_SIZE;
    float sampleFrac = samplePos - std::floor(samplePos);

    float s0_f0 = wavetableData_[frame0 * TABLE_FRAME_SIZE + s0];
    float s1_f0 = wavetableData_[frame0 * TABLE_FRAME_SIZE + s1];
    float sampleF0 = s0_f0 + sampleFrac * (s1_f0 - s0_f0);

    float s0_f1 = wavetableData_[frame1 * TABLE_FRAME_SIZE + s0];
    float s1_f1 = wavetableData_[frame1 * TABLE_FRAME_SIZE + s1];
    float sampleF1 = s0_f1 + sampleFrac * (s1_f1 - s0_f1);

    return sampleF0 + frameFrac * (sampleF1 - sampleF0);
}

void WavetableSynth::process(const ProcessContext& ctx, float** /*inBuffers*/, float** outBuffers) {
    float* outL = outBuffers[0];
    float* outR = (ctx.numChannels > 1) ? outBuffers[1] : outBuffers[0];

    std::fill_n(outL, ctx.numFrames, 0.0f);
    std::fill_n(outR, ctx.numFrames, 0.0f);

    if (!isEnabled_) return;

    for (auto& v : voices_) {
        if (!v.active) continue;

        float baseFreq = 440.0f * std::pow(2.0f, (v.noteNumber + pitchBendSemitones_ - 69.0f) / 12.0f);
        float phaseInc = baseFreq / static_cast<float>(sampleRate_);

        float effectivePos = std::clamp(tablePosition_ + (modWheel_ * 0.5f), 0.0f, 1.0f);

        for (size_t i = 0; i < ctx.numFrames; ++i) {
            float amp = v.ampEnv.getNext(sampleRate_);
            if (!v.ampEnv.isActive()) {
                v.active = false;
                break;
            }

            float sample = sampleWavetable(v.phase, effectivePos);

            v.phase += phaseInc;
            if (v.phase >= 1.0f) v.phase -= 1.0f;

            float voiceOut = sample * amp * v.velocity * 0.25f;
            outL[i] += voiceOut;
            outR[i] += voiceOut;
        }
    }
}

void WavetableSynth::setParameter(const std::string& paramName, float value) {
    if (paramName == "table_pos") setTablePosition(value);
    else if (paramName == "warp") setWarpAmount(value);
    else if (paramName == "amp_attack") for (auto& v : voices_) v.ampEnv.attackSec = std::max(value, 0.001f);
    else if (paramName == "amp_decay") for (auto& v : voices_) v.ampEnv.decaySec = std::max(value, 0.01f);
    else if (paramName == "amp_sustain") for (auto& v : voices_) v.ampEnv.sustainLevel = std::clamp(value, 0.0f, 1.0f);
    else if (paramName == "amp_release") for (auto& v : voices_) v.ampEnv.releaseSec = std::max(value, 0.01f);
}

float WavetableSynth::getParameter(const std::string& paramName) const {
    if (paramName == "table_pos") return tablePosition_;
    if (paramName == "warp") return warpAmount_;
    if (paramName == "amp_attack") return voices_[0].ampEnv.attackSec;
    if (paramName == "amp_decay") return voices_[0].ampEnv.decaySec;
    if (paramName == "amp_sustain") return voices_[0].ampEnv.sustainLevel;
    if (paramName == "amp_release") return voices_[0].ampEnv.releaseSec;
    return 0.0f;
}
