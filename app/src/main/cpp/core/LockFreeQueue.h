#pragma once

#include <atomic>
#include <cstddef>
#include <array>

/**
 * Single-Producer Single-Consumer (SPSC) Lock-Free Ring Buffer.
 * Guarantees wait-free, zero-allocation message passing between UI thread and Real-Time Audio thread.
 */
template <typename T, size_t Capacity>
class LockFreeQueue {
    static_assert((Capacity & (Capacity - 1)) == 0, "Capacity must be a power of 2");

public:
    LockFreeQueue() : head_(0), tail_(0) {}

    bool push(const T& item) noexcept {
        const size_t currentTail = tail_.load(std::memory_order_relaxed);
        const size_t currentHead = head_.load(std::memory_order_acquire);

        if ((currentTail - currentHead) >= Capacity) {
            return false; // Queue is full
        }

        buffer_[currentTail & (Capacity - 1)] = item;
        tail_.store(currentTail + 1, std::memory_order_release);
        return true;
    }

    bool pop(T& item) noexcept {
        const size_t currentHead = head_.load(std::memory_order_relaxed);
        const size_t currentTail = tail_.load(std::memory_order_acquire);

        if (currentHead == currentTail) {
            return false; // Queue is empty
        }

        item = buffer_[currentHead & (Capacity - 1)];
        head_.store(currentHead + 1, std::memory_order_release);
        return true;
    }

    size_t size() const noexcept {
        const size_t currentHead = head_.load(std::memory_order_relaxed);
        const size_t currentTail = tail_.load(std::memory_order_relaxed);
        return (currentTail >= currentHead) ? (currentTail - currentHead) : 0;
    }

    bool isEmpty() const noexcept {
        return head_.load(std::memory_order_relaxed) == tail_.load(std::memory_order_relaxed);
    }

    void clear() noexcept {
        head_.store(0, std::memory_order_relaxed);
        tail_.store(0, std::memory_order_relaxed);
    }

private:
    std::array<T, Capacity> buffer_;
    alignas(64) std::atomic<size_t> head_;
    alignas(64) std::atomic<size_t> tail_;
};
