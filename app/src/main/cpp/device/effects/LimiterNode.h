#pragma once

#include "../EffectNode.h"
#include <vector>

/**
 * Lookahead Brickwall Peak Limiter Node for mastering and bus protection.
 */
class LimiterNode : public EffectNode {
public:
    LimiterNode(std::string id = "limiter");
    ~LimiterNode() override = default;

    void prepareToPlay(double sampleRate, size_t maxBlockSize) override;
    void process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) override;
    void releaseResources() override;

    void setParameter(const std::string& paramName, float value) override;
    float getParameter(const std::string& paramName) const override;

    void setCeilingDb(float ceilingDb);
    void setReleaseMs(float releaseMs);
    void setLookaheadMs(float lookaheadMs);

private:
    double sampleRate_{44100.0};
    float ceilingDb_{-0.3f};
    float releaseMs_{50.0f};
    float lookaheadMs_{4.0f};

    float ceilingLinear_{0.966f};
    float releaseCoeff_{0.0f};

    std::vector<float> lookaheadBufferL_;
    std::vector<float> lookaheadBufferR_;
    size_t lookaheadSamples_{176};
    size_t lookaheadWriteIdx_{0};

    float currentGainLinear_{1.0f};
};
