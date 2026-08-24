#include "DeviceChain.h"
#include <algorithm>
#include <cstring>

DeviceChain::DeviceChain() = default;
DeviceChain::~DeviceChain() = default;

void DeviceChain::prepare(double sampleRate, size_t maxBlockSize) {
    sampleRate_ = sampleRate;
    maxBlockSize_ = maxBlockSize;
    for (size_t i = 0; i < deviceCount_; ++i) {
        if (devices_[i]) {
            devices_[i]->prepareToPlay(sampleRate, maxBlockSize);
        }
    }
}

void DeviceChain::process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) {
    if (deviceCount_ == 0) {
        // Pass-through: copy inputs to outputs
        if (inBuffers != outBuffers) {
            for (size_t ch = 0; ch < ctx.numOutputChannels; ++ch) {
                if (ch < ctx.numInputChannels && inBuffers[ch] && outBuffers[ch]) {
                    std::memcpy(outBuffers[ch], inBuffers[ch], ctx.numFrames * sizeof(float));
                } else if (outBuffers[ch]) {
                    std::memset(outBuffers[ch], 0, ctx.numFrames * sizeof(float));
                }
            }
        }
        return;
    }

    float** currentIn = inBuffers;
    for (size_t i = 0; i < deviceCount_; ++i) {
        auto& device = devices_[i];
        if (!device || !device->isEnabled()) continue;

        device->process(ctx, currentIn, outBuffers);
        currentIn = outBuffers; // Chain cascade
    }
}

void DeviceChain::releaseResources() {
    for (size_t i = 0; i < deviceCount_; ++i) {
        if (devices_[i]) {
            devices_[i]->releaseResources();
        }
    }
}

bool DeviceChain::addDevice(std::shared_ptr<DeviceNode> device) {
    if (!device || deviceCount_ >= MAX_DEVICES) return false;
    devices_[deviceCount_++] = std::move(device);
    return true;
}

bool DeviceChain::removeDevice(size_t index) {
    if (index >= deviceCount_) return false;
    for (size_t i = index; i < deviceCount_ - 1; ++i) {
        devices_[i] = std::move(devices_[i + 1]);
    }
    devices_[--deviceCount_] = nullptr;
    return true;
}

bool DeviceChain::removeDeviceById(const std::string& id) {
    for (size_t i = 0; i < deviceCount_; ++i) {
        if (devices_[i] && devices_[i]->getId() == id) {
            return removeDevice(i);
        }
    }
    return false;
}

bool DeviceChain::swapDevices(size_t fromIndex, size_t toIndex) {
    if (fromIndex >= deviceCount_ || toIndex >= deviceCount_) return false;
    std::swap(devices_[fromIndex], devices_[toIndex]);
    return true;
}

std::shared_ptr<DeviceNode> DeviceChain::getDevice(size_t index) const {
    if (index >= deviceCount_) return nullptr;
    return devices_[index];
}

void DeviceChain::clear() {
    for (size_t i = 0; i < deviceCount_; ++i) {
        devices_[i] = nullptr;
    }
    deviceCount_ = 0;
}
