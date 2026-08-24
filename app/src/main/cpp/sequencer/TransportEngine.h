#pragma once

#include <cstdint>
#include <cstddef>

/**
 * Sample-Accurate Transport Engine & Metronome Generator.
 */
class TransportEngine {
public:
    TransportEngine();
    ~TransportEngine() = default;

    void prepare(double sampleRate);
    void reset();

    void play();
    void pause();
    void stop();
    void seekToBeat(double beat);

    void setBpm(float bpm);
    float getBpm() const noexcept { return bpm_; }

    void setTimeSignature(int numerator, int denominator);
    void setLoop(bool enabled, double startBeat, double endBeat);

    void setMetronomeEnabled(bool enabled) noexcept { metronomeEnabled_ = enabled; }
    bool isMetronomeEnabled() const noexcept { return metronomeEnabled_; }

    void advance(size_t numFrames, double sampleRate);

    double getCurrentBeat() const noexcept { return currentBeat_; }
    double getSamplePosition() const noexcept { return samplePosition_; }
    bool isPlaying() const noexcept { return isPlaying_; }
    bool isLooping() const noexcept { return isLooping_; }

    void renderMetronome(float** outputBuffers, size_t numFrames, size_t numChannels);

private:
    double sampleRate_{44100.0};
    double currentBeat_{0.0};
    double samplePosition_{0.0};
    float bpm_{120.0f};
    int timeSigNum_{4};
    int timeSigDen_{4};

    bool isPlaying_{false};
    bool isLooping_{false};
    double loopStartBeat_{0.0};
    double loopEndBeat_{8.0};

    bool metronomeEnabled_{false};
    int lastDownbeatBeat_{-1};
    int metroSamplesRemaining_{0};
    float metroFreq_{1000.0f};
    float metroPhase_{0.0f};
};
