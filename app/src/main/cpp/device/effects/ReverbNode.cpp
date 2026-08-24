#include "ReverbNode.h"
#include <algorithm>

ReverbNode::ReverbNode(std::string id)
    : EffectNode(std::move(id)) {
    mix_ = 0.35f;
    dryWetSmoother_.reset(0.35f);
}

void ReverbNode::prepareToPlay(double sampleRate, size_t /*maxBlockSize*/) {
    sampleRate_ = sampleRate;
    const double scale = sampleRate / 44100.0;

    // Mutually prime comb filter tuning lengths
    const int combTuningsL[NUM_COMBS] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
    const int combTuningsR[NUM_COMBS] = {1139, 1211, 1300, 1379, 1445, 1514, 1580, 1640};
    const int allpassTuningsL[NUM_ALLPASSES] = {556, 441, 341, 225};
    const int allpassTuningsR[NUM_ALLPASSES] = {579, 464, 364, 248};

    for (size_t i = 0; i < NUM_COMBS; ++i) {
        combL_[i].prepare(static_cast<size_t>(combTuningsL[i] * scale));
        combR_[i].prepare(static_cast<size_t>(combTuningsR[i] * scale));
    }

    for (size_t i = 0; i < NUM_ALLPASSES; ++i) {
        allpassL_[i].prepare(static_cast<size_t>(allpassTuningsL[i] * scale));
        allpassR_[i].prepare(static_cast<size_t>(allpassTuningsR[i] * scale));
        allpassL_[i].feedback = 0.5f;
        allpassR_[i].feedback = 0.5f;
    }

    updateParameters();
}

void ReverbNode::releaseResources() {
    for (size_t i = 0; i < NUM_COMBS; ++i) {
        combL_[i].clear();
        combR_[i].clear();
    }
    for (size_t i = 0; i < NUM_ALLPASSES; ++i) {
        allpassL_[i].clear();
        allpassR_[i].clear();
    }
}

void ReverbNode::setRoomSize(float size) {
    roomSize_ = std::clamp(size, 0.0f, 1.0f);
    updateParameters();
}

void ReverbNode::setDamping(float damp) {
    damping_ = std::clamp(damp, 0.0f, 1.0f);
    updateParameters();
}

void ReverbNode::setWidth(float width) {
    width_ = std::clamp(width, 0.0f, 1.0f);
}

void ReverbNode::updateParameters() {
    const float feedback = 0.7f + (roomSize_ * 0.28f);
    const float damp = damping_ * 0.4f;

    for (size_t i = 0; i < NUM_COMBS; ++i) {
        combL_[i].setFeedback(feedback);
        combR_[i].setFeedback(feedback);
        combL_[i].setDamp(damp);
        combR_[i].setDamp(damp);
    }
}

void ReverbNode::process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) {
    if (!isEnabled_) {
        for (size_t ch = 0; ch < ctx.numChannels; ++ch) {
            std::copy_n(inBuffers[ch], ctx.numFrames, outBuffers[ch]);
        }
        return;
    }

    const float* inL = inBuffers[0];
    const float* inR = (ctx.numChannels > 1) ? inBuffers[1] : inBuffers[0];
    float* outL = outBuffers[0];
    float* outR = (ctx.numChannels > 1) ? outBuffers[1] : outBuffers[0];

    const float wet1 = width_ * 0.5f + 0.5f;
    const float wet2 = (1.0f - width_) * 0.5f;

    for (size_t i = 0; i < ctx.numFrames; ++i) {
        float inputMono = (inL[i] + inR[i]) * 0.015f; // Scaled input gain

        float outLComb = 0.0f;
        float outRComb = 0.0f;

        // 8 Parallel Comb Filters
        for (size_t c = 0; c < NUM_COMBS; ++c) {
            outLComb += combL_[c].process(inputMono);
            outRComb += combR_[c].process(inputMono);
        }

        // 4 Series Allpass Filters
        for (size_t a = 0; a < NUM_ALLPASSES; ++a) {
            outLComb = allpassL_[a].process(outLComb);
            outRComb = allpassR_[a].process(outRComb);
        }

        float wetL = (outLComb * wet1) + (outRComb * wet2);
        float wetR = (outRComb * wet1) + (outLComb * wet2);

        float mix = dryWetSmoother_.getNext();
        outL[i] = (inL[i] * (1.0f - mix)) + (wetL * mix);
        outR[i] = (inR[i] * (1.0f - mix)) + (wetR * mix);
    }
}

void ReverbNode::setParameter(const std::string& paramName, float value) {
    if (paramName == "room_size") setRoomSize(value);
    else if (paramName == "damping") setDamping(value);
    else if (paramName == "width") setWidth(value);
    else if (paramName == "mix") setDryWet(value);
}

float ReverbNode::getParameter(const std::string& paramName) const {
    if (paramName == "room_size") return roomSize_;
    if (paramName == "damping") return damping_;
    if (paramName == "width") return width_;
    if (paramName == "mix") return getDryWet();
    return 0.0f;
}
