#include "TrackNode.h"
#include <algorithm>

TrackNode::TrackNode(std::string id, int32_t trackIndex)
    : AudioNode(std::move(id), NodeType::TRACK),
      trackIndex_(trackIndex) {
}

void TrackNode::prepareToPlay(double sampleRate, size_t maxBlockSize) {
    deviceChain_.prepare(sampleRate, maxBlockSize);

    const int rampFrames = static_cast<int>(sampleRate * 0.02); // 20ms ramp
    volumeSmoother_.setRampFrames(rampFrames);
    panSmoother_.setRampFrames(rampFrames);

    setVolumeDb(volumeDb_);
    setPan(pan_);
}

void TrackNode::releaseResources() {
    deviceChain_.releaseResources();
}

void TrackNode::setVolumeDb(float volumeDb) {
    volumeDb_ = volumeDb;
    float linear = (volumeDb <= -60.0f) ? 0.0f : std::pow(10.0f, volumeDb / 20.0f);
    volumeSmoother_.setTarget(linear);
}

void TrackNode::setPan(float pan) {
    pan_ = std::clamp(pan, -1.0f, 1.0f);
    panSmoother_.setTarget(pan_);
}

void TrackNode::process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) {
    if (!isEnabled_ || isMuted_) {
        for (size_t ch = 0; ch < ctx.numChannels; ++ch) {
            std::fill_n(outBuffers[ch], ctx.numFrames, 0.0f);
        }
        peakL_ = peakR_ = rmsSumL_ = rmsSumR_ = 0.0f;
        return;
    }

    // 1. Process Insert Device Chain
    deviceChain_.process(ctx, inBuffers, outBuffers);

    // 2. Apply Smoothed Volume & Constant-Power Stereo Panning
    peakL_ = peakR_ = 0.0f;
    rmsSumL_ = rmsSumR_ = 0.0f;

    float* outL = outBuffers[0];
    float* outR = (ctx.numChannels > 1) ? outBuffers[1] : outBuffers[0];

    constexpr float PI_OVER_4 = 0.7853981633974483f;

    for (size_t i = 0; i < ctx.numFrames; ++i) {
        float gain = volumeSmoother_.getNext();
        float currentPan = panSmoother_.getNext();

        // Constant power pan law: pan in [-1, +1] -> angle in [0, PI/2]
        float panAngle = (currentPan + 1.0f) * 0.5f * (PI_OVER_4 * 2.0f);
        float leftGain = std::cos(panAngle) * 1.41421356f * gain;
        float rightGain = std::sin(panAngle) * 1.41421356f * gain;

        float sampleL = outL[i] * leftGain;
        float sampleR = outR[i] * rightGain;

        outL[i] = sampleL;
        outR[i] = sampleR;

        // Peak & RMS ballistics
        float absL = std::abs(sampleL);
        float absR = std::abs(sampleR);
        if (absL > peakL_) peakL_ = absL;
        if (absR > peakR_) peakR_ = absR;
        rmsSumL_ += sampleL * sampleL;
        rmsSumR_ += sampleR * sampleR;
    }
}

MeterFrame TrackNode::getMeterFrame() const noexcept {
    MeterFrame frame;
    frame.trackId = trackIndex_;
    frame.peakL = peakL_;
    frame.peakR = peakR_;
    frame.rmsL = std::sqrt(rmsSumL_ / 256.0f);
    frame.rmsR = std::sqrt(rmsSumR_ / 256.0f);
    frame.truePeak = std::max(peakL_, peakR_);
    frame.isClipping = (frame.truePeak >= 1.0f);
    return frame;
}
