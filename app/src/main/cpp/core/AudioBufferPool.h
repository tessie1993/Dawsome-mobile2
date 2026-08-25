#pragma once

#include <memory>

#include "EngineConfig.h"
#include "RtAssert.h"

// Fixed pool of de-interleaved float scratch buffers for graph processing
// (blueprint 2.3; Tracktion-style buffer reuse). All memory is allocated in
// prepare() on the builder thread; acquire()/release() run on the audio
// thread only and are plain freelist operations - no atomics needed because
// the pool is owned by exactly one PlaybackGraph and touched only inside its
// process call.
//
// Exhaustion returns null (graph compilation sizes the pool to its schedule's
// worst case, so a null here is a builder bug, asserted in debug).

namespace daw {

class AudioBufferPool {
public:
    struct Buffer {
        float* channels[kMaxChannels] = {nullptr, nullptr};
        int    numChannels = 0;
    };

    // [builder] Allocate `count` stereo buffers of kMaxBlock frames.
    void prepare(int count) noexcept {
        DAW_RT_ASSERT(count > 0);
        count_ = count;
        storage_ = std::make_unique<float[]>(
            static_cast<size_t>(count) * kMaxChannels * kMaxBlock);
        buffers_ = std::make_unique<Buffer[]>(static_cast<size_t>(count));
        freeList_ = std::make_unique<int[]>(static_cast<size_t>(count));
        for (int i = 0; i < count; ++i) {
            for (int ch = 0; ch < kMaxChannels; ++ch)
                buffers_[i].channels[ch] =
                    storage_.get() + (static_cast<size_t>(i) * kMaxChannels + ch) * kMaxBlock;
            buffers_[i].numChannels = kMaxChannels;
            freeList_[i] = i;
        }
        freeTop_ = count;
    }

    // [RT] Borrow a scratch buffer. Contents are undefined; caller clears if summing.
    Buffer* acquire() noexcept {
        if (freeTop_ <= 0) { DAW_RT_ASSERT(false); return nullptr; }
        return &buffers_[freeList_[--freeTop_]];
    }

    // [RT] Return a buffer acquired this block.
    void release(Buffer* b) noexcept {
        DAW_RT_ASSERT(b != nullptr && freeTop_ < count_);
        freeList_[freeTop_++] = static_cast<int>(b - buffers_.get());
    }

    int capacity() const noexcept { return count_; }
    int available() const noexcept { return freeTop_; }

private:
    std::unique_ptr<float[]>  storage_;
    std::unique_ptr<Buffer[]> buffers_;
    std::unique_ptr<int[]>    freeList_;
    int count_ = 0;
    int freeTop_ = 0;
};

} // namespace daw
