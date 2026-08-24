#include "AudioBufferPool.h"
#include <algorithm>
#include <cstring>

AudioBufferPool::AudioBufferPool(size_t maxChannels, size_t maxFrames, size_t poolSize) {
    init(maxChannels, maxFrames, poolSize);
}

AudioBufferPool::~AudioBufferPool() {
    clearAll();
}

void AudioBufferPool::init(size_t maxChannels, size_t maxFrames, size_t poolSize) {
    maxChannels_ = maxChannels;
    maxFrames_ = maxFrames;
    poolSize_ = poolSize;

    const size_t totalFloats = maxFrames_ * poolSize_;
    rawStorage_.assign(totalFloats, 0.0f);

    freeList_.clear();
    freeList_.reserve(poolSize_);
    for (size_t i = 0; i < poolSize_; ++i) {
        freeList_.push_back(&rawStorage_[i * maxFrames_]);
    }
    activeCount_ = 0;
}

float* AudioBufferPool::acquireChannelBuffer() {
    if (freeList_.empty()) {
        return nullptr; // No allocation allowed on real-time thread!
    }
    float* buf = freeList_.back();
    freeList_.pop_back();
    ++activeCount_;
    std::memset(buf, 0, maxFrames_ * sizeof(float));
    return buf;
}

void AudioBufferPool::releaseChannelBuffer(float* buffer) {
    if (!buffer) return;
    freeList_.push_back(buffer);
    if (activeCount_ > 0) --activeCount_;
}

float** AudioBufferPool::acquireStereoBuffer() {
    float* l = acquireChannelBuffer();
    float* r = acquireChannelBuffer();
    if (!l || !r) {
        if (l) releaseChannelBuffer(l);
        if (r) releaseChannelBuffer(r);
        return nullptr;
    }
    // Using static storage for 2-element array pointer
    static thread_local float* pair[2];
    pair[0] = l;
    pair[1] = r;
    return pair;
}

void AudioBufferPool::releaseStereoBuffer(float** stereoPair) {
    if (!stereoPair) return;
    releaseChannelBuffer(stereoPair[0]);
    releaseChannelBuffer(stereoPair[1]);
}

void AudioBufferPool::clearAll() {
    std::fill(rawStorage_.begin(), rawStorage_.end(), 0.0f);
}
