#pragma once

#include <cstddef>
#include <cstdint>

/**
 * Real-Time Audio Block Processing Context.
 * Contains block size, sampling parameters, musical timeline position, and tempo state.
 */
struct ProcessContext {
    float** inputs{nullptr};
    float** outputs{nullptr};
    size_t numInputChannels{2};
    size_t numOutputChannels{2};
    size_t numFrames{256};
    double sampleRate{44100.0};

    // Musical Timeline Context
    double bpm{120.0};
    double playheadBeat{0.0};
    bool isPlaying{false};
    bool isRecording{false};
    bool isLooping{false};
    double loopStartBeat{0.0};
    double loopEndBeat{16.0};

    uint64_t samplePosition{0};
};
