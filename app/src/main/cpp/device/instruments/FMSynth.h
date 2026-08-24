#pragma once

#include "../InstrumentNode.h"
#include "SubtractiveSynth.h"
#include <array>

struct FMOperatorConfig {
    float ratio{1.0f};
    float level{1.0f};
    float feedback{0.0f};
    float attackSec{0.01f};
    float decaySec{0.3f};
    float sustainLevel{0.5f};
    float releaseSec{0.3f};
};

struct FMVoice {
    int noteNumber{-1};
    float velocity{0.0f};
    bool active{false};

    std::array<float, 4> phases{0.0f, 0.0f, 0.0f, 0.0f};
    std::array<float, 4> lastOutputs{0.0f, 0.0f, 0.0f, 0.0f};
    std::array<ADSRData, 4> envs;

    void reset() {
        noteNumber = -1;
        velocity = 0.0f;
        active = false;
        phases.fill(0.0f);
        lastOutputs.fill(0.0f);
        for (auto& env : envs) env.reset();
    }
};

/**
 * 16-Voice 4-Operator Frequency Modulation (FM) Synthesizer.
 */
class FMSynth : public InstrumentNode {
public:
    static constexpr size_t MAX_VOICES = 16;
    static constexpr size_t NUM_OPS = 4;

    FMSynth(std::string id = "fm_synth");
    ~FMSynth() override = default;

    void prepareToPlay(double sampleRate, size_t maxBlockSize) override;
    void process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) override;
    void releaseResources() override;

    void setParameter(const std::string& paramName, float value) override;
    float getParameter(const std::string& paramName) const override;

    void noteOn(int noteNumber, float velocity) override;
    void noteOff(int noteNumber) override;
    void allNotesOff() override;
    void setPitchBend(float bendSemitones) override;
    void setModWheel(float modWheel) override;

    void setAlgorithm(int algorithm);
    void setOperatorRatio(size_t op, float ratio);
    void setOperatorLevel(size_t op, float level);
    void setOperatorFeedback(size_t op, float fb);

private:
    double sampleRate_{44100.0};
    int algorithm_{0}; // 0..7
    std::array<FMOperatorConfig, NUM_OPS> opConfigs_;
    std::array<FMVoice, MAX_VOICES> voices_;

    float pitchBendSemitones_{0.0f};
    float modWheel_{0.0f};
};
