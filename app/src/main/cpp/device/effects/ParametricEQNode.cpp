#include "ParametricEQNode.h"
#include <cmath>
#include <algorithm>

ParametricEQNode::ParametricEQNode(std::string id)
    : EffectNode(std::move(id)) {
    // Default 5-band configuration (Low Cut, Low Shelf, Mid Bell, High Shelf, High Cut)
    bands_[0] = {EQFilterType::LOW_CUT, 30.0f, 0.707f, 0.0f, true};
    bands_[1] = {EQFilterType::LOW_SHELF, 120.0f, 0.707f, 0.0f, true};
    bands_[2] = {EQFilterType::PEAK, 1000.0f, 1.0f, 0.0f, true};
    bands_[3] = {EQFilterType::HIGH_SHELF, 5000.0f, 0.707f, 0.0f, true};
    bands_[4] = {EQFilterType::HIGH_CUT, 18000.0f, 0.707f, 0.0f, true};
}

void ParametricEQNode::prepareToPlay(double sampleRate, size_t /*maxBlockSize*/) {
    sampleRate_ = sampleRate;
    resetStates();
    for (size_t i = 0; i < NUM_BANDS; ++i) {
        calculateCoefficients(i);
    }
}

void ParametricEQNode::releaseResources() {
    resetStates();
}

void ParametricEQNode::resetStates() {
    for (size_t b = 0; b < NUM_BANDS; ++b) {
        for (size_t ch = 0; ch < 2; ++ch) {
            states_[b][ch] = {0.0f, 0.0f};
        }
    }
}

void ParametricEQNode::setBand(size_t bandIndex, EQFilterType type, float freqHz, float q, float gainDb) {
    if (bandIndex < NUM_BANDS) {
        bands_[bandIndex].type = type;
        bands_[bandIndex].freqHz = std::clamp(freqHz, 20.0f, static_cast<float>(sampleRate_ * 0.49));
        bands_[bandIndex].q = std::clamp(q, 0.1f, 18.0f);
        bands_[bandIndex].gainDb = std::clamp(gainDb, -24.0f, 24.0f);
        calculateCoefficients(bandIndex);
    }
}

void ParametricEQNode::calculateCoefficients(size_t bandIndex) {
    const auto& band = bands_[bandIndex];
    auto& c = coeffs_[bandIndex];

    const double omega = 2.0 * M_PI * band.freqHz / sampleRate_;
    const double sinOmega = std::sin(omega);
    const double cosOmega = std::cos(omega);
    const double alpha = sinOmega / (2.0 * band.q);
    const double A = std::pow(10.0, band.gainDb / 40.0);

    double b0 = 1.0, b1 = 0.0, b2 = 0.0, a0 = 1.0, a1 = 0.0, a2 = 0.0;

    switch (band.type) {
        case EQFilterType::LOW_CUT: // Highpass
            b0 = (1.0 + cosOmega) / 2.0;
            b1 = -(1.0 + cosOmega);
            b2 = (1.0 + cosOmega) / 2.0;
            a0 = 1.0 + alpha;
            a1 = -2.0 * cosOmega;
            a2 = 1.0 - alpha;
            break;

        case EQFilterType::LOW_SHELF:
            b0 = A * ((A + 1.0) - (A - 1.0) * cosOmega + 2.0 * std::sqrt(A) * alpha);
            b1 = 2.0 * A * ((A - 1.0) - (A + 1.0) * cosOmega);
            b2 = A * ((A + 1.0) - (A - 1.0) * cosOmega - 2.0 * std::sqrt(A) * alpha);
            a0 = (A + 1.0) + (A - 1.0) * cosOmega + 2.0 * std::sqrt(A) * alpha;
            a1 = -2.0 * ((A - 1.0) + (A + 1.0) * cosOmega);
            a2 = (A + 1.0) + (A - 1.0) * cosOmega - 2.0 * std::sqrt(A) * alpha;
            break;

        case EQFilterType::PEAK:
            b0 = 1.0 + alpha * A;
            b1 = -2.0 * cosOmega;
            b2 = 1.0 - alpha * A;
            a0 = 1.0 + alpha / A;
            a1 = -2.0 * cosOmega;
            a2 = 1.0 - alpha / A;
            break;

        case EQFilterType::HIGH_SHELF:
            b0 = A * ((A + 1.0) + (A - 1.0) * cosOmega + 2.0 * std::sqrt(A) * alpha);
            b1 = -2.0 * A * ((A - 1.0) + (A + 1.0) * cosOmega);
            b2 = A * ((A + 1.0) + (A - 1.0) * cosOmega - 2.0 * std::sqrt(A) * alpha);
            a0 = (A + 1.0) - (A - 1.0) * cosOmega + 2.0 * std::sqrt(A) * alpha;
            a1 = 2.0 * ((A - 1.0) - (A + 1.0) * cosOmega);
            a2 = (A + 1.0) - (A - 1.0) * cosOmega - 2.0 * std::sqrt(A) * alpha;
            break;

        case EQFilterType::HIGH_CUT: // Lowpass
            b0 = (1.0 - cosOmega) / 2.0;
            b1 = 1.0 - cosOmega;
            b2 = (1.0 - cosOmega) / 2.0;
            a0 = 1.0 + alpha;
            a1 = -2.0 * cosOmega;
            a2 = 1.0 - alpha;
            break;
    }

    // Normalize by a0
    c.b0 = static_cast<float>(b0 / a0);
    c.b1 = static_cast<float>(b1 / a0);
    c.b2 = static_cast<float>(b2 / a0);
    c.a1 = static_cast<float>(a1 / a0);
    c.a2 = static_cast<float>(a2 / a0);
}

void ParametricEQNode::process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) {
    if (!isEnabled_) {
        for (size_t ch = 0; ch < ctx.numChannels; ++ch) {
            std::copy_n(inBuffers[ch], ctx.numFrames, outBuffers[ch]);
        }
        return;
    }

    for (size_t ch = 0; ch < std::min(ctx.numChannels, static_cast<size_t>(2)); ++ch) {
        const float* in = inBuffers[ch];
        float* out = outBuffers[ch];

        for (size_t i = 0; i < ctx.numFrames; ++i) {
            float sample = in[i];

            for (size_t b = 0; b < NUM_BANDS; ++b) {
                if (!bands_[b].isEnabled) continue;

                const auto& c = coeffs_[b];
                auto& st = states_[b][ch];

                // Direct Form II Transposed difference equation
                float filtered = (c.b0 * sample) + st.z1;
                st.z1 = (c.b1 * sample) - (c.a1 * filtered) + st.z2;
                st.z2 = (c.b2 * sample) - (c.a2 * filtered);

                sample = filtered;
            }

            out[i] = sample;
        }
    }
}

void ParametricEQNode::setParameter(const std::string& paramName, float value) {
    if (paramName == "low_gain") setBand(1, EQFilterType::LOW_SHELF, bands_[1].freqHz, bands_[1].q, value);
    else if (paramName == "mid_gain") setBand(2, EQFilterType::PEAK, bands_[2].freqHz, bands_[2].q, value);
    else if (paramName == "high_gain") setBand(3, EQFilterType::HIGH_SHELF, bands_[3].freqHz, bands_[3].q, value);
    else if (paramName == "mid_freq") setBand(2, EQFilterType::PEAK, value, bands_[2].q, bands_[2].gainDb);
    else if (paramName == "mix") setDryWet(value);
}

float ParametricEQNode::getParameter(const std::string& paramName) const {
    if (paramName == "low_gain") return bands_[1].gainDb;
    if (paramName == "mid_gain") return bands_[2].gainDb;
    if (paramName == "high_gain") return bands_[3].gainDb;
    if (paramName == "mid_freq") return bands_[2].freqHz;
    if (paramName == "mix") return getDryWet();
    return 0.0f;
}
