#pragma once

#include <vector>

// Assuming r8brain-free-src is in include path
// #include "r8bbase.h"
// #include "CDSPResampler.h"

namespace dsp {

/**
 * Wrapper for r8brain-free-src.
 * Replaces libsamplerate.
 */
class Resampler {
public:
    Resampler(double srcSampleRate, double destSampleRate) {
        // Example initialization:
        // resampler_ = new r8b::CDSPResampler16(srcSampleRate, destSampleRate, 2.0);
    }

    ~Resampler() {
        // delete resampler_;
    }

    void process(const float* input, int inputLength, std::vector<double>& outputBuffer) {
        // 1. Convert input floats to doubles for r8brain
        // std::vector<double> doubleInput(input, input + inputLength);
        
        // 2. Process via r8brain
        // double* op0;
        // int outputLength = resampler_->process(&doubleInput[0], inputLength, op0);
        
        // 3. Write back to outputBuffer
        // outputBuffer.clear();
        // outputBuffer.insert(outputBuffer.end(), op0, op0 + outputLength);
    }

private:
    // r8b::CDSPResampler* resampler_{nullptr};
};

} // namespace dsp
