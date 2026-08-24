#pragma once

#include "PlaybackEngine.h"
#include <vector>
#include <array>

struct NativeMidiNote {
    int pitch{60};
    double startBeat{0.0};
    double lengthBeats{1.0};
    float velocity{0.8f};
};

struct NativeArrangementClip {
    double startBeat{0.0};
    double lengthBeats{4.0};
    bool isMuted{false};
    std::vector<NativeMidiNote> notes;
};

/**
 * Arrangement Timeline Evaluator.
 * Queries note events spanning real-time audio block windows.
 */
class ArrangementEngine : public PlaybackEngine {
public:
    static constexpr size_t MAX_CLIPS = 128;

    ArrangementEngine();
    ~ArrangementEngine() override = default;

    void prepare(double sampleRate) override;
    void evaluate(double startBeat, double endBeat, TrackNode* track) override;
    void reset() override;

    void addClip(const NativeArrangementClip& clip);
    void clearClips();

private:
    std::vector<NativeArrangementClip> clips_;
    double sampleRate_{44100.0};
};
