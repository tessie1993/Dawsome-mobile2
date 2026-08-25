#include "AudioEngine.h"

#include <cmath>

#include "../core/ScopedNoDenormals.h"
#include "../device/DeviceRegistry.h"
#include "../graph/GraphBuilder.h"

namespace daw {

// The GraphBuilder ctor does NOT spawn its thread; start() below runs in
// the ctor BODY, after every member (whatever its declaration order) is
// constructed - that ordering, not member position, is the safety invariant.
AudioEngine::AudioEngine() : builder_(std::make_unique<GraphBuilder>(*this)) {
    registerBuiltinDevices();   // idempotent; before the first graph compile
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
    metronome_.prepare(driver_.sampleRate());
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

double AudioEngine::barBeats() const noexcept {
    const double num = transport_.timeSigNumerator();
    const double den = transport_.timeSigDenominator();
    return den > 0.0 ? num * 4.0 / den : 4.0;   // beats are quarter notes
}

void AudioEngine::applySession(const EngineMessage& m) noexcept {
    const double now = transport_.positionBeat();
    const bool playing = transport_.playing();
    auto flush = [this](NodeUid uid, int off) { midiScheduler_.flushTrack(uid, off); };
    switch (static_cast<SessionOp>(m.op)) {
        case SessionOp::LaunchClip:
            // Launch while stopped activates immediately AND starts the
            // transport (the researched Ableton rule).
            if (sessionPlayer_.launch(m.nodeUid, m.b, now, playing, barBeats()))
                transport_.play();
            break;
        case SessionOp::StopSlot:
            sessionPlayer_.stopSlot(m.nodeUid, now, playing, barBeats());
            break;
        case SessionOp::ReturnTrack:
            sessionPlayer_.returnTrack(m.nodeUid, 0, flush);
            break;
        case SessionOp::ReturnAll:
            sessionPlayer_.returnAll(0, flush);
            break;
        case SessionOp::SetLaunchQuantum:
            if (m.a <= static_cast<uint32_t>(SessionPlayer::QuantumMode::FixedBeats))
                sessionPlayer_.setQuantum(
                    static_cast<SessionPlayer::QuantumMode>(m.a), m.v0);
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
                // Live-input notes route to instrument VoiceInterfaces with
                // the onscreen-input milestone; until then consume + count.
                // ONs and OFFs drop symmetrically - no reservation imbalance.
                droppedNotes_.fetch_add(1, std::memory_order_relaxed);
                break;
            case MsgFamily::System:
                if (static_cast<SystemOp>(m.op) == SystemOp::Panic)
                    panics_.fetch_add(1, std::memory_order_relaxed);
                break;
            case MsgFamily::Session:
                applySession(m);
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
        sessionPlayer_.pruneAgainst(timeline_);   // stale uids never pin rows
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
    //    wraps) and schedule MIDI per span. Each span is further split at
    //    session launch boundaries so activations are sample-exact: the
    //    SessionPlayer activates due pendings at every (sub-)span start,
    //    cutting the outgoing source's notes, and the scheduler consults it
    //    per track (session-owned lanes play their launched clip instead of
    //    the arrangement). Wrapped spans first re-anchor unreachable
    //    boundaries to the wrap point. If the split guard runs out, the
    //    leftovers activate at the next block start (bounded lateness).
    TransportSpan spans[2];
    const int spanCount = transport_.advance(numFrames, spans);
    const bool playing = transport_.playing();
    auto flushTrack = [this](NodeUid uid, int off) {
        midiScheduler_.flushTrack(uid, off);
    };
    for (int s = 0; s < spanCount; ++s) {
        TransportSpan rest = spans[s];
        if (playing && rest.wrapped) sessionPlayer_.onLoopWrap(rest.startBeat);
        for (int cuts = 0; playing && cuts < SessionPlayer::kMaxSplitsPerSpan; ++cuts) {
            sessionPlayer_.activateDueAt(rest.startBeat, rest.offsetFrames,
                                         playing, flushTrack);
            const double cut =
                sessionPlayer_.nextBoundaryWithin(rest.startBeat, rest.endBeat);
            if (cut >= rest.endBeat) break;
            const int64_t cutSample = transport_.tempoMap().sampleAt(cut);
            const int headFrames = static_cast<int>(cutSample - rest.startSample);
            if (headFrames <= 0) {
                // Sub-sample-early boundary: activate at the current offset.
                sessionPlayer_.activateDueAt(cut, rest.offsetFrames, playing, flushTrack);
                continue;
            }
            if (headFrames >= rest.frames) break;   // lands in the next span/block
            TransportSpan head = rest;
            head.endBeat = cut;
            head.frames = headFrames;
            midiScheduler_.scheduleSpan(timeline_, head, transport_.tempoMap(),
                                        playing, &sessionPlayer_);
            metronome_.scheduleSpan(head, transport_.tempoMap(), playing,
                                    transport_.metronome());
            rest.startSample += headFrames;
            rest.startBeat = cut;
            rest.offsetFrames += headFrames;
            rest.frames -= headFrames;
            rest.wrapped = false;
        }
        midiScheduler_.scheduleSpan(timeline_, rest, transport_.tempoMap(),
                                    playing, &sessionPlayer_);
        metronome_.scheduleSpan(rest, transport_.tempoMap(), playing,
                                transport_.metronome());
    }
    midiScheduler_.finalizeBlock();   // one sorted run per track for the graph

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
        graph_->processBlock(numFrames, facts,
                             midiScheduler_.events().begin(),
                             midiScheduler_.segments().begin(),
                             midiScheduler_.segments().size());

        for (int f = 0; f < numFrames; ++f) {
            outputs[0][f] = graph_->mainL[f];
            outputs[1][f] = graph_->mainR[f];
        }
        for (const MeterFrame& mf : graph_->pendingMeters) meterBus_.tryPush(mf);
    } else {
        for (int c = 0; c < kMaxChannels; ++c)
            for (int f = 0; f < numFrames; ++f) outputs[c][f] = 0.0f;
    }

    // 6b) The click renders after the graph, straight onto the output bus:
    //     never recorded, never metered, unaffected by the mix (§3.2; Cue
    //     folds into Main until unfolded routing exists).
    metronome_.render(outputs[0], outputs[1], numFrames);

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
