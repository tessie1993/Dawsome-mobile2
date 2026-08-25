#pragma once

#include <atomic>
#include <type_traits>

// Seqlock publication of a small POD from ONE writer to any number of
// readers, none of whom can ever block the writer (CONTRACTS.md seam 4;
// used for TimeAnchor, TransportClock and the TempoMap RT tail).
//
// Writer (may be the audio thread): version -> odd, write payload, version ->
// even. Readers: load version (even?), copy payload, re-check version;
// retry on mismatch. Readers spin only while the writer is mid-store of a
// few dozen bytes - bounded and tiny.

namespace daw {

template <typename T>
class Seqlock {
    static_assert(std::is_trivially_copyable_v<T>, "seqlock payload must be POD");

public:
    // Single writer thread. Fence placement per Boehm's seqlock analysis:
    // the release fence keeps the odd version store visible before the
    // payload stores; the final release store publishes them.
    void publish(const T& value) noexcept {
        const uint32_t v = version_.load(std::memory_order_relaxed);
        version_.store(v + 1, std::memory_order_relaxed);      // odd: writing
        std::atomic_thread_fence(std::memory_order_release);
        payload_ = value;
        version_.store(v + 2, std::memory_order_release);      // even: stable
    }

    // Any reader thread. Returns the version the snapshot was taken at
    // (callers stamp derived work with it, e.g. tempoMapRev).
    uint32_t read(T& out) const noexcept {
        for (;;) {
            const uint32_t v0 = version_.load(std::memory_order_acquire);
            if ((v0 & 1u) == 0) {
                out = payload_;
                std::atomic_thread_fence(std::memory_order_acquire);
                if (version_.load(std::memory_order_relaxed) == v0) return v0;
            }
        }
    }

    uint32_t version() const noexcept {
        return version_.load(std::memory_order_acquire);
    }

private:
    std::atomic<uint32_t> version_{0};
    T payload_{};
};

} // namespace daw
