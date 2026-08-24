#pragma once

#include "../EffectNode.h"
#include <vector>
#include <cmath>

namespace effects {

/**
 * LCR Chorus Node inspired by joonastuo/Chorus (MIT).
 * Implements Left-Center-Right modulation.
 */
class ChorusNode : public EffectNode {
public:
    ChorusNode(std::string id = "chorus") 
        : EffectNode(std::move(id)) {
        bufferL_.resize(44100, 0.0f); 
        bufferR_.resize(44100, 0.0f);
    }

    void prepareToPlay(double sampleRate, size_t maxBlockSize) override {
        sampleRate_ = sampleRate;
        bufferL_.assign(bufferL_.size(), 0.0f);
        bufferR_.assign(bufferR_.size(), 0.0f);
        writeIdx_ = 0;
    }

    void process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) override {
        if (mix_ <= 0.001f) return;

        size_t numSamples = ctx.numSamples;
        for (size_t i = 0; i < numSamples; ++i) {
            float inL = inBuffers[0][i];
            float inR = inBuffers[1][i]; // Assuming Stereo

            // Calculate LFOs (Left, Center, Right phases)
            float lfoL = std::sin(phase_) * depth_;
            float lfoR = std::sin(phase_ + M_PI_2) * depth_; // 90 degrees out of phase
            
            phase_ += rate_ / sampleRate_;
            if (phase_ > 2.0f * M_PI) phase_ -= 2.0f * M_PI;

            // Calculate delay samples
            float delayL = (delayMs_ + lfoL) / 1000.0f * sampleRate_;
            float delayR = (delayMs_ + lfoR) / 1000.0f * sampleRate_;

            int readL = writeIdx_ - static_cast<int>(delayL);
            int readR = writeIdx_ - static_cast<int>(delayR);
            if (readL < 0) readL += bufferL_.size();
            if (readR < 0) readR += bufferR_.size();

            // Read
            float wetL = bufferL_[readL];
            float wetR = bufferR_[readR];

            // Write
            bufferL_[writeIdx_] = inL;
            bufferR_[writeIdx_] = inR;

            // Output Mix
            outBuffers[0][i] = (inL * (1.0f - mix_)) + (wetL * mix_);
            outBuffers[1][i] = (inR * (1.0f - mix_)) + (wetR * mix_);

            writeIdx_ = (writeIdx_ + 1) % bufferL_.size();
        }
    }

    void setRate(float rate) { rate_ = rate; }
    void setDepth(float depth) { depth_ = depth; }

private:
    double sampleRate_{44100.0};
    std::vector<float> bufferL_;
    std::vector<float> bufferR_;
    size_t writeIdx_{0};

    float delayMs_{20.0f}; // Base delay
    float rate_{1.5f};     // LFO Hz
    float depth_{5.0f};    // Depth Ms
    float phase_{0.0f};
};

} // namespace effects
