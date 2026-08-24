#pragma once

#include "../InstrumentNode.h"
#include <array>

enum class SynthWaveform {
    SAW = 0,
    PULSE,
    TRIANGLE,
    SINE,
    NOISE
};

enum class EnvStage {
    IDLE = 0,
    ATTACK,
    DECAY,
    SUSTAIN,
    RELEASE
};

struct ADSRData {
    float attackSec{0.01f};
    float decaySec{0.2f};
    float sustainLevel{0.7f};
    float releaseSec{0.3f};

    EnvStage stage{EnvStage::IDLE};
    float currentLevel{0.0f};
    float rate{0.0f};

    void trigger() {
        stage = EnvStage::ATTACK;
        currentLevel = 0.0f;
    }

    void release() {
        if (stage != EnvStage::IDLE) {
            stage = EnvStage::RELEASE;
        }
    }

    void reset() {
        stage = EnvStage::IDLE;
        currentLevel = 0.0f;
    }

    inline float getNext(double sampleRate) {
        switch (stage) {
            case EnvStage::ATTACK:
                rate = 1.0f / (attackSec * static_cast<float>(sampleRate));
                currentLevel += rate;
                if (currentLevel >= 1.0f) {
                    currentLevel = 1.0f;
                    stage = EnvStage::DECAY;
                }
                break;
            case EnvStage::DECAY:
                rate = (1.0f - sustainLevel) / (decaySec * static_cast<float>(sampleRate));
                currentLevel -= rate;
                if (currentLevel <= sustainLevel) {
                    currentLevel = sustainLevel;
                    stage = EnvStage::SUSTAIN;
                }
                break;
            case EnvStage::SUSTAIN:
                currentLevel = sustainLevel;
                break;
            case EnvStage::RELEASE:
                rate = sustainLevel / (releaseSec * static_cast<float>(sampleRate));
                currentLevel -= rate;
                if (currentLevel <= 0.0f) {
                    currentLevel = 0.0f;
                    stage = EnvStage::IDLE;
                }
                break;
            case EnvStage::IDLE:
                currentLevel = 0.0f;
                break;
        }
        return currentLevel;
    }

    bool isActive() const noexcept { return stage != EnvStage::IDLE; }
};

struct MoogLadderFilter {
    float y1{0.0f}, y2{0.0f}, y3{0.0f}, y4{0.0f};
    float oldx{0.0f}, oldy1{0.0f}, oldy2{0.0f}, oldy3{0.0f};

    void reset() {
        y1 = y2 = y3 = y4 = 0.0f;
        oldx = oldy1 = oldy2 = oldy3 = 0.0f;
    }

    inline float process(float input, float cutoffHz, float resonance, double sampleRate) {
        float f = (cutoffHz * 2.0f) / static_cast<float>(sampleRate);
        f = std::clamp(f, 0.001f, 0.99f);
        float k = 3.6f * f - 1.6f * f * f - 1.0f;
        float p = (k + 1.0f) * 0.5f;
        float scale = std::exp((1.0f - p) * 1.386249f);
        float r = resonance * scale;

        float x = input - r * y4;
        y1 = x * p + oldx * p - k * y1;
        y2 = y1 * p + oldy1 * p - k * y2;
        y3 = y2 * p + oldy2 * p - k * y3;
        y4 = y3 * p + oldy3 * p - k * y4;

        oldx = x;
        oldy1 = y1;
        oldy2 = y2;
        oldy3 = y3;

        return y4;
    }
};

struct SubtractiveVoice {
    int noteNumber{-1};
    float velocity{0.0f};
    float phase1{0.0f};
    float phase2{0.0f};
    bool active{false};

    ADSRData ampEnv;
    ADSRData filterEnv;
    MoogLadderFilter filter;

    void reset() {
        noteNumber = -1;
        velocity = 0.0f;
        phase1 = phase2 = 0.0f;
        active = false;
        ampEnv.reset();
        filterEnv.reset();
        filter.reset();
    }
};

/**
 * 16-Voice Polyphonic Virtual Analog Subtractive Synthesizer.
 */
class SubtractiveSynth : public InstrumentNode {
public:
    static constexpr size_t MAX_VOICES = 16;

    SubtractiveSynth(std::string id = "subtractive_synth");
    ~SubtractiveSynth() override = default;

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

    void setFilterCutoff(float hz);
    void setFilterResonance(float reso);

private:
    double sampleRate_{44100.0};
    std::array<SubtractiveVoice, MAX_VOICES> voices_;

    SynthWaveform osc1Wave_{SynthWaveform::SAW};
    SynthWaveform osc2Wave_{SynthWaveform::PULSE};
    float osc2DetuneSemitones_{0.0f};
    float oscMix_{0.5f};

    float filterCutoffHz_{2500.0f};
    float filterResonance_{0.4f};
    float filterEnvAmount_{3000.0f};

    float pitchBendSemitones_{0.0f};
    float modWheel_{0.0f};
};
