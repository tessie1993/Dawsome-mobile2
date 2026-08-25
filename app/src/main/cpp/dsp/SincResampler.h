#pragma once

#include <cmath>
#include <memory>
#include <mutex>
#include <vector>

// Offline windowed-sinc resampler (blueprint dsp/ roster) - the D5
// conform-at-load path: SampleCache resamples decoded audio ONCE to the
// device rate, so realtime playback is a plain table read. [non-RT] only:
// allocates, meant for load/export threads; streaming/warp playback gets
// its own realtime machinery at M7.
//
// Design (standard offline polyphase): Kaiser-windowed sinc, kTaps zero
// crossings resolved through a kPhases-entry phase table with linear
// interpolation between adjacent phases - effectively kTaps x kPhases
// precomputed coefficients, transition band scaled by min(1, outRate/inRate)
// so downsampling low-passes below the OUTPUT Nyquist (the anti-aliasing
// duty), upsampling keeps the source band intact.

namespace daw::dsp {

class SincResampler {
public:
    static constexpr int kTaps = 32;      // per side: 16 zero crossings each way
    static constexpr int kPhases = 256;
    static constexpr double kKaiserBeta = 9.0;   // ~90 dB stopband

    // One planar channel: consumes `inFrames` at inRate, returns the
    // conformed samples at outRate. Identity rates copy through.
    static std::vector<float> process(const float* in, int64_t inFrames,
                                      double inRate, double outRate) {
        std::vector<float> out;
        if (in == nullptr || inFrames <= 0 || inRate <= 0.0 || outRate <= 0.0)
            return out;
        if (inRate == outRate) {
            out.assign(in, in + inFrames);
            return out;
        }

        const double ratio = outRate / inRate;          // >1 = upsample
        const double cutoff = ratio < 1.0 ? ratio : 1.0; // of input Nyquist
        const Table& table = tableFor(cutoff);

        const int64_t outFrames =
            static_cast<int64_t>(std::floor(static_cast<double>(inFrames) * ratio));
        out.resize(static_cast<size_t>(outFrames > 0 ? outFrames : 0), 0.0f);

        const double step = 1.0 / ratio;                // input frames per output
        const int half = kTaps / 2;
        for (int64_t o = 0; o < outFrames; ++o) {
            const double srcPos = static_cast<double>(o) * step;
            const int64_t base = static_cast<int64_t>(std::floor(srcPos));
            const double frac = srcPos - static_cast<double>(base);

            // Phase pair straddling frac, linearly interpolated.
            const double ph = frac * kPhases;
            const int p0 = static_cast<int>(ph);
            const int p1 = p0 + 1 < kPhases ? p0 + 1 : 0;   // wraps to next tap set
            const float pfr = static_cast<float>(ph - p0);

            double acc = 0.0;
            for (int t = -half + 1; t <= half; ++t) {
                const int64_t idx = base + t;
                if (idx < 0 || idx >= inFrames) continue;   // zero-pad edges
                const float c0 = table.coeff(p0, t + half - 1);
                // p1 == 0 means the straddle reached frac = 1: the kernel
                // argument becomes (t - 1), i.e. phase 0 of the PREVIOUS
                // tap slot (the window's far-left tap falls off the table -
                // its coefficient is windowed to ~0, so 0 is exact enough).
                const float c1 = p1 == 0
                    ? ((t + half - 2) >= 0 ? table.coeff(0, t + half - 2) : 0.0f)
                    : table.coeff(p1, t + half - 1);
                acc += static_cast<double>(in[idx]) * (c0 + (c1 - c0) * pfr);
            }
            out[static_cast<size_t>(o)] = static_cast<float>(acc);
        }
        return out;
    }

private:
    struct Table {
        // coeffs[phase][tap]; phase p represents fractional offset p/kPhases.
        std::vector<float> c;
        double cutoff = 0.0;
        float coeff(int phase, int tap) const noexcept {
            return c[static_cast<size_t>(phase) * kTaps + static_cast<size_t>(tap)];
        }
    };

    // Cutoff-keyed table cache: conforms in one process typically hit one or
    // two ratios (device rate vs 44.1/48k sources). [non-RT]; mutex-guarded
    // (loads may run on several worker threads), unique_ptr elements keep
    // returned references stable across vector growth.
    static const Table& tableFor(double cutoff) {
        static std::mutex m;
        static std::vector<std::unique_ptr<Table>> tables;
        std::lock_guard<std::mutex> lock(m);
        for (const auto& t : tables)
            if (t->cutoff == cutoff) return *t;
        tables.push_back(std::make_unique<Table>(build(cutoff)));
        return *tables.back();
    }

    static Table build(double cutoff) {
        Table t;
        t.cutoff = cutoff;
        t.c.resize(size_t(kPhases) * kTaps);
        const int half = kTaps / 2;
        const double i0beta = besselI0(kKaiserBeta);
        for (int p = 0; p < kPhases; ++p) {
            const double frac = static_cast<double>(p) / kPhases;
            double sum = 0.0;
            for (int k = 0; k < kTaps; ++k) {
                // Tap k evaluates sinc at (k - (half - 1) - frac).
                const double x = static_cast<double>(k - (half - 1)) - frac;
                const double sinc = x == 0.0
                    ? cutoff
                    : std::sin(3.14159265358979 * cutoff * x) / (3.14159265358979 * x);
                const double w = kaiser(x / half, i0beta);
                const double v = sinc * w;
                t.c[size_t(p) * kTaps + size_t(k)] = static_cast<float>(v);
                sum += v;
            }
            // Normalize each phase to unity DC gain (removes ripple-induced
            // level differences between phases).
            if (sum != 0.0) {
                const float g = static_cast<float>(1.0 / sum);
                for (int k = 0; k < kTaps; ++k)
                    t.c[size_t(p) * kTaps + size_t(k)] *= g;
            }
        }
        return t;
    }

    static double kaiser(double x, double i0beta) noexcept {
        const double a = 1.0 - x * x;
        if (a <= 0.0) return 0.0;
        return besselI0(kKaiserBeta * std::sqrt(a)) / i0beta;
    }

    static double besselI0(double x) noexcept {
        // Power series; converges fast for the beta range used here.
        double sum = 1.0, term = 1.0;
        for (int k = 1; k < 32; ++k) {
            term *= (x / (2.0 * k)) * (x / (2.0 * k));
            sum += term;
            if (term < 1e-12 * sum) break;
        }
        return sum;
    }
};

} // namespace daw::dsp
