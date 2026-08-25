#include "AudioEngine.h"

#include <cmath>

#include "../core/ScopedNoDenormals.h"
#include "../graph/GraphBuilder.h"

namespace daw {

// builder_ is the last member: everything it touches (transport, offer
// slots, rings) is fully constructed before its thread spawns.
AudioEngine::AudioEngine() : builder_(std::make_unique<GraphBuilder>(*this)) {
    builder_->start();
}

AudioEngine::~AudioEngine() {
    stop();               // streams closed -> no more render() callbacks
    builder_->stop();     // join before members are torn down
}

bool AudioEngine::start(const OboeDriver::Config& cfg) noexcept {
    if (!driver_.open(*this, cfg)) return false;
    // Prepare only on an actual rate change: a same-rate reopen (D5 route
    // recovery) keeps position and the live tempo tail. A rate change resets
    // the map (its sample anchors are rate-bound); the full re-prepare
    // sequence (cache re-keys, re-prime) arrives with the media system.
    if (driver_.sampleRate() != transport_.tempoMap().sampleRate())
        transport_.prepare(driver_.sampleRate());
    return driver_.start();
}

void AudioEngine::stop() noexcept {
    driver_.stop();
    driver_.close();
}

void AudioEngine::applyTransport(const EngineMessage& m) noexcept {
    switch (static_cast<TransportOp>(m.op)) {
        case TransportOp::Play:        transport_.play(); break;
        case TransportOp::Stop:        transport_.stop(); break;
        case TransportOp::TogglePlay:  transport_.togglePlay(); break;
        case TransportOp::RecordOn:    transport_.setRecording(true); break;
        case TransportOp::RecordOff:   transport_.setRecording(false); break;
        case TransportOp::SeekSample:  if (m.samplePos >= 0) transport_.seekSample(m.samplePos); break;
        case TransportOp::SeekBeat:    if (!std::isnan(m.beat)) transport_.seekBeat(m.beat); break;
        case TransportOp::SetLoopRegion: transport_.setLoopRegion(m.v0, m.v1); break;
        case TransportOp::LoopOn:      transport_.setLooping(true); break;
        case TransportOp::LoopOff:     transport_.setLooping(false); break;
        case TransportOp::SetTempo:    transport_.setTempo(m.v0); break;
        case TransportOp::NudgeTempo:  transport_.nudgeTempo(m.v0); break;
        case TransportOp::SetTimeSig:
            transport_.setTimeSig(static_cast<int>(m.a >> 16),
                                  static_cast<int>(m.a & 0xFFFFu));
            break;
        case TransportOp::MetronomeOn:  transport_.setMetronome(true); break;
        case TransportOp::MetronomeOff: transport_.setMetronome(false); break;
        case TransportOp::SetTimebaseSource:
            if (m.a <= static_cast<uint32_t>(TimebaseSource::MidiClockSlave))
                transport_.setTimebaseSource(static_cast<TimebaseSource>(m.a));
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

    // 3) Block-boundary swaps + transport advance + MIDI scheduling. Claim
    //    any offered timeline (ack retires the predecessor - the builder
    //    frees it only after this ack; the scheduler reconciles sounding
    //    notes against the new snapshot); the transport's advance() claims
    //    offered tempo bases the same way and splits the block at a loop
    //    wrap. Scheduled events feed instruments from M4.
    midiScheduler_.beginBlock();
    if (TimelineSnapshot* ts = timelineOffer_.claim()) {
        const TimelineSnapshot* retired = timeline_;
        timeline_ = ts;
        if (retired != nullptr) timelineOffer_.ackRetired(retired->epoch);
        midiScheduler_.onTimelineSwap(timeline_);
    }
    TransportSpan spans[2];
    const int spanCount = transport_.advance(numFrames, spans);
    for (int s = 0; s < spanCount; ++s) {
        midiScheduler_.scheduleSpan(timeline_, spans[s],
                                    transport_.tempoMap(), transport_.playing());
    }

    // 4) Keep the duplex input flowing (monitor/record taps arrive M2/M6).
    float* ins[kMaxChannels] = { inScratchL_, inScratchR_ };
    input.consume(ins, numFrames);

    // 5) Render: silence until the PlaybackGraph lands (M2).
    for (int c = 0; c < kMaxChannels; ++c)
        for (int f = 0; f < numFrames; ++f) outputs[c][f] = 0.0f;

    // 6) Publish the block clock from the real transport.
    TransportClockData d;
    d.samplePos = transport_.positionSamples();
    d.beat = transport_.positionBeat();
    d.bpm = transport_.bpm();
    d.flags = (transport_.playing()   ? kClockPlaying   : 0u) |
              (transport_.recording() ? kClockRecording : 0u) |
              (transport_.looping()   ? kClockLooping   : 0u) |
              (transport_.metronome() ? kClockMetronome : 0u);
    clock_.publish(d);
}

} // namespace daw
