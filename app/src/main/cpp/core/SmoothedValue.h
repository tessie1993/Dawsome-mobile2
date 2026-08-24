#pragma once

#include <cmath>

/**
 * 1-pole Low-Pass Filter Parameter Smoother.
 * Eliminates audio clicks, zipper noise, and pops during real-time modulation and knob turns.
 */
template <typename FloatType = float>
class SmoothedValue {
public:
    SmoothedValue() noexcept : currentValue_(0), targetValue_(0), step_(0), remainingSteps_(0) {}

    explicit SmoothedValue(FloatType initialValue) noexcept
        : currentValue_(initialValue), targetValue_(initialValue), step_(0), remainingSteps_(0) {}

    void reset(double sampleRate, double rampTimeSeconds) noexcept {
        sampleRate_ = sampleRate;
        rampLengthSteps_ = static_cast<int>(std::max(1.0, rampTimeSeconds * sampleRate));
        remainingSteps_ = 0;
        currentValue_ = targetValue_;
    }

    void setTargetValue(FloatType newTarget) noexcept {
        if (targetValue_ == newTarget) return;

        targetValue_ = newTarget;
        if (rampLengthSteps_ <= 0) {
            currentValue_ = newTarget;
            remainingSteps_ = 0;
            return;
        }

        remainingSteps_ = rampLengthSteps_;
        step_ = (targetValue_ - currentValue_) / static_cast<FloatType>(remainingSteps_);
    }

    void setCurrentAndTargetValue(FloatType value) noexcept {
        currentValue_ = value;
        targetValue_ = value;
        remainingSteps_ = 0;
        step_ = 0;
    }

    inline FloatType getNextValue() noexcept {
        if (remainingSteps_ > 0) {
            --remainingSteps_;
            currentValue_ += step_;
            if (remainingSteps_ == 0) {
                currentValue_ = targetValue_;
            }
        }
        return currentValue_;
    }

    inline FloatType getCurrentValue() const noexcept {
        return currentValue_;
    }

    inline FloatType getTargetValue() const noexcept {
        return targetValue_;
    }

    inline bool isSmoothing() const noexcept {
        return remainingSteps_ > 0;
    }

private:
    double sampleRate_{44100.0};
    int rampLengthSteps_{256};
    FloatType currentValue_{0};
    FloatType targetValue_{0};
    FloatType step_{0};
    int remainingSteps_{0};
};
