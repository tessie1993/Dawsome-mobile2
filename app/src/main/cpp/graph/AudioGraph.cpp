#include "AudioGraph.h"
#include <algorithm>
#include <cstring>

AudioGraph::AudioGraph()
    : bufferPool_(16, 256) {
    masterNode_ = std::make_shared<MasterNode>();
    for (size_t r = 0; r < MAX_RETURNS; ++r) {
        returnTracks_[r] = std::make_shared<ReturnTrackNode>("return_" + std::to_string(r), static_cast<int32_t>(r));
    }
}

AudioGraph::~AudioGraph() {
    releaseResources();
}

void AudioGraph::prepare(double sampleRate, size_t maxBlockSize) {
    sampleRate_ = sampleRate;
    maxBlockSize_ = maxBlockSize;

    bufferPool_.prepare(maxBlockSize, 2);
    routingMatrix_.prepare(sampleRate);
    masterNode_->prepareToPlay(sampleRate, maxBlockSize);

    for (size_t r = 0; r < MAX_RETURNS; ++r) {
        if (returnTracks_[r]) {
            returnTracks_[r]->prepareToPlay(sampleRate, maxBlockSize);
        }
    }

    for (size_t t = 0; t < trackCount_; ++t) {
        if (tracks_[t]) {
            tracks_[t]->prepareToPlay(sampleRate, maxBlockSize);
        }
    }
}

void AudioGraph::releaseResources() {
    masterNode_->releaseResources();
    for (size_t r = 0; r < MAX_RETURNS; ++r) {
        if (returnTracks_[r]) returnTracks_[r]->releaseResources();
    }
    for (size_t t = 0; t < trackCount_; ++t) {
        if (tracks_[t]) tracks_[t]->releaseResources();
    }
}

std::shared_ptr<TrackNode> AudioGraph::addTrack(const std::string& id) {
    if (trackCount_ >= MAX_TRACKS) return nullptr;
    auto track = std::make_shared<TrackNode>(id, static_cast<int32_t>(trackCount_));
    track->prepareToPlay(sampleRate_, maxBlockSize_);
    tracks_[trackCount_++] = track;
    return track;
}

bool AudioGraph::removeTrack(size_t trackIndex) {
    if (trackIndex >= trackCount_) return false;
    tracks_[trackIndex]->releaseResources();
    for (size_t i = trackIndex; i < trackCount_ - 1; ++i) {
        tracks_[i] = tracks_[i + 1];
    }
    tracks_[--trackCount_] = nullptr;
    return true;
}

void AudioGraph::clearTracks() {
    for (size_t i = 0; i < trackCount_; ++i) {
        if (tracks_[i]) tracks_[i]->releaseResources();
        tracks_[i] = nullptr;
    }
    trackCount_ = 0;
}

std::shared_ptr<TrackNode> AudioGraph::getTrack(size_t index) const {
    if (index < trackCount_) return tracks_[index];
    return nullptr;
}

std::shared_ptr<ReturnTrackNode> AudioGraph::getReturnTrack(size_t index) const {
    if (index < MAX_RETURNS) return returnTracks_[index];
    return nullptr;
}

void AudioGraph::process(const ProcessContext& ctx, float** outputBuffers) {
    float** trackScratch = bufferPool_.acquireBuffer();
    float** masterSumScratch = bufferPool_.acquireBuffer();
    float** returnScratch[MAX_RETURNS];

    for (size_t r = 0; r < MAX_RETURNS; ++r) {
        returnScratch[r] = bufferPool_.acquireBuffer();
        for (size_t ch = 0; ch < ctx.numChannels; ++ch) {
            std::fill_n(returnScratch[r][ch], ctx.numFrames, 0.0f);
        }
    }

    for (size_t ch = 0; ch < ctx.numChannels; ++ch) {
        std::fill_n(masterSumScratch[ch], ctx.numFrames, 0.0f);
    }

    // Check if any track is soloed
    bool hasSolo = false;
    for (size_t t = 0; t < trackCount_; ++t) {
        if (tracks_[t] && tracks_[t]->isSoloed()) {
            hasSolo = true;
            break;
        }
    }

    // 1. Render all active Tracks & compute sends
    for (size_t t = 0; t < trackCount_; ++t) {
        auto& track = tracks_[t];
        if (!track) continue;

        if (hasSolo && !track->isSoloed()) {
            continue; // Mute tracks that are not soloed
        }

        for (size_t ch = 0; ch < ctx.numChannels; ++ch) {
            std::fill_n(trackScratch[ch], ctx.numFrames, 0.0f);
        }

        track->process(ctx, trackScratch, trackScratch);

        // Sum track output to master bus
        for (size_t ch = 0; ch < ctx.numChannels; ++ch) {
            float* trk = trackScratch[ch];
            float* mst = masterSumScratch[ch];
            for (size_t i = 0; i < ctx.numFrames; ++i) {
                mst[i] += trk[i];
            }
        }

        // Distribute to Returns
        for (size_t r = 0; r < MAX_RETURNS; ++r) {
            for (size_t i = 0; i < ctx.numFrames; ++i) {
                float sendGain = routingMatrix_.getNextSmoothedSend(t, r);
                if (sendGain > 0.0001f) {
                    for (size_t ch = 0; ch < ctx.numChannels; ++ch) {
                        returnScratch[r][ch][i] += trackScratch[ch][i] * sendGain;
                    }
                }
            }
        }
    }

    // 2. Render all Return Tracks and sum to master
    for (size_t r = 0; r < MAX_RETURNS; ++r) {
        auto& retTrack = returnTracks_[r];
        if (retTrack && retTrack->isEnabled() && !retTrack->isMuted()) {
            retTrack->process(ctx, returnScratch[r], returnScratch[r]);
            for (size_t ch = 0; ch < ctx.numChannels; ++ch) {
                float* ret = returnScratch[r][ch];
                float* mst = masterSumScratch[ch];
                for (size_t i = 0; i < ctx.numFrames; ++i) {
                    mst[i] += ret[i];
                }
            }
        }
    }

    // 3. Process Master Node (Master Inserts + Master Fader + Brickwall Limiter)
    masterNode_->process(ctx, masterSumScratch, outputBuffers);

    // Release preallocated scratch buffers
    bufferPool_.releaseBuffer(trackScratch);
    bufferPool_.releaseBuffer(masterSumScratch);
    for (size_t r = 0; r < MAX_RETURNS; ++r) {
        bufferPool_.releaseBuffer(returnScratch[r]);
    }
}

size_t AudioGraph::collectMeterFrames(MeterFrame* dest, size_t maxFrames) const {
    size_t count = 0;
    for (size_t t = 0; t < trackCount_ && count < maxFrames; ++t) {
        if (tracks_[t]) {
            dest[count++] = tracks_[t]->getMeterFrame();
        }
    }
    if (masterNode_ && count < maxFrames) {
        dest[count++] = masterNode_->getMasterMeterFrame();
    }
    return count;
}
