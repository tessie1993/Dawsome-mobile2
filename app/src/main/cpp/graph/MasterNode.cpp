#include "MasterNode.h"
#include <algorithm>
#include <cmath>

MasterNode::MasterNode()
    : AudioNode("master", NodeType::MASTER) {
}

void MasterNode::prepareToPlay(double sampleRate, size_t maxBlockSize) {
    deviceChain_.prepare(sampleRate, maxBlockSize);

    const int rampFrames = static_cast<int>(sampleRate * 0.02);
    volumeSmoother_.setRampFrames(rampFrames);

    setVolumeDb(volumeDb_);
    setLimiterCeilingDb(limiterCeilingDb_);
}

void MasterNode::releaseResources() {
    deviceChain_.releaseResources();
}

void MasterNode::setVolumeDb(float volumeDb) {
    volumeDb_ = volumeDb;
    float linear = (volumeDb <= -60.0f) ? 0.0f : std::pow(10.0f, volumeDb / 20.0f);
    volumeSmoother_.setTarget(linear);
}

void MasterNode::setLimiterCeilingDb(float ceilingDb) {
    limiterCeilingDb_ = ceilingDb;
}

void MasterNode::process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) {
    // 1. Process Mastering Device Chain
    deviceChain_.process(ctx, inBuffers, outBuffers);

    // 2. Apply Master Volume Gain & Brickwall Peak Clamping
    float* outL = outBuffers[0];
    float* outR = (ctx.numChannels > 1) ? outBuffers[1] : outBuffers[0];

    float ceilingLinear = std::pow(10.0f, limiterCeilingDb_ / 20.0f);

    peakL_ = peakR_ = 0.0f;
    rmsSumL_ = rmsSumR_ = 0.0f;

    for (size_t i = 0; i < ctx.numFrames; ++i) {
        float gain = volumeSmoother_.getNext();
        float sampleL = outL[i] * gain;
        float sampleR = outR[i] * gain;

        // Brickwall limiter clamping
        if (isLimiterEnabled_) {
            sampleL = std::clamp(sampleL, -ceilingLinear, ceilingLinear);
            sampleR = std::clamp(sampleR, -ceilingLinear, ceilingLinear);
        }

        outL[i] = sampleL;
        outR[i] = sampleR;

        float absL = std::abs(sampleL);
        float absR = std::abs(sampleR);
        if (absL > peakL_) peakL_ = absL;
        if (absR > peakR_) peakR_ = absR;
        rmsSumL_ += sampleL * sampleL;
        rmsSumR_ += sampleR * sampleR;
    }
}

MeterFrame MasterNode::getMasterMeterFrame() const noexcept {
    MeterFrame frame;
    frame.trackId = 999; // Master ID sentinel
    frame.peakL = peakL_;
    frame.peakR = peakR_;
    frame.rmsL = std::sqrt(rmsSumL_ / 256.0f);
    frame.rmsR = std::sqrt(rmsSumR_ / 256.0f);
    frame.truePeak = std::max(peakL_, peakR_);
    frame.isClipping = (frame.truePeak >= 1.0f);
    return frame;
}
