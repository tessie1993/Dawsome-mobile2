#pragma once

#include <atomic>
#include <cstdint>
#include <vector>

// Resident decoded audio + the refcounted handle instruments hold
// (blueprint media/SampleCache, CONTRACTS seam 3: NodeState blocks may
// carry SampleHandles - never raw pointers into cache-evictable memory).
//
// Lifetime protocol: SampleCache owns every SampleBuffer; a SampleBuffer is
// evictable only while its refcount is zero, and deallocation happens ONLY
// inside cache sweeps on non-RT threads. A handle's destructor is a plain
// atomic decrement - RT-safe as a belt (reaching zero merely makes the
// entry evictABLE later; the data stays valid until a sweep). The audio
// thread reads sample data exclusively through handles acquired for it
// builder-side at graph compile/adopt time.

namespace daw {

using FileId = uint64_t;   // Kotlin-computed content identity (fnv1a64 discipline)

struct SampleBuffer {
    FileId   fileId = 0;
    double   sampleRate = 0.0;     // the CONFORMED rate (cache key half)
    double   sourceRate = 0.0;     // as decoded, before conform (D5 bookkeeping)
    int      channels = 0;         // stored deinterleaved, capped at 2
    int64_t  frames = 0;
    std::vector<float> data;       // channel-planar: [ch0 frames][ch1 frames]

    // Clamps to the last real channel (mono reads as both stereo sides).
    const float* channel(int ch) const noexcept {
        const int last = channels > 0 ? channels - 1 : 0;
        return data.data() + size_t(ch < 0 ? 0 : (ch > last ? last : ch)) * size_t(frames);
    }
    size_t bytes() const noexcept { return data.size() * sizeof(float); }

    // ---- cache-internal (SampleCache only) ----------------------------------
    std::atomic<uint32_t> refs{0};
    uint64_t lastUseTick = 0;      // LRU stamp, cache-mutex-guarded
};

// Copyable pin on a cache entry. While any handle lives, the entry cannot
// be evicted; destruction never frees (see protocol above).
class SampleHandle {
public:
    SampleHandle() = default;
    explicit SampleHandle(SampleBuffer* b) noexcept : buf_(b) {
        if (buf_ != nullptr) buf_->refs.fetch_add(1, std::memory_order_relaxed);
    }
    SampleHandle(const SampleHandle& o) noexcept : SampleHandle(o.buf_) {}
    SampleHandle(SampleHandle&& o) noexcept : buf_(o.buf_) { o.buf_ = nullptr; }
    SampleHandle& operator=(SampleHandle o) noexcept {
        SampleBuffer* tmp = buf_; buf_ = o.buf_; o.buf_ = tmp;
        return *this;
    }
    ~SampleHandle() {
        if (buf_ != nullptr) buf_->refs.fetch_sub(1, std::memory_order_release);
    }

    const SampleBuffer* get() const noexcept { return buf_; }
    const SampleBuffer* operator->() const noexcept { return buf_; }
    explicit operator bool() const noexcept { return buf_ != nullptr; }
    void reset() noexcept {
        if (buf_ != nullptr) buf_->refs.fetch_sub(1, std::memory_order_release);
        buf_ = nullptr;
    }

private:
    SampleBuffer* buf_ = nullptr;
};

} // namespace daw
