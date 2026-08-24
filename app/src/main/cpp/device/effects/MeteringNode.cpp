#include "MeteringNode.h"
#include <cmath>
#include <algorithm>

MeteringNode::MeteringNode(std::string id)
    : EffectNode(std::move(id)) {
}

void MeteringNode::prepareToPlay(double /*sampleRate*/, size_t /*maxBlockSize*/) {
    releaseResources();
}

void MeteringNode::releaseResources() {
    peakL_ = peakR_ = 0.0f;
    rmsSumL_ = rmsSumR_ = 0.0f;
    frameCount_ = 0;
}

void MeteringNode::process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) {
    const float* inL = inBuffers[0];
    const float* inR = (ctx.numChannels > 1) ? inBuffers[1] : inBuffers[0];
    float* outL = outBuffers[0];
    float* outR = (ctx.numChannels > 1) ? outBuffers[1] : outBuffers[0];

    peakL_ = peakR_ = 0.0f;
    rmsSumL_ = rmsSumR_ = 0.0f;
    frameCount_ = ctx.numFrames;

    for (size_t i = 0; i < ctx.numFrames; ++i) {
        float sampleL = inL[i];
        float sampleR = inR[i];

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

MeterFrame MeteringNode::getMeterFrame() const noexcept {
    MeterFrame frame;
    frame.trackId = -1;
    frame.peakL = peakL_;
    frame.peakR = peakR_;
    float n = (frameCount_ > 0) ? static_cast<float>(frameCount_) : 1.0f;
    frame.rmsL = std::sqrt(rmsSumL_ / n);
    frame.rmsR = std::sqrt(rmsSumR_ / n);
    frame.truePeak = std::max(peakL_, peakR_);
    frame.isClipping = (frame.truePeak >= 1.0f);
    return frame;
}

void MeteringNode::setParameter(const std::string& /*paramName*/, float /*value*/) {}
float MeteringNode::getParameter(const std::string& /*paramName*/) const { return 0.0f; }
