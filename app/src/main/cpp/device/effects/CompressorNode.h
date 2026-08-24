#pragma once

#include "../EffectNode.h"

/**
 * High-Precision Studio Compressor with Soft-Knee and Peak/RMS Ballistics.
 */
class CompressorNode : public EffectNode {
public:
    CompressorNode(std::string id = "compressor");
    ~CompressorNode() override = default;

    void prepareToPlay(double sampleRate, size_t maxBlockSize) override;
    void process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) override;
    void releaseResources() override;

    void setParameter(const std::string& paramName, float value) override;
    float getParameter(const std::string& paramName) const override;

    void setThresholdDb(float thresholdDb);
    void setRatio(float ratio);
    void setAttackMs(float attackMs);
    void setReleaseMs(float releaseMs);
    void setMakeupGainDb(float makeupDb);
    void setKneeDb(float kneeDb);

    float getGainReductionDb() const noexcept { return currentGainReductionDb_; }

private:
    float computeGainDb(float detectorDb);

    double sampleRate_{44100.0};
    float thresholdDb_{-18.0f};
    float ratio_{4.0f};
    float attackMs_{10.0f};
    float releaseMs_{100.0f};
    float makeupGainDb_{0.0f};
    float kneeDb_{4.0f};

    float attackCoeff_{0.0f};
    float releaseCoeff_{0.0f};
    float makeupLinear_{1.0f};

    float envelopeDb_{-96.0f};
    float currentGainReductionDb_{0.0f};
};
