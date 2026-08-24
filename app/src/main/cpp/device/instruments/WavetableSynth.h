#pragma once

#include "../InstrumentNode.h"
#include "SubtractiveSynth.h"
#include <array>
#include <vector>

struct WavetableVoice {
    int noteNumber{-1};
    float velocity{0.0f};
    float phase{0.0f};
    bool active{false};
    ADSRData ampEnv;

    void reset() {
        noteNumber = -1;
        velocity = 0.0f;
        phase = 0.0f;
        active = false;
        ampEnv.reset();
    }
};

/**
 * 16-Voice Polyphonic 3D Wavetable Synthesizer.
 * Supports continuous wavetable position morphing and unison detune.
 */
class WavetableSynth : public InstrumentNode {
public:
    static constexpr size_t TABLE_FRAME_SIZE = 256;
    static constexpr size_t NUM_TABLE_FRAMES = 64;
    static constexpr size_t MAX_VOICES = 16;

    WavetableSynth(std::string id = "wavetable_synth");
    ~WavetableSynth() override = default;

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

    void setTablePosition(float pos);
    void setWarpAmount(float warp);

private:
    void generateDefaultWavetables();
    float sampleWavetable(float phase, float tablePos);

    double sampleRate_{44100.0};
    std::array<WavetableVoice, MAX_VOICES> voices_;

    // 64 frames x 256 samples per frame
    std::vector<float> wavetableData_;

    float tablePosition_{0.0f}; // 0.0 to 1.0
    float warpAmount_{0.0f};
    float pitchBendSemitones_{0.0f};
    float modWheel_{0.0f};
};
