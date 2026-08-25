#pragma once

#include <atomic>
#include <cstdint>

#include "RtAssert.h"

// Single-slot handover of built artifacts (PlaybackGraph, TimelineSnapshot
// units, TempoMap base) from the builder to the audio thread, with
// epoch-acknowledged retirement (CONTRACTS.md seams 3/4, blueprint 2.3).
//
// The artifact type T carries its own epoch (`uint64_t T::epoch`), assigned
// by the builder before offering and immutable afterwards - the epoch always
// travels with the pointer, so a claim racing a replacement offer can never
// pair a pointer with the wrong epoch.
//
//   builder: offer(built)      - replaces any unclaimed older offer, which is
//                                returned for immediate builder-side GC.
//   RT:      claim()           - at a block boundary; returns the offered
//                                artifact or null; epoch = claimed->epoch.
//   RT:      ackRetired(epoch) - release-store after state adoption; the
//                                builder frees the retired artifact only
//                                after retiredAcked(epoch) turns true.
//
// The audio thread never frees memory; the builder never touches a claimed
// artifact until a successor's adoption acked the retirement.

namespace daw {

template <typename T>
class OfferSlot {
public:
    // ---- builder thread -----------------------------------------------------

    // Returns the replaced, never-claimed offer (builder GCs it), or null.
    T* offer(T* built) noexcept {
        DAW_RT_ASSERT(built != nullptr && built->epoch != 0);
        return offered_.exchange(built, std::memory_order_acq_rel);
    }

    // Has the audio thread released the artifact retired at `epoch`?
    bool retiredAcked(uint64_t epoch) const noexcept {
        return ackedEpoch_.load(std::memory_order_acquire) >= epoch;
    }

    // ---- audio thread -------------------------------------------------------

    // Claim the pending offer, if any. Called at block boundaries only.
    T* claim() noexcept {
        return offered_.exchange(nullptr, std::memory_order_acq_rel);
    }

    // Publish that the artifact retired at `epoch` is no longer referenced.
    void ackRetired(uint64_t epoch) noexcept {
        DAW_RT_ASSERT(epoch >= ackedEpoch_.load(std::memory_order_relaxed));
        ackedEpoch_.store(epoch, std::memory_order_release);
    }

private:
    std::atomic<T*>       offered_{nullptr};
    std::atomic<uint64_t> ackedEpoch_{0};
};

} // namespace daw
