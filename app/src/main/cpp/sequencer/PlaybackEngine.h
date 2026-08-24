#pragma once

#include "../graph/TrackNode.h"

/**
 * Base Abstract Playback Engine for Sequencers (Arrangement Timeline & Session Grid).
 */
class PlaybackEngine {
public:
    virtual ~PlaybackEngine() = default;

    virtual void prepare(double sampleRate) = 0;
    virtual void evaluate(double startBeat, double endBeat, TrackNode* track) = 0;
    virtual void reset() = 0;
};
