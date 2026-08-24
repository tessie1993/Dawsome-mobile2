#include "GroupTrackNode.h"
#include <algorithm>
#include <cmath>

GroupTrackNode::GroupTrackNode(std::string id, int32_t groupIndex)
    : AudioNode(std::move(id), NodeType::GROUP_TRACK),
      groupIndex_(groupIndex) {
    childTrackIndices_.fill(-1);
}

void GroupTrackNode::prepareToPlay(double sampleRate, size_t maxBlockSize) {
    deviceChain_.prepare(sampleRate, maxBlockSize);

    const int rampFrames = static_cast<int>(sampleRate * 0.02);
    volumeSmoother_.setRampFrames(rampFrames);
    panSmoother_.setRampFrames(rampFrames);

    setVolumeDb(volumeDb_);
    setPan(pan_);
}

void GroupTrackNode::releaseResources() {
    deviceChain_.releaseResources();
}

bool GroupTrackNode::addChildTrack(int32_t trackIndex) {
    if (childCount_ >= MAX_CHILD_TRACKS) return false;
    for (size_t i = 0; i < childCount_; ++i) {
        if (childTrackIndices_[i] == trackIndex) return false; // Already present
    }
    childTrackIndices_[childCount_++] = trackIndex;
    return true;
}

bool GroupTrackNode::removeChildTrack(int32_t trackIndex) {
    for (size_t i = 0; i < childCount_; ++i) {
        if (childTrackIndices_[i] == trackIndex) {
            for (size_t j = i; j < childCount_ - 1; ++j) {
                childTrackIndices_[j] = childTrackIndices_[j + 1];
            }
            childTrackIndices_[--childCount_] = -1;
            return true;
        }
    }
    return false;
}

void GroupTrackNode::clearChildren() {
    childTrackIndices_.fill(-1);
    childCount_ = 0;
}

int32_t GroupTrackNode::getChildTrack(size_t index) const {
    if (index < childCount_) {
        return childTrackIndices_[index];
    }
    return -1;
}

void GroupTrackNode::setVolumeDb(float volumeDb) {
    volumeDb_ = volumeDb;
    float linear = (volumeDb <= -60.0f) ? 0.0f : std::pow(10.0f, volumeDb / 20.0f);
    volumeSmoother_.setTarget(linear);
}

void GroupTrackNode::setPan(float pan) {
    pan_ = std::clamp(pan, -1.0f, 1.0f);
    panSmoother_.setTarget(pan_);
}

void GroupTrackNode::process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) {
    if (!isEnabled_ || isMuted_) {
        for (size_t ch = 0; ch < ctx.numChannels; ++ch) {
            std::fill_n(outBuffers[ch], ctx.numFrames, 0.0f);
        }
        return;
    }

    // Process Insert Device Chain on summed child audio
    deviceChain_.process(ctx, inBuffers, outBuffers);

    // Apply Group Volume & Panning
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
