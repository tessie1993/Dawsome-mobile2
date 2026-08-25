# Mobile DAW Architecture & Living Codebase Map

This document is the **authoritative living map of the codebase**, maintained and kept continuously synchronized with every class, interface, method, and relationship implemented across the architecture (both Native C++ NDK DSP Engine and Kotlin UDF Layer).

**Scope:** this map documents code that exists in the source tree today. The target end-state architecture is specified in [`docs/spec/ARCHITECTURE_BLUEPRINT.md`](spec/ARCHITECTURE_BLUEPRINT.md) (contracts: [`CONTRACTS.md`](spec/CONTRACTS.md); functional specs: [`SPEC_PART1_FUNCTIONAL.md`](spec/SPEC_PART1_FUNCTIONAL.md), [`SPEC_PART2_WORKFLOW.md`](spec/SPEC_PART2_WORKFLOW.md)); classes move into this map when their source lands. The old pre-blueprint C++ skeleton has been fully removed - `app/src/main/cpp/` now contains only new-engine modules (`core/`, `device/`, `dsp/`, `engine/`, `graph/`, `jni/`, `sequencer/`) built to the blueprint, not yet wired into the Gradle build (that happens when the engine is ready to link; blueprint M0).

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
        <<facade + RT callback spine: anchor publish -> BLOCK-BOUNDARY SWAPS (PlaybackGraph claim: executeAdopt while the old graph is still valid, install, ack retired ?: epoch-1, publishInstalledGraphSeq to both tables, reapplyNewerThan through the NEW resolver; TimelineSnapshot claim + scheduler reconcile) -> drains (param moves resolve through the installed graph, misses = counted seam-4 skew) -> transport advance + MIDI scheduling per span -> input consume -> graph processBlock (Main bus -> driver outs; silence before first claim) -> meters to MeterBus -> clock publish. Same-rate reopen keeps transport state. Instruments write into track buffers from M4>>
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

    %% ---- M2 device/ (the device platform contract, seams 1+3+6) ----
    class ProcessContext {
        <<[RT] read-only per process() call: buffers (in-place allowed), block facts (frames <= kMaxBlock, timeline position, beat, bpm, transport flags, offline flag), midiIn: MidiEventSpan, midiOut/sidechain seams (fwd: MidiEventSink, SidechainBus)>>
    }
    class ParamDescriptor {
        <<seam 6: stable semantic key (NEVER reindexed), display, plain range/default, curve (Linear/Log/Exp/Db/Switch), unit, smoothingMs, rtSafe (false = structure-shaped), excludeFromRandomize, isQualityMode>>
    }
    class NodeStateHeader {
        <<seam 3: nodeUid, configHash (topology-relevant config only), sizeBytes, per-type version, flags>>
    }
    class NodeState {
        <<header + POD body; version mismatch = reset-with-fade, never partial adoption>>
    }
    class DeviceNode {
        <<abstract; THE device contract (frozen): prepare [builder], process [RT], reset [RT], latencySamples CONSTANT between prepares, param descriptors + setParamImmediate(dense) [RT post-resolution], saveState [RT at swap, pointer/POD only] / loadState. Bypass belongs to the CHAIN (latency-preserving, ~10ms equal-power). Devices never test denormals (global FTZ/DAZ)>>
        +prepare(sampleRate, maxBlock)*
        +process(ctx: ProcessContext)*
        +reset()*
        +latencySamples() int*
        +paramCount() int*
        +paramDescriptor(i) ParamDescriptor*
        +setParamImmediate(denseIndex, plain)*
        +stateBytes() size_t*
        +saveState(out: NodeState)*
        +loadState(in: NodeState) bool*
    }
    class MpeNoteState {
        <<per-note expression: pressure, pitchBendSemitones, slide>>
    }
    class VoiceInterface {
        <<abstract; instruments add: noteOn(note, velocity, MpeNoteState), noteOff, allNotesOff, stealVoices(count) - the ledger's steal demand (releasing -> oldest -> quietest, protect recent + drum transients)>>
    }

    DeviceNode ..> ProcessContext
    DeviceNode ..> ParamDescriptor
    DeviceNode ..> NodeState
    NodeState *-- NodeStateHeader
    ProcessContext ..> MidiEventSpan
    VoiceInterface ..> MpeNoteState

    %% ---- M3 device/ (platform core: chains + registry) ----
    class DeviceChain {
        <<composite DeviceNode - THE bypass contract lives here: dry path delayed by each device's latencySamples (delay lines kept WARM during active operation so an engage never blends stale history), ~10ms equal-power crossfade (wet = cos, dry = sin of xfade t), device processes exactly while the fade runs then rests; chain latency = sum, bypass-independent. One device.bypass switch param per slot (dense = slot, registered under the DEVICE's uid). Chain state (targets + fade positions) migrates; members migrate as their own entries; configHash covers slot count + uids + latencies>>
        +addDevice(uid, device, startBypassed) bool
        +computeConfigHash() uint64_t
        +process(ctx)
        +latencySamples() int
        +setParamImmediate(slot, plain)
        +saveState(out) / loadState(in)
    }
    class DeviceRegistry {
        <<singleton, non-RT; FROZEN DeviceTypeId wire numbering (0 SubtractiveSynth .. 13 Limiter, append-only - replaces Kotlin ordinals); starts EMPTY, factories register at their milestones (M4+), builder skips + counts unregistered types; registerType runs the seam-6 hostside rule: FNV-1a-32 uniqueness of semantic keys within the type>>
        +instance() DeviceRegistry
        +registerType(id, name, factory, params, paramCount) bool
        +create(typeId) unique_ptr~DeviceNode~
        +info(typeId) TypeInfo*
        +keysCollisionFree(params, count) bool
    }
    class ParamValueRecord {
        <<8B wire record: semantic key hash + plain value (DeviceDeltaPayload length-driven params tail - the model residency that lets rebuilds bake device params; param-only refreshes never mark the graph dirty)>>
    }

    DeviceNode <|-- DeviceChain
    DeviceChain ..> SmoothedValue
    DeviceChain ..> DelayLine
    DeviceRegistry ..> DeviceNode
    GraphBuilder ..> DeviceRegistry
    GraphBuilder ..> DeviceChain

    %% ---- M3 device/graph (voice platform) ----
    class StealCandidate {
        <<one voice's steal ranking facts: releasing, protected (transient window / most-recent notes), ageSerial (smaller = older), level>>
    }
    class VoiceGroup {
        <<abstract steal contract instruments expose to the ledger>>
        +activeVoiceCount() int*
        +bestStealCandidate() StealCandidate*
        +stealVoices(count)*
    }
    class VoiceAllocator~VoiceT_MaxVoices~ {
        <<per-instrument pool implementing VoiceGroup: `polyphony` musical voices + headroom slots absorbing steal fades (stolen voices fast-release IN PLACE while the new note takes a free slot; pool exhaustion kills the quietest fading slot - documented last resort). Contract steal order: releasing -> unprotected -> oldest, level tiebreak; protection = transient window OR the 2 most recent serials. noteOff releases the NEWEST non-releasing voice with the id (loop-pass instance ids make collisions rare). VoiceT duck-type: active/releasing/level/inTransientWindow/beginRelease/fastRelease/kill>>
        +setPolyphony(n)
        +acquire(noteId) Slot*
        +noteOff(noteId)
        +allNotesOff()
        +killAll()
        +activeVoiceCount() int
        +bestStealCandidate() StealCandidate
        +stealVoices(count)
    }
    class VoiceBudgetLedger {
        <<global kVoiceBudget=64 enforcement inside a PlaybackGraph (voice accounting is transient render state): beginBlock recounts from every group (drift-proof); requestVoice grants within budget or ranks all groups' best candidates by the contract order and demands stealVoices(1) from the winner; refuses only when nothing is stealable>>
        +registerGroup(g: VoiceGroup) bool
        +beginBlock()
        +requestVoice() bool
        +activeVoices() int
        +stealCount() uint32_t
    }

    VoiceGroup <|-- VoiceAllocator~VoiceT_MaxVoices~
    VoiceGroup ..> StealCandidate
    VoiceBudgetLedger ..> VoiceGroup
    PlaybackGraph *-- VoiceBudgetLedger

    %% ---- M3 device/ (racks + macros + modulation core; QualityMode) ----
    %% QualityMode: enum Eco/Standard/High + "quality" key convention
    %% (device/QualityMode.h) - an ordinary rt-safe param, isQualityMode
    %% flagged; DegradationGovernor forces Eco at M15.
    %% DelayCompNode + PdcCalculator moved to device/DelayComp.h (racks
    %% balance parallel chains internally; graph depends downward).
    class ModSlot {
        <<one modulation routing: ModSource -> target key within the owning device, depth -1..1, bipolar flag>>
    }
    class ModMatrix {
        <<offset-only modulation core (§5: modulation never rewrites the base): fixed 32 slots, offsetFor(key, sources) sums normalized offsets; instruments wire sources (LFOs/env/velocity/MPE/macros/random) from M4; full resolution layering assembles at the automation milestone>>
        +addSlot(s: ModSlot) bool
        +clear()
        +offsetFor(key, sources) float
    }
    class MacroMapping {
        <<macro -> member param plain range (inverted ranges legal)>>
    }
    class MacroTable {
        <<rack macro core (§5 top layer): kMaxMacros knobs, 64 mappings total; expandMacro routes through an apply callback = the installed resolver, so macro targets smooth exactly like direct moves>>
        +addMapping(macroIndex, m: MacroMapping) bool
        +setMacro(macroIndex, value01)
        +macroValue(macroIndex) float
        +expandMacro(macroIndex, apply)
    }
    class RackDevice {
        <<parallel-chain composite DeviceNode: input fans out to each member DeviceChain, internal PDC balances every chain to the slowest (rack reports ONE latency - composes), ChainMixer = smoothed per-chain gains, sum out. Params: macro.1-16 (expand via the builder-wired resolver hook) + rack.chainGain.1-8. State = macro values + mixer gains; members migrate as their own entries; configHash combines chain hashes. Zones (key/velocity/selector/freq-band) + VariationStore join at the racks workflow milestone>>
        +addChain(chain: DeviceChain) bool
        +macros() MacroTable
        +setApplyHook(fn, ctx)
        +computeConfigHash() uint64_t
        +process(ctx)
        +latencySamples() int
        +saveState(out) / loadState(in)
    }
    class ParamBlockEntry {
        <<16B bulk-set triple (ParamBlockSet frame = envelope + N of these): preset load / variation recall / full reconcile ride one frame into the coalescing table; generation barrier deferred to the presets milestone>>
    }

    DeviceNode <|-- RackDevice
    RackDevice *-- MacroTable
    RackDevice ..> DeviceChain
    RackDevice ..> DelayCompNode
    RackDevice ..> SmoothedValue
    MacroTable *-- MacroMapping
    ModMatrix *-- ModSlot

    %% ---- M4 device/ (instruments: first sound) ----
    class MidiTrackRun {
        <<core handoff POD: one track's contiguous (offset, OFF-before-ON)-sorted run in a block's event pool - MidiScheduler.finalizeBlock's product, the graph's per-lane midiIn source>>
        +NodeUid trackUid
        +uint32_t first
        +uint32_t count
    }
    class InstrumentNode {
        <<abstract; DeviceNode + seam-1 VoiceInterface + the compiler's wiring hooks: voiceGroup() for ledger registration and a type-erased admission callback (ledger requestVoice) consulted before every allocation; registry's isInstrument flag makes the builder's static_cast RTTI-free. registerBuiltinDevices() (RegisterBuiltins.cpp, idempotent, engine-ctor-called) fills the registry as milestones land>>
        +setVoiceAdmission(fn, ctx)
        +voiceGroup() VoiceGroup*
    }
    class PolyInstrument~VoiceT_SharedT_StateVersion_PoolVoices_DefaultPolyphony~ {
        <<template shell every polyphonic instrument derives from: owns the VoiceAllocator, the sample-accurate event-split process loop over ctx.midiIn (scheduler instance ids key the allocator), ledger admission before every allocation (budgetRefusals counter), the seam-1 live interface (note number -> voice id), and SharedT-POD save/load (shared params migrate; sounding voices reset on structural rebuilds - deferred polish, BUILD_LOG). Concrete synths supply VoiceT + SharedT + the descriptor trio (paramCount/paramDescriptor/setParamImmediate)>>
        +prepare(sampleRate, maxBlock)
        +process(ctx)
        +reset() / latencySamples()
        +saveState(out) / loadState(in)
        +noteOn(note, velocity, mpe) / noteOff / allNotesOff / stealVoices
        +voiceGroup() VoiceGroup*
        #handleEvent(e) / noteOnId(id, pitch, vel01) / renderVoices(l, r, n)
        #SharedT shared_
    }
    class SubtractiveShared {
        <<POD settings the voices read = the synth's migrating state body: osc waves/detune/semi/mix, noise, cutoff/res/envAmount(oct)/keyTrack, amp+filter ADSR, LFO rate/toPitch/toCutoff, velocity depths, quality>>
    }
    class SubtractiveVoice {
        <<heap-free, trivially copyable virtual-analog voice (researched convention): 2 polyBLEP osc (osc2 detuned, phase-offset) + noise -> Simper SVF lowpass -> analog ADSR VCA; filter/LFO/pitch at control rate (their DSP prepared at rate/16), amp env + oscs at audio rate; velocity scales amp + filter-env depth; VoiceT contract incl. 30ms transient window + 4ms steal fade>>
        +prepare(sampleRate)
        +start(pitch, velocity01, shared)
        +renderAdd(l, r, n, shared)
        +active() / releasing() / level() / inTransientWindow()
        +beginRelease() / fastRelease() / kill()
    }
    class SubtractiveSynth {
        <<the first instrument (DeviceTypeId 0): PolyInstrument of SubtractiveVoice/SubtractiveShared (pool 16, poly 8); 24 contract descriptors (kSubtractiveParams)>>
        +paramCount() / paramDescriptor(i)
        +setParamImmediate(dense, plain)
    }
    class WavetableBank {
        <<process-global GENERATED table set (no assets): 8 morph frames (sine/tri/saw/square/bright/metallic/organ/formant) x 8 mips x 2048, built additively with per-mip harmonic caps (128 >> mip, the classic anti-aliasing ladder) at registration [non-RT], read-only afterwards - RT reads need no lifetime protocol. One gain per frame from the full-res mip keeps level continuous across mip crossings>>
        +instance() WavetableBank&
        +mipForInc(phaseInc) int
        +sample(frame, mip, phase) float
    }
    class WavetableShared {
        <<POD settings/migrating state body: position + posEnv/posLfo morph depths, cutoff/res/envOct/keyTrack, amp+filter ADSR, LFO rate/toPitch, velocity depths, quality>>
    }
    class WavetableVoice {
        <<table read (linear phase interp + linear frame morph, nearest mip) -> Simper SVF lowpass -> amp ADSR; the position knob morphs across frames swept by its own envelope and the LFO (the signature wavetable move); filter/LFO/pitch/position at control rate 16; same VoiceT contract (transient window, steal fade)>>
        +prepare / start / renderAdd
        +active() / releasing() / level() / inTransientWindow()
        +beginRelease() / fastRelease() / kill()
    }
    class WavetableSynth {
        <<DeviceTypeId 1 "Wavetable Lab" - the default project's lead: PolyInstrument of WavetableVoice/WavetableShared (pool 16, poly 8); 20 contract descriptors (kWavetableParams)>>
        +paramCount() / paramDescriptor(i)
        +setParamImmediate(dense, plain)
    }
    class FmAlgorithm {
        <<topology POD: modSources[op] = bitmask of ops phase-modulating `op` (masks only reference HIGHER ops so 3->0 compute order always has modulators ready), carriers = bitmask summed to output. kFmAlgorithms = the classic eight (stack, 2-into-1, two stacks, branched, bright bell, mostly-additive, two pairs, organ)>>
        +uint8_t modSources[4]
        +uint8_t carriers
    }
    class FmShared {
        <<POD settings/migrating state body: algorithm, op3 feedback, per-op ratio/level/ADSR arrays, LFO rate/toPitch, velToAmp + velToMod (velocity scales modulator levels = brightness), quality>>
    }
    class FmVoice {
        <<4-op phase modulation (DX lineage): per-op sine at audio-rate with its OWN audio-rate ADSR (FM timbre IS the envelope motion), kModDepth 2pi, op3 one-sample-delayed self-feedback, carrier sum normalized by carrier count; algorithm latched at note start; carrier envelopes gate voice life; LFO/pitch at control rate 16>>
        +prepare / start / renderAdd
        +active() / releasing() / level() / inTransientWindow()
        +beginRelease() / fastRelease() / kill()
    }
    class FmSynth {
        <<DeviceTypeId 2 "FM Four": PolyInstrument of FmVoice/FmShared (pool 16, poly 8); 31 contract descriptors (kFmParams)>>
        +paramCount() / paramDescriptor(i)
        +setParamImmediate(dense, plain)
    }

    DeviceNode <|-- InstrumentNode
    VoiceInterface <|-- InstrumentNode
    InstrumentNode <|-- PolyInstrument~VoiceT_SharedT_StateVersion_PoolVoices_DefaultPolyphony~
    PolyInstrument~VoiceT_SharedT_StateVersion_PoolVoices_DefaultPolyphony~ <|-- SubtractiveSynth
    PolyInstrument~VoiceT_SharedT_StateVersion_PoolVoices_DefaultPolyphony~ <|-- WavetableSynth
    PolyInstrument~VoiceT_SharedT_StateVersion_PoolVoices_DefaultPolyphony~ <|-- FmSynth
    PolyInstrument~VoiceT_SharedT_StateVersion_PoolVoices_DefaultPolyphony~ ..> VoiceAllocator~VoiceT_MaxVoices~
    SubtractiveSynth *-- SubtractiveVoice
    SubtractiveSynth *-- SubtractiveShared
    SubtractiveVoice ..> Oscillator
    SubtractiveVoice ..> SvfFilter
    SubtractiveVoice ..> AdsrEnvelope
    SubtractiveVoice ..> Lfo
    SubtractiveVoice ..> NoiseGen
    WavetableSynth *-- WavetableVoice
    WavetableSynth *-- WavetableShared
    WavetableVoice ..> WavetableBank
    WavetableVoice ..> SvfFilter
    WavetableVoice ..> AdsrEnvelope
    WavetableVoice ..> Lfo
    FmSynth *-- FmVoice
    FmSynth *-- FmShared
    FmVoice ..> FmAlgorithm
    FmVoice ..> AdsrEnvelope
    FmVoice ..> Lfo
    MidiScheduler ..> MidiTrackRun
    PlaybackGraph ..> MidiTrackRun

    %% ---- M4 sequencer/ (MetronomeNode) ----
    class MetronomeNode {
        <<the click (§3.2 bus-routable: Route Cue|Main|Both, Cue folded until unfolded routing): engine-owned, rendered AFTER the graph onto the output bus - never recorded/metered/mixed. Two sine bursts (accented bar 1600Hz / beat 1100Hz) with instant attack + exp decay; beat crossings derive from the SAME TransportSpans + TempoMap conversions the schedulers use, so clicks stay sample-locked through tempo changes and loop wraps; fixed click pool rings across blocks>>
        +prepare(sampleRate)
        +setRoute(r: Route)
        +scheduleSpan(span, map, playing, enabled)
        +render(l, r, numFrames)
        +reset()
    }

    MetronomeNode ..> TransportSpan
    MetronomeNode ..> TempoMap
    AudioEngine *-- MetronomeNode

    %% ---- M2 graph/ (strips + meters) ----
    class TrackStrip {
        <<channel strip AS a DeviceNode (resolver/migration/state uniform): volume (dB->gain at set), constant-power pan (-3dB center, per-channel gain targets), click-free mute; contract keys mixer.volume/pan/mute; gain-domain linear smoothing, current+target migrate (never-jumps); latency 0, configHash 0 (always adoptable); send levels live on SendNodes (M2 f2)>>
        +process(ctx) in-place
        +setParamImmediate(dense, plain)
        +saveState(out) / loadState(in)
        +volumeDb() / pan() / muted()
    }
    class MeterProbe {
        <<post-fader peak+RMS over a ~30Hz window -> one MeterFrame per window (seq, clip flag); pure + hostside-testable, the graph schedule pushes ready frames to the MeterBus>>
        +prepare(uid, sampleRate)
        +sample(l, r, numFrames, out: MeterFrame) bool
    }

    DeviceNode <|-- TrackStrip
    TrackStrip ..> SmoothedValue
    MeterProbe ..> MeterFrame

    %% ---- M2 graph/ (compiled mixer: PlaybackGraph + PDC + migration) ----
    class SendNode {
        <<per-(track, return-bus) post-fader tap AS a DeviceNode; DOCUMENTED semantic deviation: process() ACCUMULATES outputs += inputs * level (graph-internal, never aliased); resolver maps (trackUid, mixer.sendA/B) here so Kotlin keeps addressing sends on the track uid; smoothed level migrates>>
        +process(ctx) accumulate
        +setParamImmediate(0, plain)
        +saveState(out) / loadState(in)
        +busIndex() int
    }
    class DelayCompNode {
        <<fixed integer stereo delay in place (builder-prepared DelayLines); zero delay = transparent pass>>
        +prepare(delaySamples, maxBlock)
        +process(l, r, numFrames)
        +reset()
    }
    class PdcCalculator {
        <<builder-side join balancing (the researched industry rule): every path into a join delayed by maxLatencyIntoJoin - ownLatency; M2 latencies are all zero so no comps insert, but the computation runs so M3 chains / M8 lookahead just work; live-input monitoring bypasses PDC by contract>>
        +beginJoin()
        +addPath(latencySamples) int
        +maxLatency() int
        +compFor(index) int
    }
    class MigrationEntry {
        <<seam-3 pair: newNode + oldNode>>
    }
    class MigrationPlan {
        <<ADOPT ENTRIES ONLY (uid + configHash + rate/maxBlock match); fresh/reset state pre-installed by the builder; executeAdopt [RT at swap] = bounded save->load POD moves through a pre-sized scratch, no allocation>>
        +add(newNode, oldNode, stateBytes)
        +finalize()
        +executeAdopt()
    }
    class ParamResolver {
        <<seam 6: key->dense resolution existing ONLY inside a compiled graph; open-addressed pow2 table built by the compiler, consulted [RT] at apply time; a miss = seam-4 skew (caller counts + skips)>>
        +reserve(paramCount)
        +add(uid, key, node, dense) bool
        +apply(uid, key, plain) bool
    }
    class RenderFacts {
        <<per-block facts fanned into every ProcessContext>>
        +double sampleRate
        +int64_t blockStartSample
        +double blockStartBeat
        +double bpm
        +bool playing
        +bool recording
    }
    class TrackUnit {
        <<one mixer lane (track/return/master): arena buffer slices, strip, optional sendA/B, optional comp, meter>>
    }
    class PlaybackGraph {
        <<the compiled realtime mixer artifact (epoch offer/ack): builder-allocated arena + owned nodes; M2 topology: zeroed track buffer (silence source; instruments write here from M4) -> strip -> post-fader send taps -> [comp] -> master join; returns -> strip -> join; master mix -> MasterStrip -> Main bus (Cue folds on stereo hw). processBlock [RT]: no allocation, meters collect in pendingMeters for the engine to drain. nodeIndex is builder-only (next compile's adoption scan). GRAPH ARTIFACT LIFETIME: replaced unclaimed offers are NEVER eagerly freed (MigrationPlans reference predecessors' nodes); only the acked-front GC rule releases them>>
        +uint64_t epoch
        +uint32_t builtFromEditSeq
        +double sampleRate
        +processBlock(numFrames, facts: RenderFacts)
        +ParamResolver resolver
        +MigrationPlan migration
        +FixedVector~MeterFrame~ pendingMeters
    }

    DeviceNode <|-- SendNode
    SendNode ..> SmoothedValue
    DelayCompNode ..> DelayLine
    MigrationPlan *-- MigrationEntry
    MigrationPlan ..> NodeState
    PlaybackGraph *-- TrackUnit
    PlaybackGraph *-- ParamResolver
    PlaybackGraph *-- MigrationPlan
    PlaybackGraph ..> RenderFacts
    TrackUnit *-- MeterProbe
    TrackUnit ..> TrackStrip
    TrackUnit ..> SendNode
    TrackUnit ..> DelayCompNode
    GraphBuilder ..> PlaybackGraph
    GraphBuilder ..> PdcCalculator
    AudioEngine ..> PlaybackGraph

    %% ---- M1 graph/ (GraphBuilder - the background compile thread) ----
    class GraphBuilder {
        <<owns EngineModel + the compile thread (50ms wait_for cycle): drains ModelDelta bundle inbox (mutex+condvar; single engine-io producer preserves edit order), applies deltas, rebuilds dirty artifacts - TimelineSnapshot (exact-reserve flat stores), TempoMapBase from model tempo deltas (rate-gated, retried) or forced tail consolidation (samples the SAME governing function at boundary beats; equal-tempo merges preserve post-seek discontinuities; skipped while an offer is in flight). All handovers via OfferSlot epochs; only this thread frees retired artifacts (after RT ack; tempo bg pointer republished once predecessor ack proves the claim). Device adopt entries hash their TYPE - a same-uid type swap never migrates one synth's Shared POD into another's layout (per-type state versions make cross-type version equality meaningless). PlaybackGraph compile joins at M2>>
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
        +MainDawScreen(store: ProjectStore, readback: EngineReadback?, modifier: Modifier)
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
        <<first UI consumer of the engine readback path: `meters` remaps EngineReadback's uid-keyed MeterReadings to track ids (+ "master") via makeNodeUid, kept SEPARATE from `state` so ~30Hz ticks never recompose the edit-driven strips; empty when the engine is unavailable>>
        +StateFlow~MixerUiState~ state
        +StateFlow~Map_String_MeterReading~ meters
        +selectTrack(trackId: String)
        +setTrackVolume(trackId: String, volumeDb: Float)
        +setTrackPan(trackId: String, pan: Float)
        +toggleMute(trackId: String)
        +toggleSolo(trackId: String)
        +toggleArm(trackId: String)
        +setSend(trackId: String, sendIndex: Int, level: Float)
        +setMasterVolume(volumeDb: Float)
    }

    MixerStateHolder ..> EngineReadback
    MixerStateHolder ..> MeterReading
    MixerStateHolder ..> WireProtocol

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
