#pragma once

#include "AudioNode.h"
#include "../device/DeviceChain.h"
#include "../core/SmoothedValue.h"
#include <array>

/**
 * Group / Bus Track Node for summing and processing submixes.
 */
class GroupTrackNode : public AudioNode {
public:
    static constexpr size_t MAX_CHILD_TRACKS = 16;

    GroupTrackNode(std::string id, int32_t groupIndex);
    ~GroupTrackNode() override = default;

    void prepareToPlay(double sampleRate, size_t maxBlockSize) override;
    void process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) override;
    void releaseResources() override;

    bool addChildTrack(int32_t trackIndex);
    bool removeChildTrack(int32_t trackIndex);
    void clearChildren();

    size_t getChildCount() const noexcept { return childCount_; }
    int32_t getChildTrack(size_t index) const;

    void setVolumeDb(float volumeDb);
    float getVolumeDb() const noexcept { return volumeDb_; }

    void setPan(float pan);
    float getPan() const noexcept { return pan_; }

    DeviceChain& getDeviceChain() noexcept { return deviceChain_; }

private:
    int32_t groupIndex_{-1};
    std::array<int32_t, MAX_CHILD_TRACKS> childTrackIndices_;
    size_t childCount_{0};

    DeviceChain deviceChain_;
    float volumeDb_{0.0f};
    float pan_{0.0f};

    SmoothedValue<float> volumeSmoother_{1.0f};
    SmoothedValue<float> panSmoother_{0.0f};
};
