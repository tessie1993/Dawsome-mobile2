#pragma once

#include "AudioNode.h"
#include "../device/DeviceChain.h"
#include "../core/SmoothedValue.h"
#include "../core/MeterFrame.h"
#include <cmath>

/**
 * Standard Audio / MIDI Track Node in Directed Acyclic Graph.
 * Processes device insert chain, volume fader, stereo pan, and audio metering.
 */
class TrackNode : public AudioNode {
public:
    TrackNode(std::string id, int32_t trackIndex);
    ~TrackNode() override = default;

    void prepareToPlay(double sampleRate, size_t maxBlockSize) override;
    void process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) override;
    void releaseResources() override;

    void setVolumeDb(float volumeDb);
    float getVolumeDb() const noexcept { return volumeDb_; }

    void setPan(float pan);
    float getPan() const noexcept { return pan_; }

    void setArmed(bool armed) noexcept { isArmed_ = armed; }
    bool isArmed() const noexcept { return isArmed_; }

    DeviceChain& getDeviceChain() noexcept { return deviceChain_; }
    const DeviceChain& getDeviceChain() const noexcept { return deviceChain_; }

    MeterFrame getMeterFrame() const noexcept;

private:
    int32_t trackIndex_{-1};
    DeviceChain deviceChain_;

    float volumeDb_{0.0f};
    float pan_{0.0f};
    bool isArmed_{false};

    SmoothedValue<float> volumeSmoother_{1.0f};
    SmoothedValue<float> panSmoother_{0.0f};

    // Ballistic metering accumulators
    float peakL_{0.0f};
    float peakR_{0.0f};
    float rmsSumL_{0.0f};
    float rmsSumR_{0.0f};
};
