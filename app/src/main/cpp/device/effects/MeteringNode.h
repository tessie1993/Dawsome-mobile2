#pragma once

#include "../EffectNode.h"
#include "../../core/MeterFrame.h"

/**
 * Real-Time Signal Level Analyzer Node (Peak, RMS, True-Peak, Clipping).
 */
class MeteringNode : public EffectNode {
public:
    MeteringNode(std::string id = "meter");
    ~MeteringNode() override = default;

    void prepareToPlay(double sampleRate, size_t maxBlockSize) override;
    void process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) override;
    void releaseResources() override;

    void setParameter(const std::string& paramName, float value) override;
    float getParameter(const std::string& paramName) const override;

    MeterFrame getMeterFrame() const noexcept;

private:
    float peakL_{0.0f};
    float peakR_{0.0f};
    float rmsSumL_{0.0f};
    float rmsSumR_{0.0f};
    size_t frameCount_{0};
};
