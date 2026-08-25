#pragma once

#include <cstdint>

// RAII flush-to-zero / denormals-are-zero guard for the audio thread.
// Denormal operands cost up to ~100x on some cores during IIR/reverb decay
// tails; the callback arms this once per callback, restoring on exit.
//
// ARM64: FPCR bit 24 (FZ). x86-64 (host test builds): MXCSR FTZ (bit 15) and
// DAZ (bit 6). Other hosts: no-op.

namespace daw {

class ScopedNoDenormals {
public:
    ScopedNoDenormals() noexcept {
#if defined(__aarch64__)
        asm volatile("mrs %0, fpcr" : "=r"(saved_));
        const uint64_t fz = saved_ | (uint64_t(1) << 24);
        asm volatile("msr fpcr, %0" : : "r"(fz));
#elif defined(__x86_64__) || defined(__i386__)
        saved_ = static_cast<uint64_t>(getMxcsr());
        setMxcsr(static_cast<uint32_t>(saved_) | (1u << 15) | (1u << 6));
#else
        saved_ = 0;
#endif
    }

    ~ScopedNoDenormals() noexcept {
#if defined(__aarch64__)
        asm volatile("msr fpcr, %0" : : "r"(saved_));
#elif defined(__x86_64__) || defined(__i386__)
        setMxcsr(static_cast<uint32_t>(saved_));
#endif
    }

    ScopedNoDenormals(const ScopedNoDenormals&) = delete;
    ScopedNoDenormals& operator=(const ScopedNoDenormals&) = delete;

private:
#if defined(__x86_64__) || defined(__i386__)
    static uint32_t getMxcsr() noexcept {
        uint32_t v;
        asm volatile("stmxcsr %0" : "=m"(v));
        return v;
    }
    static void setMxcsr(uint32_t v) noexcept {
        asm volatile("ldmxcsr %0" : : "m"(v));
    }
#endif
    uint64_t saved_ = 0;
};

} // namespace daw
