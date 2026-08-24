#pragma once

#include <vector>
#include <complex>

// Assuming signalsmith-math is in include path
// #include "signalsmith-math/fft.h"

namespace dsp {

/**
 * Wrapper for Signalsmith's highly optimized MIT FFT.
 * Replaces libfftw.
 */
class FFTProcessor {
public:
    FFTProcessor(size_t size) : size_(size) {
        // Initialize Signalsmith FFT object here
        // e.g. fft_.resize(size);
    }

    void performForward(const float* timeDomain, std::complex<float>* freqDomain) {
        // fft_.fft(timeDomain, freqDomain);
    }

    void performInverse(const std::complex<float>* freqDomain, float* timeDomain) {
        // fft_.ifft(freqDomain, timeDomain);
    }

    size_t getSize() const noexcept { return size_; }

private:
    size_t size_;
    // signalsmith::fft::FFT<float> fft_;
};

} // namespace dsp
