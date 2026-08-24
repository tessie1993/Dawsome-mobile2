#include "LimiterNode.h"
#include <cmath>
#include <algorithm>

LimiterNode::LimiterNode(std::string id)
    : EffectNode(std::move(id)) {
}

void LimiterNode::prepareToPlay(double sampleRate, size_t /*maxBlockSize*/) {
    sampleRate_ = sampleRate;
    lookaheadSamples_ = std::max(static_cast<size_t>(sampleRate_ * (lookaheadMs_ * 0.001f)), static_cast<size_t>(1));

    lookaheadBufferL_.assign(lookaheadSamples_ + 16, 0.0f);
    lookaheadBufferR_.assign(lookaheadSamples_ + 16, 0.0f);
    lookaheadWriteIdx_ = 0;
    currentGainLinear_ = 1.0f;

    setCeilingDb(ceilingDb_);
    setReleaseMs(releaseMs_);
}

void LimiterNode::releaseResources() {
    std::fill(lookaheadBufferL_.begin(), lookaheadBufferL_.end(), 0.0f);
    std::fill(lookaheadBufferR_.begin(), lookaheadBufferR_.end(), 0.0f);
    currentGainLinear_ = 1.0f;
}

void LimiterNode::setCeilingDb(float ceilingDb) {
    ceilingDb_ = std::clamp(ceilingDb, -12.0f, 0.0f);
    ceilingLinear_ = std::pow(10.0f, ceilingDb_ / 20.0f);
}

void LimiterNode::setReleaseMs(float releaseMs) {
    releaseMs_ = std::clamp(releaseMs, 5.0f, 500.0f);
    releaseCoeff_ = std::exp(-1.0f / (static_cast<float>(sampleRate_) * (releaseMs_ * 0.001f)));
}

void LimiterNode::setLookaheadMs(float lookaheadMs) {
    lookaheadMs_ = std::clamp(lookaheadMs, 0.5f, 20.0f);
    lookaheadSamples_ = std::max(static_cast<size_t>(sampleRate_ * (lookaheadMs_ * 0.001f)), static_cast<size_t>(1));
    lookaheadBufferL_.assign(lookaheadSamples_ + 16, 0.0f);
    lookaheadBufferR_.assign(lookaheadSamples_ + 16, 0.0f);
    lookaheadWriteIdx_ = 0;
}

void LimiterNode::process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) {
    if (!isEnabled_ || lookaheadBufferL_.empty()) {
        for (size_t ch = 0; ch < ctx.numChannels; ++ch) {
            std::copy_n(inBuffers[ch], ctx.numFrames, outBuffers[ch]);
        }
        return;
    }

    const float* inL = inBuffers[0];
    const float* inR = (ctx.numChannels > 1) ? inBuffers[1] : inBuffers[0];
    float* outL = outBuffers[0];
    float* outR = (ctx.numChannels > 1) ? outBuffers[1] : outBuffers[0];

    const size_t bufSize = lookaheadBufferL_.size();

    for (size_t i = 0; i < ctx.numFrames; ++i) {
        // Push current input to lookahead ring buffer
        lookaheadBufferL_[lookaheadWriteIdx_] = inL[i];
        lookaheadBufferR_[lookaheadWriteIdx_] = inR[i];

        // Determine delayed read index
        size_t readIdx = (lookaheadWriteIdx_ + bufSize - lookaheadSamples_) % bufSize;
        float delayedL = lookaheadBufferL_[readIdx];
        float delayedR = lookaheadBufferR_[readIdx];

        if (++lookaheadWriteIdx_ >= bufSize) lookaheadWriteIdx_ = 0;

        // Peak detector on current incoming sample
        float inputPeak = std::max(std::abs(inL[i]), std::abs(inR[i]));
        float targetGain = (inputPeak > ceilingLinear_) ? (ceilingLinear_ / inputPeak) : 1.0f;

        // Instant attack, exponential release
        if (targetGain < currentGainLinear_) {
            currentGainLinear_ = targetGain;
        } else {
            currentGainLinear_ = targetGain + releaseCoeff_ * (currentGainLinear_ - targetGain);
        }

        outL[i] = delayedL * currentGainLinear_;
        outR[i] = delayedR * currentGainLinear_;
    }
}

void LimiterNode::setParameter(const std::string& paramName, float value) {
    if (paramName == "ceiling") setCeilingDb(value);
    else if (paramName == "release") setReleaseMs(value);
    else if (paramName == "lookahead") setLookaheadMs(value);
}

float LimiterNode::getParameter(const std::string& paramName) const {
    if (paramName == "ceiling") return ceilingDb_;
    if (paramName == "release") return releaseMs_;
    if (paramName == "lookahead") return lookaheadMs_;
    return 0.0f;
}
