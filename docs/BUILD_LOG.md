# Build Log

Running hand-off record for the class-by-class build (blueprint
`docs/spec/ARCHITECTURE_BLUEPRINT.md`, contracts `docs/spec/CONTRACTS.md`).
Newest entry first. Each entry: where the build stands, what comes next.

---

## 2026-08-25 — M2 COMPLETE (feature 3: RT swap + live mixer loop)

The graph-and-mixer milestone closes. AudioEngine's render now runs the
whole dual-model protocol: claim graph -> executeAdopt (old graph valid
until ack) -> install -> ack(retired ?: epoch-1, releasing never-claimed
predecessors) -> publishInstalledGraphSeq to both param tables ->
reapplyNewerThan through the NEW resolver; live drains resolve through the
installed graph (misses = counted seam-4 skew); render goes through
processBlock with Main-bus copy-out; graph meters drain to the MeterBus.
Master volume closed end-to-end: EngineSync sends a type-4 track row keyed
by the well-known master uid so rebuilt graphs start correct (params alone
could be reclaimed as "baked" without being in the model).

Kotlin: MixerStateHolder grew a `meters` flow (uid -> track-id remap via
makeNodeUid, "master" key, separate from edit state so meter ticks don't
recompose strips); MixerScreen strips + master feed StereoLedLevelMeter
from live MeterReadings (dark when the engine is absent); MainDawScreen
takes an optional EngineReadback and MainActivity passes DawRuntime's.

Documented M2 deferrals: GroupTrackNode + RoutingTable wait for group
tracks / Routing deltas in the model (fixed topology today: tracks ->
master, sendA/B -> returns 1/2 -> master); Cue stays folded into Main until
routable sources (metronome M4, preview M14) exist. M3 TODO recorded:
device params need a model home (DeviceDeltaPayload params blob or
BlockSet) so rebuilds bake them - mixer params are covered via track rows.
Map: 155 classes, sweep green.

**Next (M3, blueprint §11):** DeviceNode chains (DeviceChain with
latency-preserving bypass), ParamRegistry (hostside collision assert),
VoiceAllocator + VoiceBudgetLedger, preset plumbing, racks/macros core,
QualityMode - then M4 first sound (SubtractiveSynth + MetronomeNode).

## 2026-08-25 — M2 features 1+2 done: device contract + compiled graph

Feature 1: `device/DeviceNode.h` (seams 1/3/6 verbatim: ProcessContext,
DeviceNode, ParamDescriptor, NodeState, VoiceInterface), `graph/TrackStrip`
(strip AS a DeviceNode: contract keys, gain-domain smoothing, never-jumps
migration), `graph/MeterProbe` (~30 Hz peak/RMS frames). MidiEvent.h moved
to core/ (device layer references MidiEventSpan; sequencer -> device -> dsp
-> core direction).

Feature 2: `graph/SendNode` (accumulating post-fader tap, resolver-bridged
to the track uid), `graph/DelayComp.h` (DelayCompNode + PdcCalculator - the
industry join-balancing rule computed at compile; all-zero inputs today),
`graph/MigrationPlan` (adopt-only, RT save->load POD moves through pre-sized
scratch), `graph/PlaybackGraph.{h,cpp}` (arena + nodes + resolver + M2
topology processBlock: zeroed track buffers -> strips -> send taps ->
returns -> master -> Main bus, meters collected per block),
GraphBuilder::buildGraph (ordered lanes, model-initialized strips, adoption
scan against the previous artifact, resolver registration, PDC pass, offer).

Self-caught during reread: a replaced UNCLAIMED graph offer must never be
eagerly freed - the new offer's MigrationPlan references the predecessor's
nodes. Graph artifacts therefore free ONLY via the acked-front GC rule
(provably safe by epoch monotonicity), with a chain cap (8) coalescing
rebuilds while the audio thread isn't claiming. Timeline/tempo artifacts
keep eager replacement (no cross-references). Map: 155 classes, sweep green.

**Next: M2 feature 3** — RT swap in AudioEngine (claim -> executeAdopt ->
ack(retired ?: claimed-1) -> publishInstalledGraphSeq -> reapplyNewerThan
through the new resolver), drainDirty through resolver, render through the
graph, meters -> MeterBus, then Kotlin MixerStateHolder wiring.

## 2026-08-25 — M1 COMPLETE (feature 4: editSeq + structure-delta sync)

The dual-model loop is closed end to end: edit -> editSeq-stamped bundle ->
GraphBuilder -> TimelineSnapshot -> RT claim -> MidiScheduler reconciliation.

- ProjectStore: monotonic editSeq on every published change; onEngineSync is
  now `(ProjectAction?, ProjectState, Int)` - undo/redo notify with a NULL
  action (state authoritative, engine resyncs wholesale). Closed the M0 gap
  where undo silently diverged the engine.
- DeltaEncoder: ModelDelta bundles bit-identical to DeltaSchemas.h
  (envelope + contract-ordered 16B StateCodec headers, empty payload =
  remove).
- EngineController: native session (and its builder thread) created EAGERLY
  at construction - deltas apply while audio is closed; sendModelDelta
  (idempotent, never backpressured, growable direct buffer).
- EngineSync: full three-class classification stamped with real editSeq.
  Structure edits map per action (clip placement-only vs content-only
  updates); cascading removes derive from PRE-state; shared content removed
  only when unreferenced post-state; canonical linked-pair content id =
  lexicographic MIN of clip ids (forward-compatible with explicit
  ClipContent + copy-on-unlink at the session milestone); drum steps
  flatten via DrumPadType.midiPitch with stable fnv32 step ids; solo/arm/
  mute reach the model's track flags (audibility matrix input, M2); SetBpm
  sends the live splice AND the canonical tempo delta; NULL action + every
  RUNNING transition trigger full push + param resend.
- Model v2 note: the deep entity split (explicit ClipContent, TakeLane,
  automation entities) lands with the milestones that consume it; M1's
  slice is identity + editSeq + linked-content derivation, keeping the UI
  layer stable. Map: 137 classes, sweep green.

**Next (M2, blueprint §11):** PlaybackGraph compile + swap (GraphBuilder
kDirtyGraph consumer), TrackStrip/MasterNode with the contract param keys,
key->dense resolver, PDC skeleton, MigrationPlan adopt path, meter probes -
the first audible milestone.

## 2026-08-25 — M1 feature 3 done: MidiScheduler

`sequencer/MidiEvent.h` (the seam-1 MidiEventSpan types; OFF sorts before ON
at equal offsets) + `sequencer/MidiScheduler.h`. Design rule: positional
facts (which notes start in a span, loop-pass indices, the future
probability seed inputs) are derived from the TransportSpan + clip geometry,
never accumulated — the sounding-note table is the ONLY state. Stuck-note
guarantees, end to end: beat-mapped OFFs, flush on stop/seek/loop-wrap,
synthetic OFFs on timeline swaps (matched by content id while downstream
voices key on per-pass instance ids so loop retriggers never collide), and
an admission invariant (capacity − poolSize ≥ soundingCount) that reserves a
pool slot for every future OFF — emitOff cannot fail, mirroring EventRing's
reserved-OFF rule. Output = flat pool + per-track segments sorted (offset,
OFF-before-ON). AudioEngine drives it per block: beginBlock → swap
reconcile → schedule per transport span; instruments consume at M4.
Map: 136 classes, sweep green.

**Next: M1 feature 4 (final)** — Kotlin model v2 additions (stable entity
ids, shared ClipContent for linked clips), ProjectStore editSeq stamping,
EngineSync structure-delta serialization (ModelDeltaEnvelope + StateCodec
frames via a new DeltaEncoder), full-model push on attach. That closes M1.

## 2026-08-25 — M1 feature 2 done: EngineModel + TimelineSnapshot + GraphBuilder

The builder side of the dual-model architecture is live:

- `jni/DeltaSchemas.h` — versioned payload layouts per entityKind (Track 20B,
  Clip 40B, ClipContent head+NoteRecords, Device 16B, Scene 8B, TempoMap
  head+events) + the ModelDeltaEnvelope (editSeq per bundle). byteLen==0 =
  remove; entityId = the same makeNodeUid used everywhere.
- `engine/EngineModel.{h,cpp}` — builder-thread-only mirror of the edit
  model: idempotent delta application, dirty classes (Timeline/Tempo/Graph),
  non-cascading removal (compiles skip dangling refs, counted - seam-4 skew),
  Rack/Routing/LaneGroup/Groove deferred to M2/M3.
- `sequencer/TimelineSnapshot.h` — immutable compiled timeline: flat stores
  (exact-reserve-then-fill keeps view pointers stable), per-track ClipViews
  sorted by placement, notes sorted content-local, binary-search NoteSpans;
  stamped {epoch, builtFromEditSeq, tempoMapRev}.
- `graph/GraphBuilder.{h,cpp}` — the compile thread (50ms cycle): mutex
  inbox of delta bundles (engine-io producer preserves edit order), model
  apply, timeline rebuild on dirty, tempo base rebuild from model deltas
  (rate-gated with retry), forced tail consolidation sampling the SAME
  governing function at boundary beats (equal-tempo merges preserve
  post-seek sample discontinuities; consolidation skipped while an offer is
  in flight). OfferSlot epoch GC: only the builder frees, only after RT
  acks; tempo background pointer republishes when the predecessor's ack
  proves the claim.
- AudioEngine now constructs/joins the builder (model syncs while streams
  are closed), claims offered timelines at block boundaries (ack-retire),
  exposes builder()/timelineOffer()/timeline(); bridge routes ModelDelta
  payloads into the builder inbox. TempoMap.sampleRate became atomic
  (prepare writes vs builder reads).
- Map: 133 classes, sweep green.

**Next: M1 feature 3 — MidiScheduler** (spans + timeline snapshot + map ->
sample-offset-sorted note events, sounding-note table, loop-pass indices,
synthetic offs on snapshot swap). Then f4: Kotlin model v2 + delta sync.

## 2026-08-25 — M1 feature 1 done: TempoMap + TransportEngine

`cpp/sequencer/` begins. TempoMap implements the frozen contract: immutable
builder-compiled base (piecewise-linear segments — ramps densify at compile
time, the RT path is lookup + lerp) swapped via OfferSlot epochs, plus the
fixed-cap RT tail (anchored live tempo splices, seqlock-published,
rev-stamped; claim retains events newer than foldRev; governing rule =
newest tail event at-or-before position, which also handles seek-back tempo
sets whose anchors are non-monotonic). Background snapshots take a mutex the
RT thread never touches. TransportEngine owns play/pause/stop-to-zero,
seeks, beat-anchored loop region (sample anchors recomputed on tempo
events/base claims), time-sig + metronome flags, the TimebaseSource seam
(external authority rejects tempo messages + counts), and advance() which
claims offered bases then splits the block at a loop wrap (1-2 spans).
AudioEngine now routes ALL 16 transport ops, advances real transport, and
publishes a real beat clock; same-rate reopens keep transport state. Status
wire claims its reserved word as timeSigPacked; metronome bit added
(kClockMetronome == kStatusMetronome), mirrored through WireProtocol /
EngineReadback / CommandEncoder (setTimebaseSource added). Map: 109 classes,
sweep green.

**Next: M1 feature 2** — EngineModel + TimelineSnapshot units + GraphBuilder
thread skeleton (StateCodec delta application, snapshot compile + OfferSlot
swap, epoch GC, tempo-tail consolidation nudge). Then f3 MidiScheduler,
f4 Kotlin model v2 + delta sync.

## 2026-08-25 (later still) — M0 COMPLETE (feature 4: the JNI seam)

Engine foundation milestone done. Feature 4 delivered the full Kotlin <-> C++
seam per CONTRACTS.md seam 5:

- `cpp/jni/` — CommandCodec (pure, visitor-driven frame decoder; record-
  granular backpressure; unknown kinds skipped), StateCodec (contract-ordered
  16-byte entity-delta framing, field-wise reads for the unaligned u64;
  consumer = the M1 builder), ReadbackWire (frozen 80-byte EngineStatusWire +
  meter wire doc), NativeAudioBridge.cpp (the ONLY jni.h TU: RegisterNatives
  from JNI_OnLoad, jlong BridgeHandle, direct-ByteBuffer push/poll/meters,
  Param/Move -> ParamMoveTable vs everything-else -> EventRing routing).
  OboeDriver gained an atomic `inputOpen()` fact for the poll path. CMake
  dawcore now lists jni/NativeAudioBridge.cpp (still NOT wired into Gradle).
- Kotlin `com.example.synth.engine` — WireProtocol (bit-exact fnv1a32/64 +
  makeNodeUid mirrors, all layout/opcode constants), ParamKeys (contract
  semantic keys for M2 strips), EnginePrefs/EngineCaps, NativeAudioBridge
  (guarded loadLibrary: UI-only while the .so doesn't ship), CommandEncoder
  (batch builder; backlog cap -> front-of-queue Panic + reconcile),
  EngineController (ALL natives serialized on one daw-engine-io thread = the
  SPSC producer; backpressure retry; D5 reopen), EngineReadback (16 ms
  status/meter polls on the same dispatcher; playhead extrapolation),
  EngineSync (transport + param-move classification off POST-reduction state;
  reconcile + attach-time full param re-send that queues until start).
- `com.example.DawRuntime` — process-scoped composition root (audio survives
  rotation, spec Part 1 §15); MainActivity now passes the shared store into
  MainDawScreen.
- Map: 101 classes, bidirectional sweep green. Verified data-flow walk:
  dispatch -> reduce -> onEngineSync -> send{} -> encode -> push -> decode ->
  ring/table -> RT drain -> clock/meter publish -> poll -> flows -> UI.

**Next (M1, blueprint §11):** Kotlin domain model v2 (+editSeq stamping),
EngineSync structure-delta serialization (StateCodec), native EngineModel +
GraphBuilder skeleton, TempoMap (immutable base + RT tail), TransportEngine
replacing AudioEngine's placeholder transport, TimelineSnapshot swap
mechanics, MidiScheduler.

## 2026-08-25 (later) — condemned skeleton fully removed

Per the owner's directive after review sign-off: all code in direct argument
with the approved blueprint is gone so building agents cannot be confused by
it. Deleted the entire old pre-blueprint C++ skeleton (49 files: graph/,
device/, sequencer/ — mutable shared graph, string-keyed params, the
blueprint's rejected alternative). `app/src/main/cpp/` now contains ONLY
new-engine modules (core/, dsp/) plus CMakeLists. The living map carries 75
classes, all with source, sweep green. Anyone needing the old code finds it
in git history before this commit.

## 2026-08-25 — M0 in progress (2 of 4 features done)

**Done so far**
- Phase A: blueprint authored from the two specs (rev 3).
- Phase B: adversarial DSP review — 3 rounds, 17 blockers resolved,
  **APPROVED**. Condition honored: `CONTRACTS.md` v1.0.0 frozen first.
- M0 feature 1 — `cpp/core/` complete (13 classes): SpscRing, EngineMessage
  (64-byte seam-2 POD), EventRing (note-off reservation), ParamMoveTable
  (per-producer, seqlock slots, reclaim + reconcile), Seqlock (Boehm fences),
  OfferSlot (epoch-in-artifact), SmoothedValue, AudioBufferPool, TimeAnchor,
  MeterFrame, RtRandom (musical seed), FixedVector, ScopedNoDenormals.
  Old condemned core files deleted (LockFreeQueue, EngineCommand,
  ProcessContext, AudioBufferPool.cpp).
- M0 feature 2 — `cpp/dsp/` first wave (7 classes + DspMath): polyBLEP
  Oscillator (old condemned one deleted), NoiseGen, Simper SVF, RBJ Biquad,
  AdsrEnvelope, Lfo, DelayLine. Conventions (pan law, dB floor, equal-power)
  live as code in DspMath.h.
- CMakeLists.txt exists (dawcore + dawcore_host targets) but is deliberately
  NOT wired into Gradle — no-compile phase; wire via externalNativeBuild when
  the user asks for a build.
- `docs/ARCHITECTURE.md` updated after each feature; bidirectional
  map↔source sweep green (119 classes).

**DONE (M0 feature 3): engine driver + engine skeleton** — InputJitterRing
(D2 policies), OboeDriver (full-duplex per the researched pattern, sub-chunks
bursts to kMaxBlock, timestamps → StreamTime, reopen flag on route change),
AudioEngine (RT spine: anchor publish → bounded drains → param tables retain
for post-swap reapply → input consume → silence render → clock publish).
CMake dawcore target now lists the engine .cpp files.

**Original feature-3 scope (kept for reference):**
- `engine/InputJitterRing.h` — duplex input ring (same-thread, D2 policies:
  ~2-burst prime, underfill zeros+count, overfill drop-oldest+count).
- `engine/OboeDriver.{h,cpp}` — output stream w/ callback + callback-less
  input (Oboe FullDuplex pattern: output built first, input matches rate,
  2x input capacity, 3-phase sync), LowLatency+Exclusive w/ fallback,
  per-stream latency reports, route/rate-change re-prepare hooks, xrun
  counters, stream timestamps → TimeAnchor data. Written against the Oboe
  API headers (arrive via prefab at wire-up).
- `engine/AudioEngine.{h,cpp}` — skeleton: owns per-producer rings + tables,
  TimeAnchorPublisher, MeterBus ring; callback path = ScopedNoDenormals →
  drain input → drain messages (events + ParamMoveTable) → sub-chunk
  numFrames ≤ kMaxBlock → render (silence until M2 graph) → publish anchor/
  clock. Seams left for TransportEngine (M1) and PlaybackGraph (M2).

**Then (M0 feature 4):** `jni/` NativeAudioBridge + CommandCodec/StateCodec
(seam 5) + Kotlin `engine/` bridge counterparts. That completes M0; M1 (model
v2 + time) follows per blueprint §11.

**Standing constraints:** no compiling, no tests (user-gated); Earth.Design
V2 for all UI; map updated per feature; commits reread before push.
