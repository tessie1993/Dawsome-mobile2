#pragma once

#include "TrackNode.h"
#include "GroupTrackNode.h"
#include "ReturnTrackNode.h"
#include "MasterNode.h"
#include "RoutingMatrix.h"
#include "../core/AudioBufferPool.h"
#include "../core/MeterFrame.h"
#include <array>
#include <memory>

/**
 * Top-Level Directed Acyclic Graph (DAG) Audio Evaluator.
 * Orchestrates multi-track rendering, send/return routing, and master summing.
 */
class AudioGraph {
public:
    static constexpr size_t MAX_TRACKS = 64;
    static constexpr size_t MAX_RETURNS = 8;

    AudioGraph();
    ~AudioGraph();

    void prepare(double sampleRate, size_t maxBlockSize);
    void process(const ProcessContext& ctx, float** outputBuffers);
    void releaseResources();

    std::shared_ptr<TrackNode> addTrack(const std::string& id);
    bool removeTrack(size_t trackIndex);
    void clearTracks();

    size_t getTrackCount() const noexcept { return trackCount_; }
    std::shared_ptr<TrackNode> getTrack(size_t index) const;

    std::shared_ptr<ReturnTrackNode> getReturnTrack(size_t index) const;
    std::shared_ptr<MasterNode> getMasterNode() const noexcept { return masterNode_; }
    RoutingMatrix& getRoutingMatrix() noexcept { return routingMatrix_; }

    size_t collectMeterFrames(MeterFrame* dest, size_t maxFrames) const;

private:
    std::array<std::shared_ptr<TrackNode>, MAX_TRACKS> tracks_;
    size_t trackCount_{0};

    std::array<std::shared_ptr<ReturnTrackNode>, MAX_RETURNS> returnTracks_;
    std::shared_ptr<MasterNode> masterNode_;
    RoutingMatrix routingMatrix_;

    AudioBufferPool bufferPool_;
    double sampleRate_{44100.0};
    size_t maxBlockSize_{256};
};
