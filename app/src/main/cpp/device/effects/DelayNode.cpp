#include "DelayNode.h"
#include <cmath>
#include <algorithm>

DelayNode::DelayNode(std::string id)
    : EffectNode(std::move(id)) {
    mix_ = 0.3f;
    dryWetSmoother_.reset(0.3f);
}

void DelayNode::prepareToPlay(double sampleRate, size_t /*maxBlockSize*/) {
    sampleRate_ = sampleRate;
    // Preallocate 2.0 seconds maximum delay capacity
    bufferSize_ = static_cast<size_t>(sampleRate_ * 2.0) + 16;
    bufferL_.assign(bufferSize_, 0.0f);
    bufferR_.assign(bufferSize_, 0.0f);
    writeIdx_ = 0;
    filterL_ = filterR_ = 0.0f;

    setDelayTimeMs(delayTimeMs_);
    setToneHz(toneHz_);
}

void DelayNode::releaseResources() {
    std::fill(bufferL_.begin(), bufferL_.end(), 0.0f);
    std::fill(bufferR_.begin(), bufferR_.end(), 0.0f);
    filterL_ = filterR_ = 0.0f;
}

void DelayNode::setDelayTimeMs(float ms) {
    delayTimeMs_ = std::clamp(ms, 1.0f, 1990.0f);
    float targetSamples = static_cast<float>(sampleRate_ * (delayTimeMs_ * 0.001f));
    delaySamplesSmoother_.setTarget(targetSamples);
}

void DelayNode::setFeedback(float fb) {
    feedback_ = std::clamp(fb, 0.0f, 0.98f);
}

void DelayNode::setToneHz(float toneHz) {
    toneHz_ = std::clamp(toneHz, 200.0f, 20000.0f);
    float costheta = std::cos(2.0f * static_cast<float>(M_PI) * toneHz_ / static_cast<float>(sampleRate_));
    toneCoeff_ = 2.0f - costheta - std::sqrt((2.0f - costheta) * (2.0f - costheta) - 1.0f);
}

void DelayNode::process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) {
    if (!isEnabled_ || bufferSize_ == 0) {
        for (size_t ch = 0; ch < ctx.numChannels; ++ch) {
            std::copy_n(inBuffers[ch], ctx.numFrames, outBuffers[ch]);
        }
        return;
    }

    const float* inL = inBuffers[0];
    const float* inR = (ctx.numChannels > 1) ? inBuffers[1] : inBuffers[0];
    float* outL = outBuffers[0];
    float* outR = (ctx.numChannels > 1) ? outBuffers[1] : outBuffers[0];

    for (size_t i = 0; i < ctx.numFrames; ++i) {
        float delaySamples = delaySamplesSmoother_.getNext();

        // Read with linear interpolation
        float readPos = static_cast<float>(writeIdx_) - delaySamples;
        if (readPos < 0.0f) readPos += bufferSize_;

        size_t idx0 = static_cast<size_t>(readPos) % bufferSize_;
        size_t idx1 = (idx0 + 1) % bufferSize_;
        float frac = readPos - std::floor(readPos);

        float delayOutL = bufferL_[idx0] + frac * (bufferL_[idx1] - bufferL_[idx0]);
        float delayOutR = bufferR_[idx0] + frac * (bufferR_[idx1] - bufferR_[idx0]);

        // Feedback lowpass filtering
        filterL_ += toneCoeff_ * (delayOutL - filterL_);
        filterR_ += toneCoeff_ * (delayOutR - filterR_);

        // Ping-pong cross-routing vs standard stereo
        float fbL = pingPong_ ? (filterR_ * feedback_) : (filterL_ * feedback_);
        float fbR = pingPong_ ? (filterL_ * feedback_) : (filterR_ * feedback_);

        bufferL_[writeIdx_] = inL[i] + fbL;
        bufferR_[writeIdx_] = inR[i] + fbR;

        if (++writeIdx_ >= bufferSize_) writeIdx_ = 0;

        float mix = dryWetSmoother_.getNext();
        outL[i] = (inL[i] * (1.0f - mix)) + (delayOutL * mix);
        outR[i] = (inR[i] * (1.0f - mix)) + (delayOutR * mix);
    }
}

void DelayNode::setParameter(const std::string& paramName, float value) {
    if (paramName == "time_ms") setDelayTimeMs(value);
    else if (paramName == "feedback") setFeedback(value);
    else if (paramName == "ping_pong") setPingPong(value > 0.5f);
    else if (paramName == "tone") setToneHz(value);
    else if (paramName == "mix") setDryWet(value);
}

float DelayNode::getParameter(const std::string& paramName) const {
    if (paramName == "time_ms") return delayTimeMs_;
    if (paramName == "feedback") return feedback_;
    if (paramName == "ping_pong") return pingPong_ ? 1.0f : 0.0f;
    if (paramName == "tone") return toneHz_;
    if (paramName == "mix") return getDryWet();
    return 0.0f;
}
