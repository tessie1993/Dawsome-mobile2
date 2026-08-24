#pragma once

#include "../graph/AudioNode.h"
#include "../core/SmoothedValue.h"
#include <unordered_map>

/**
 * Base abstract class for Devices (Instruments & Audio Effects).
 */
class DeviceNode : public AudioNode {
public:
    DeviceNode(std::string id, NodeType type) : AudioNode(std::move(id), type) {}
    ~DeviceNode() override = default;

    virtual void setParameter(const std::string& paramName, float value) = 0;
    virtual float getParameter(const std::string& paramName) const = 0;
};
