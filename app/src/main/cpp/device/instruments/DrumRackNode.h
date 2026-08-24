#pragma once

#include "../InstrumentNode.h"
#include <array>
#include <vector>

struct DrumPadVoice {
    int chokeGroup{0}; // 0 = no choke, 1..4 = choke groups
    float tuneSemitones{0.0f};
    float decaySec{0.3f};
    float volumeLinear{1.0f};
    float pan{0.0f};

    // Playback state
    bool isPlaying{false};
    float currentPlayhead{0.0f};
    float velocity{0.0f};

    // Synthetic drum generator state
    float synthFreq{120.0f};
    float synthPhase{0.0f};
    float envLevel{0.0f};

    std::vector<float> sampleData;

    void trigger(float vel) {
        isPlaying = true;
        currentPlayhead = 0.0f;
        velocity = vel;
        envLevel = 1.0f;
        synthPhase = 0.0f;
    }

    void choke() {
        isPlaying = false;
        envLevel = 0.0f;
    }
};

/**
 * 16-Pad Studio Drum Rack Node with Choke Groups and Synthetic/Sample Playback.
 */
class DrumRackNode : public InstrumentNode {
public:
    static constexpr size_t NUM_PADS = 16;

    DrumRackNode(std::string id = "drum_rack");
    ~DrumRackNode() override = default;

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

    void triggerPad(size_t padIndex, float velocity);
    void setPadSample(size_t padIndex, const float* buffer, size_t numFrames);
    void setPadChokeGroup(size_t padIndex, int chokeGroup);
    void setPadTune(size_t padIndex, float semitones);
    void setPadDecay(size_t padIndex, float decaySec);

private:
    double sampleRate_{44100.0};
    std::array<DrumPadVoice, NUM_PADS> pads_;
};
