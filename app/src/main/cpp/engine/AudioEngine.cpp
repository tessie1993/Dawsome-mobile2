#include "AudioEngine.h"

#include "../core/ScopedNoDenormals.h"

namespace daw {

bool AudioEngine::start(const OboeDriver::Config& cfg) noexcept {
    if (!driver_.open(*this, cfg)) return false;
    return driver_.start();
}

void AudioEngine::stop() noexcept {
    driver_.stop();
    driver_.close();
}

void AudioEngine::applyTransport(const EngineMessage& m) noexcept {
    switch (static_cast<TransportOp>(m.op)) {
        case TransportOp::Play:        playing_ = true; break;
        case TransportOp::Stop:        playing_ = false; samplePos_ = 0; break;
        case TransportOp::TogglePlay:  playing_ = !playing_; break;
        case TransportOp::RecordOn:    recording_ = true; break;
        case TransportOp::RecordOff:   recording_ = false; break;
        case TransportOp::SeekSample:  if (m.samplePos >= 0) samplePos_ = m.samplePos; break;
        case TransportOp::SetTempo:    if (m.v0 > 0.0) bpm_ = m.v0; break;
        case TransportOp::NudgeTempo:  bpm_ += m.v0; break;
        case TransportOp::LoopOn:      looping_ = true; break;
        case TransportOp::LoopOff:     looping_ = false; break;
        default:
            // SeekBeat/SetLoopRegion/SetTimeSig/metronome/timebase need the
            // TempoMap and TransportEngine (M1); ignored by the skeleton.
            break;
    }
}

void AudioEngine::drainEvents(EventRing<>& ring) noexcept {
    EngineMessage m;
    for (int i = 0; i < kEventDrainCap && ring.tryPop(m); ++i) {
        switch (m.family) {
            case MsgFamily::Transport:
                applyTransport(m);
                break;
            case MsgFamily::Note:
                // No instruments yet (M4): consume and count. ONs and OFFs are
                // dropped symmetrically, so no reservation imbalance builds up.
                droppedNotes_.fetch_add(1, std::memory_order_relaxed);
                break;
            case MsgFamily::System:
                if (static_cast<SystemOp>(m.op) == SystemOp::Panic)
                    panics_.fetch_add(1, std::memory_order_relaxed);
                break;
            case MsgFamily::Param:      // BlockSet buffers arrive with the graph (M2)
            case MsgFamily::Structure:
                break;
        }
    }
}

void AudioEngine::render(float* const* outputs, int numFrames,
                         InputJitterRing& input, const StreamTime& time) {
    ScopedNoDenormals noDenormals;

    // 1) Anchor this slice for cross-clock conversion (MIDI, recording, sync).
    if (time.valid) {
        TimeAnchor a;
        a.framePosition = time.framePosition;
        a.monotonicNanos = time.monotonicNanos;
        a.sampleRate = driver_.sampleRate();
        anchor_.publish(a);
    }

    // 2) Drain producer channels (bounded work per slice).
    drainEvents(jniEvents_);
    drainEvents(midiEvents_);
    jniParams_.drainDirty([](NodeUid, ParamKeyHash, double, uint32_t) {
        // Resolver arrives with the PlaybackGraph (M2). Values stay resident
        // in the table for reapplyNewerThan() at the first graph install.
    });
    midiParams_.drainDirty([](NodeUid, ParamKeyHash, double, uint32_t) {});

    // 3) Keep the duplex input flowing (monitor/record taps arrive M2/M6).
    float* ins[kMaxChannels] = { inScratchL_, inScratchR_ };
    input.consume(ins, numFrames);

    // 4) Render: silence until the PlaybackGraph lands (M2).
    for (int c = 0; c < kMaxChannels; ++c)
        for (int f = 0; f < numFrames; ++f) outputs[c][f] = 0.0f;

    // 5) Advance placeholder transport and publish the block clock.
    if (playing_) samplePos_ += numFrames;
    TransportClockData d;
    d.samplePos = samplePos_;
    d.bpm = bpm_;
    d.beat = driver_.sampleRate() > 0.0
                 ? static_cast<double>(samplePos_) / driver_.sampleRate() * (bpm_ / 60.0)
                 : 0.0;
    d.flags = (playing_ ? kClockPlaying : 0u) |
              (recording_ ? kClockRecording : 0u) |
              (looping_ ? kClockLooping : 0u);
    clock_.publish(d);
}

} // namespace daw
