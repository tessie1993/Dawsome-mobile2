#pragma once

#include "DeviceNode.h"
#include "../core/SmoothedValue.h"

/**
 * Base abstract class for Audio Effects with dry/wet crossfade smoothing.
 */
class EffectNode : public DeviceNode {
public:
    EffectNode(std::string id) : DeviceNode(std::move(id), NodeType::EFFECT) {}
    ~EffectNode() override = default;

    virtual void setDryWet(float mix) {
        mix_ = std::clamp(mix, 0.0f, 1.0f);
        dryWetSmoother_.setTarget(mix_);
    }

    float getDryWet() const noexcept { return mix_; }

protected:
    float mix_{1.0f};
    SmoothedValue<float> dryWetSmoother_{1.0f};
};
