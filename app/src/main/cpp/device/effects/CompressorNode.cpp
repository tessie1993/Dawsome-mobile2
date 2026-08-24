#include "CompressorNode.h"
#include <cmath>
#include <algorithm>

CompressorNode::CompressorNode(std::string id)
    : EffectNode(std::move(id)) {
}

void CompressorNode::prepareToPlay(double sampleRate, size_t /*maxBlockSize*/) {
    sampleRate_ = sampleRate;
    envelopeDb_ = -96.0f;
    currentGainReductionDb_ = 0.0f;

    setAttackMs(attackMs_);
    setReleaseMs(releaseMs_);
    setMakeupGainDb(makeupGainDb_);
}

void CompressorNode::releaseResources() {
    envelopeDb_ = -96.0f;
    currentGainReductionDb_ = 0.0f;
}

void CompressorNode::setThresholdDb(float thresholdDb) {
    thresholdDb_ = std::clamp(thresholdDb, -60.0f, 0.0f);
}

void CompressorNode::setRatio(float ratio) {
    ratio_ = std::clamp(ratio, 1.0f, 30.0f);
}

void CompressorNode::setAttackMs(float attackMs) {
    attackMs_ = std::clamp(attackMs, 0.1f, 200.0f);
    attackCoeff_ = std::exp(-1.0f / (static_cast<float>(sampleRate_) * (attackMs_ * 0.001f)));
}

void CompressorNode::setReleaseMs(float releaseMs) {
    releaseMs_ = std::clamp(releaseMs, 5.0f, 2000.0f);
    releaseCoeff_ = std::exp(-1.0f / (static_cast<float>(sampleRate_) * (releaseMs_ * 0.001f)));
}

void CompressorNode::setMakeupGainDb(float makeupDb) {
    makeupGainDb_ = std::clamp(makeupDb, 0.0f, 36.0f);
    makeupLinear_ = std::pow(10.0f, makeupGainDb_ / 20.0f);
}

void CompressorNode::setKneeDb(float kneeDb) {
    kneeDb_ = std::clamp(kneeDb, 0.0f, 20.0f);
}

float CompressorNode::computeGainDb(float detectorDb) {
    const float halfKnee = kneeDb_ * 0.5f;

    if (detectorDb <= (thresholdDb_ - halfKnee)) {
        // Below knee -> 1:1, no compression
        return 0.0f;
    } else if (kneeDb_ > 0.0f && detectorDb < (thresholdDb_ + halfKnee)) {
        // Inside soft knee curve
        float delta = detectorDb - thresholdDb_ + halfKnee;
        float compressionSlope = (1.0f / ratio_) - 1.0f;
        return compressionSlope * (delta * delta) / (2.0f * kneeDb_);
    } else {
        // Above knee -> full ratio compression
        return (thresholdDb_ + (detectorDb - thresholdDb_) / ratio_) - detectorDb;
    }
}

void CompressorNode::process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) {
    if (!isEnabled_) {
        for (size_t ch = 0; ch < ctx.numChannels; ++ch) {
            std::copy_n(inBuffers[ch], ctx.numFrames, outBuffers[ch]);
        }
        currentGainReductionDb_ = 0.0f;
        return;
    }

    const float* inL = inBuffers[0];
    const float* inR = (ctx.numChannels > 1) ? inBuffers[1] : inBuffers[0];
    float* outL = outBuffers[0];
    float* outR = (ctx.numChannels > 1) ? outBuffers[1] : outBuffers[0];

    float maxGr = 0.0f;

    for (size_t i = 0; i < ctx.numFrames; ++i) {
        float peakSample = std::max(std::abs(inL[i]), std::abs(inR[i]));
        float inputDb = (peakSample < 1e-5f) ? -96.0f : (20.0f * std::log10(peakSample));

        // Ballistic envelope follower
        if (inputDb > envelopeDb_) {
            envelopeDb_ = inputDb + attackCoeff_ * (envelopeDb_ - inputDb);
        } else {
            envelopeDb_ = inputDb + releaseCoeff_ * (envelopeDb_ - inputDb);
        }

        // Compute static gain curve
        float gainReductionDb = computeGainDb(envelopeDb_);
        if (gainReductionDb < maxGr) maxGr = gainReductionDb;

        float compressionLinear = std::pow(10.0f, gainReductionDb / 20.0f) * makeupLinear_;

        outL[i] = inL[i] * compressionLinear;
        outR[i] = inR[i] * compressionLinear;
    }

    currentGainReductionDb_ = maxGr;
}

void CompressorNode::setParameter(const std::string& paramName, float value) {
    if (paramName == "threshold") setThresholdDb(value);
    else if (paramName == "ratio") setRatio(value);
    else if (paramName == "attack") setAttackMs(value);
    else if (paramName == "release") setReleaseMs(value);
    else if (paramName == "makeup") setMakeupGainDb(value);
    else if (paramName == "mix") setDryWet(value);
}

float CompressorNode::getParameter(const std::string& paramName) const {
    if (paramName == "threshold") return thresholdDb_;
    if (paramName == "ratio") return ratio_;
    if (paramName == "attack") return attackMs_;
    if (paramName == "release") return releaseMs_;
    if (paramName == "makeup") return makeupGainDb_;
    if (paramName == "mix") return getDryWet();
    return 0.0f;
}
