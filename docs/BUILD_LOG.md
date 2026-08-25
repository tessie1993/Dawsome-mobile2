# Build Log

Running hand-off record for the class-by-class build (blueprint
`docs/spec/ARCHITECTURE_BLUEPRINT.md`, contracts `docs/spec/CONTRACTS.md`).
Newest entry first. Each entry: where the build stands, what comes next.

---

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
