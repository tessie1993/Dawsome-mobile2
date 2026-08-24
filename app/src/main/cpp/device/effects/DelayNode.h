#pragma once

#include "../EffectNode.h"
#include <vector>

/**
 * Stereo Ping-Pong Delay with Tone Damping Filter.
 */
class DelayNode : public EffectNode {
public:
    DelayNode(std::string id = "delay");
    ~DelayNode() override = default;

    void prepareToPlay(double sampleRate, size_t maxBlockSize) override;
    void process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) override;
    void releaseResources() override;

    void setParameter(const std::string& paramName, float value) override;
    float getParameter(const std::string& paramName) const override;

    void setDelayTimeMs(float ms);
    void setFeedback(float fb);
    void setPingPong(bool pingPong) noexcept { pingPong_ = pingPong; }
    void setToneHz(float toneHz);

private:
    double sampleRate_{44100.0};
    float delayTimeMs_{375.0f}; 
    float feedback_{0.45f};
    bool pingPong_{true};
    float toneHz_{4000.0f};

    std::vector<float> bufferL_;
    std::vector<float> bufferR_;
    size_t bufferSize_{0};
    size_t writeIdx_{0};

    SmoothedValue<float> delaySamplesSmoother_{16537.5f};
    float toneCoeff_{0.5f};
    float filterL_{0.0f};
    float filterR_{0.0f};
};
