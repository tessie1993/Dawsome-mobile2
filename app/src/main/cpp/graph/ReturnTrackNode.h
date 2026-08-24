#pragma once

#include "AudioNode.h"
#include "../device/DeviceChain.h"
#include "../core/SmoothedValue.h"

/**
 * Auxiliary Return Track Node for shared spatial and modulation effect chains.
 */
class ReturnTrackNode : public AudioNode {
public:
    ReturnTrackNode(std::string id, int32_t returnIndex);
    ~ReturnTrackNode() override = default;

    void prepareToPlay(double sampleRate, size_t maxBlockSize) override;
    void process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) override;
    void releaseResources() override;

    int32_t getReturnIndex() const noexcept { return returnIndex_; }

    void setVolumeDb(float volumeDb);
    float getVolumeDb() const noexcept { return volumeDb_; }

    void setPan(float pan);
    float getPan() const noexcept { return pan_; }

    DeviceChain& getDeviceChain() noexcept { return deviceChain_; }
    const DeviceChain& getDeviceChain() const noexcept { return deviceChain_; }

private:
    int32_t returnIndex_{-1};
    DeviceChain deviceChain_;

    float volumeDb_{0.0f};
    float pan_{0.0f};

    SmoothedValue<float> volumeSmoother_{1.0f};
    SmoothedValue<float> panSmoother_{0.0f};
};
