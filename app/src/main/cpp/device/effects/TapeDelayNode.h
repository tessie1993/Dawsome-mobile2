#pragma once

#include "../EffectNode.h"
#include <vector>
#include <cmath>

namespace effects {

/**
 * Tape Delay Node inspired by dllim/anotherdelay (MIT).
 * Emulates analog tape flutter, wow, and saturation.
 */
class TapeDelayNode : public EffectNode {
public:
    TapeDelayNode(std::string id = "tape_delay") 
        : EffectNode(std::move(id)) {
        // Initialize delay buffers
        buffer_.resize(44100 * 2, 0.0f); // 2 seconds max at 44.1kHz
    }

    void prepareToPlay(double sampleRate, size_t maxBlockSize) override {
        sampleRate_ = sampleRate;
        buffer_.assign(buffer_.size(), 0.0f);
        writeIdx_ = 0;
    }

    void process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) override {
        if (mix_ <= 0.001f) {
            // Bypass
            return;
        }

        size_t numSamples = ctx.numSamples;
        for (size_t i = 0; i < numSamples; ++i) {
            float inputSample = inBuffers[0][i]; // Assuming Mono for simplicity in skeleton
            
            // Tape flutter modulation (LFO on delay time)
            float mod = std::sin(phase_) * wowDepth_;
            phase_ += wowRate_ / sampleRate_;
            if (phase_ > 2.0f * M_PI) phase_ -= 2.0f * M_PI;

            // Calculate read index with modulated delay time
            float currentDelayTime = delayTimeMs_ + mod;
            float delaySamples = (currentDelayTime / 1000.0f) * sampleRate_;
            
            int readIdx = writeIdx_ - static_cast<int>(delaySamples);
            if (readIdx < 0) readIdx += buffer_.size();

            // Read delayed sample
            float delayedSample = buffer_[readIdx];
            
            // Saturation (soft clipping)
            delayedSample = std::tanh(delayedSample * drive_);

            // Write to buffer with feedback
            buffer_[writeIdx_] = inputSample + (delayedSample * feedback_);
            
            // Output mix
            outBuffers[0][i] = (inputSample * (1.0f - mix_)) + (delayedSample * mix_);

            writeIdx_ = (writeIdx_ + 1) % buffer_.size();
        }
    }

    void setDelayTime(float ms) { delayTimeMs_ = ms; }
    void setFeedback(float fb) { feedback_ = fb; }
    void setWowDepth(float depth) { wowDepth_ = depth; }

private:
    double sampleRate_{44100.0};
    std::vector<float> buffer_;
    size_t writeIdx_{0};

    float delayTimeMs_{400.0f};
    float feedback_{0.5f};
    float drive_{1.5f};
    
    float phase_{0.0f};
    float wowRate_{0.5f};
    float wowDepth_{2.0f};
};

} // namespace effects
