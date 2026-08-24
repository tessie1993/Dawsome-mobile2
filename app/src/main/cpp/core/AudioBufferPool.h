#pragma once

#include <vector>
#include <cstddef>
#include <memory>

/**
 * Pre-allocated Scratch Buffer Pool.
 * Provides real-time thread scratch buffers without invoking malloc/new in audio callback.
 */
class AudioBufferPool {
public:
    AudioBufferPool(size_t maxChannels = 8, size_t maxFrames = 4096, size_t poolSize = 16);
    ~AudioBufferPool();

    void init(size_t maxChannels, size_t maxFrames, size_t poolSize);

    float* acquireChannelBuffer();
    void releaseChannelBuffer(float* buffer);

    float** acquireStereoBuffer();
    void releaseStereoBuffer(float** stereoPair);

    void clearAll();

private:
    size_t maxChannels_{8};
    size_t maxFrames_{4096};
    size_t poolSize_{16};

    std::vector<float> rawStorage_;
    std::vector<float*> freeList_;
    size_t activeCount_{0};
};
