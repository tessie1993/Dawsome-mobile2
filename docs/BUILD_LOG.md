# Build Log

Running hand-off record for the class-by-class build (blueprint
`docs/spec/ARCHITECTURE_BLUEPRINT.md`, contracts `docs/spec/CONTRACTS.md`).
Newest entry first. Each entry: where the build stands, what comes next.

---

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
