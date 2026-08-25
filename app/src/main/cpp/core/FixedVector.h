#pragma once

#include <cstddef>
#include <type_traits>

#include "RtAssert.h"

// Fixed-capacity vector for realtime structures: all storage inline, no heap,
// no exceptions. POD-only by design - RT containers hold handles and values,
// never owning objects (ownership lives builder-side).

namespace daw {

template <typename T, size_t Capacity>
class FixedVector {
    static_assert(std::is_trivially_copyable_v<T> && std::is_trivially_destructible_v<T>,
                  "FixedVector is for POD payloads");

public:
    bool push_back(const T& value) noexcept {
        if (size_ >= Capacity) { DAW_RT_ASSERT(false); return false; }
        items_[size_++] = value;
        return true;
    }

    void pop_back() noexcept {
        DAW_RT_ASSERT(size_ > 0);
        if (size_ > 0) --size_;
    }

    // Remove by swapping the last element in - O(1), order not preserved.
    void eraseUnordered(size_t i) noexcept {
        DAW_RT_ASSERT(i < size_);
        if (i < size_) items_[i] = items_[--size_];
    }

    void clear() noexcept { size_ = 0; }

    T&       operator[](size_t i) noexcept { DAW_RT_ASSERT(i < size_); return items_[i]; }
    const T& operator[](size_t i) const noexcept { DAW_RT_ASSERT(i < size_); return items_[i]; }

    T*       begin() noexcept { return items_; }
    T*       end() noexcept { return items_ + size_; }
    const T* begin() const noexcept { return items_; }
    const T* end() const noexcept { return items_ + size_; }

    size_t size() const noexcept { return size_; }
    bool   empty() const noexcept { return size_ == 0; }
    bool   full() const noexcept { return size_ >= Capacity; }
    static constexpr size_t capacity() noexcept { return Capacity; }

private:
    T      items_[Capacity]{};
    size_t size_ = 0;
};

} // namespace daw
