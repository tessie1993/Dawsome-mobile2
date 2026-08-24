#pragma once

#include "../InstrumentNode.h"
#include "SubtractiveSynth.h"
#include <vector>
#include <array>

enum class SamplerLoopMode {
    ONE_SHOT = 0,
    FORWARD_LOOP,
    PING_PONG_LOOP
};

struct SamplerVoice {
    int noteNumber{-1};
    float velocity{0.0f};
    double playheadPosition{0.0};
    bool isPlaying{false};
    bool isLoopReversing{false};
    ADSRData ampEnv;

    void reset() {
        noteNumber = -1;
        velocity = 0.0f;
        playheadPosition = 0.0;
        isPlaying = false;
        isLoopReversing = false;
        ampEnv.reset();
    }
};

/**
 * High-Performance Polyphonic Sampler Instrument Node.
 */
class AdvancedSamplerNode : public InstrumentNode {
public:
    static constexpr size_t MAX_VOICES = 16;

    AdvancedSamplerNode(std::string id = "advanced_sampler");
    ~AdvancedSamplerNode() override = default;

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

    void loadSampleBuffer(const float* buffer, size_t numFrames, double sampleRate, int rootNote = 60);
    void setLoopRegion(size_t startFrame, size_t endFrame, SamplerLoopMode mode);

private:
    double engineSampleRate_{44100.0};
    double sampleSourceRate_{44100.0};
    int rootNote_{60};

    std::vector<float> sampleData_;
    size_t loopStart_{0};
    size_t loopEnd_{0};
    SamplerLoopMode loopMode_{SamplerLoopMode::ONE_SHOT};

    std::array<SamplerVoice, MAX_VOICES> voices_;
    float pitchBendSemitones_{0.0f};
    float modWheel_{0.0f};
};
