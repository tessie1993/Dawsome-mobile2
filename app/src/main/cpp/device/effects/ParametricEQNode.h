#pragma once

#include "../EffectNode.h"
#include <array>

enum class EQFilterType {
    LOW_CUT = 0,
    LOW_SHELF,
    PEAK,
    HIGH_SHELF,
    HIGH_CUT
};

struct BiquadCoeffs {
    float b0{1.0f}, b1{0.0f}, b2{0.0f}, a1{0.0f}, a2{0.0f};
};

struct BiquadState {
    float z1{0.0f};
    float z2{0.0f};
};

/**
 * 5-Band Pro-Grade Parametric Equalizer Node.
 * Direct Form II Transposed biquad topology with zero allocations during process.
 */
class ParametricEQNode : public EffectNode {
public:
    static constexpr size_t NUM_BANDS = 5;

    ParametricEQNode(std::string id = "eq_5band");
    ~ParametricEQNode() override = default;

    void prepareToPlay(double sampleRate, size_t maxBlockSize) override;
    void process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) override;
    void releaseResources() override;

    void setParameter(const std::string& paramName, float value) override;
    float getParameter(const std::string& paramName) const override;

    void setBand(size_t bandIndex, EQFilterType type, float freqHz, float q, float gainDb);
    void resetStates();

private:
    void calculateCoefficients(size_t bandIndex);

    double sampleRate_{44100.0};

    struct BandConfig {
        EQFilterType type{EQFilterType::PEAK};
        float freqHz{1000.0f};
        float q{0.707f};
        float gainDb{0.0f};
        bool isEnabled{true};
    };

    std::array<BandConfig, NUM_BANDS> bands_;
    std::array<BiquadCoeffs, NUM_BANDS> coeffs_;
    std::array<std::array<BiquadState, 2>, NUM_BANDS> states_; // 2 channels per band
};
