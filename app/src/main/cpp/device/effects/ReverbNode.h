#pragma once

#include "../EffectNode.h"
#include <array>
#include <vector>

struct CombFilter {
    std::vector<float> buffer;
    size_t bufferSize{0};
    size_t bufIdx{0};
    float feedback{0.5f};
    float filterStore{0.0f};
    float damp1{0.5f};
    float damp2{0.5f};

    void prepare(size_t size) {
        buffer.assign(size, 0.0f);
        bufferSize = size;
        bufIdx = 0;
        filterStore = 0.0f;
    }

    void setDamp(float val) {
        damp1 = val;
        damp2 = 1.0f - val;
    }

    void setFeedback(float val) {
        feedback = val;
    }

    inline float process(float input) {
        float output = buffer[bufIdx];
        filterStore = (output * damp2) + (filterStore * damp1);
        buffer[bufIdx] = input + (filterStore * feedback);
        if (++bufIdx >= bufferSize) bufIdx = 0;
        return output;
    }

    void clear() {
        std::fill(buffer.begin(), buffer.end(), 0.0f);
        filterStore = 0.0f;
        bufIdx = 0;
    }
};

struct AllpassFilter {
    std::vector<float> buffer;
    size_t bufferSize{0};
    size_t bufIdx{0};
    float feedback{0.5f};

    void prepare(size_t size) {
        buffer.assign(size, 0.0f);
        bufferSize = size;
        bufIdx = 0;
    }

    inline float process(float input) {
        float bufOut = buffer[bufIdx];
        float output = -input + bufOut;
        buffer[bufIdx] = input + (bufOut * feedback);
        if (++bufIdx >= bufferSize) bufIdx = 0;
        return output;
    }

    void clear() {
        std::fill(buffer.begin(), buffer.end(), 0.0f);
        bufIdx = 0;
    }
};

/**
 * High-Density Algorithmic Reverb Node (8 Parallel Combs + 4 Series Allpasses).
 */
class ReverbNode : public EffectNode {
public:
    static constexpr size_t NUM_COMBS = 8;
    static constexpr size_t NUM_ALLPASSES = 4;

    ReverbNode(std::string id = "reverb");
    ~ReverbNode() override = default;

    void prepareToPlay(double sampleRate, size_t maxBlockSize) override;
    void process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) override;
    void releaseResources() override;

    void setParameter(const std::string& paramName, float value) override;
    float getParameter(const std::string& paramName) const override;

    void setRoomSize(float size);
    void setDamping(float damp);
    void setWidth(float width);

private:
    void updateParameters();

    double sampleRate_{44100.0};
    float roomSize_{0.5f};
    float damping_{0.5f};
    float width_{1.0f};

    std::array<CombFilter, NUM_COMBS> combL_;
    std::array<CombFilter, NUM_COMBS> combR_;
    std::array<AllpassFilter, NUM_ALLPASSES> allpassL_;
    std::array<AllpassFilter, NUM_ALLPASSES> allpassR_;
};
