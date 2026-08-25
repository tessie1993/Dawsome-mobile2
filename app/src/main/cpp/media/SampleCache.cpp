#include "SampleCache.h"

#include <vector>

#include "../dsp/SincResampler.h"
#include "AudioFileDecoder.h"

namespace daw {

SampleCache& SampleCache::instance() {
    static SampleCache cache;
    return cache;
}

SampleHandle SampleCache::acquire(FileId id, const char* path, double targetRate) {
    const Key key{id, rateKey(targetRate)};
    {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = entries_.find(key);
        if (it != entries_.end()) {
            it->second->lastUseTick = ++tick_;
            return SampleHandle(it->second.get());
        }
    }

    // Miss: decode + conform OUTSIDE the lock (this is the slow path).
    DecodeResult decoded = AudioFileDecoder::decodeFile(path);
    if (!decoded.ok) {
        std::lock_guard<std::mutex> lock(mutex_);
        ++decodeFailures_;
        return {};
    }

    auto buf = std::make_unique<SampleBuffer>();
    buf->fileId = id;
    buf->sourceRate = decoded.sampleRate;
    buf->sampleRate = targetRate;
    buf->channels = decoded.channels;
    if (decoded.sampleRate == targetRate) {
        buf->frames = decoded.frames;
        buf->data = std::move(decoded.planar);
    } else {
        // Conform once per channel; channel lengths stay identical because
        // the resampler's output length is a pure function of the rates.
        for (int c = 0; c < decoded.channels; ++c) {
            std::vector<float> conformed = dsp::SincResampler::process(
                decoded.planar.data() + size_t(c) * size_t(decoded.frames),
                decoded.frames, decoded.sampleRate, targetRate);
            if (c == 0) {
                buf->frames = static_cast<int64_t>(conformed.size());
                buf->data.reserve(size_t(decoded.channels) * conformed.size());
            }
            buf->data.insert(buf->data.end(), conformed.begin(), conformed.end());
        }
        if (buf->frames <= 0) {
            std::lock_guard<std::mutex> lock(mutex_);
            ++decodeFailures_;
            return {};
        }
    }

    std::lock_guard<std::mutex> lock(mutex_);
    auto it = entries_.find(key);
    if (it == entries_.end()) {
        // Winner of a concurrent-load race inserts; losers adopt the winner.
        buf->lastUseTick = ++tick_;
        residentBytes_ += buf->bytes();
        it = entries_.emplace(key, std::move(buf)).first;
        if (residentBytes_ > budgetBytes_) sweepLocked();
    } else {
        it->second->lastUseTick = ++tick_;
    }
    return SampleHandle(it->second.get());
}

SampleHandle SampleCache::peek(FileId id, double targetRate) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = entries_.find(Key{id, rateKey(targetRate)});
    if (it == entries_.end()) return {};
    it->second->lastUseTick = ++tick_;
    return SampleHandle(it->second.get());
}

void SampleCache::setBudget(size_t bytes) {
    std::lock_guard<std::mutex> lock(mutex_);
    budgetBytes_ = bytes;
    if (residentBytes_ > budgetBytes_) sweepLocked();
}

void SampleCache::sweep() {
    std::lock_guard<std::mutex> lock(mutex_);
    sweepLocked();
}

void SampleCache::sweepLocked() {
    // Byte-weighted LRU: evict the oldest unpinned entries until under
    // budget. Pinned entries (refs > 0) are untouchable - an unreachable
    // budget is counted, never forced.
    while (residentBytes_ > budgetBytes_) {
        auto victim = entries_.end();
        uint64_t oldest = ~uint64_t(0);
        for (auto it = entries_.begin(); it != entries_.end(); ++it) {
            if (it->second->refs.load(std::memory_order_acquire) != 0) continue;
            if (it->second->lastUseTick < oldest) {
                oldest = it->second->lastUseTick;
                victim = it;
            }
        }
        if (victim == entries_.end()) {
            ++budgetOverruns_;
            return;
        }
        residentBytes_ -= victim->second->bytes();
        entries_.erase(victim);        // the ONLY deallocation site
    }
}

size_t SampleCache::residentBytes() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return residentBytes_;
}

size_t SampleCache::entryCount() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return entries_.size();
}

uint32_t SampleCache::decodeFailures() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return decodeFailures_;
}

uint32_t SampleCache::budgetOverruns() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return budgetOverruns_;
}

} // namespace daw
