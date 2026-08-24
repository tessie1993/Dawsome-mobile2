#include "RoutingMatrix.h"

RoutingMatrix::RoutingMatrix() {
    reset();
}

void RoutingMatrix::prepare(double sampleRate) {
    // 20ms smoothing ramp for send level changes to eliminate zipper noise
    const int rampFrames = static_cast<int>(sampleRate * 0.02);
    for (size_t t = 0; t < MAX_TRACKS; ++t) {
        for (size_t r = 0; r < MAX_RETURNS; ++r) {
            sendSmoothers_[t][r].setRampFrames(rampFrames);
        }
    }
}

void RoutingMatrix::reset() {
    for (size_t t = 0; t < MAX_TRACKS; ++t) {
        sidechainSources_[t] = -1; // -1 means no sidechain input
        for (size_t r = 0; r < MAX_RETURNS; ++r) {
            sendSmoothers_[t][r].reset(0.0f);
        }
    }
}

void RoutingMatrix::setSendLevel(size_t srcTrack, size_t returnIndex, float levelLinear) {
    if (srcTrack < MAX_TRACKS && returnIndex < MAX_RETURNS) {
        sendSmoothers_[srcTrack][returnIndex].setTarget(levelLinear);
    }
}

float RoutingMatrix::getSendLevel(size_t srcTrack, size_t returnIndex) const {
    if (srcTrack < MAX_TRACKS && returnIndex < MAX_RETURNS) {
        return sendSmoothers_[srcTrack][returnIndex].getTarget();
    }
    return 0.0f;
}

float RoutingMatrix::getNextSmoothedSend(size_t srcTrack, size_t returnIndex) {
    if (srcTrack < MAX_TRACKS && returnIndex < MAX_RETURNS) {
        return sendSmoothers_[srcTrack][returnIndex].getNext();
    }
    return 0.0f;
}

void RoutingMatrix::setSidechainRoute(size_t destTrack, int srcTrack) {
    if (destTrack < MAX_TRACKS) {
        sidechainSources_[destTrack] = srcTrack;
    }
}

int RoutingMatrix::getSidechainSource(size_t destTrack) const {
    if (destTrack < MAX_TRACKS) {
        return sidechainSources_[destTrack];
    }
    return -1;
}
