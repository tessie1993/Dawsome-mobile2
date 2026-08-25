# Mobile DAW Architecture & Living Codebase Map

This document is the **authoritative living map of the codebase**, maintained and kept continuously synchronized with every class, interface, method, and relationship implemented across the architecture (both Native C++ NDK DSP Engine and Kotlin UDF Layer).

**Scope:** this map documents code that exists in the source tree today. The target end-state architecture is specified in [`docs/spec/ARCHITECTURE_BLUEPRINT.md`](spec/ARCHITECTURE_BLUEPRINT.md) (contracts: [`CONTRACTS.md`](spec/CONTRACTS.md); functional specs: [`SPEC_PART1_FUNCTIONAL.md`](spec/SPEC_PART1_FUNCTIONAL.md), [`SPEC_PART2_WORKFLOW.md`](spec/SPEC_PART2_WORKFLOW.md)); classes move into this map when their source lands. The old pre-blueprint C++ skeleton has been fully removed - `app/src/main/cpp/` now contains only new-engine modules (`core/`, `dsp/`, `engine/`, `graph/`, `jni/`, `sequencer/`) built to the blueprint, not yet wired into the Gradle build (that happens when the engine is ready to link; blueprint M0).

```mermaid
classDiagram
    %% ==========================================
    %% INHERITANCE / COMPONENT HIERARCHY
    %% ==========================================
    ComponentActivity <|-- MainActivity
    RoomDatabase <|-- DawDatabase

    %% ==========================================
    %% 1. NATIVE REAL-TIME C++ AUDIO ENGINE (NDK)
    %% ==========================================
    %% ---- M0 core/ (NEW ENGINE - realtime infrastructure, CONTRACTS.md) ----
    class SpscRing~T_Capacity~ {
        <<single-producer single-consumer ring, acquire-release, cache-line-split indices>>
        +tryPush(value: T) bool
        +tryPop(out: T) bool
        +freeSlots() size_t
        +pending() size_t
    }

    class EngineMessage {
        <<64-byte POD, seam-2 frozen layout>>
        +MsgFamily family
        +uint8_t op
        +uint16_t flags
        +uint32_t editSeq
        +NodeUid nodeUid
        +ParamKeyHash paramKeyHash
        +uint32_t a
        +int64_t samplePos
        +double beat
        +double v0
        +double v1
        +uint64_t b
    }

    class EventRing~Capacity~ {
        <<lossless message channel; note-ON reserves its OFF slot>>
        +tryPush(m: EngineMessage) bool
        +tryPop(out: EngineMessage) bool
    }

    class ParamMoveTable {
        <<per-producer coalescing latest-wins table, per-slot seqlock>>
        +set(uid, key, plain, editSeq) bool
        +drainDirty(apply)
        +reapplyNewerThan(graphSeq, apply)
        +publishInstalledGraphSeq(seq)
        +consumeOverflowFlag() bool
    }

    class Seqlock~T~ {
        <<single-writer POD publication, Boehm fence discipline>>
        +publish(value: T)
        +read(out: T) uint32_t
        +version() uint32_t
    }

    class OfferSlot~T~ {
        <<single-slot builder-to-RT handover; artifact carries its own epoch>>
        +offer(built: T*) T*
        +claim() T*
        +ackRetired(epoch)
        +retiredAcked(epoch) bool
    }

    class SmoothedValue {
        <<linear ramp, exact arrival, state migrates across swaps>>
        +prepare(sampleRate, smoothingMs)
        +snap(value: float)
        +setTarget(value: float)
        +getNext() float
        +skip(n: int)
        +isSmoothing() bool
    }

    class AudioBufferPool {
        <<builder-allocated stereo scratch buffers, RT freelist>>
        +prepare(count: int)
        +acquire() Buffer*
        +release(b: Buffer*)
        +available() int
    }

    class TimeAnchor {
        <<per-callback clock anchor published via Seqlock>>
        +int64_t framePosition
        +int64_t monotonicNanos
        +double sampleRate
        +framesAt(nanos: int64_t) int64_t
    }

    class MeterFrame {
        <<32-byte lossy metering POD>>
        +NodeUid uid
        +float peakL
        +float peakR
        +float rmsL
        +float rmsR
        +float gainReductionDb
        +uint16_t flags
        +uint16_t seq
    }

    class RtRandom {
        <<xorshift64* + musical seed (clipUid, quantizedPos, loopPassIndex)>>
        +reseed(seed)
        +nextU64() uint64_t
        +nextFloat01() float
        +chance(p: float) bool
        +musicalSeed(clipUid, quantizedPos, loopPassIndex) uint64_t
    }

    class FixedVector~T_Capacity~ {
        <<inline-storage POD vector, no heap>>
        +push_back(value: T) bool
        +eraseUnordered(i: size_t)
        +clear()
        +size() size_t
    }

    class ScopedNoDenormals {
        <<RAII FTZ-DAZ guard: ARM64 FPCR bit 24, x86 MXCSR bits 15 and 6>>
        +ScopedNoDenormals()
        +~ScopedNoDenormals()
    }

    %% ---- M0 dsp/ (NEW ENGINE - pure DSP primitives, host-compilable) ----
    class Oscillator {
        <<polyBLEP band-limited: sine, saw, pulse (PWM), triangle; hard sync>>
        +prepare(sampleRate)
        +setWave(w: Wave)
        +setFrequency(hz: float)
        +setPulseWidth(pw: float)
        +sync(phase)
        +process() float
    }
    class NoiseGen {
        <<white + Kellet pink (-3 dB/oct)>>
        +white() float
        +pink() float
        +reseed(seed)
    }
    class SvfFilter {
        <<Simper trapezoidal ZDF SVF - audio-rate-mod safe; LP/BP/HP/notch/peak/AP>>
        +prepare(sampleRate)
        +setMode(m: Mode)
        +setParams(cutoffHz, q)
        +process(in: float) float
    }
    class BiquadFilter {
        <<TDF-II + RBJ cookbook designs - static EQ bands, shelves, crossovers>>
        +prepare(sampleRate)
        +design(type, freqHz, q, gainDb)
        +process(in: float) float
    }
    class AdsrEnvelope {
        <<analog-style one-pole segments with overshoot targets>>
        +prepare(sampleRate)
        +setTimes(attackMs, decayMs, sustain, releaseMs)
        +noteOn()
        +noteOff()
        +process() float
        +isActive() bool
    }
    class Lfo {
        <<sine/tri/saws/square/S&H/smooth-random, seedable for render parity>>
        +prepare(sampleRate)
        +setShape(s: Shape)
        +setFrequency(hz)
        +process() float
    }
    class DelayLine {
        <<power-of-2 mono history; integer, linear and cubic-Hermite reads>>
        +prepare(maxDelaySamples)
        +write(in: float)
        +read(delay: int) float
        +readLinear(delay: float) float
        +readHermite(delay: float) float
    }
    %% ---- M1 sequencer/ (time system: TempoMap + TransportEngine) ----
    class TempoSegment {
        <<piecewise-linear map atom; ramps/curves densified into these by the builder>>
        +double startBeat
        +int64_t startSample
        +double samplesPerBeat
    }
    class TimeSigEvent {
        <<bar-aligned meter change; beats are quarter notes>>
        +double startBeat
        +int32_t barAtStart
        +uint16_t numerator
        +uint16_t denominator
    }
    class TempoMapBase {
        <<immutable builder-compiled map: segments + time sigs; carries epoch (OfferSlot) + foldRev (tail retention)>>
        +uint64_t epoch
        +uint32_t foldRev
        +FixedVector~TempoSegment~ segments
        +FixedVector~TimeSigEvent~ timeSigs
    }
    class TempoTailEvent {
        <<RT-appended live tempo splice, anchored at the exact transport position (continuous by construction); rev for base-claim retention>>
        +double startBeat
        +int64_t startSample
        +double samplesPerBeat
        +uint32_t rev
    }
    class TempoTailBlock {
        <<seqlock-published POD tail view for background readers>>
        +uint32_t count
        +uint32_t rev
        +TempoTailEvent[] events
    }
    class TempoMap {
        <<musical time authority (CONTRACTS TempoMap contract): immutable base (epoch offer/ack swap) + fixed-cap RT tail (seqlock, rev bump per append/claim); governing rule = newest tail event at-or-before position else base segment; claim retains tail events with rev > foldRev; background snapshot = mutex bgBase + seqlock tail, rev-stamped, RT never touches the mutex>>
        +prepare(sampleRate, defaultBpm)
        +rtSetTempo(bpm, atSample, atBeat)
        +rtNudgeTempo(bpmDelta, atSample, atBeat)
        +rtClaimOfferedBase() bool
        +rtTailNeedsConsolidation() bool
        +beatAt(sample) double
        +sampleAt(beat) int64_t
        +bpmAt(beat) double
        +barBeatAt(beat) BarBeat
        +rev() uint32_t
        +offerBase(built) TempoMapBase*
        +retiredBaseAcked(epoch) bool
        +publishBackgroundBase(base)
        +snapshot() Snapshot
    }
    class TransportSpan {
        <<one contiguous musical slice of a render block (loop wrap splits at the boundary); interior beat positions must convert through the map per event>>
        +int64_t startSample
        +double startBeat
        +double endBeat
        +int offsetFrames
        +int frames
        +bool wrapped
    }
    class TransportEngine {
        <<RT transport state machine over the TempoMap: play/pause/stop-to-zero, record+metronome flags, seeks, loop region (beat-anchored, sample anchors recomputed on tempo events/base claims), TimebaseSource seam (Internal live; Link/MidiClockSlave via SyncAdapter M16, external authority rejects tempo msgs + counts), advance() = claim offered base then split block at loop wrap (1-2 spans, one wrap per block)>>
        +prepare(sampleRate, defaultBpm)
        +play()
        +stop()
        +togglePlay()
        +setRecording(on)
        +setLooping(on)
        +setMetronome(on)
        +seekSample(sample)
        +seekBeat(beat)
        +setLoopRegion(startBeat, endBeat)
        +setTempo(bpm)
        +nudgeTempo(bpmDelta)
        +setTimeSig(numerator, denominator)
        +setTimebaseSource(src: TimebaseSource)
        +advance(numFrames, out: TransportSpan[2]) int
        +tempoMap() TempoMap
        +positionSamples() int64_t
        +positionBeat() double
        +bpm() double
    }

    TempoMap *-- TempoMapBase
    TempoMap *-- TempoTailEvent
    TempoMap ..> TempoTailBlock
    TempoMap ..> Seqlock
    TempoMap ..> OfferSlot
    TempoMapBase ..> FixedVector
    TransportEngine *-- TempoMap
    TransportEngine ..> TransportSpan

    %% ---- M1 sequencer/ (TimelineSnapshot - seam-4 compiled timeline) ----
    class SnapshotNote {
        <<immutable note POD in a snapshot's flat store>>
        +uint32_t id
        +uint16_t pitch
        +uint16_t velocity
        +double startBeat
        +double lengthBeats
    }
    class NoteSpan {
        <<pointer+count read window (seam-4 span idiom)>>
        +const SnapshotNote* data
        +size_t count
    }
    class ClipView {
        <<one placed arrangement clip with shared content resolved; notes sorted by content-local startBeat>>
        +NodeUid clipUid
        +NodeUid trackUid
        +NodeUid contentUid
        +double startBeat
        +double lengthBeats
        +double contentLengthBeats
        +bool looping
        +notesInRange(fromBeat, toBeat) NoteSpan
    }
    class TrackTimeline {
        <<per-track view: arrangement clips sorted by placement>>
        +NodeUid trackUid
        +uint8_t trackType
        +const ClipView* clips
        +uint32_t clipCount
    }
    class TimelineSnapshot {
        <<immutable builder-compiled timeline artifact: flat stores + views (pointers stable by exact-reserve-then-fill); epoch (OfferSlot), builtFromEditSeq (ordering rule), tempoMapRev (stale detect); RT only reads. Session-slot views join at M5; dangling refs skipped at compile (seam-4 skew)>>
        +uint64_t epoch
        +uint32_t builtFromEditSeq
        +uint32_t tempoMapRev
        +trackByUid(uid) TrackTimeline*
    }

    TimelineSnapshot *-- SnapshotNote
    TimelineSnapshot *-- ClipView
    TimelineSnapshot *-- TrackTimeline
    ClipView ..> NoteSpan

    %% ---- M1 sequencer/ (MidiScheduler - sample-accurate note scheduling) ----
    class MidiEvent {
        <<24B block-local event (offset indexes block buffers; OFF sorts before ON at equal offsets)>>
        +NodeUid trackUid
        +uint32_t noteId
        +int32_t sampleOffset
        +uint16_t pitch
        +uint8_t type
        +uint8_t velocity
    }
    class MidiEventSpan {
        <<the seam-1 read window ProcessContext carries (pointer + count)>>
        +begin() / end() / size() / empty()
    }
    class MidiScheduler {
        <<[RT] allocation-free scheduling from the installed TimelineSnapshot: positional facts (starting notes, loop-pass index, future probability seeds) DERIVED from the TransportSpan, never accumulated - only state is the sounding-note table. Stuck-note guarantees: mapped OFFs, flush on stop/seek/wrap, synthetic OFFs on snapshot swap (matched by content id; loop passes get fresh instance ids), admission invariant reserves one pool slot per sounding note so emitOff is infallible, refused ONs counted. Output: flat event pool + per-track segments sorted (offset, OFF-before-ON); instruments consume from M4; MidiClipPlayer (comping/MPE/probability state) grows out at M7>>
        +beginBlock()
        +onTimelineSwap(t: TimelineSnapshot*)
        +scheduleSpan(timeline, span: TransportSpan, map: TempoMap, playing)
        +allNotesOff(sampleOffset)
        +events() FixedVector~MidiEvent~
        +segments() FixedVector~TrackEvents~
        +scheduledOns() / scheduledOffs() / syntheticOffs() / overflowDrops()
    }

    MidiScheduler ..> TimelineSnapshot
    MidiScheduler ..> TempoMap
    MidiScheduler ..> TransportSpan
    MidiScheduler *-- MidiEvent
    MidiScheduler ..> MidiEventSpan
    AudioEngine *-- MidiScheduler

    %% ---- M0 engine/ (NEW ENGINE - driver + callback spine) ----
    class StreamTime {
        <<per-callback DAC anchor from Oboe stream timestamps>>
        +int64_t framePosition
        +int64_t monotonicNanos
        +bool valid
    }
    class RenderSink {
        <<abstract>>
        +render(outputs, numFrames <= kMaxBlock, input: InputJitterRing, time: StreamTime)*
    }
    class InputJitterRing {
        <<duplex input smoothing: 2-burst prime, zero-fill underfill, drop-oldest overfill, counters>>
        +prepare(capacityFrames, channels, primeFrames)
        +push(interleaved, frames)
        +consume(deinterleaved, frames)
        +primed() bool
    }
    class OboeDriver {
        <<output-driven full duplex per D2: output stream w/ callback (LowLatency, Exclusive->Shared, native rate), callback-less input matched+2x capacity, non-blocking drain, sub-chunks bursts to kMaxBlock, latency reports, reopen flag on route change>>
        +open(sink: RenderSink, cfg: Config) bool
        +start() bool
        +stop()
        +close()
        +sampleRate() double
        +outputLatencyMs() double
        +inputLatencyMs() double
        +xrunCount() int32_t
        +needsReopen() bool
        +onAudioReady(stream, audioData, numFrames) DataCallbackResult
    }
    class TransportClockData {
        <<per-block transport snapshot published via Seqlock>>
        +int64_t samplePos
        +double beat
        +double bpm
        +uint32_t flags
    }
    class AudioEngine {
        <<facade + RT callback spine: anchor publish -> bounded ring drains (transport ops -> TransportEngine) -> param-table drain (values retained for post-swap reapply) -> transport advance (base claim + loop-wrap spans) -> input consume -> render (silence until M2 graph) -> clock publish from real transport. Same-rate reopen keeps transport state; rate change re-prepares. Seams open: PlaybackGraph (M2), instruments (M4)>>
        +start(cfg: OboeDriver.Config) bool
        +stop()
        +jniEvents() EventRing
        +jniParams() ParamMoveTable
        +midiEvents() EventRing
        +midiParams() ParamMoveTable
        +popMeter(out: MeterFrame) bool
        +clock() TransportClockData
        +anchor() TimeAnchor
        +transport() TransportEngine
        +builder() GraphBuilder
        +timelineOffer() OfferSlot~TimelineSnapshot~
        +timeline() TimelineSnapshot*
        +midi() MidiScheduler
        +render(outputs, numFrames, input, time)
    }

    RenderSink <|-- AudioEngine
    OboeDriver ..> RenderSink
    OboeDriver *-- InputJitterRing
    AudioEngine *-- OboeDriver
    AudioEngine *-- TransportEngine
    AudioEngine *-- ParamMoveTable
    AudioEngine *-- GraphBuilder
    AudioEngine ..> EngineMessage
    AudioEngine ..> MeterFrame
    AudioEngine ..> TimeAnchor
    AudioEngine ..> TimelineSnapshot

    %% ---- M1 engine/ (EngineModel - the builder-side edit-model mirror) ----
    class ModelNote {
        <<builder-side note (content-local)>>
        +uint32_t id
        +uint16_t pitch
        +uint16_t velocity
        +double startBeat
        +double lengthBeats
    }
    class ModelTrack {
        <<track mirror: type, flags, order, mixer values>>
    }
    class ModelClip {
        <<clip placement mirror: trackUid, contentUid (linked-clip ref), startBeat, lengthBeats, slotIndex (-1 = arrangement), looping>>
    }
    class ModelClipContent {
        <<shared clip content mirror: loop length + notes (unsorted; snapshots sort)>>
    }
    class ModelDevice {
        <<device mirror: trackUid, type, enabled, chain order>>
    }
    class ModelScene {
        <<scene mirror: index>>
    }
    class ModelTempo {
        <<canonical tempo/meter event lists (sorted at apply)>>
    }
    class EngineModel {
        <<compact C++ mirror of the edit model (blueprint 2.3): GraphBuilder-thread-only, fed by StateCodec deltas (idempotent upserts; byteLen 0 = remove; non-cascading - compiles skip dangling refs per seam-4 skew); dirty classes kDirtyTimeline/Tempo/Graph consumed per build cycle; Rack/Routing/LaneGroup/Groove kinds counted-deferred to M2/M3>>
        +applyDelta(d: EntityDelta) bool
        +consumeDirty() uint32_t
        +tracks() / clips() / contents() / devices() / scenes() / tempo()
        +lastEditSeq() uint32_t
        +noteEditSeq(seq)
    }

    EngineModel *-- ModelTrack
    EngineModel *-- ModelClip
    EngineModel *-- ModelClipContent
    EngineModel *-- ModelDevice
    EngineModel *-- ModelScene
    EngineModel *-- ModelTempo
    ModelClipContent *-- ModelNote
    EngineModel ..> EntityDelta

    %% ---- M1 graph/ (GraphBuilder - the background compile thread) ----
    class GraphBuilder {
        <<owns EngineModel + the compile thread (50ms wait_for cycle): drains ModelDelta bundle inbox (mutex+condvar; single engine-io producer preserves edit order), applies deltas, rebuilds dirty artifacts - TimelineSnapshot (exact-reserve flat stores), TempoMapBase from model tempo deltas (rate-gated, retried) or forced tail consolidation (samples the SAME governing function at boundary beats; equal-tempo merges preserve post-seek discontinuities; skipped while an offer is in flight). All handovers via OfferSlot epochs; only this thread frees retired artifacts (after RT ack; tempo bg pointer republished once predecessor ack proves the claim). PlaybackGraph compile joins at M2>>
        +start()
        +stop()
        +submitDeltas(payload, len)
        +nudge()
        +deltasApplied() / deltasRejected() / timelineBuilds() / tempoBuilds() / danglingRefs()
    }

    GraphBuilder *-- EngineModel
    GraphBuilder ..> TimelineSnapshot
    GraphBuilder ..> TempoMapBase
    GraphBuilder ..> StateCodec
    GraphBuilder ..> ModelDeltaEnvelope
    GraphBuilder ..> AudioEngine

    %% ---- M0 jni/ (NEW ENGINE - seam-5 wire codecs + the one JNI TU) ----
    class FrameHeader {
        <<8-byte command frame envelope: u16 version, u16 kind, u32 byteLen>>
    }
    class ControlOpPayload {
        <<8-byte non-RT control op: u32 op + u32 arg (Nop only today)>>
    }
    class CommandCodec {
        <<pure static Kotlin->C++ command decoder/encoder (seam 5): length-delimited frames, EngineMessageBatch = N x 64B POD little-endian as-is; visitor-driven, record-granular backpressure, unknown kinds skipped+counted, unknown version refused>>
        +decode(data, len, visitor) Result
        +writeFrameHeader(dst, dstLen, kind, payloadLen) size_t
    }
    class StateCodec {
        <<pure static entity-delta codec (model deltas -> EngineModel builder, M1): contract-ordered 16-byte header read field-wise (u64 unaligned at offset 4); idempotent upserts/removes, no backpressure on the builder path>>
        +decode(data, len, visitor) Result
        +writeDeltaHeader(dst, dstLen, kind, entityId, payloadLen) size_t
    }
    class EntityDelta {
        <<one decoded delta handed to the builder>>
        +uint16_t entityKind
        +uint64_t entityId
        +const uint8_t* payload
        +uint32_t byteLen
    }
    class ModelDeltaEnvelope {
        <<8-byte prefix of every ModelDelta payload: editSeq stamps the bundle (one edit action = one bundle)>>
        +uint32_t editSeq
        +uint32_t flags
    }
    class TrackDeltaPayload {
        <<20B: type, flags (mute/solo/arm/override), order, volumeDb, pan, sendA, sendB>>
    }
    class ClipDeltaPayload {
        <<40B: trackUid, contentUid (linked-clip ref), startBeat, lengthBeats, slotIndex (-1 = arrangement), loop flag>>
    }
    class ClipContentDeltaHead {
        <<16B head: lengthBeats + noteCount, followed by NoteRecords>>
    }
    class NoteRecord {
        <<24B: id (fnv32), pitch, velocity, startBeat, lengthBeats; drum steps flatten to these Kotlin-side>>
    }
    class DeviceDeltaPayload {
        <<16B: trackUid, deviceType, enabled flag, chain order>>
    }
    class SceneDeltaPayload {
        <<8B: index + reserved flags>>
    }
    class TempoMapDeltaHead {
        <<8B head: tempoCount + sigCount, followed by TempoEventRecords then SigEventRecords>>
    }
    class TempoEventRecord {
        <<16B: beat + bpm (constant until next; ramps densify at M13)>>
    }
    class SigEventRecord {
        <<16B: bar-aligned beat + numerator/denominator>>
    }
    class EngineStatusWire {
        <<80-byte frozen poll POD (ReadbackWire.h): status flags (clock bits + running/needsReopen/inputOpen), transport clock, TimeAnchor, stream facts, engine counters>>
        +uint32_t version
        +uint32_t flags
        +int64_t samplePos
        +double beat
        +double bpm
        +int64_t anchorFrame
        +int64_t anchorNanos
        +double sampleRate
        +float outputLatencyMs
        +float inputLatencyMs
        +uint32_t xruns
        +uint32_t droppedNotes
        +uint32_t panics
        +uint32_t timeSigPacked
    }
    class BridgeHandle {
        <<one native engine session; NativeAudioBridge.cpp is the ONLY jni.h TU, natives RegisterNatives'd from JNI_OnLoad against Kotlin NativeAudioBridge; jlong handle = pointer; lifecycle+push confined to the Kotlin engine-io thread>>
        +AudioEngine engine
        +atomic~bool~ running
        +atomic~uint32_t~ codecErrors
        +atomic~uint32_t~ deferredFrames
    }
    class PushVisitor {
        <<routes decoded records: Param/Move -> ParamMoveTable.set (overflow = reconcile flag, never backpressure), everything else -> EventRing.tryPush (refusal = backpressure); ModelDelta payloads -> GraphBuilder.submitDeltas; ParamBlockSet counted-deferred until the graph (M2)>>
        +onMessage(m: EngineMessage) bool
        +onControl(c: ControlOpPayload) bool
        +onBlockSet(p, len)
        +onModelDelta(p, len)
    }

    CommandCodec ..> FrameHeader
    CommandCodec ..> ControlOpPayload
    CommandCodec ..> EngineMessage
    StateCodec ..> EntityDelta
    BridgeHandle *-- AudioEngine
    BridgeHandle ..> CommandCodec
    BridgeHandle ..> PushVisitor
    BridgeHandle ..> EngineStatusWire
    PushVisitor ..> AudioEngine
    PushVisitor ..> GraphBuilder

    %% ==========================================
    %% 2. APP ENTRY POINT & WORKSPACE SCREENS (EARTH.DESIGN)
    %% ==========================================
    class MainActivity {
        +onCreate(savedInstanceState: Bundle)
    }

    class DawRuntime {
        <<object; process-scoped composition root: the one ProjectStore + engine trio live here so audio survives rotation/navigation (spec Part 1 §15); idempotent ensureStarted from activity onCreate>>
        +ProjectStore store
        +EngineController controller
        +EngineReadback readback
        +ensureStarted()
    }

    class MainDawScreen {
        <<composable>>
        +MainDawScreen(store: ProjectStore, modifier: Modifier)
    }

    class ArrangerScreen {
        <<composable>>
        +ArrangerScreen(arrangementStateHolder: ArrangementStateHolder, mixerStateHolder: MixerStateHolder, modifier: Modifier)
    }

    class SessionViewScreen {
        <<composable>>
        +SessionViewScreen(sessionStateHolder: SessionStateHolder, modifier: Modifier)
    }

    class MixerScreen {
        <<composable>>
        +MixerScreen(mixerStateHolder: MixerStateHolder, modifier: Modifier)
    }

    class PianoRollScreen {
        <<composable>>
        +PianoRollScreen(pianoRollStateHolder: PianoRollStateHolder, modifier: Modifier)
    }

    class ModularSynthScreen {
        <<composable>>
        +ModularSynthScreen(deviceRackStateHolder: DeviceRackStateHolder, modifier: Modifier)
    }

    class SamplerDrumLabScreen {
        <<composable>>
        +SamplerDrumLabScreen(deviceRackStateHolder: DeviceRackStateHolder, modifier: Modifier)
    }

    class SoundBrowserScreen {
        <<composable>>
        +SoundBrowserScreen(browserStateHolder: SoundBrowserStateHolder, onLoadItem: (String)->Unit, modifier: Modifier)
    }

    class MasteringSuiteScreen {
        <<composable>>
        +MasteringSuiteScreen(masteringStateHolder: MasteringStateHolder, modifier: Modifier)
    }

    %% ==========================================
    %% 3. PRO-AUDIO DESIGN SYSTEM (EARTH.DESIGN)
    %% ==========================================
    class EarthColorTokens {
        <<object>>
        +Color BgObsidianDeep
        +Color GlassEspresso
        +Color GlassSurface
        +Color GlassSurfaceRaised
        +Color GlassBorderSubtle
        +Color GlassBorderRimAmber
        +Color EarthAmber
        +Color AutumnMapleAmber
        +Color AutumnHarvestGold
        +Color AutumnTerracotta
        +Color AutumnRust
        +Color AutumnCrimsonMaple
        +Color NatureEmerald
        +Color NatureMossSage
        +Color TextPrimary
        +Color TextSecondary
        +Color TextDisabled
    }

    class EarthColors {
        <<data class>>
        +Color bgObsidian
        +Color glassBase
        +Color glassSurface
        +Color primaryAmber
        +Color autumnRust
        +Color autumnTerracotta
        +Color autumnHarvestGold
        +Color natureForestPine
        +Color natureEmerald
        +Color natureMossSage
    }

    class EarthGlassTokens {
        <<object>>
        +GlassElevation Level1Dock
        +GlassElevation Level2Panel
        +GlassElevation Level3Device
        +GlassElevation Level4Modal
    }

    class EarthTypography {
        <<data class>>
        +TextStyle displayTime
        +TextStyle bpmValue
        +TextStyle trackTitle
        +TextStyle sectionLabel
        +TextStyle paramLabel
        +TextStyle paramValue
        +TextStyle microBadge
    }

    class EarthTheme {
        <<composable + object>>
        +EarthTheme(colors: EarthColors, typography: EarthTypography, content)
        +EarthColors colors
        +EarthTypography typography
    }

    class EarthTransportBar {
        <<composable>>
    }

    class MacroCutoffKnob {
        <<composable>>
    }

    class BiDirectionalPanKnob {
        <<composable>>
    }

    class MicroEncoder {
        <<composable>>
    }

    class PrecisionCrystalFader {
        <<composable>>
    }

    class StereoLedLevelMeter {
        <<composable>>
    }

    class SoloMuteArmToggles {
        <<composable>>
    }

    class ClipLauncherTile {
        <<composable>>
    }

    class VelocityDrumPad {
        <<composable>>
    }

    class InteractiveWaveformCanvas {
        <<composable>>
    }

    class ParametricEqGraph {
        <<composable>>
    }

    class AdsrEnvelopeGraph {
        <<composable>>
    }

    %% ==========================================
    %% 4. UNIDIRECTIONAL DATA FLOW (UDF) STORE & ACTIONS
    %% ==========================================
    class ProjectStore {
        <<UDF store; every published change carries a monotonic editSeq (blueprint 2.2 ordering); undo/redo notify the sync listener with a NULL action = state is authoritative, engine resyncs wholesale>>
        -MutableStateFlow~ProjectState~ _state
        +StateFlow~ProjectState~ state
        -ArrayDeque~ProjectState~ undoStack
        -ArrayDeque~ProjectState~ redoStack
        +Int editSeq
        +((ProjectAction?, ProjectState, Int)->Unit)? onEngineSync
        +dispatch(action: ProjectAction)
        +undo()
        +redo()
    }

    class ProjectState {
        <<data class>>
        +String id
        +String name
        +Float bpm
        +Int timeSigNum
        +Int timeSigDen
        +Int keyRoot
        +MusicalScale scale
        +List~TrackModel~ tracks
        +List~SessionScene~ scenes
        +Float masterVolumeDb
        +Float masterLimiterCeilingDb
        +Boolean isPlaying
        +Boolean isRecording
        +Boolean isLooping
        +Float loopStartBeat
        +Float loopEndBeat
        +Float playheadBeat
        +String? selectedTrackId
        +String? selectedClipId
        +DawTab activeTab
    }

    class ProjectAction {
        <<sealed interface>>
    }

    class TrackModel {
        <<data class>>
        +String id
        +String name
        +TrackType type
        +String colorHex
        +Float volumeDb
        +Float pan
        +Boolean isMuted
        +Boolean isSoloed
        +Boolean isArmed
        +Boolean isOverriddenBySession
        +Float sendLevelA
        +Float sendLevelB
        +List~DeviceModel~ devices
        +List~ArrangementClip~ arrangementClips
        +List~SessionClip~ sessionClips
        +Map~AutomationParameter, AutomationLane~ automationLanes
        +Float peakMeterL
        +Float peakMeterR
    }

    class ArrangementClip {
        <<data class>>
        +String id
        +String name
        +String trackId
        +Float startBeat
        +Float lengthBeats
        +List~MidiNote~ notes
        +Map~DrumPadType, List~Float~~ drumSteps
        +String? audioFilePath
        +String? linkedSessionClipId
        +Boolean isMuted
    }

    class SessionClip {
        <<data class>>
        +String id
        +String name
        +String trackId
        +Int slotIndex
        +List~MidiNote~ notes
        +Map~DrumPadType, List~Float~~ drumSteps
        +String? audioFilePath
        +Float lengthBeats
        +String? linkedArrangementClipId
        +Boolean isPlaying
        +Boolean isQueued
        +Boolean isRecording
        +Float playProgress
    }

    class SessionScene {
        <<data class>>
        +String id
        +String name
        +Int index
        +Float? bpm
        +String colorHex
    }

    class MidiNote {
        <<data class>>
        +String id
        +Int pitch
        +Float startBeat
        +Float lengthBeats
        +Float velocity
        +Float releaseVelocity
        +Float probability
        +Float slideSemitones
    }

    class AutomationLane {
        <<data class>>
        +AutomationParameter parameter
        +Boolean isEnabled
        +List~AutomationPoint~ points
        +getValueAtBeat(beat: Float) Float
    }

    class AutomationParameter {
        <<enumeration>>
        VOLUME
        PAN
        FILTER_CUTOFF
        FILTER_RESONANCE
        LFO_RATE
        REVERB_SEND
        DELAY_SEND
        DRIVE_DISTORTION
        CHORUS_MIX
        FM_DEPTH
        WAVETABLE_POS
        SAMPLER_START
        SAMPLER_PITCH
    }

    class TrackType {
        <<enumeration>>
        MIDI
        AUDIO
        DRUM
        RETURN
        MASTER
    }

    class DrumPadType {
        <<enumeration>>
        KICK
        SNARE
        CLAP
        HIHAT_CLOSED
        HIHAT_OPEN
        TOM_LOW
        TOM_MID
        TOM_HIGH
        CRASH
        RIDE
        PERC_1
        PERC_2
        SUB_BOOM
        SHAKER
        COWBELL
        RIMSHOT
    }

    class DeviceModel {
        <<data class>>
        +String id
        +DeviceType type
        +String name
        +Boolean isEnabled
        +Boolean isFolded
        +Map~String, Float~ params
    }

    class DeviceType {
        <<enumeration>>
        SUBTRACTIVE_SYNTH
        WAVETABLE_SYNTH
        FM_SYNTH
        SAMPLER
        ELECTRIC_PIANO
        STRING_PAD
        DRUM_RACK
        PARAMETRIC_EQ
        COMPRESSOR
        REVERB
        DELAY
        DISTORTION
        CHORUS
        LIMITER
    }

    class DawTab {
        <<enumeration>>
        SESSION
        ARRANGER
        MIXER
        PIANO_ROLL
        SYNTH
        SAMPLER
        DRUMS
        BROWSER
        MASTERING
    }

    class MusicalScale {
        <<enumeration>>
        CHROMATIC
        MAJOR
        NATURAL_MINOR
        DORIAN
        PENTATONIC_MAJOR
        PENTATONIC_MINOR
        BLUES
        HIRAJOSHI
        ARABIC
    }

    %% ==========================================
    %% 5. MODULAR UI STATE HOLDERS (COMPOSE RETAINED)
    %% ==========================================
    class TransportStateHolder {
        +StateFlow~TransportUiState~ state
        +play()
        +stop()
        +togglePlay()
        +toggleRecord()
        +toggleLoop()
        +setBpm(bpm: Float)
        +seekToBeat(beat: Float)
        +setLoopRegion(start: Float, end: Float)
        +setScale(root: Int, scale: MusicalScale)
    }

    class ArrangementStateHolder {
        +StateFlow~ArrangementUiState~ state
        +selectTrack(trackId: String)
        +moveClip(clipId: String, newStartBeat: Float)
        +resizeClip(clipId: String, newLengthBeats: Float)
        +deleteClip(clipId: String)
        +addClip(clip: ArrangementClip)
        +seekToBeat(beat: Float)
    }

    class SessionStateHolder {
        +StateFlow~SessionUiState~ state
        +triggerClip(trackId: String, slotIndex: Int)
        +triggerScene(sceneIndex: Int)
        +returnTrackToArrangement(trackId: String)
        +returnAllToArrangement()
    }

    class MixerStateHolder {
        +StateFlow~MixerUiState~ state
        +selectTrack(trackId: String)
        +setTrackVolume(trackId: String, volumeDb: Float)
        +setTrackPan(trackId: String, pan: Float)
        +toggleMute(trackId: String)
        +toggleSolo(trackId: String)
        +toggleArm(trackId: String)
        +setSend(trackId: String, sendIndex: Int, level: Float)
        +setMasterVolume(volumeDb: Float)
    }

    class DeviceRackStateHolder {
        +StateFlow~DeviceRackUiState~ state
        +addDevice(trackId: String, type: DeviceType)
        +removeDevice(trackId: String, deviceId: String)
        +toggleDevice(trackId: String, deviceId: String)
        +setParam(trackId: String, deviceId: String, paramName: String, value: Float)
    }

    class PianoRollStateHolder {
        +StateFlow~PianoRollUiState~ state
        +addNote(note: MidiNote)
        +deleteNote(noteId: String)
        +quantizeNotes(gridBeat: Float)
        +toggleDrumStep(pad: DrumPadType, stepBeat: Float)
    }

    class BrowserItem {
        <<data class>>
        +String id
        +String name
        +BrowserCategory category
        +List~String~ tags
        +String author
    }

    class SoundBrowserStateHolder {
        +StateFlow~BrowserUiState~ state
        +selectCategory(cat: BrowserCategory)
        +search(query: String)
        +toggleTag(tag: String)
    }

    class MasteringStateHolder {
        +StateFlow~MasteringUiState~ state
        +setMasterVolume(volumeDb: Float)
        +setLimiterCeiling(ceilingDb: Float)
        +toggleLimiter()
        +toggleMultiband()
    }

    %% ==========================================
    %% 6. PERSISTENCE (ROOM)
    %% ==========================================
    class DawDatabase {
        <<abstract>>
        +projectDao() ProjectDao
        +getDatabase(context: Context) DawDatabase
    }

    class ProjectDao {
        <<interface>>
        +getAllProjects() Flow~List~ProjectEntity~~
        +getProjectById(id: Long) ProjectEntity?
        +insertProject(project: ProjectEntity) Long
        +deleteProjectById(id: Long)
    }

    class ProjectEntity {
        <<entity>>
        +Long id
        +String name
        +String genre
        +Float bpm
        +Int keyRoot
        +String scaleName
        +Long lastModified
        +String projectDataJson
    }

    %% ==========================================
    %% 7. KOTLIN ENGINE BRIDGE (com.example.synth.engine)
    %% ==========================================
    class WireProtocol {
        <<object; single Kotlin source of seam-5 truth: frame kinds/versions, EngineMessage family+op numbering, status/meter layouts, native result codes, and bit-exact fnv1a32/fnv1a64/makeNodeUid mirrors of NodeUid.h>>
        +paramKey(key: String) Int
        +fnv1a64(s: String) Long
        +makeNodeUid(kind: String, entityId: String) Long
        +Long masterNodeUid
    }
    class ParamKeys {
        <<object; contract semantic key strings - M2 TrackStrip/MasterStrip ParamDescriptors declare exactly these>>
        +String MIXER_VOLUME
        +String MIXER_PAN
        +String MIXER_SEND_A
        +String MIXER_SEND_B
        +String MIXER_MUTE
    }
    class EnginePrefs {
        <<data class; audio session config: enableInput (off until the M6 permission flow), bufferBursts, manualLatencyOffsetMs>>
    }
    class EngineCaps {
        <<object; Kotlin mirror of the EngineConfig.h contractual capacities>>
    }
    class NativeAudioBridge {
        <<object; guarded System.loadLibrary("dawcore") - app runs UI-only when the .so is absent; externals registered native-side against this exact class name; every native serialized on the engine-io thread>>
        +Boolean isLoaded
        +nativeCreate() Long
        +nativeDestroy(handle: Long)
        +nativeStart(handle, enableInput, bufferBursts) Boolean
        +nativeStop(handle: Long)
        +nativePushCommands(handle, buffer: ByteBuffer, byteLen) Int
        +nativePollStatus(handle, buffer: ByteBuffer) Boolean
        +nativeDrainMeters(handle, buffer: ByteBuffer, maxFrames) Int
        +nativeConsumeParamOverflow(handle: Long) Boolean
    }
    class CommandEncoder {
        <<engine-io-confined batch builder: seam-2 records into EngineMessageBatch frames in one reused direct LE buffer; consumed-prefix removal on backpressure; backlog cap replaces the queue with a front-of-queue Panic + onBacklogDropped reconcile>>
        +play()
        +stop()
        +togglePlay()
        +record(on: Boolean)
        +seekBeat(beat: Double)
        +seekSample(samplePos: Long)
        +setTempo(bpm: Double)
        +setLoopRegion(startBeat, endBeat)
        +loop(on: Boolean)
        +metronome(on: Boolean)
        +setTimeSig(numerator, denominator)
        +paramMove(nodeUid, paramKeyHash, plain, editSeq)
        +paramTouch(nodeUid, paramKeyHash, editSeq)
        +paramRelease(nodeUid, paramKeyHash, editSeq)
        +noteOn(nodeUid, noteId, pitchSemitones, velocity)
        +noteOff(nodeUid, noteId, releaseVelocity)
        +allNotesOff(nodeUid)
        +panic()
        +flush(handle: Long) FlushResult
    }
    class EngineController {
        <<native lifecycle owner; ALL native calls run on its single daw-engine-io dispatcher = the JNI SPSC producer thread; the native session (and its GraphBuilder thread) is created EAGERLY at construction so model deltas apply while audio is closed; states UNAVAILABLE/IDLE/RUNNING/FAILED; send{} = enqueue+flush with 5ms backpressure retry; sendModelDelta wraps DeltaEncoder bundles in ModelDelta frames (idempotent, never backpressured, growable direct buffer); param overflow -> onReconcileNeeded; D5 route loss -> requestReopen (stop+start)>>
        +StateFlow~EngineState~ state
        +start(enginePrefs: EnginePrefs)
        +stop()
        +release()
        +requestReopen()
        +send(block: CommandEncoder lambda)
        +sendModelDelta(bundle: ByteArray)
    }
    class DeltaEncoder {
        <<builds one ModelDelta bundle: 8-byte envelope (editSeq) + StateCodec entity frames, bit-identical to DeltaSchemas.h (contract-ordered 16B headers, unaligned u64 at offset 4; empty payload = remove); one edit action = one bundle>>
        +upsertTrack(uid, type, flags, order, volumeDb, pan, sendA, sendB)
        +upsertClip(uid, trackUid, contentUid, startBeat, lengthBeats, slotIndex, looping)
        +upsertContent(uid, lengthBeats, notes: List~WireNote~)
        +upsertDevice(uid, trackUid, type, enabled, order)
        +upsertScene(uid, index)
        +tempoMap(events, sigNumerator, sigDenominator)
        +remove(entityKind, uid)
        +build() ByteArray
    }
    class EngineStatus {
        <<data class; decoded EngineStatusWire snapshot + polledAtNanos>>
    }
    class MeterReading {
        <<data class; decoded MeterFrame: linear peak/RMS, gain reduction, clip flags, seq>>
    }
    class EngineReadback {
        <<polls status+meters every 16ms ON the controller's engine-io dispatcher (single legal meter-ring consumer; destroy-vs-poll races impossible by construction); flows for UI holders; wall-clock playhead extrapolation; needsReopen -> controller.requestReopen, storm-guarded>>
        +StateFlow~EngineStatus~ status
        +StateFlow~Map_NodeUid_MeterReading~ meters
        +start()
        +stop()
        +estimatedBeat(nowNanos: Long) Double
        +estimatedSamplePos(nowNanos: Long) Long
    }
    class EngineSync {
        <<the change-classification seam (dual-model), complete for M1: transport intents -> messages, param moves -> Param/Move, structure edits -> ModelDelta bundles - all stamped with the store's real editSeq. Cascading removes derive from the PRE-change state; shared ClipContent removed only when unreferenced in post-state; canonical content id of a linked arr/session pair = lexicographic MIN of the clip ids (forward-compatible with explicit ClipContent + copy-on-unlink at the session milestone). Drum steps flatten to NoteRecords via DrumPadType.midiPitch with stable fnv32 step ids. NULL store action (undo/redo) and every RUNNING transition -> full model push + param resend (idempotent wholesale resync)>>
        +attach()
        +detach()
        +pushFullModel(state, editSeq)
        +resendAuthoritativeParams()
    }

    EngineSync ..> DeltaEncoder
    DeltaEncoder ..> WireProtocol
    EngineController *-- CommandEncoder
    EngineController ..> NativeAudioBridge
    EngineController ..> EnginePrefs
    EngineReadback ..> EngineController
    EngineReadback ..> NativeAudioBridge
    EngineReadback ..> WireProtocol
    EngineReadback *-- EngineStatus
    EngineReadback *-- MeterReading
    CommandEncoder ..> WireProtocol
    CommandEncoder ..> NativeAudioBridge
    EngineSync ..> ProjectStore
    EngineSync ..> EngineController
    EngineSync ..> WireProtocol
    EngineSync ..> ParamKeys

    %% ==========================================
    %% 8. RELATIONSHIPS & DEPENDENCY FLOW
    %% ==========================================

    MainActivity ..> DawRuntime
    DawRuntime *-- ProjectStore
    DawRuntime *-- EngineController
    DawRuntime *-- EngineReadback
    DawRuntime *-- EngineSync
    NativeAudioBridge ..> BridgeHandle : JNI (seam 5)

    MainActivity ..> MainDawScreen
    MainDawScreen ..> ProjectStore
    MainDawScreen *-- EarthTransportBar
    MainDawScreen *-- ArrangerScreen
    MainDawScreen *-- SessionViewScreen
    MainDawScreen *-- MixerScreen
    MainDawScreen *-- PianoRollScreen
    MainDawScreen *-- ModularSynthScreen
    MainDawScreen *-- SamplerDrumLabScreen
    MainDawScreen *-- SoundBrowserScreen
    MainDawScreen *-- MasteringSuiteScreen

    ArrangerScreen *-- InteractiveWaveformCanvas
    ArrangerScreen *-- SoloMuteArmToggles
    SessionViewScreen *-- ClipLauncherTile
    MixerScreen *-- PrecisionCrystalFader
    MixerScreen *-- StereoLedLevelMeter
    MixerScreen *-- BiDirectionalPanKnob
    MixerScreen *-- MicroEncoder
    PianoRollScreen *-- SoloMuteArmToggles
    ModularSynthScreen *-- MacroCutoffKnob
    ModularSynthScreen *-- ParametricEqGraph
    ModularSynthScreen *-- AdsrEnvelopeGraph
    SamplerDrumLabScreen *-- VelocityDrumPad

    ProjectStore *-- ProjectState
    ProjectStore ..> ProjectAction
    ProjectState *-- TrackModel
    ProjectState *-- SessionScene
    TrackModel *-- DeviceModel
    TrackModel *-- ArrangementClip
    TrackModel *-- SessionClip
    TrackModel *-- AutomationLane
    AutomationLane *-- AutomationPoint
    ArrangementClip *-- MidiNote
    SessionClip *-- MidiNote

    TransportStateHolder --> ProjectStore
    ArrangementStateHolder --> ProjectStore
    SessionStateHolder --> ProjectStore
    MixerStateHolder --> ProjectStore
    DeviceRackStateHolder --> ProjectStore
    PianoRollStateHolder --> ProjectStore
    SoundBrowserStateHolder --> ProjectStore
    MasteringStateHolder --> ProjectStore

    DawDatabase *-- ProjectDao
```
