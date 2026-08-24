#pragma once

#include "AudioNode.h"
#include "../device/DeviceChain.h"
#include "../core/SmoothedValue.h"
#include "../core/MeterFrame.h"

/**
 * Master Output Node in DAG.
 * Applies master bus inserts, fader gain, true-peak brickwall limiting, and loudness metering.
 */
class MasterNode : public AudioNode {
public:
    MasterNode();
    ~MasterNode() override = default;

    void prepareToPlay(double sampleRate, size_t maxBlockSize) override;
    void process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) override;
    void releaseResources() override;

    void setVolumeDb(float volumeDb);
    float getVolumeDb() const noexcept { return volumeDb_; }

    void setLimiterCeilingDb(float ceilingDb);
    float getLimiterCeilingDb() const noexcept { return limiterCeilingDb_; }

    void setLimiterEnabled(bool enabled) noexcept { isLimiterEnabled_ = enabled; }
    bool isLimiterEnabled() const noexcept { return isLimiterEnabled_; }

    DeviceChain& getDeviceChain() noexcept { return deviceChain_; }
    const DeviceChain& getDeviceChain() const noexcept { return deviceChain_; }

    MeterFrame getMasterMeterFrame() const noexcept;

private:
    DeviceChain deviceChain_;
    float volumeDb_{0.0f};
    float limiterCeilingDb_{-0.3f};
    bool isLimiterEnabled_{true};

    SmoothedValue<float> volumeSmoother_{1.0f};

    float peakL_{0.0f};
    float peakR_{0.0f};
    float rmsSumL_{0.0f};
    float rmsSumR_{0.0f};
};
