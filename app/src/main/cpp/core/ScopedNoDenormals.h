#pragma once

#include <cstdint>

/**
 * Scoped RAII guard that enables Flush-To-Zero (FTZ) and Denormals-Are-Zero (DAZ)
 * in the ARM FPSCR floating point control register.
 * Prevents 100x CPU degradation during filter & reverb decay tails.
 */
class ScopedNoDenormals {
public:
    ScopedNoDenormals() noexcept {
#if defined(__arm__) || defined(__aarch64__)
        #if defined(__aarch64__)
            uint64_t fpcr;
            asm volatile("mrs %0, fpcr" : "=r"(fpcr));
            savedFpcr_ = fpcr;
            // Set FZ (bit 24) to enable Flush-to-zero mode
            fpcr |= (1ULL << 24);
            asm volatile("msr fpcr, %0" : : "r"(fpcr));
        #else
            uint32_t fpscr;
            asm volatile("vmrs %0, fpscr" : "=r"(fpscr));
            savedFpcr_ = fpscr;
            // Set FZ (bit 24) to enable Flush-to-zero mode
            fpscr |= (1U << 24);
            asm volatile("vmsr fpscr, %0" : : "r"(fpscr));
        #endif
#else
        savedFpcr_ = 0;
#endif
    }

    ~ScopedNoDenormals() noexcept {
#if defined(__arm__) || defined(__aarch64__)
        #if defined(__aarch64__)
            asm volatile("msr fpcr, %0" : : "r"(savedFpcr_));
        #else
            asm volatile("vmsr fpscr, %0" : : "r"((uint32_t)savedFpcr_));
        #endif
#endif
    }

    ScopedNoDenormals(const ScopedNoDenormals&) = delete;
    ScopedNoDenormals& operator=(const ScopedNoDenormals&) = delete;

private:
    uint64_t savedFpcr_{0};
};
