# Mobile DAW Architecture & Living Codebase Map

This document is the **authoritative living map of the codebase**, maintained and kept continuously synchronized with every class, interface, method, and relationship implemented across the architecture (both Native C++ NDK DSP Engine and Kotlin UDF Layer).

```mermaid
classDiagram
    %% ==========================================
    %% INHERITANCE / COMPONENT HIERARCHY
    %% ==========================================
    ComponentActivity <|-- MainActivity
    RoomDatabase <|-- DawDatabase

    %% Native C++ Graph Nodes
    AudioNode <|-- TrackNode
    AudioNode <|-- GroupTrackNode
    AudioNode <|-- ReturnTrackNode
    AudioNode <|-- MasterNode
    AudioNode <|-- DeviceNode

    %% Native C++ Device Hierarchy
    DeviceNode <|-- InstrumentNode
    DeviceNode <|-- EffectNode

    InstrumentNode <|-- SubtractiveSynth
    InstrumentNode <|-- WavetableSynth
    InstrumentNode <|-- FMSynth
    InstrumentNode <|-- DrumRackNode
    InstrumentNode <|-- AdvancedSamplerNode

    EffectNode <|-- ParametricEQNode
    EffectNode <|-- CompressorNode
    EffectNode <|-- ReverbNode
    EffectNode <|-- DelayNode
    EffectNode <|-- LimiterNode
    EffectNode <|-- MeteringNode

    %% Sequencer Engines
    PlaybackEngine <|-- ArrangementEngine
    PlaybackEngine <|-- SessionEngine

    %% Kotlin MIDI Effects
    MidiEffectDevice <|-- RealtimeArpeggiator
    MidiEffectDevice <|-- RealtimeChordDevice
    MidiEffectDevice <|-- RealtimeScaleDevice
    MidiEffectDevice <|-- RealtimeVelocityDevice
    MidiEffectDevice <|-- RealtimeNoteEchoDevice

    %% ==========================================
    %% 1. NATIVE REAL-TIME C++ AUDIO ENGINE (NDK)
    %% ==========================================
    class AudioEngine {
        -AudioGraph audioGraph_
        -TransportEngine transport_
        -AudioBufferPool bufferPool_
        -LockFreeQueue~EngineCommand, 1024~ commandQueue_
        -LockFreeQueue~MeterFrame, 128~ meterQueue_
        +start() bool
        +stop()
        +processAudio(float** outputBuffers, size_t numFrames)
        +postCommand(EngineCommand cmd) bool
        +pollMeters(MeterFrame* dest, size_t maxFrames) size_t
    }

    class AudioNode {
        <<abstract>>
        #string id_
        #NodeType type_
        #bool isEnabled_
        #bool isMuted_
        #bool isSoloed_
        +prepareToPlay(sampleRate: double, maxBlockSize: size_t)*
        +process(ctx: ProcessContext, inBuffers: float**, outBuffers: float**)*
        +releaseResources()*
        +getId() string
        +getType() NodeType
        +setEnabled(enabled: bool)
        +setMuted(muted: bool)
        +setSoloed(soloed: bool)
    }

    class TrackNode {
        -int32_t trackIndex_
        -DeviceChain deviceChain_
        -float volumeDb_
        -float pan_
        -bool isArmed_
        -SmoothedValue~float~ volumeSmoother_
        -SmoothedValue~float~ panSmoother_
        +prepareToPlay(sampleRate: double, maxBlockSize: size_t)
        +process(ctx: ProcessContext, inBuffers: float**, outBuffers: float**)
        +setVolumeDb(volumeDb: float)
        +setPan(pan: float)
        +setArmed(armed: bool)
        +getDeviceChain() DeviceChain
        +getMeterFrame() MeterFrame
    }

    class GroupTrackNode {
        -int32_t groupIndex_
        -array~int32_t, 16~ childTrackIndices_
        -size_t childCount_
        -DeviceChain deviceChain_
        -SmoothedValue~float~ volumeSmoother_
        -SmoothedValue~float~ panSmoother_
        +addChildTrack(trackIndex: int32_t) bool
        +removeChildTrack(trackIndex: int32_t) bool
        +process(ctx: ProcessContext, inBuffers: float**, outBuffers: float**)
    }

    class ReturnTrackNode {
        -int32_t returnIndex_
        -DeviceChain deviceChain_
        -SmoothedValue~float~ volumeSmoother_
        -SmoothedValue~float~ panSmoother_
        +setVolumeDb(volumeDb: float)
        +setPan(pan: float)
        +getDeviceChain() DeviceChain
        +process(ctx: ProcessContext, inBuffers: float**, outBuffers: float**)
    }

    class MasterNode {
        -DeviceChain deviceChain_
        -float volumeDb_
        -float limiterCeilingDb_
        -bool isLimiterEnabled_
        -SmoothedValue~float~ volumeSmoother_
        +setVolumeDb(volumeDb: float)
        +setLimiterCeilingDb(ceilingDb: float)
        +setLimiterEnabled(enabled: bool)
        +getDeviceChain() DeviceChain
        +getMasterMeterFrame() MeterFrame
        +process(ctx: ProcessContext, inBuffers: float**, outBuffers: float**)
    }

    class RoutingMatrix {
        -array~array~SmoothedValue~float~, 8~, 64~ sendSmoothers_
        -array~int, 64~ sidechainSources_
        +prepare(sampleRate: double)
        +setSendLevel(srcTrack: size_t, returnIndex: size_t, level: float)
        +getNextSmoothedSend(srcTrack: size_t, returnIndex: size_t) float
        +setSidechainRoute(destTrack: size_t, srcTrack: int)
        +getSidechainSource(destTrack: size_t) int
    }

    class AudioGraph {
        -array~TrackNode, 64~ tracks_
        -array~ReturnTrackNode, 8~ returnTracks_
        -MasterNode masterNode_
        -RoutingMatrix routingMatrix_
        -AudioBufferPool bufferPool_
        +prepare(sampleRate: double, maxBlockSize: size_t)
        +process(ctx: ProcessContext, outputBuffers: float**)
        +addTrack(id: string) TrackNode
        +removeTrack(trackIndex: size_t) bool
        +getTrack(index: size_t) TrackNode
        +getReturnTrack(index: size_t) ReturnTrackNode
        +getMasterNode() MasterNode
        +getRoutingMatrix() RoutingMatrix
        +collectMeterFrames(dest: MeterFrame*, maxFrames: size_t) size_t
    }

    class DeviceNode {
        <<abstract>>
        +setParameter(paramName: string, value: float)*
        +getParameter(paramName: string) float*
    }

    class DeviceChain {
        -array~DeviceNode, 16~ devices_
        -size_t deviceCount_
        +prepare(sampleRate: double, maxBlockSize: size_t)
        +process(ctx: ProcessContext, inBuffers: float**, outBuffers: float**)
        +addDevice(device: DeviceNode) bool
        +removeDevice(index: size_t) bool
        +getDevice(index: size_t) DeviceNode
        +getDeviceCount() size_t
    }

    class InstrumentNode {
        <<abstract>>
        #int polyphony_
        +noteOn(noteNumber: int, velocity: float)*
        +noteOff(noteNumber: int)*
        +allNotesOff()*
        +setPitchBend(bendSemitones: float)*
        +setModWheel(modWheel: float)*
    }

    class EffectNode {
        <<abstract>>
        #float mix_
        #SmoothedValue~float~ dryWetSmoother_
        +setDryWet(mix: float)
        +getDryWet() float
    }

    class SubtractiveSynth {
        +noteOn(noteNumber: int, velocity: float)
        +noteOff(noteNumber: int)
        +setFilterCutoff(cutoffHz: float)
        +setFilterResonance(resonance: float)
    }

    class WavetableSynth {
        +setTablePosition(pos: float)
        +setWarpMode(mode: int)
        +noteOn(noteNumber: int, velocity: float)
    }

    class FMSynth {
        +setAlgorithm(algorithm: int)
        +setOperatorRatio(op: int, ratio: float)
        +setOperatorLevel(op: int, level: float)
    }

    class DrumRackNode {
        +triggerPad(padIndex: int, velocity: float)
        +setPadSample(padIndex: int, sampleBuffer: float*, numFrames: size_t)
    }

    class AdvancedSamplerNode {
        +loadSample(buffer: float*, numFrames: size_t, sampleRate: double)
        +setLoopRegion(startFrame: size_t, endFrame: size_t)
        +noteOn(noteNumber: int, velocity: float)
    }

    class ParametricEQNode {
        +setBand(index: int, type: int, freqHz: float, q: float, gainDb: float)
    }

    class CompressorNode {
        +setThresholdDb(thresholdDb: float)
        +setRatio(ratio: float)
        +setAttackMs(attackMs: float)
        +setReleaseMs(releaseMs: float)
    }

    class ReverbNode {
        +setRoomSize(size: float)
        +setDamping(damping: float)
        +setPreDelayMs(ms: float)
    }

    class DelayNode {
        +setDelayTimeMs(ms: float)
        +setFeedback(feedback: float)
        +setPingPong(enabled: bool)
    }

    class LimiterNode {
        +setCeilingDb(ceilingDb: float)
        +setLookaheadMs(ms: float)
    }

    class MeteringNode {
        +getMeterFrame() MeterFrame
    }

    class TransportEngine {
        -double currentBeat_
        -double samplePosition_
        -float bpm_
        -int timeSigNum_
        -int timeSigDen_
        -bool isPlaying_
        -bool isLooping_
        +advance(numFrames: size_t, sampleRate: double)
        +seekToBeat(beat: double)
        +setBpm(bpm: float)
        +setLoop(startBeat: double, endBeat: double)
    }

    class PlaybackEngine {
        <<abstract>>
        +evaluate(startBeat: double, endBeat: double, track: TrackNode)*
    }

    class ArrangementEngine {
        +evaluate(startBeat: double, endBeat: double, track: TrackNode)
    }

    class SessionEngine {
        +launchClip(track: int, slot: int)
        +launchScene(sceneIndex: int)
        +evaluate(startBeat: double, endBeat: double, track: TrackNode)
    }

    class AutomationEngine {
        +evaluateBlock(startBeat: double, endBeat: double, numFrames: size_t)
    }

    class ProcessContext {
        +size_t numChannels
        +size_t numFrames
        +double sampleRate
        +double currentBeat
        +double samplePosition
        +bool isPlaying
        +bool isRecording
    }

    class AudioBufferPool {
        +prepare(maxBlockSize: size_t, channelCount: size_t)
        +acquireBuffer() float**
        +releaseBuffer(buffer: float**)
    }

    class ScopedNoDenormals {
        -uint32_t savedFpscr_
        +ScopedNoDenormals()
        +~ScopedNoDenormals()
    }

    class SmoothedValue~T~ {
        +setRampFrames(frames: int)
        +setTarget(target: T)
        +getNext() T
        +getTarget() T
        +reset(initialValue: T)
    }

    class MeterFrame {
        +int32_t trackId
        +float peakL
        +float peakR
        +float rmsL
        +float rmsR
        +float truePeak
        +float gainReductionDb
        +bool isClipping
    }

    class EngineCommand {
        +CommandType type
        +int16_t trackIndex
        +int16_t deviceIndex
        +int16_t paramId
        +int16_t noteNumber
        +float floatValue1
        +float floatValue2
        +int32_t intValue1
        +int32_t intValue2
    }

    class NativeAudioBridge {
        <<singleton>>
        +initEngine(sampleRate: int, bufferSize: int)
        +startEngine()
        +stopEngine()
        +postCommand(cmd: EngineCommand)
        +pollMeterData(dest: FloatArray) int
    }

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
    %% 6. MIDI & MODULATION ENGINES
    %% ==========================================
    class MidiTransformations {
        <<object>>
        +quantize(notes, gridBeat, amount) List~MidiNote~
        +humanize(notes, timingDevBeats, velocityDev) List~MidiNote~
        +strum(notes, delayPerNoteBeats, isUpward) List~MidiNote~
        +chop(notes, divisions) List~MidiNote~
        +scaleConstrain(notes, rootNote, scale) List~MidiNote~
        +transpose(notes, semitones) List~MidiNote~
        +invert(notes) List~MidiNote~
        +reverse(notes) List~MidiNote~
    }

    class MidiGenerators {
        <<object>>
        +generateEuclidean(steps, pulses, pitch, stepBeat, velocity) List~MidiNote~
        +generateChordProgression(rootNote, scale, progressionDegrees, beatsPerChord) List~MidiNote~
        +generateBassline(rootNote, scale, totalBars, density) List~MidiNote~
    }

    class MidiEffectDevice {
        <<interface>>
        +String id
        +String name
        +Boolean isEnabled
        +process(notes: List~MidiNote~, currentBeat: Float) List~MidiNote~
    }

    class RealtimeArpeggiator {
        +ArpStyle style
        +Float rateBeat
        +Int octaveRange
        +Float gate
    }

    class RealtimeChordDevice {
        +List~Int~ intervals
    }

    class RealtimeScaleDevice {
        +Int rootNote
        +MusicalScale scale
    }

    class RealtimeVelocityDevice {
        +Float gain
        +Float randomizeAmount
    }

    class RealtimeNoteEchoDevice {
        +Int repeats
        +Float delayBeats
        +Float decay
        +Int pitchShiftPerRepeat
    }

    class ModulationMatrixEngine {
        +List~ModMatrixRoute~ routes
        +addRoute(source: ModSource, destination: ModDestination, depth: Float)
        +removeRoute(id: String)
        +computeDestinationModulation(destination, sampleRate, lfo1RateHz, lfo2RateHz, lfo3RateHz, velocity, modWheel) Float
    }

    class ModMatrixRoute {
        <<data class>>
        +String id
        +ModSource source
        +ModDestination destination
        +Float depth
        +Boolean isBipolar
        +Boolean isEnabled
    }

    class ModSource {
        <<enumeration>>
        LFO_1
        LFO_2
        LFO_3
        ENV_AMP
        ENV_FILTER
        ENV_MOD
        VELOCITY
        MOD_WHEEL
        KEY_TRACK
        RANDOM_SH
    }

    class ModDestination {
        <<enumeration>>
        OSC1_PITCH
        OSC2_PITCH
        WT_POSITION
        FM_DEPTH
        FILTER_CUTOFF
        FILTER_RESO
        DRIVE
        PAN
        REVERB_SEND
    }

    %% ==========================================
    %% 7. PERSISTENCE & AUDIO EXPORT
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

    class AudioRecorderEngine {
        +Boolean isRecording
        +Float inputRmsLevel
        +Float inputPeakLevel
        +startRecording()
        +stopRecording(destinationWavFile: File?) ShortArray
    }

    class StemExporter {
        <<object>>
        +exportProjectStems(projectState, outputDir, sampleRate, renderBars) List~File~
        +packageStemsToZip(stemFiles, zipOutputFile) File
    }

    class WavWriter {
        <<object>>
        +createWavFile(file: File, pcmData: ShortArray, sampleRate: Int, channels: Int)
    }

    %% ==========================================
    %% 8. RELATIONSHIPS & DEPENDENCY FLOW
    %% ==========================================
    AudioEngine *-- AudioGraph
    AudioEngine *-- TransportEngine
    AudioEngine *-- AudioBufferPool
    AudioEngine ..> EngineCommand
    AudioEngine ..> MeterFrame

    AudioGraph *-- TrackNode
    AudioGraph *-- GroupTrackNode
    AudioGraph *-- ReturnTrackNode
    AudioGraph *-- MasterNode
    AudioGraph *-- RoutingMatrix
    AudioGraph *-- AudioBufferPool

    TrackNode *-- DeviceChain
    GroupTrackNode *-- DeviceChain
    ReturnTrackNode *-- DeviceChain
    MasterNode *-- DeviceChain
    DeviceChain *-- DeviceNode

    NativeAudioBridge ..> AudioEngine

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

    ModulationMatrixEngine *-- ModMatrixRoute
    ModMatrixRoute --> ModSource
    ModMatrixRoute --> ModDestination

    DawDatabase *-- ProjectDao

    AudioRecorderEngine ..> WavWriter
    StemExporter ..> WavWriter
```
