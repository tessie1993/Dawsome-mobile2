#include "ReturnTrackNode.h"
#include <algorithm>
#include <cmath>

ReturnTrackNode::ReturnTrackNode(std::string id, int32_t returnIndex)
    : AudioNode(std::move(id), NodeType::RETURN_TRACK),
      returnIndex_(returnIndex) {
}

void ReturnTrackNode::prepareToPlay(double sampleRate, size_t maxBlockSize) {
    deviceChain_.prepare(sampleRate, maxBlockSize);

    const int rampFrames = static_cast<int>(sampleRate * 0.02);
    volumeSmoother_.setRampFrames(rampFrames);
    panSmoother_.setRampFrames(rampFrames);

    setVolumeDb(volumeDb_);
    setPan(pan_);
}

void ReturnTrackNode::releaseResources() {
    deviceChain_.releaseResources();
}

void ReturnTrackNode::setVolumeDb(float volumeDb) {
    volumeDb_ = volumeDb;
    float linear = (volumeDb <= -60.0f) ? 0.0f : std::pow(10.0f, volumeDb / 20.0f);
    volumeSmoother_.setTarget(linear);
}

void ReturnTrackNode::setPan(float pan) {
    pan_ = std::clamp(pan, -1.0f, 1.0f);
    panSmoother_.setTarget(pan_);
}

void ReturnTrackNode::process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) {
    if (!isEnabled_ || isMuted_) {
        for (size_t ch = 0; ch < ctx.numChannels; ++ch) {
            std::fill_n(outBuffers[ch], ctx.numFrames, 0.0f);
        }
        return;
    }

    // Process Return Effect Chain
    deviceChain_.process(ctx, inBuffers, outBuffers);

    // Apply Return Volume & Pan
    float* outL = outBuffers[0];
    float* outR = (ctx.numChannels > 1) ? outBuffers[1] : outBuffers[0];

    constexpr float PI_OVER_4 = 0.7853981633974483f;

    for (size_t i = 0; i < ctx.numFrames; ++i) {
        float gain = volumeSmoother_.getNext();
        float currentPan = panSmoother_.getNext();

        float panAngle = (currentPan + 1.0f) * 0.5f * (PI_OVER_4 * 2.0f);
        float leftGain = std::cos(panAngle) * 1.41421356f * gain;
        float rightGain = std::sin(panAngle) * 1.41421356f * gain;

        outL[i] *= leftGain;
        outR[i] *= rightGain;
    }
}
