#pragma once

#include "DeviceNode.h"
#include <array>
#include <memory>

/**
 * Serial Insert Device Chain for Tracks and Master Bus.
 * Fixed maximum capacity of 16 devices to guarantee zero allocation during playback.
 */
class DeviceChain {
public:
    DeviceChain();
    ~DeviceChain();

    void prepare(double sampleRate, size_t maxBlockSize);
    void process(const ProcessContext& ctx, float** inBuffers, float** outBuffers);
    void releaseResources();

    bool addDevice(std::shared_ptr<DeviceNode> device);
    bool removeDevice(size_t index);
    bool removeDeviceById(const std::string& id);
    bool swapDevices(size_t fromIndex, size_t toIndex);

    size_t getDeviceCount() const noexcept { return deviceCount_; }
    std::shared_ptr<DeviceNode> getDevice(size_t index) const;

    void clear();

private:
    static constexpr size_t MAX_DEVICES = 16;
    std::array<std::shared_ptr<DeviceNode>, MAX_DEVICES> devices_;
    size_t deviceCount_{0};
    double sampleRate_{44100.0};
    size_t maxBlockSize_{256};
};
