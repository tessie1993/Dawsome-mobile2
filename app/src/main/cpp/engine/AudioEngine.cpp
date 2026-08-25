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

    // 2) Block-boundary swaps FIRST, so this block's drains apply to the
    //    graph that will render it.
    //    - PlaybackGraph: executeAdopt (bounded POD moves, old graph still
    //      valid), install, ack the retired epoch (first-ever claim acks
    //      epoch-1 so older never-claimed artifacts release), publish the
    //      installed graph's editSeq to both param tables, re-apply retained
    //      values newer than it through the NEW resolver (blueprint 2.2).
    //    - TimelineSnapshot: install + ack; the scheduler reconciles
    //      sounding notes against the new snapshot.
    midiScheduler_.beginBlock();
    if (PlaybackGraph* ng = graphOffer_.claim()) {
        ng->migration.executeAdopt();
        const uint64_t retiredEpoch = graph_ != nullptr ? graph_->epoch : ng->epoch - 1;
        graph_ = ng;
        graphOffer_.ackRetired(retiredEpoch);
        jniParams_.publishInstalledGraphSeq(ng->builtFromEditSeq);
        midiParams_.publishInstalledGraphSeq(ng->builtFromEditSeq);
        auto reapply = [this](NodeUid uid, ParamKeyHash key, double plain, uint32_t) {
            if (!graph_->resolver.apply(uid, key, static_cast<float>(plain)))
                ++paramSkews_;
        };
        jniParams_.reapplyNewerThan(ng->builtFromEditSeq, reapply);
        midiParams_.reapplyNewerThan(ng->builtFromEditSeq, reapply);
    }
    if (TimelineSnapshot* ts = timelineOffer_.claim()) {
        const TimelineSnapshot* retired = timeline_;
        timeline_ = ts;
        if (retired != nullptr) timelineOffer_.ackRetired(retired->epoch);
        midiScheduler_.onTimelineSwap(timeline_);
    }

    // 3) Drain producer channels (bounded work per slice). Param moves
    //    resolve through the installed graph; a miss is seam-4 skew
    //    (counted, converges at the next swap). With no graph yet, values
    //    stay resident in the tables for the first install's re-apply.
    drainEvents(jniEvents_);
    drainEvents(midiEvents_);
    auto applyParam = [this](NodeUid uid, ParamKeyHash key, double plain, uint32_t) {
        if (graph_ != nullptr && !graph_->resolver.apply(uid, key, static_cast<float>(plain)))
            ++paramSkews_;
    };
    jniParams_.drainDirty(applyParam);
    midiParams_.drainDirty(applyParam);

    // 4) Advance the transport (claims offered tempo bases, splits at loop
    //    wraps) and schedule MIDI per span; events feed instruments at M4.
    TransportSpan spans[2];
    const int spanCount = transport_.advance(numFrames, spans);
    for (int s = 0; s < spanCount; ++s) {
        midiScheduler_.scheduleSpan(timeline_, spans[s],
                                    transport_.tempoMap(), transport_.playing());
    }

    // 5) Keep the duplex input flowing (monitor/record taps arrive at M6).
    float* ins[kMaxChannels] = { inScratchL_, inScratchR_ };
    input.consume(ins, numFrames);

    // 6) Render through the installed graph; silence before the first claim.
    if (graph_ != nullptr) {
        RenderFacts facts;
        facts.sampleRate = driver_.sampleRate();
        facts.blockStartSample = spans[0].startSample;
        facts.blockStartBeat = spans[0].startBeat;
        facts.bpm = transport_.bpm();
        facts.playing = transport_.playing();
        facts.recording = transport_.recording();
        graph_->processBlock(numFrames, facts);

        for (int f = 0; f < numFrames; ++f) {
            outputs[0][f] = graph_->mainL[f];
            outputs[1][f] = graph_->mainR[f];
        }
        for (const MeterFrame& mf : graph_->pendingMeters) meterBus_.tryPush(mf);
    } else {
        for (int c = 0; c < kMaxChannels; ++c)
            for (int f = 0; f < numFrames; ++f) outputs[c][f] = 0.0f;
    }

    // 7) Publish the block clock from the real transport.
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
