#include "TransportEngine.h"
#include <cmath>
#include <algorithm>

TransportEngine::TransportEngine() {
    reset();
}

void TransportEngine::prepare(double sampleRate) {
    sampleRate_ = sampleRate;
}

void TransportEngine::reset() {
    currentBeat_ = 0.0;
    samplePosition_ = 0.0;
    isPlaying_ = false;
    lastDownbeatBeat_ = -1;
    metroSamplesRemaining_ = 0;
    metroPhase_ = 0.0f;
}

void TransportEngine::play() {
    isPlaying_ = true;
}

void TransportEngine::pause() {
    isPlaying_ = false;
}

void TransportEngine::stop() {
    isPlaying_ = false;
    currentBeat_ = 0.0;
    samplePosition_ = 0.0;
    lastDownbeatBeat_ = -1;
    metroSamplesRemaining_ = 0;
}

void TransportEngine::seekToBeat(double beat) {
    currentBeat_ = std::max(0.0, beat);
    double beatsPerSec = bpm_ / 60.0;
    samplePosition_ = (currentBeat_ / beatsPerSec) * sampleRate_;
    lastDownbeatBeat_ = static_cast<int>(currentBeat_) - 1;
}

void TransportEngine::setBpm(float bpm) {
    bpm_ = std::clamp(bpm, 20.0f, 300.0f);
}

void TransportEngine::setTimeSignature(int numerator, int denominator) {
    timeSigNum_ = std::max(1, numerator);
    timeSigDen_ = std::max(1, denominator);
}

void TransportEngine::setLoop(bool enabled, double startBeat, double endBeat) {
    isLooping_ = enabled;
    loopStartBeat_ = std::max(0.0, startBeat);
    loopEndBeat_ = std::max(loopStartBeat_ + 0.25, endBeat);
}

void TransportEngine::advance(size_t numFrames, double sampleRate) {
    sampleRate_ = sampleRate;
    if (!isPlaying_) return;

    double beatsPerSec = bpm_ / 60.0;
    double beatsPerSample = beatsPerSec / sampleRate_;
    double advanceBeats = static_cast<double>(numFrames) * beatsPerSample;

    currentBeat_ += advanceBeats;
    samplePosition_ += static_cast<double>(numFrames);

    // Loop wraparound check
    if (isLooping_ && currentBeat_ >= loopEndBeat_) {
        double overflow = currentBeat_ - loopEndBeat_;
        currentBeat_ = loopStartBeat_ + overflow;
        samplePosition_ = (currentBeat_ / beatsPerSec) * sampleRate_;
        lastDownbeatBeat_ = static_cast<int>(currentBeat_) - 1;
    }

    // Metronome trigger check
    int currentBeatInt = static_cast<int>(currentBeat_);
    if (metronomeEnabled_ && currentBeatInt > lastDownbeatBeat_) {
        lastDownbeatBeat_ = currentBeatInt;
        bool isDownbeat = (currentBeatInt % timeSigNum_ == 0);
        metroFreq_ = isDownbeat ? 1200.0f : 800.0f;
        metroSamplesRemaining_ = static_cast<int>(sampleRate_ * 0.03); // 30ms blip
        metroPhase_ = 0.0f;
    }
}

void TransportEngine::renderMetronome(float** outputBuffers, size_t numFrames, size_t numChannels) {
    if (metroSamplesRemaining_ <= 0) return;

    float* outL = outputBuffers[0];
    float* outR = (numChannels > 1) ? outputBuffers[1] : outputBuffers[0];

    float phaseInc = (metroFreq_ * 6.2831853f) / static_cast<float>(sampleRate_);

    for (size_t i = 0; i < numFrames && metroSamplesRemaining_ > 0; ++i) {
        float sample = std::sin(metroPhase_) * 0.25f;
        metroPhase_ += phaseInc;

        outL[i] += sample;
        outR[i] += sample;

        --metroSamplesRemaining_;
    }
}
