#include "GraphBuilder.h"

#include <algorithm>
#include <chrono>
#include <cstring>
#include <utility>

#include "../device/DeviceRegistry.h"
#include "../engine/AudioEngine.h"

namespace daw {

GraphBuilder::GraphBuilder(AudioEngine& engine) : engine_(engine) {}

GraphBuilder::~GraphBuilder() { stop(); }

void GraphBuilder::start() {
    std::lock_guard<std::mutex> lk(mx_);
    if (running_) return;
    running_ = true;
    thread_ = std::thread([this] { threadMain(); });
}

void GraphBuilder::stop() {
    {
        std::lock_guard<std::mutex> lk(mx_);
        if (!running_) return;
        running_ = false;
    }
    cv_.notify_all();
    if (thread_.joinable()) thread_.join();
}

void GraphBuilder::submitDeltas(const uint8_t* payload, size_t len) {
    if (payload == nullptr || len == 0) return;
    {
        std::lock_guard<std::mutex> lk(mx_);
        inbox_.emplace_back(payload, payload + len);
    }
    cv_.notify_all();
}

void GraphBuilder::nudge() {
    {
        std::lock_guard<std::mutex> lk(mx_);
        wake_ = true;
    }
    cv_.notify_all();
}

void GraphBuilder::threadMain() {
    std::vector<std::vector<uint8_t>> work;
    for (;;) {
        {
            std::unique_lock<std::mutex> lk(mx_);
            cv_.wait_for(lk, std::chrono::milliseconds(50),
                         [&] { return !running_ || wake_ || !inbox_.empty(); });
            if (!running_) return;
            wake_ = false;
            work.swap(inbox_);
        }
        for (const auto& bundle : work) applyBundle(bundle);
        work.clear();

        const uint32_t dirty = model_.consumeDirty();
        TempoMap& map = engine_.transport().tempoMap();

        if ((dirty & kDirtyTempo) || pendingTempoRebuild_) {
            rebuildTempoBaseFromModel();
        } else if (tempoArtifacts_.empty() || tempoArtifacts_.back().bgPublished) {
            // No tempo offer in flight: check the forced-consolidation rule.
            const TempoMap::Snapshot snap = map.snapshot();
            if (snap.tail.count >= uint32_t((kTempoTailCap * 3) / 4))
                consolidateTempoTail(snap);
        }
        if (dirty & kDirtyTimeline) buildTimeline();
        if ((dirty & kDirtyGraph) || pendingGraphBuild_) buildGraph();

        gcTimeline();
        gcGraph();
        gcTempo();
    }
}

void GraphBuilder::applyBundle(const std::vector<uint8_t>& bundle) {
    if (bundle.size() < kModelDeltaEnvelopeBytes) {
        rejected_.fetch_add(1, std::memory_order_relaxed);
        return;
    }
    ModelDeltaEnvelope env;
    std::memcpy(&env, bundle.data(), sizeof env);
    model_.noteEditSeq(env.editSeq);

    struct Visitor {
        GraphBuilder& b;
        void onDelta(const EntityDelta& d) {
            if (b.model_.applyDelta(d))
                b.applied_.fetch_add(1, std::memory_order_relaxed);
            else
                b.rejected_.fetch_add(1, std::memory_order_relaxed);
        }
    } v{*this};

    const StateCodec::Result r = StateCodec::decode(
        bundle.data() + kModelDeltaEnvelopeBytes,
        bundle.size() - kModelDeltaEnvelopeBytes, v);
    if (r.status != StateCodec::Status::Ok)
        rejected_.fetch_add(1, std::memory_order_relaxed);
}

// ---- timeline ---------------------------------------------------------------

void GraphBuilder::buildTimeline() {
    auto snap = std::make_unique<TimelineSnapshot>();
    snap->epoch = nextEpoch_++;
    snap->builtFromEditSeq = model_.lastEditSeq();
    snap->tempoMapRev = engine_.transport().tempoMap().snapshot().rev;

    // Deterministic track order: model order field, then uid.
    std::vector<std::pair<NodeUid, const ModelTrack*>> ordered;
    ordered.reserve(model_.tracks().size());
    for (const auto& [uid, t] : model_.tracks()) ordered.emplace_back(uid, &t);
    std::sort(ordered.begin(), ordered.end(),
              [](const auto& a, const auto& b) {
                  if (a.second->order != b.second->order) return a.second->order < b.second->order;
                  return a.first < b.first;
              });

    // Pass 1: size the flat stores (arrangement clips whose track AND content
    // resolve; dangling references are skipped + counted - seam-4 skew rule).
    size_t clipTotal = 0, noteTotal = 0;
    for (const auto& [cuid, c] : model_.clips()) {
        if (c.slotIndex != -1) continue;                       // session clips: M5
        if (model_.tracks().find(c.trackUid) == model_.tracks().end() ||
            model_.contents().find(c.contentUid) == model_.contents().end()) {
            danglingRefs_.fetch_add(1, std::memory_order_relaxed);
            continue;
        }
        ++clipTotal;
        noteTotal += model_.contents().at(c.contentUid).notes.size();
    }
    snap->noteStore.reserve(noteTotal);
    snap->clipStore.reserve(clipTotal);

    // Pass 2: fill stores track by track; views take pointers only after the
    // exact reserve, so nothing reallocates underneath them.
    struct TrackRange { NodeUid uid; uint8_t type; size_t firstClip; size_t clipCount; };
    std::vector<TrackRange> ranges;
    ranges.reserve(ordered.size());

    std::vector<const ModelClip*> trackClips;
    std::vector<std::pair<NodeUid, const ModelClip*>> clipIndex;   // clipUid + data
    clipIndex.reserve(model_.clips().size());
    for (const auto& [cuid, c] : model_.clips()) clipIndex.emplace_back(cuid, &c);

    for (const auto& [tuid, track] : ordered) {
        trackClips.clear();
        std::vector<NodeUid> clipUids;
        for (const auto& [cuid, c] : clipIndex) {
            if (c->trackUid != tuid || c->slotIndex != -1) continue;
            if (model_.contents().find(c->contentUid) == model_.contents().end()) continue;
            trackClips.push_back(c);
            clipUids.push_back(cuid);
        }
        // Sort this track's clips by placement.
        std::vector<size_t> idx(trackClips.size());
        for (size_t i = 0; i < idx.size(); ++i) idx[i] = i;
        std::sort(idx.begin(), idx.end(), [&](size_t a, size_t b) {
            return trackClips[a]->startBeat < trackClips[b]->startBeat;
        });

        const size_t firstClip = snap->clipStore.size();
        for (const size_t i : idx) {
            const ModelClip& c = *trackClips[i];
            const ModelClipContent& content = model_.contents().at(c.contentUid);

            const size_t firstNote = snap->noteStore.size();
            for (const ModelNote& n : content.notes) {
                SnapshotNote sn;
                sn.id = n.id;
                sn.pitch = n.pitch;
                sn.velocity = n.velocity;
                sn.startBeat = n.startBeat;
                sn.lengthBeats = n.lengthBeats;
                snap->noteStore.push_back(sn);
            }
            std::sort(snap->noteStore.begin() + firstNote, snap->noteStore.end(),
                      [](const SnapshotNote& a, const SnapshotNote& b) {
                          return a.startBeat < b.startBeat;
                      });

            ClipView view;
            view.clipUid = clipUids[i];
            view.trackUid = tuid;
            view.contentUid = c.contentUid;
            view.startBeat = c.startBeat;
            view.lengthBeats = c.lengthBeats;
            view.contentLengthBeats = content.lengthBeats;
            view.looping = c.looping;
            view.notes = snap->noteStore.data() + firstNote;
            view.noteCount = static_cast<uint32_t>(snap->noteStore.size() - firstNote);
            snap->clipStore.push_back(view);
        }
        ranges.push_back({tuid, track->type, firstClip,
                          snap->clipStore.size() - firstClip});
    }

    snap->tracks.reserve(ranges.size());
    for (const TrackRange& r : ranges) {
        TrackTimeline t;
        t.trackUid = r.uid;
        t.trackType = r.type;
        t.clips = snap->clipStore.data() + r.firstClip;
        t.clipCount = static_cast<uint32_t>(r.clipCount);
        snap->tracks.push_back(t);
    }

    TimelineSnapshot* replaced = engine_.timelineOffer().offer(snap.get());
    if (replaced != nullptr) {
        for (auto it = timelineArtifacts_.begin(); it != timelineArtifacts_.end(); ++it) {
            if (it->snapshot.get() == replaced) { timelineArtifacts_.erase(it); break; }
        }
    }
    timelineArtifacts_.push_back({std::move(snap)});
    timelineBuilds_.fetch_add(1, std::memory_order_relaxed);
}

void GraphBuilder::gcTimeline() {
    while (timelineArtifacts_.size() > 1 &&
           engine_.timelineOffer().retiredAcked(timelineArtifacts_.front().snapshot->epoch)) {
        timelineArtifacts_.erase(timelineArtifacts_.begin());
    }
}

// ---- playback graph ---------------------------------------------------------

void GraphBuilder::buildGraph() {
    const double rate = engine_.transport().tempoMap().sampleRate();
    if (rate <= 0.0) {                    // engine not prepared yet; retry later
        pendingGraphBuild_ = true;
        return;
    }
    // Coalesce while the audio thread isn't claiming (engine stopped, or a
    // storm of edits): unclaimed offers may not be freed eagerly (see the
    // lifetime note at offer below), so cap the chain and fold newer dirt
    // into one rebuild once RT catches up.
    if (graphArtifacts_.size() >= 8) {
        pendingGraphBuild_ = true;
        return;
    }
    pendingGraphBuild_ = false;

    auto g = std::make_unique<PlaybackGraph>();
    g->epoch = nextEpoch_++;
    g->builtFromEditSeq = model_.lastEditSeq();
    g->sampleRate = rate;

    // Deterministic order: model order field, then uid.
    std::vector<std::pair<NodeUid, const ModelTrack*>> ordered;
    ordered.reserve(model_.tracks().size());
    for (const auto& [uid, t] : model_.tracks()) ordered.emplace_back(uid, &t);
    std::sort(ordered.begin(), ordered.end(),
              [](const auto& a, const auto& b) {
                  if (a.second->order != b.second->order) return a.second->order < b.second->order;
                  return a.first < b.first;
              });

    std::vector<std::pair<NodeUid, const ModelTrack*>> regular, returnRows;
    const ModelTrack* masterRow = nullptr;   // Kotlin sends master as a type-4 row
    for (const auto& row : ordered) {
        const uint8_t type = row.second->type;
        if (type == 3) {
            if (returnRows.size() < size_t(kMaxReturns)) returnRows.push_back(row);
        } else if (type <= 2) {
            if (regular.size() < size_t(kMaxTracks)) regular.push_back(row);
        } else if (type == 4) {
            masterRow = row.second;          // strip values for the master lane
        }
        // type 5 (groups) joins with group tracks.
    }

    // Arena: one stereo lane per regular track, per return, plus the master.
    const size_t lanes = regular.size() + returnRows.size() + 1;
    g->arena.assign(lanes * 2 * size_t(kMaxBlock), 0.0f);
    auto lanePtr = [&](size_t lane, int ch) {
        return g->arena.data() + (lane * 2 + size_t(ch)) * size_t(kMaxBlock);
    };

    // Adoption source: the newest previous graph at the same rate. The full
    // seam-3 condition: uid AND configHash AND rate all match.
    const PlaybackGraph* prev =
        graphArtifacts_.empty() ? nullptr : graphArtifacts_.back().graph.get();
    auto findPrev = [&](NodeUid uid, uint64_t configHash) -> DeviceNode* {
        if (prev == nullptr || prev->sampleRate != rate) return nullptr;
        for (const auto& e : prev->nodeIndex)
            if (e.uid == uid && e.configHash == configHash) return e.node;
        return nullptr;
    };

    // Resolver sizing: strips + sends + master, plus every registered
    // device's descriptors and one bypass entry per device.
    size_t deviceParams = 0;
    for (const auto& [duid, md] : model_.devices()) {
        (void)duid;
        if (const auto* info = DeviceRegistry::instance().info(md.type))
            deviceParams += size_t(info->paramCount) + 1;
    }
    g->resolver.reserve(regular.size() * 5 + returnRows.size() * 3 + 3 + deviceParams);

    // Pre-strip device chain for one lane, compiled from the model.
    auto makeChain = [&](TrackUnit& u, NodeUid laneUid) {
        std::vector<std::pair<NodeUid, const ModelDevice*>> devs;
        for (const auto& [duid, md] : model_.devices())
            if (md.trackUid == laneUid) devs.emplace_back(duid, &md);
        if (devs.empty()) return;
        std::sort(devs.begin(), devs.end(), [](const auto& a, const auto& b) {
            if (a.second->order != b.second->order) return a.second->order < b.second->order;
            return a.first < b.first;
        });

        auto chain = std::make_unique<DeviceChain>();
        for (const auto& [duid, md] : devs) {
            auto dev = DeviceRegistry::instance().create(md->type);
            if (dev == nullptr) {
                // Type's milestone hasn't landed: skip + count, mixer still works.
                unregisteredDevices_.fetch_add(1, std::memory_order_relaxed);
                continue;
            }
            // Bake model param values + register descriptors under the
            // DEVICE's uid (the identity Kotlin addresses).
            for (int pi = 0; pi < dev->paramCount(); ++pi) {
                const ParamDescriptor& pd = dev->paramDescriptor(pi);
                const ParamKeyHash kh = fnv1a32(pd.key);
                for (const auto& [h, v] : md->params) {
                    if (h == kh) { dev->setParamImmediate(pi, v); break; }
                }
                g->resolver.add(duid, kh, dev.get(), pi);
            }
            if (DeviceNode* old = findPrev(duid, 0))
                g->migration.add(dev.get(), old, dev->stateBytes());
            g->nodeIndex.push_back({duid, dev.get(), 0});
            chain->addDevice(duid, dev.get(), !md->enabled);
            g->nodes.push_back(std::move(dev));
        }
        if (chain->slotCount() == 0) return;

        chain->prepare(rate, kMaxBlock);        // prepares members, sizes dry delays
        const NodeUid cuid = chainNodeUid(laneUid);
        const uint64_t chash = chain->computeConfigHash();
        if (DeviceNode* old = findPrev(cuid, chash))
            g->migration.add(chain.get(), old, chain->stateBytes());
        for (int slot = 0; slot < chain->slotCount(); ++slot) {
            g->resolver.add(chain->slotUid(slot), paramKey("device.bypass"),
                            chain.get(), slot);
        }
        g->nodeIndex.push_back({cuid, chain.get(), chash});
        u.chain = chain.get();
        g->nodes.push_back(std::move(chain));
    };

    auto makeStrip = [&](TrackUnit& u, NodeUid uid, const ModelTrack* mt,
                         size_t lane, uint8_t wireType) {
        u.uid = uid;
        u.wireType = wireType;
        u.bufL = lanePtr(lane, 0);
        u.bufR = lanePtr(lane, 1);
        auto strip = std::make_unique<TrackStrip>();
        if (mt != nullptr) {
            strip->setParamImmediate(0, mt->volumeDb);
            strip->setParamImmediate(1, mt->pan);
            strip->setParamImmediate(2, (mt->flags & kTrackFlagMuted) ? 1.0f : 0.0f);
        }
        strip->prepare(rate, kMaxBlock);            // snaps to the set targets
        if (DeviceNode* old = findPrev(uid, 0))
            g->migration.add(strip.get(), old, strip->stateBytes());
        g->resolver.add(uid, paramKey("mixer.volume"), strip.get(), 0);
        g->resolver.add(uid, paramKey("mixer.pan"), strip.get(), 1);
        g->resolver.add(uid, paramKey("mixer.mute"), strip.get(), 2);
        g->nodeIndex.push_back({uid, strip.get(), 0});
        u.strip = strip.get();
        u.meter.prepare(uid, rate);
        g->nodes.push_back(std::move(strip));
    };

    size_t lane = 0;
    g->tracks.reserve(regular.size());
    for (const auto& [uid, mt] : regular) {
        TrackUnit u;
        makeStrip(u, uid, mt, lane, mt->type);
        makeChain(u, uid);
        // Post-fader sends exist only where a target return exists; send
        // values for absent buses stay retained in the ParamMoveTable and
        // apply when a return appears (post-swap reapply).
        for (int bus = 0; bus < 2; ++bus) {
            if (returnRows.size() <= size_t(bus)) break;
            auto send = std::make_unique<SendNode>(bus);
            const NodeUid suid = sendNodeUid(uid, bus);
            send->setParamImmediate(0, bus == 0 ? mt->sendA : mt->sendB);
            send->prepare(rate, kMaxBlock);
            if (DeviceNode* old = findPrev(suid, 0))
                g->migration.add(send.get(), old, send->stateBytes());
            g->resolver.add(uid, paramKey(bus == 0 ? "mixer.sendA" : "mixer.sendB"),
                            send.get(), 0);
            g->nodeIndex.push_back({suid, send.get(), 0});
            (bus == 0 ? u.sendA : u.sendB) = send.get();
            g->nodes.push_back(std::move(send));
        }
        g->tracks.push_back(u);
        ++lane;
    }

    g->returns.reserve(returnRows.size());
    for (const auto& [uid, mt] : returnRows) {
        TrackUnit u;
        makeStrip(u, uid, mt, lane, 3);
        makeChain(u, uid);
        g->returns.push_back(u);
        ++lane;
    }

    makeStrip(g->master, kMasterNodeUid, masterRow, lane, 4);
    makeChain(g->master, kMasterNodeUid);
    g->mixL = g->master.bufL;
    g->mixR = g->master.bufR;
    g->mainL = g->mixL;
    g->mainR = g->mixR;
    g->cueFolded = true;

    // PDC (blueprint 3.3): balance the master join with real chain
    // latencies. A track's path latency = its chain; a return path's
    // latency = the slowest sender + the return's own chain (per-send
    // compensation INTO each return input is deferred until the first
    // nonzero-latency device lands - M8 lookahead - and is called out in
    // BUILD_LOG; all values are zero until then).
    auto laneLatency = [](const TrackUnit& u) {
        return u.chain != nullptr ? u.chain->latencySamples() : 0;
    };
    int maxSender = 0;
    for (const TrackUnit& t : g->tracks)
        if (laneLatency(t) > maxSender) maxSender = laneLatency(t);

    PdcCalculator pdc;
    pdc.beginJoin();
    for (const TrackUnit& t : g->tracks) pdc.addPath(laneLatency(t));
    for (const TrackUnit& r : g->returns) pdc.addPath(maxSender + laneLatency(r));

    int pathIndex = 0;
    auto attachComp = [&](TrackUnit& u) {
        const int comp = pdc.compFor(pathIndex++);
        if (comp <= 0) return;
        auto node = std::make_unique<DelayCompNode>();
        node->prepare(comp, kMaxBlock);
        u.comp = node.get();
        g->comps.push_back(std::move(node));
    };
    for (TrackUnit& t : g->tracks) attachComp(t);
    for (TrackUnit& r : g->returns) attachComp(r);

    g->migration.finalize();

    // LIFETIME RULE (differs from the timeline/tempo paths): a replaced
    // unclaimed graph must NOT be freed here - the offer we just made holds
    // MigrationPlan oldNode pointers into it. gcGraph's acked-front rule is
    // the only safe release: RT ack epochs are monotonic, and an artifact's
    // epoch being acked proves every plan that references it has either
    // executed (its owner was claimed) or been superseded.
    (void)engine_.graphOffer().offer(g.get());
    graphArtifacts_.push_back({std::move(g)});
    graphBuilds_.fetch_add(1, std::memory_order_relaxed);
}

void GraphBuilder::gcGraph() {
    while (graphArtifacts_.size() > 1 &&
           engine_.graphOffer().retiredAcked(graphArtifacts_.front().graph->epoch)) {
        graphArtifacts_.erase(graphArtifacts_.begin());
    }
}

// ---- tempo ------------------------------------------------------------------

void GraphBuilder::rebuildTempoBaseFromModel() {
    TempoMap& map = engine_.transport().tempoMap();
    const double rate = map.sampleRate();
    if (rate <= 0.0) {                    // engine not prepared yet; retry later
        pendingTempoRebuild_ = true;
        return;
    }
    pendingTempoRebuild_ = false;

    const TempoMap::Snapshot snap = map.snapshot();
    auto base = std::make_unique<TempoMapBase>();
    base->epoch = nextEpoch_++;
    base->foldRev = snap.rev;             // canonical rebuild folds everything known

    const ModelTempo& t = model_.tempo();
    double prevBeat = 0.0;
    int64_t prevSample = 0;
    double prevSpb = rate * 60.0 / 120.0;
    base->segments.push_back({0.0, 0, prevSpb});
    for (const ModelTempo::Ev& ev : t.events) {
        if (base->segments.full()) break;                 // cap: coarse but bounded
        const double spb = rate * 60.0 / ev.bpm;
        if (ev.beat <= 0.0) {                             // event at/before origin replaces the seed
            base->segments[0].samplesPerBeat = spb;
            prevSpb = spb;
            continue;
        }
        const int64_t startSample =
            prevSample + int64_t((ev.beat - prevBeat) * prevSpb + 0.5);
        base->segments.push_back({ev.beat, startSample, spb});
        prevBeat = ev.beat;
        prevSample = startSample;
        prevSpb = spb;
    }

    base->timeSigs.push_back({0.0, 0, 4, 4});
    int32_t bar = 0;
    double sigBeat = 0.0;
    double beatsPerBar = 4.0;
    for (const ModelTempo::Sig& s : t.sigs) {
        if (base->timeSigs.full()) break;
        if (s.beat <= 0.0) {
            base->timeSigs[0].numerator = s.num;
            base->timeSigs[0].denominator = s.den;
            beatsPerBar = 4.0 * double(s.num) / double(s.den);
            continue;
        }
        bar += int32_t((s.beat - sigBeat) / beatsPerBar + 0.5);
        base->timeSigs.push_back({s.beat, bar, s.num, s.den});
        sigBeat = s.beat;
        beatsPerBar = 4.0 * double(s.num) / double(s.den);
    }

    offerTempoBase(std::move(base));
}

void GraphBuilder::consolidateTempoTail(const TempoMap::Snapshot& snap) {
    if (snap.base == nullptr || snap.tail.count == 0 || snap.sampleRate <= 0.0) return;

    auto base = std::make_unique<TempoMapBase>();
    base->epoch = nextEpoch_++;
    base->foldRev = snap.rev;

    // Boundary beats = base segment starts + tail anchors; the new segments
    // sample the SAME governing function the RT thread evaluates, so the
    // consolidated mapping is identical by construction.
    std::vector<double> beats;
    beats.reserve(snap.base->segments.size() + snap.tail.count);
    for (const TempoSegment& s : snap.base->segments) beats.push_back(s.startBeat);
    for (uint32_t i = 0; i < snap.tail.count; ++i) beats.push_back(snap.tail.events[i].startBeat);
    std::sort(beats.begin(), beats.end());
    beats.erase(std::unique(beats.begin(), beats.end()), beats.end());

    for (const double b : beats) {
        if (base->segments.full()) break;
        const double bpm = snap.bpmAt(b);
        if (bpm <= 0.0) continue;
        const double spb = snap.sampleRate * 60.0 / bpm;
        const int64_t sample = snap.sampleAt(b);
        if (!base->segments.empty()) {
            // Merge only a truly redundant boundary: same tempo AND the
            // previous segment's linear extension lands on the same sample.
            // (A post-seek anchored splice can JUMP in sample at equal tempo;
            // that discontinuity must survive consolidation.)
            const TempoSegment& prev = base->segments[base->segments.size() - 1];
            const int64_t expected = prev.startSample +
                int64_t((b - prev.startBeat) * prev.samplesPerBeat + 0.5);
            const int64_t jump = sample - expected;
            if (prev.samplesPerBeat == spb && jump >= -1 && jump <= 1) continue;
        }
        base->segments.push_back({b, sample, spb});
    }
    if (base->segments.empty())
        base->segments.push_back({0.0, 0, snap.sampleRate * 60.0 / 120.0});

    for (const TimeSigEvent& s : snap.base->timeSigs) {
        if (base->timeSigs.full()) break;
        base->timeSigs.push_back(s);
    }
    if (base->timeSigs.empty()) base->timeSigs.push_back({0.0, 0, 4, 4});

    offerTempoBase(std::move(base));
}

void GraphBuilder::offerTempoBase(std::unique_ptr<TempoMapBase> built) {
    TempoMap& map = engine_.transport().tempoMap();
    uint64_t predecessor =
        tempoArtifacts_.empty() ? 0 : tempoArtifacts_.back().base->epoch;

    TempoMapBase* replaced = map.offerBase(built.get());
    if (replaced != nullptr) {
        // The replaced offer was never claimed: inherit ITS predecessor (the
        // artifact the RT thread still holds) and free it now.
        for (auto it = tempoArtifacts_.begin(); it != tempoArtifacts_.end(); ++it) {
            if (it->base.get() == replaced) {
                predecessor = it->predecessorEpoch;
                tempoArtifacts_.erase(it);
                break;
            }
        }
    }
    tempoArtifacts_.push_back({std::move(built), predecessor, false});
    tempoBuilds_.fetch_add(1, std::memory_order_relaxed);
}

void GraphBuilder::gcTempo() {
    TempoMap& map = engine_.transport().tempoMap();

    // The newest artifact becomes the background base once its predecessor's
    // retirement is acked (proof the RT thread claimed it). predecessor == 0
    // (the immortal default) acks trivially - correct for the first offer.
    if (!tempoArtifacts_.empty()) {
        TempoArtifact& newest = tempoArtifacts_.back();
        if (!newest.bgPublished && map.retiredBaseAcked(newest.predecessorEpoch)) {
            map.publishBackgroundBase(newest.base.get());
            newest.bgPublished = true;
        }
    }

    // Free retired artifacts: acked by RT AND no longer the background base
    // (a newer artifact has been bg-published past them).
    int lastPublished = -1;
    for (size_t i = 0; i < tempoArtifacts_.size(); ++i)
        if (tempoArtifacts_[i].bgPublished) lastPublished = static_cast<int>(i);

    for (size_t i = 0; i + 1 < tempoArtifacts_.size();) {
        const bool bgMovedPast = static_cast<int>(i) < lastPublished;
        if (bgMovedPast && map.retiredBaseAcked(tempoArtifacts_[i].base->epoch)) {
            tempoArtifacts_.erase(tempoArtifacts_.begin() + static_cast<long>(i));
            --lastPublished;
        } else {
            ++i;
        }
    }
}

} // namespace daw
