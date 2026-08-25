#pragma once

#include <cstdint>

#include "../dsp/DelayLine.h"

// Plugin-delay-compensation building blocks (blueprint 3.3).
//
// The rule (researched: the industry-standard join-balancing algorithm):
// at every point where parallel paths join - the master sum, each return's
// send collector, sidechain edges, rack chain merges - every incoming path
// is delayed by (maxLatencyIntoJoin - ownLatency), so all arrivals align
// with the slowest path. PdcCalculator computes those deltas at COMPILE
// time (builder thread); DelayCompNode applies them at render time.
//
// M2 reality: strips and sends report zero latency, so every computed comp
// is zero and the graph inserts no DelayCompNodes yet - but the machinery
// compiles the real rule, so M3 device chains and the M8 lookahead limiter
// only add nonzero inputs. Live-input monitoring paths bypass PDC by
// contract (flagged, not compensated).

namespace daw {

// Fixed integer delay on a stereo buffer, in place. Builder-prepared
// (allocation happens in prepare); process is RT-safe.
class DelayCompNode {
public:
    void prepare(int delaySamples, int maxBlock) {
        (void)maxBlock;
        delay_ = delaySamples < 0 ? 0 : delaySamples;
        if (delay_ > 0) {
            l_.prepare(delay_ + 1);
            r_.prepare(delay_ + 1);
        }
    }

    int delaySamples() const noexcept { return delay_; }

    void process(float* l, float* r, int numFrames) noexcept {
        if (delay_ == 0) return;
        for (int f = 0; f < numFrames; ++f) {
            l_.write(l[f]);
            r_.write(r[f]);
            l[f] = l_.read(delay_);
            r[f] = r_.read(delay_);
        }
    }

    void reset() noexcept {
        if (delay_ > 0) { l_.clear(); r_.clear(); }
    }

private:
    int delay_ = 0;
    dsp::DelayLine l_;
    dsp::DelayLine r_;
};

// Builder-side join balancing. Feed every path's latency into a join, read
// back each path's compensation delta.
class PdcCalculator {
public:
    void beginJoin() noexcept { count_ = 0; maxLatency_ = 0; }

    // Returns the path's index within this join.
    int addPath(int latencySamples) noexcept {
        if (count_ < kMaxPaths) latencies_[count_] = latencySamples;
        if (latencySamples > maxLatency_) maxLatency_ = latencySamples;
        return count_++;
    }

    int maxLatency() const noexcept { return maxLatency_; }

    // Delay to insert on path `index` so it aligns with the slowest path.
    int compFor(int index) const noexcept {
        if (index < 0 || index >= count_ || index >= kMaxPaths) return 0;
        return maxLatency_ - latencies_[index];
    }

private:
    static constexpr int kMaxPaths = 256;
    int latencies_[kMaxPaths]{};
    int count_ = 0;
    int maxLatency_ = 0;
};

} // namespace daw
