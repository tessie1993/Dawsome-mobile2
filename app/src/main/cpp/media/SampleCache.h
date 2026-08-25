#pragma once

#include <cstdint>
#include <map>
#include <memory>
#include <mutex>
#include <utility>

#include "SampleBuffer.h"

// Budgeted resident sample store (blueprint media/SampleCache, decision D5):
// keys are (fileId, conformedRate) - originals are never resampled in place,
// each requested rate gets its own conformed copy, built ONCE at load via
// dsp::SincResampler so realtime playback is a plain buffer read.
//
// Threading: every method is [non-RT] (worker/builder/io threads) and
// mutex-guarded; decode + conform run OUTSIDE the lock (loads are seconds,
// lookups are microseconds), with a double-check reinsert race guard. The
// audio thread never calls in here - it reads sample data through
// SampleHandles pinned for it at graph compile/adopt time (see
// SampleBuffer.h's lifetime protocol: refs==0 entries are evictable,
// deallocation happens only inside sweep() on these non-RT threads).
//
// Eviction: byte-weighted LRU over refs==0 entries until under budget
// (pinned entries are untouchable regardless of budget; a budget overrun
// with everything pinned is counted and surfaced, never forced). Budget
// defaults to the blueprint's low tier; the Kotlin side sets the device
// tier at engine start (256/512/768 MB).

namespace daw {

class SampleCache {
public:
    static constexpr size_t kDefaultBudgetBytes = size_t(256) << 20;

    static SampleCache& instance();

    // Blocking load-or-lookup: decode + conform on a miss. Empty handle on
    // decode failure (counted). `path` is only consulted on a miss.
    SampleHandle acquire(FileId id, const char* path, double targetRate);

    // Lookup only - never touches the filesystem.
    SampleHandle peek(FileId id, double targetRate);

    void setBudget(size_t bytes);
    void sweep();                      // evict LRU refs==0 until under budget

    size_t residentBytes() const;
    size_t entryCount() const;
    uint32_t decodeFailures() const;
    uint32_t budgetOverruns() const;   // sweeps that could not reach budget

private:
    using Key = std::pair<FileId, uint32_t>;   // (fileId, rate in Hz)

    static uint32_t rateKey(double rate) noexcept {
        return static_cast<uint32_t>(rate + 0.5);
    }
    void sweepLocked();                // caller holds mutex_

    mutable std::mutex mutex_;
    std::map<Key, std::unique_ptr<SampleBuffer>> entries_;
    size_t budgetBytes_ = kDefaultBudgetBytes;
    size_t residentBytes_ = 0;
    uint64_t tick_ = 0;
    uint32_t decodeFailures_ = 0;
    uint32_t budgetOverruns_ = 0;
};

} // namespace daw
