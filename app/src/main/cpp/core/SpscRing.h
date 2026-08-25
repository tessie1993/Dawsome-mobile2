#pragma once

#include <atomic>
#include <cstddef>
#include <type_traits>

#include "EngineConfig.h"
#include "RtAssert.h"

// Single-producer / single-consumer lock-free ring for trivially copyable
// payloads. One instance per producer thread (CONTRACTS.md seam 2); the audio
// thread is always the consumer.
//
// Standard SPSC discipline: each side loads its own index relaxed and the
// other side's index with acquire; publishes with release. Indices are
// monotonically increasing and masked on access (capacity must be a power of
// two), which distinguishes full from empty without a wasted slot. Producer
// and consumer state live on separate cache lines to avoid false sharing.

namespace daw {

template <typename T, size_t Capacity>
class SpscRing {
    static_assert(std::is_trivially_copyable_v<T>, "ring payloads must be POD");
    static_assert(Capacity >= 2 && (Capacity & (Capacity - 1)) == 0,
                  "capacity must be a power of two");

public:
    // Producer thread only.
    bool tryPush(const T& value) noexcept {
        const uint64_t tail = tail_.load(std::memory_order_relaxed);
        const uint64_t head = head_.load(std::memory_order_acquire);
        if (tail - head >= Capacity) return false;              // full
        slots_[tail & kMask] = value;
        tail_.store(tail + 1, std::memory_order_release);
        return true;
    }

    // Producer thread only: free slots at this instant (may grow concurrently,
    // never shrink from the producer's point of view).
    size_t freeSlots() const noexcept {
        const uint64_t tail = tail_.load(std::memory_order_relaxed);
        const uint64_t head = head_.load(std::memory_order_acquire);
        return Capacity - static_cast<size_t>(tail - head);
    }

    // Consumer thread only.
    bool tryPop(T& out) noexcept {
        const uint64_t head = head_.load(std::memory_order_relaxed);
        const uint64_t tail = tail_.load(std::memory_order_acquire);
        if (head == tail) return false;                          // empty
        out = slots_[head & kMask];
        head_.store(head + 1, std::memory_order_release);
        return true;
    }

    // Consumer thread only: pending items at this instant.
    size_t pending() const noexcept {
        const uint64_t head = head_.load(std::memory_order_relaxed);
        const uint64_t tail = tail_.load(std::memory_order_acquire);
        return static_cast<size_t>(tail - head);
    }

    static constexpr size_t capacity() noexcept { return Capacity; }

private:
    static constexpr uint64_t kMask = Capacity - 1;

    alignas(kCacheLine) std::atomic<uint64_t> head_{0};   // consumer-owned
    alignas(kCacheLine) std::atomic<uint64_t> tail_{0};   // producer-owned
    alignas(kCacheLine) T slots_[Capacity]{};
};

} // namespace daw
