# Mobile DAW Architecture & Living Codebase Map

This document is the **authoritative living map of the codebase**, maintained and kept continuously synchronized with every class, interface, method, and relationship implemented across the architecture (both Native C++ NDK DSP Engine and Kotlin UDF Layer).

**Scope:** this map documents code that exists in the source tree today. The target end-state architecture is specified in [`docs/spec/ARCHITECTURE_BLUEPRINT.md`](spec/ARCHITECTURE_BLUEPRINT.md) (contracts: [`CONTRACTS.md`](spec/CONTRACTS.md); functional specs: [`SPEC_PART1_FUNCTIONAL.md`](spec/SPEC_PART1_FUNCTIONAL.md), [`SPEC_PART2_WORKFLOW.md`](spec/SPEC_PART2_WORKFLOW.md)); classes move into this map when their source lands. The old pre-blueprint C++ skeleton has been fully removed - `app/src/main/cpp/` now contains only new-engine modules (`core/`, `dsp/`) built to the blueprint, not yet wired into the Gradle build (that happens when the engine is ready to link; blueprint M0).

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
        <<facade + RT callback spine: anchor publish -> bounded ring drains -> param-table drain (values retained for post-swap reapply) -> input consume -> render (silence until M2 graph) -> clock publish. Seams open for TransportEngine (M1), PlaybackGraph (M2), instruments (M4)>>
        +start(cfg: OboeDriver.Config) bool
        +stop()
        +jniEvents() EventRing
        +jniParams() ParamMoveTable
        +midiEvents() EventRing
        +midiParams() ParamMoveTable
        +popMeter(out: MeterFrame) bool
        +clock() TransportClockData
        +anchor() TimeAnchor
        +render(outputs, numFrames, input, time)
    }

    RenderSink <|-- AudioEngine
    OboeDriver ..> RenderSink
    OboeDriver *-- InputJitterRing
    AudioEngine *-- OboeDriver
    AudioEngine *-- ParamMoveTable
    AudioEngine ..> EngineMessage
    AudioEngine ..> MeterFrame
    AudioEngine ..> TimeAnchor

    %% ==========================================
    %% 2. APP ENTRY POINT & WORKSPACE SCREENS (EARTH.DESIGN)
    %% ==========================================
    class MainActivity {
        +onCreate(savedInstanceState: Bundle)
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
        -MutableStateFlow~ProjectState~ _state
        +StateFlow~ProjectState~ state
        -ArrayDeque~ProjectState~ undoStack
        -ArrayDeque~ProjectState~ redoStack
        +((ProjectAction, ProjectState)->Unit)? onEngineSync
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
    %% 7. RELATIONSHIPS & DEPENDENCY FLOW
    %% ==========================================

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
