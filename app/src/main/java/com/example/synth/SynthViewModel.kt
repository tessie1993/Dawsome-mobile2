package com.example.synth

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ProjectSerializer
import com.example.data.local.DawDatabase
import com.example.data.local.ProjectEntity
import com.example.data.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class DawTab(val title: String, val shortTitle: String, val shortcut: String) {
    SESSION("Session Clips", "SESSION", "Tab"),
    ARRANGER("Arranger Timeline", "ARRANGER", "Arr"),
    SYNTH("Instruments & Synths", "SYNTH", "Dev"),
    SAMPLER("Ableton Simpler", "SAMPLER", "Smp"),
    PIANO_ROLL("MIDI Editor", "CLIP", "Midi"),
    DRUMS("Drum Rack", "DRUMS", "Drm"),
    MIXER("Mixer & Master FX", "MIXER", "Mix")
}

class SynthViewModel(application: Application) : AndroidViewModel(application) {
    private val _engine = SynthEngine()
    val engine: SynthEngine get() = _engine

    // Room Database Repository
    private val database = DawDatabase.getDatabase(application)
    private val projectRepository = ProjectRepository(database.projectDao())

    val allSavedProjects: StateFlow<List<ProjectEntity>> = projectRepository.allProjects.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _currentProjectId = MutableStateFlow<Long?>(null)
    val currentProjectId: StateFlow<Long?> = _currentProjectId.asStateFlow()

    private val _currentProjectName = MutableStateFlow("Synthwave Sunset")
    val currentProjectName: StateFlow<String> = _currentProjectName.asStateFlow()

    private val _currentGenre = MutableStateFlow("Retrowave 80s")
    val currentGenre: StateFlow<String> = _currentGenre.asStateFlow()

    private val _isSaveDialogOpen = MutableStateFlow(false)
    val isSaveDialogOpen: StateFlow<Boolean> = _isSaveDialogOpen.asStateFlow()

    private val _isBrowserDrawerOpen = MutableStateFlow(false)
    val isBrowserDrawerOpen: StateFlow<Boolean> = _isBrowserDrawerOpen.asStateFlow()

    private val _statusToast = MutableStateFlow<String?>(null)
    val statusToast: StateFlow<String?> = _statusToast.asStateFlow()

    // Navigation Tab (Default to Ableton SESSION view)
    private val _currentTab = MutableStateFlow(DawTab.SESSION)
    val currentTab: StateFlow<DawTab> = _currentTab.asStateFlow()

    // Visual State
    val isSounding = _engine.isSounding
    val activeNotes = _engine.currentActiveNotes

    // Presets
    private val _selectedPresetIndex = MutableStateFlow(0)
    val selectedPresetIndex: StateFlow<Int> = _selectedPresetIndex.asStateFlow()

    // --- Master Transport & Sequencer State ---
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _bpm = MutableStateFlow(120f)
    val bpm: StateFlow<Float> = _bpm.asStateFlow()

    private val _swing = MutableStateFlow(0f) // 0% to 75% swing
    val swing: StateFlow<Float> = _swing.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0f) // in beats (0 to 32)
    val playbackPosition: StateFlow<Float> = _playbackPosition.asStateFlow()

    private val _currentStep = MutableStateFlow(0) // 0 to 15 (16th notes per bar)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _isMetronomeOn = MutableStateFlow(false)
    val isMetronomeOn: StateFlow<Boolean> = _isMetronomeOn.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordedWavFile = MutableStateFlow<File?>(null)
    val recordedWavFile: StateFlow<File?> = _recordedWavFile.asStateFlow()

    // --- Scale & Keyboard State ---
    private val _rootNote = MutableStateFlow(0) // 0 = C, 1 = C#, ... 11 = B
    val rootNote: StateFlow<Int> = _rootNote.asStateFlow()

    private val _currentScale = MutableStateFlow(MusicalScale.PENTATONIC_MINOR)
    val currentScale: StateFlow<MusicalScale> = _currentScale.asStateFlow()

    private val _keyboardOctave = MutableStateFlow(4) // C4 default
    val keyboardOctave: StateFlow<Int> = _keyboardOctave.asStateFlow()

    // --- Patterns & Project State ---
    private val _leadNotes = MutableStateFlow<List<MidiNote>>(ProjectSong.DEMO_PROJECTS[0].leadNotes)
    val leadNotes: StateFlow<List<MidiNote>> = _leadNotes.asStateFlow()

    private val _bassNotes = MutableStateFlow<List<MidiNote>>(ProjectSong.DEMO_PROJECTS[0].bassNotes)
    val bassNotes: StateFlow<List<MidiNote>> = _bassNotes.asStateFlow()

    private val _drumGrid = MutableStateFlow<Map<DrumType, List<Float>>>(ProjectSong.DEMO_PROJECTS[0].drumGrid)
    val drumGrid: StateFlow<Map<DrumType, List<Float>>> = _drumGrid.asStateFlow()

    private val _selectedDrumType = MutableStateFlow(DrumType.KICK)
    val selectedDrumType: StateFlow<DrumType> = _selectedDrumType.asStateFlow()

    private val _currentProjectIndex = MutableStateFlow(0)
    val currentProjectIndex: StateFlow<Int> = _currentProjectIndex.asStateFlow()

    // Metering State Flow (polled at 60fps for UI)
    private val _vuSynth = MutableStateFlow(0f)
    val vuSynth: StateFlow<Float> = _vuSynth.asStateFlow()

    private val _vuBass = MutableStateFlow(0f)
    val vuBass: StateFlow<Float> = _vuBass.asStateFlow()

    private val _vuDrum = MutableStateFlow(0f)
    val vuDrum: StateFlow<Float> = _vuDrum.asStateFlow()

    private val _vuMaster = MutableStateFlow(0f)
    val vuMaster: StateFlow<Float> = _vuMaster.asStateFlow()

    // Effects Rack State
    private val _rackModules = MutableStateFlow<List<AudioEffectModule>>(_engine.effectsRack.getModules())
    val rackModules: StateFlow<List<AudioEffectModule>> = _rackModules.asStateFlow()

    private val _selectedRackPreset = MutableStateFlow("Default Master Chain")
    val selectedRackPreset: StateFlow<String> = _selectedRackPreset.asStateFlow()

    // Track Automation Lanes State
    private val _leadAutomation = MutableStateFlow<Map<AutomationParameter, AutomationLane>>(
        AutomationParameter.values().associateWith { param ->
            if (param == AutomationParameter.FILTER_CUTOFF) {
                // Demo default cutoff sweep for Lead
                AutomationLane(
                    parameter = param,
                    isEnabled = true,
                    points = listOf(
                        AutomationPoint(0.0f, 0.2f),
                        AutomationPoint(4.0f, 0.75f),
                        AutomationPoint(8.0f, 0.35f),
                        AutomationPoint(12.0f, 0.9f),
                        AutomationPoint(16.0f, 0.2f)
                    )
                )
            } else {
                AutomationLane.defaultLane(param)
            }
        }
    )
    val leadAutomation: StateFlow<Map<AutomationParameter, AutomationLane>> = _leadAutomation.asStateFlow()

    private val _bassAutomation = MutableStateFlow<Map<AutomationParameter, AutomationLane>>(
        AutomationParameter.values().associateWith { param ->
            if (param == AutomationParameter.FILTER_CUTOFF) {
                AutomationLane(
                    parameter = param,
                    isEnabled = true,
                    points = listOf(
                        AutomationPoint(0.0f, 0.15f),
                        AutomationPoint(8.0f, 0.6f),
                        AutomationPoint(16.0f, 0.15f)
                    )
                )
            } else {
                AutomationLane.defaultLane(param)
            }
        }
    )
    val bassAutomation: StateFlow<Map<AutomationParameter, AutomationLane>> = _bassAutomation.asStateFlow()

    private val _selectedAutoParam = MutableStateFlow(AutomationParameter.FILTER_CUTOFF)
    val selectedAutoParam: StateFlow<AutomationParameter> = _selectedAutoParam.asStateFlow()

    private val _isAutomationLaneVisible = MutableStateFlow(true)
    val isAutomationLaneVisible: StateFlow<Boolean> = _isAutomationLaneVisible.asStateFlow()

    private val _liveAutomatedValues = MutableStateFlow<Map<String, Float>>(emptyMap())
    val liveAutomatedValues: StateFlow<Map<String, Float>> = _liveAutomatedValues.asStateFlow()

    // Real-Time Automation Recording (Overdub / Live Write)
    private val _isAutomationRecordArmed = MutableStateFlow(false)
    val isAutomationRecordArmed: StateFlow<Boolean> = _isAutomationRecordArmed.asStateFlow()

    // ==========================================
    // MULTI-INSTRUMENT & ABLETON SIMPLER ENGINES
    // ==========================================
    private val _activeInstrument = MutableStateFlow(InstrumentType.ANALOG_SUB)
    val activeInstrument: StateFlow<InstrumentType> = _activeInstrument.asStateFlow()

    // Wavetable Engine Parameters
    private val _wavetableBank = MutableStateFlow(WavetableBank.MODERN_ANALOG)
    val wavetableBank: StateFlow<WavetableBank> = _wavetableBank.asStateFlow()

    private val _wavetablePosition = MutableStateFlow(0.35f)
    val wavetablePosition: StateFlow<Float> = _wavetablePosition.asStateFlow()

    private val _wavetableWarpMode = MutableStateFlow(WavetableWarpMode.PWM)
    val wavetableWarpMode: StateFlow<WavetableWarpMode> = _wavetableWarpMode.asStateFlow()

    private val _wavetableWarpAmount = MutableStateFlow(0.25f)
    val wavetableWarpAmount: StateFlow<Float> = _wavetableWarpAmount.asStateFlow()

    private val _unisonVoices = MutableStateFlow(4)
    val unisonVoices: StateFlow<Int> = _unisonVoices.asStateFlow()

    private val _unisonDetune = MutableStateFlow(0.25f)
    val unisonDetune: StateFlow<Float> = _unisonDetune.asStateFlow()

    // 4-Operator FM Synth Parameters
    private val _fmAlgorithm = MutableStateFlow(FmAlgorithm.CASCADE_STACK)
    val fmAlgorithm: StateFlow<FmAlgorithm> = _fmAlgorithm.asStateFlow()

    // Ableton Simpler / Sampler Parameters
    private val _samplerPresetIndex = MutableStateFlow(0)
    val samplerPresetIndex: StateFlow<Int> = _samplerPresetIndex.asStateFlow()

    private val _samplerMode = MutableStateFlow(SamplerPlaybackMode.CLASSIC)
    val samplerMode: StateFlow<SamplerPlaybackMode> = _samplerMode.asStateFlow()

    private val _samplerStartPoint = MutableStateFlow(0.0f)
    val samplerStartPoint: StateFlow<Float> = _samplerStartPoint.asStateFlow()

    private val _samplerEndPoint = MutableStateFlow(1.0f)
    val samplerEndPoint: StateFlow<Float> = _samplerEndPoint.asStateFlow()

    private val _samplerLoopStart = MutableStateFlow(0.2f)
    val samplerLoopStart: StateFlow<Float> = _samplerLoopStart.asStateFlow()

    private val _samplerLoopEnd = MutableStateFlow(0.8f)
    val samplerLoopEnd: StateFlow<Float> = _samplerLoopEnd.asStateFlow()

    private val _isSamplerLoopEnabled = MutableStateFlow(true)
    val isSamplerLoopEnabled: StateFlow<Boolean> = _isSamplerLoopEnabled.asStateFlow()

    private val _isSamplerReversed = MutableStateFlow(false)
    val isSamplerReversed: StateFlow<Boolean> = _isSamplerReversed.asStateFlow()

    private val _samplerTranspose = MutableStateFlow(0)
    val samplerTranspose: StateFlow<Int> = _samplerTranspose.asStateFlow()

    private val _activeSamplerSlice = MutableStateFlow(0)
    val activeSamplerSlice: StateFlow<Int> = _activeSamplerSlice.asStateFlow()

    // Electric Piano & String Pad Parameters
    private val _rhodesTineDecay = MutableStateFlow(1.8f)
    val rhodesTineDecay: StateFlow<Float> = _rhodesTineDecay.asStateFlow()

    private val _rhodesTremoloRate = MutableStateFlow(4.5f)
    val rhodesTremoloRate: StateFlow<Float> = _rhodesTremoloRate.asStateFlow()

    private val _rhodesTremoloDepth = MutableStateFlow(0.4f)
    val rhodesTremoloDepth: StateFlow<Float> = _rhodesTremoloDepth.asStateFlow()

    private val _rhodesDrive = MutableStateFlow(0.2f)
    val rhodesDrive: StateFlow<Float> = _rhodesDrive.asStateFlow()

    private val _stringPadChorus = MutableStateFlow(0.65f)
    val stringPadChorus: StateFlow<Float> = _stringPadChorus.asStateFlow()

    private val _stringPadSpeed = MutableStateFlow(0.8f)
    val stringPadSpeed: StateFlow<Float> = _stringPadSpeed.asStateFlow()

    private val _stringPadOctave = MutableStateFlow(true)
    val stringPadOctave: StateFlow<Boolean> = _stringPadOctave.asStateFlow()

    // Internal Sequencing Jobs
    private var transportJob: Job? = null
    private val activePlayingLead = mutableMapOf<String, Int>()
    private val activePlayingBass = mutableMapOf<String, Int>()
    private var lastTapTimeMs = 0L

    // Recording Player
    private var mediaPlayer: MediaPlayer? = null
    private val _isAudioPlaying = MutableStateFlow(false)
    val isAudioPlaying: StateFlow<Boolean> = _isAudioPlaying.asStateFlow()

    // Session View Clips and Scenes (Ableton Live Session Matrix)
    private val _scenes = MutableStateFlow<List<SessionScene>>(createInitialScenes())
    val scenes: StateFlow<List<SessionScene>> = _scenes.asStateFlow()

    private val _activeSceneIndex = MutableStateFlow<Int?>(null)
    val activeSceneIndex: StateFlow<Int?> = _activeSceneIndex.asStateFlow()

    private val _activePlayingClips = MutableStateFlow<Map<SessionTrackType, Int?>>(
        mapOf(
            SessionTrackType.LEAD to 0,
            SessionTrackType.BASS to 0,
            SessionTrackType.DRUMS to 0
        )
    )
    val activePlayingClips: StateFlow<Map<SessionTrackType, Int?>> = _activePlayingClips.asStateFlow()

    // ==========================================
    // ABLETON ARRANGEMENT VIEW & TRACK GROUPS
    // ==========================================
    private val _trackGroups = MutableStateFlow<List<TrackGroup>>(createInitialTrackGroups())
    val trackGroups: StateFlow<List<TrackGroup>> = _trackGroups.asStateFlow()

    private val _arrangementTracks = MutableStateFlow<List<ArrangementTrack>>(createInitialArrangementTracks())
    val arrangementTracks: StateFlow<List<ArrangementTrack>> = _arrangementTracks.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _loopStartBar = MutableStateFlow(0f)
    val loopStartBar: StateFlow<Float> = _loopStartBar.asStateFlow()

    private val _loopEndBar = MutableStateFlow(8f)
    val loopEndBar: StateFlow<Float> = _loopEndBar.asStateFlow()

    private val _isLoopEnabled = MutableStateFlow(true)
    val isLoopEnabled: StateFlow<Boolean> = _isLoopEnabled.asStateFlow()

    private val _zoomLevel = MutableStateFlow(1.0f) // 0.5x, 1x, 2x, 4x
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()

    private val _gridSnapBars = MutableStateFlow(1.0f) // 1 bar, 0.5 bar, 0.25 bar
    val gridSnapBars: StateFlow<Float> = _gridSnapBars.asStateFlow()

    private val _isAutomationDrawMode = MutableStateFlow(false)
    val isAutomationDrawMode: StateFlow<Boolean> = _isAutomationDrawMode.asStateFlow()

    private val _isFollowPlayhead = MutableStateFlow(true)
    val isFollowPlayhead: StateFlow<Boolean> = _isFollowPlayhead.asStateFlow()

    // Return & Master Routing
    private val _returnVolumeA = MutableStateFlow(0.85f)
    val returnVolumeA: StateFlow<Float> = _returnVolumeA.asStateFlow()

    private val _returnVolumeB = MutableStateFlow(0.85f)
    val returnVolumeB: StateFlow<Float> = _returnVolumeB.asStateFlow()

    // ==========================================
    // ABLETON BROWSER & SAMPLE AUDITION
    // ==========================================
    private val _selectedBrowserCategory = MutableStateFlow(BrowserCategory.SOUNDS)
    val selectedBrowserCategory: StateFlow<BrowserCategory> = _selectedBrowserCategory.asStateFlow()

    private val _browserSearchQuery = MutableStateFlow("")
    val browserSearchQuery: StateFlow<String> = _browserSearchQuery.asStateFlow()

    private val _browserSamples = MutableStateFlow<List<BrowserSampleItem>>(createInitialBrowserSamples())
    val browserSamples: StateFlow<List<BrowserSampleItem>> = _browserSamples.asStateFlow()

    private val _auditioningSampleId = MutableStateFlow<String?>(null)
    val auditioningSampleId: StateFlow<String?> = _auditioningSampleId.asStateFlow()

    // ==========================================
    // ABLETON MACRO FX RACK & LFO DEVICE
    // ==========================================
    private val _macroRack = MutableStateFlow(MacroRack())
    val macroRack: StateFlow<MacroRack> = _macroRack.asStateFlow()

    private val _lfoDevice = MutableStateFlow(LfoDevice())
    val lfoDevice: StateFlow<LfoDevice> = _lfoDevice.asStateFlow()

    init {
        // Apply default demo project patch
        loadProject(0)

        // Seed Room Database with demo projects if empty
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = projectRepository.getProjectCount()
                if (count == 0) {
                    ProjectSong.DEMO_PROJECTS.forEachIndexed { idx, demo ->
                        val serialized = ProjectSerializer.serializeStateToJson(
                            name = demo.name,
                            genre = demo.genre,
                            bpm = demo.bpm,
                            swing = 0f,
                            rootNote = 0,
                            scale = MusicalScale.NATURAL_MINOR,
                            keyboardOctave = 4,
                            patch = demo.patch,
                            leadNotes = demo.leadNotes,
                            bassNotes = demo.bassNotes,
                            drumGrid = demo.drumGrid,
                            leadAutomation = _leadAutomation.value,
                            bassAutomation = _bassAutomation.value,
                            synthVolume = 0.85f, synthPan = 0f, synthMute = false,
                            bassVolume = 0.85f, bassPan = 0f, bassMute = false,
                            drumVolume = 0.9f, drumPan = 0f, drumMute = false,
                            masterVolume = 0.85f,
                            rackModules = _rackModules.value,
                            scenes = _scenes.value
                        )
                        val insertedId = projectRepository.insertProject(
                            ProjectEntity(
                                name = demo.name,
                                genre = demo.genre,
                                bpm = demo.bpm,
                                keyRoot = 0,
                                scaleName = MusicalScale.NATURAL_MINOR.displayName,
                                lastModified = System.currentTimeMillis() - (idx * 60000),
                                projectDataJson = serialized
                            )
                        )
                        if (idx == 0) {
                            _currentProjectId.value = insertedId
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Launch VU Metering collector loop
        viewModelScope.launch {
            while (isActive) {
                _vuSynth.value = _engine.vuSynthLevel
                _vuBass.value = _engine.vuBassLevel
                _vuDrum.value = _engine.vuDrumLevel
                _vuMaster.value = _engine.vuMasterLevel
                delay(33) // ~30fps UI update
            }
        }
    }

    private fun createInitialScenes(): List<SessionScene> {
        val p1 = ProjectSong.DEMO_PROJECTS[0]
        val p2 = ProjectSong.DEMO_PROJECTS[1]
        val p3 = ProjectSong.DEMO_PROJECTS[2]

        return listOf(
            SessionScene(
                name = "1 Intro Space",
                bpm = 118f,
                clips = mapOf(
                    SessionTrackType.LEAD to SessionClip(name = "Sunset Arp A", trackType = SessionTrackType.LEAD, leadNotes = p1.leadNotes.take(4)),
                    SessionTrackType.BASS to SessionClip(name = "Rolling Bass A", trackType = SessionTrackType.BASS, bassNotes = p1.bassNotes.take(4)),
                    SessionTrackType.DRUMS to SessionClip(name = "Kick & Hat", trackType = SessionTrackType.DRUMS, drumGrid = p1.drumGrid)
                )
            ),
            SessionScene(
                name = "2 Main Hook",
                bpm = 120f,
                clips = mapOf(
                    SessionTrackType.LEAD to SessionClip(name = "Sunset Melody", trackType = SessionTrackType.LEAD, leadNotes = p1.leadNotes),
                    SessionTrackType.BASS to SessionClip(name = "Drive Bass", trackType = SessionTrackType.BASS, bassNotes = p1.bassNotes),
                    SessionTrackType.DRUMS to SessionClip(name = "Full Groove", trackType = SessionTrackType.DRUMS, drumGrid = p1.drumGrid)
                )
            ),
            SessionScene(
                name = "3 Acid Breakdown",
                bpm = 132f,
                clips = mapOf(
                    SessionTrackType.LEAD to SessionClip(name = "Acid 303 Riff", trackType = SessionTrackType.LEAD, leadNotes = p2.leadNotes),
                    SessionTrackType.BASS to SessionClip(name = "Sub Pulse", trackType = SessionTrackType.BASS, bassNotes = p2.bassNotes),
                    SessionTrackType.DRUMS to SessionClip(name = "Industrial Beat", trackType = SessionTrackType.DRUMS, drumGrid = p2.drumGrid)
                )
            ),
            SessionScene(
                name = "4 Deep Outro",
                bpm = 124f,
                clips = mapOf(
                    SessionTrackType.LEAD to SessionClip(name = "Deep Chords", trackType = SessionTrackType.LEAD, leadNotes = p3.leadNotes),
                    SessionTrackType.BASS to SessionClip(name = "Sub Groove", trackType = SessionTrackType.BASS, bassNotes = p3.bassNotes),
                    SessionTrackType.DRUMS to SessionClip(name = "House Beat", trackType = SessionTrackType.DRUMS, drumGrid = p3.drumGrid)
                )
            )
        )
    }

    private fun createInitialTrackGroups(): List<TrackGroup> {
        return listOf(
            TrackGroup(
                id = "group_synths",
                name = "SYNTH GROUP",
                colorHex = 0xFFB28DFF,
                isFolded = false,
                volume = 0.9f,
                trackIds = listOf("track_lead", "track_bass")
            ),
            TrackGroup(
                id = "group_drums",
                name = "DRUM BUS",
                colorHex = 0xFFFED142,
                isFolded = false,
                volume = 0.95f,
                trackIds = listOf("track_drums")
            )
        )
    }

    private fun createInitialArrangementTracks(): List<ArrangementTrack> {
        val p1 = ProjectSong.DEMO_PROJECTS[0]
        val p2 = ProjectSong.DEMO_PROJECTS[1]
        val p3 = ProjectSong.DEMO_PROJECTS[2]

        val leadClips = listOf(
            ArrangementClip(
                name = "Intro Space",
                trackId = "track_lead",
                startBar = 0f,
                lengthBars = 4f,
                colorHex = 0xFFFF764D,
                leadNotes = p1.leadNotes.take(4)
            ),
            ArrangementClip(
                name = "Main Hook",
                trackId = "track_lead",
                startBar = 4f,
                lengthBars = 4f,
                colorHex = 0xFFFF764D,
                leadNotes = p1.leadNotes
            ),
            ArrangementClip(
                name = "Acid 303 Riff",
                trackId = "track_lead",
                startBar = 8f,
                lengthBars = 4f,
                colorHex = 0xFFFF5722,
                leadNotes = p2.leadNotes
            ),
            ArrangementClip(
                name = "Deep Chords",
                trackId = "track_lead",
                startBar = 12f,
                lengthBars = 4f,
                colorHex = 0xFFFF8A65,
                leadNotes = p3.leadNotes
            )
        )

        val bassClips = listOf(
            ArrangementClip(
                name = "Sub Pulse",
                trackId = "track_bass",
                startBar = 0f,
                lengthBars = 4f,
                colorHex = 0xFF38A3FF,
                bassNotes = p1.bassNotes
            ),
            ArrangementClip(
                name = "Drive Bass",
                trackId = "track_bass",
                startBar = 4f,
                lengthBars = 4f,
                colorHex = 0xFF38A3FF,
                bassNotes = p1.bassNotes
            ),
            ArrangementClip(
                name = "Acid Bassline",
                trackId = "track_bass",
                startBar = 8f,
                lengthBars = 4f,
                colorHex = 0xFF2196F3,
                bassNotes = p2.bassNotes
            ),
            ArrangementClip(
                name = "Low House Sub",
                trackId = "track_bass",
                startBar = 12f,
                lengthBars = 4f,
                colorHex = 0xFF64B5F6,
                bassNotes = p3.bassNotes
            )
        )

        val drumClips = listOf(
            ArrangementClip(
                name = "Beat Intro",
                trackId = "track_drums",
                startBar = 0f,
                lengthBars = 4f,
                colorHex = 0xFFFED142,
                drumGrid = p1.drumGrid
            ),
            ArrangementClip(
                name = "Full Groove",
                trackId = "track_drums",
                startBar = 4f,
                lengthBars = 4f,
                colorHex = 0xFFFED142,
                drumGrid = p1.drumGrid
            ),
            ArrangementClip(
                name = "Industrial 909",
                trackId = "track_drums",
                startBar = 8f,
                lengthBars = 4f,
                colorHex = 0xFFFFC107,
                drumGrid = p2.drumGrid
            ),
            ArrangementClip(
                name = "Deep House 4x4",
                trackId = "track_drums",
                startBar = 12f,
                lengthBars = 4f,
                colorHex = 0xFFFFE082,
                drumGrid = p3.drumGrid
            )
        )

        return listOf(
            ArrangementTrack(
                id = "track_lead",
                name = "1 Lead Synth",
                trackType = SessionTrackType.LEAD,
                groupId = "group_synths",
                colorHex = 0xFFFF764D,
                volume = 0.85f,
                pan = 0f,
                sendA = 0.35f,
                sendB = 0.25f,
                clips = leadClips,
                automationLanes = _leadAutomation.value
            ),
            ArrangementTrack(
                id = "track_bass",
                name = "2 Bassline",
                trackType = SessionTrackType.BASS,
                groupId = "group_synths",
                colorHex = 0xFF38A3FF,
                volume = 0.85f,
                pan = 0f,
                sendA = 0.15f,
                sendB = 0.10f,
                clips = bassClips,
                automationLanes = _bassAutomation.value
            ),
            ArrangementTrack(
                id = "track_drums",
                name = "3 Drum Machine",
                trackType = SessionTrackType.DRUMS,
                groupId = "group_drums",
                colorHex = 0xFFFED142,
                volume = 0.90f,
                pan = 0f,
                sendA = 0.20f,
                sendB = 0.15f,
                clips = drumClips
            )
        )
    }

    private fun createInitialBrowserSamples(): List<BrowserSampleItem> {
        return listOf(
            // SOUNDS / PRESETS
            BrowserSampleItem(name = "Cosmic 80s Lead", category = BrowserCategory.SOUNDS, subCategory = "Synth Lead", bpm = 120f, musicalKey = "C Minor", previewType = SamplePreviewType.SYNTH_CHORD, description = "Warm dual-oscillator analog synth with stereo chorus", trackTypeTarget = SessionTrackType.LEAD),
            BrowserSampleItem(name = "Cyberpunk Acid Saw", category = BrowserCategory.SOUNDS, subCategory = "Bass & Lead", bpm = 132f, musicalKey = "C Minor", previewType = SamplePreviewType.SYNTH_CHORD, description = "Resonant 303 acid saw with screaming envelope", trackTypeTarget = SessionTrackType.LEAD),
            BrowserSampleItem(name = "Fat Moog Sub", category = BrowserCategory.SOUNDS, subCategory = "Sub Bass", bpm = 120f, musicalKey = "F Minor", previewType = SamplePreviewType.BASS_SLAP, description = "Heavy low-end mono bass with rich harmonics", trackTypeTarget = SessionTrackType.BASS),
            BrowserSampleItem(name = "FM Glass EP", category = BrowserCategory.SOUNDS, subCategory = "Keys", bpm = 118f, musicalKey = "A Minor", previewType = SamplePreviewType.SYNTH_CHORD, description = "Shimmering frequency modulated electric piano", trackTypeTarget = SessionTrackType.LEAD),
            BrowserSampleItem(name = "Lush Juno Pad", category = BrowserCategory.SOUNDS, subCategory = "Pads", bpm = 124f, musicalKey = "G Minor", previewType = SamplePreviewType.SYNTH_CHORD, description = "Silky analog pad with slow attack and chorus", trackTypeTarget = SessionTrackType.LEAD),

            // DRUMS
            BrowserSampleItem(name = "808 Deep Sub Kick", category = BrowserCategory.DRUMS, subCategory = "Kicks", bpm = null, musicalKey = "C1", previewType = SamplePreviewType.DRUM_HIT, description = "Punchy 808 kick with tuned sub tail", trackTypeTarget = SessionTrackType.DRUMS),
            BrowserSampleItem(name = "909 Vintage Punch Kick", category = BrowserCategory.DRUMS, subCategory = "Kicks", bpm = null, musicalKey = null, previewType = SamplePreviewType.DRUM_HIT, description = "Classic house and techno 909 punch kick", trackTypeTarget = SessionTrackType.DRUMS),
            BrowserSampleItem(name = "Lofi Tight Snare", category = BrowserCategory.DRUMS, subCategory = "Snares", bpm = null, musicalKey = null, previewType = SamplePreviewType.DRUM_HIT, description = "Warm textured snare with snappy transient", trackTypeTarget = SessionTrackType.DRUMS),
            BrowserSampleItem(name = "Studio Analog Clap", category = BrowserCategory.DRUMS, subCategory = "Claps", bpm = null, musicalKey = null, previewType = SamplePreviewType.DRUM_HIT, description = "Layered analog handclap with subtle room reverb", trackTypeTarget = SessionTrackType.DRUMS),
            BrowserSampleItem(name = "Crisp 808 Closed Hat", category = BrowserCategory.DRUMS, subCategory = "Hi-Hats", bpm = null, musicalKey = null, previewType = SamplePreviewType.DRUM_HIT, description = "Sizzling high frequency closed hi-hat", trackTypeTarget = SessionTrackType.DRUMS),
            BrowserSampleItem(name = "Open House Sizzle Hat", category = BrowserCategory.DRUMS, subCategory = "Hi-Hats", bpm = null, musicalKey = null, previewType = SamplePreviewType.DRUM_HIT, description = "Sustained open hi-hat for upbeat grooves", trackTypeTarget = SessionTrackType.DRUMS),
            BrowserSampleItem(name = "Cyberpunk Low Tom", category = BrowserCategory.DRUMS, subCategory = "Toms & Perc", bpm = null, musicalKey = "G1", previewType = SamplePreviewType.DRUM_HIT, description = "Heavy electronic pitch-dropping tom", trackTypeTarget = SessionTrackType.DRUMS),

            // INSTRUMENTS
            BrowserSampleItem(name = "Analog Lead Monosynth", category = BrowserCategory.INSTRUMENTS, subCategory = "Synthesizers", bpm = null, musicalKey = null, previewType = SamplePreviewType.SYNTH_CHORD, description = "Full subtractive dual-VCO synth with FM & Ring Mod", trackTypeTarget = SessionTrackType.LEAD),
            BrowserSampleItem(name = "Acid 303 Bassline Unit", category = BrowserCategory.INSTRUMENTS, subCategory = "Bass", bpm = null, musicalKey = null, previewType = SamplePreviewType.BASS_SLAP, description = "Aggressive diode ladder filter bass synthesizer", trackTypeTarget = SessionTrackType.BASS),
            BrowserSampleItem(name = "16-Step Drum Machine Rack", category = BrowserCategory.INSTRUMENTS, subCategory = "Drum Machines", bpm = null, musicalKey = null, previewType = SamplePreviewType.DRUM_LOOP, description = "6-voice dynamic drum engine with individual gains", trackTypeTarget = SessionTrackType.DRUMS),

            // AUDIO FX
            BrowserSampleItem(name = "Ableton Chamber Reverb", category = BrowserCategory.AUDIO_FX, subCategory = "Space & Reverb", bpm = null, musicalKey = null, previewType = SamplePreviewType.FX_SWEEP, description = "High-density algorithmic stereo reverberator"),
            BrowserSampleItem(name = "Ping-Pong Stereo Delay", category = BrowserCategory.AUDIO_FX, subCategory = "Echo & Delay", bpm = null, musicalKey = null, previewType = SamplePreviewType.FX_SWEEP, description = "Tempo-synced cross-feedback delay unit"),
            BrowserSampleItem(name = "Tube Warmth Distortion", category = BrowserCategory.AUDIO_FX, subCategory = "Drive & Distortion", bpm = null, musicalKey = null, previewType = SamplePreviewType.FX_SWEEP, description = "Asymmetric soft-clipping analog saturation"),
            BrowserSampleItem(name = "Dimension Multi-Chorus", category = BrowserCategory.AUDIO_FX, subCategory = "Modulation", bpm = null, musicalKey = null, previewType = SamplePreviewType.FX_SWEEP, description = "4-voice BBD stereo chorus widener"),
            BrowserSampleItem(name = "Glue Master Compressor", category = BrowserCategory.AUDIO_FX, subCategory = "Dynamics", bpm = null, musicalKey = null, previewType = SamplePreviewType.FX_SWEEP, description = "VCA bus compressor with sidechain highpass"),

            // MIDI FX
            BrowserSampleItem(name = "Ableton Arpeggiator", category = BrowserCategory.MIDI_FX, subCategory = "Arp & Patterns", bpm = null, musicalKey = null, previewType = SamplePreviewType.SYNTH_CHORD, description = "Multi-directional melodic arpeggiation engine"),
            BrowserSampleItem(name = "Chord & Scale Memory", category = BrowserCategory.MIDI_FX, subCategory = "Scale Tools", bpm = null, musicalKey = null, previewType = SamplePreviewType.SYNTH_CHORD, description = "Auto-harmonizer and key quantizer"),

            // SAMPLES & LOOPS
            BrowserSampleItem(name = "Synthwave 80s Sunset Loop", category = BrowserCategory.SAMPLES_LOOPS, subCategory = "Synth Loops", bpm = 118f, musicalKey = "C Minor", previewType = SamplePreviewType.MELODIC_LOOP, description = "Full 4-bar retrowave melody with shimmering arpeggio", trackTypeTarget = SessionTrackType.LEAD),
            BrowserSampleItem(name = "Acid 303 Underground Riff", category = BrowserCategory.SAMPLES_LOOPS, subCategory = "Bass Loops", bpm = 132f, musicalKey = "C Minor", previewType = SamplePreviewType.MELODIC_LOOP, description = "Screaming 16th-note acid bass sequence", trackTypeTarget = SessionTrackType.BASS),
            BrowserSampleItem(name = "Deep House 4x4 Groove", category = BrowserCategory.SAMPLES_LOOPS, subCategory = "Drum Loops", bpm = 124f, musicalKey = null, previewType = SamplePreviewType.DRUM_LOOP, description = "Crisp swinging house groove with clap and open hat", trackTypeTarget = SessionTrackType.DRUMS),
            BrowserSampleItem(name = "Industrial Acid Beat 132 BPM", category = BrowserCategory.SAMPLES_LOOPS, subCategory = "Drum Loops", bpm = 132f, musicalKey = null, previewType = SamplePreviewType.DRUM_LOOP, description = "Hard driving 909 kicks and snappy rolls", trackTypeTarget = SessionTrackType.DRUMS)
        )
    }

    // --- Ableton Session View Clip & Scene Triggers ---
    fun triggerScene(sceneIndex: Int) {
        val scList = _scenes.value
        if (sceneIndex in scList.indices) {
            _activeSceneIndex.value = sceneIndex
            val sc = scList[sceneIndex]
            _bpm.value = sc.bpm

            val clipLead = sc.clips[SessionTrackType.LEAD]
            if (clipLead != null && clipLead.leadNotes.isNotEmpty()) {
                _leadNotes.value = clipLead.leadNotes
            }
            val clipBass = sc.clips[SessionTrackType.BASS]
            if (clipBass != null && clipBass.bassNotes.isNotEmpty()) {
                _bassNotes.value = clipBass.bassNotes
            }
            val clipDrums = sc.clips[SessionTrackType.DRUMS]
            if (clipDrums != null && clipDrums.drumGrid.isNotEmpty()) {
                _drumGrid.value = clipDrums.drumGrid
            }

            _activePlayingClips.value = mapOf(
                SessionTrackType.LEAD to sceneIndex,
                SessionTrackType.BASS to sceneIndex,
                SessionTrackType.DRUMS to sceneIndex
            )

            if (!_isPlaying.value) {
                togglePlay()
            }
        }
    }

    fun triggerClip(track: SessionTrackType, clipIndex: Int) {
        val scList = _scenes.value
        if (clipIndex in scList.indices) {
            val sc = scList[clipIndex]
            val clip = sc.clips[track]
            if (clip != null) {
                when (track) {
                    SessionTrackType.LEAD -> if (clip.leadNotes.isNotEmpty()) _leadNotes.value = clip.leadNotes
                    SessionTrackType.BASS -> if (clip.bassNotes.isNotEmpty()) _bassNotes.value = clip.bassNotes
                    SessionTrackType.DRUMS -> if (clip.drumGrid.isNotEmpty()) _drumGrid.value = clip.drumGrid
                }
                val current = _activePlayingClips.value.toMutableMap()
                current[track] = clipIndex
                _activePlayingClips.value = current

                if (!_isPlaying.value) {
                    togglePlay()
                }
            }
        }
    }

    fun stopTrackClip(track: SessionTrackType) {
        val current = _activePlayingClips.value.toMutableMap()
        current[track] = null
        _activePlayingClips.value = current
        when (track) {
            SessionTrackType.LEAD -> _leadNotes.value = emptyList()
            SessionTrackType.BASS -> _bassNotes.value = emptyList()
            SessionTrackType.DRUMS -> _drumGrid.value = DrumType.values().associateWith { List(16) { 0f } }
        }
    }

    // ==========================================
    // ABLETON ARRANGEMENT TIMELINE OPERATIONS
    // ==========================================
    fun selectClip(clipId: String?) {
        _selectedClipId.value = clipId
    }

    fun setLoopRange(startBar: Float, endBar: Float) {
        val s = startBar.coerceAtLeast(0f)
        val e = endBar.coerceAtLeast(s + 1f)
        _loopStartBar.value = s
        _loopEndBar.value = e
    }

    fun toggleArrangementLoop() {
        _isLoopEnabled.value = !_isLoopEnabled.value
    }

    fun setZoomLevel(zoom: Float) {
        _zoomLevel.value = zoom.coerceIn(0.5f, 4.0f)
    }

    fun setGridSnap(snapBars: Float) {
        _gridSnapBars.value = snapBars
    }

    fun toggleAutomationDrawMode() {
        _isAutomationDrawMode.value = !_isAutomationDrawMode.value
    }

    fun toggleFollowPlayhead() {
        _isFollowPlayhead.value = !_isFollowPlayhead.value
    }

    fun scrubPlaybackPosition(bar: Float) {
        val clamped = bar.coerceIn(0f, 32f)
        _playbackPosition.value = clamped * 4f // 4 beats per bar
        _currentStep.value = ((clamped * 4f * 4).toInt()) % 16
    }

    // --- Clip Editing Actions ---
    fun addClipToTrack(trackId: String, startBar: Float, lengthBars: Float = 4f, name: String? = null) {
        val tracks = _arrangementTracks.value.toMutableList()
        val trackIdx = tracks.indexOfFirst { it.id == trackId }
        if (trackIdx != -1) {
            val track = tracks[trackIdx]
            val clipName = name ?: "${track.name.split(" ").lastOrNull() ?: "Clip"} ${(track.clips.size + 1)}"
            val p1 = ProjectSong.DEMO_PROJECTS[0]
            val newClip = ArrangementClip(
                name = clipName,
                trackId = trackId,
                startBar = startBar,
                lengthBars = lengthBars,
                colorHex = track.colorHex,
                leadNotes = if (track.trackType == SessionTrackType.LEAD) p1.leadNotes.take(6) else emptyList(),
                bassNotes = if (track.trackType == SessionTrackType.BASS) p1.bassNotes.take(6) else emptyList(),
                drumGrid = if (track.trackType == SessionTrackType.DRUMS) p1.drumGrid else emptyMap()
            )
            val updatedClips = (track.clips + newClip).sortedBy { it.startBar }
            tracks[trackIdx] = track.copy(clips = updatedClips)
            _arrangementTracks.value = tracks
            _selectedClipId.value = newClip.id
            showToast("Added clip '$clipName' at Bar ${(startBar + 1).toInt()}")
        }
    }

    fun duplicateSelectedClip() {
        val selId = _selectedClipId.value ?: return
        val tracks = _arrangementTracks.value.toMutableList()
        for (i in tracks.indices) {
            val track = tracks[i]
            val clip = track.clips.find { it.id == selId }
            if (clip != null) {
                val newStart = clip.startBar + clip.lengthBars
                val duplicated = clip.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    name = "${clip.name} (Copy)",
                    startBar = newStart
                )
                val newClips = (track.clips + duplicated).sortedBy { it.startBar }
                tracks[i] = track.copy(clips = newClips)
                _arrangementTracks.value = tracks
                _selectedClipId.value = duplicated.id
                showToast("Duplicated '${clip.name}' to Bar ${(newStart + 1).toInt()}")
                return
            }
        }
    }

    fun splitSelectedClip() {
        val selId = _selectedClipId.value ?: return
        val curBar = _playbackPosition.value / 4.0f
        val tracks = _arrangementTracks.value.toMutableList()

        for (i in tracks.indices) {
            val track = tracks[i]
            val clip = track.clips.find { it.id == selId }
            if (clip != null && curBar > clip.startBar && curBar < clip.startBar + clip.lengthBars) {
                val splitOffset = curBar - clip.startBar
                val firstPart = clip.copy(lengthBars = splitOffset)
                val secondPart = clip.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    name = "${clip.name} Pt2",
                    startBar = curBar,
                    lengthBars = clip.lengthBars - splitOffset
                )
                val newClips = track.clips.filter { it.id != selId } + listOf(firstPart, secondPart)
                tracks[i] = track.copy(clips = newClips.sortedBy { it.startBar })
                _arrangementTracks.value = tracks
                _selectedClipId.value = secondPart.id
                showToast("Split clip at Bar ${(curBar + 1).toInt()}")
                return
            }
        }
    }

    fun moveSelectedClip(deltaBars: Float) {
        val selId = _selectedClipId.value ?: return
        val tracks = _arrangementTracks.value.toMutableList()
        for (i in tracks.indices) {
            val track = tracks[i]
            val clip = track.clips.find { it.id == selId }
            if (clip != null) {
                val newStart = (clip.startBar + deltaBars).coerceAtLeast(0f)
                val updatedClip = clip.copy(startBar = newStart)
                val newClips = (track.clips.filter { it.id != selId } + updatedClip).sortedBy { it.startBar }
                tracks[i] = track.copy(clips = newClips)
                _arrangementTracks.value = tracks
                return
            }
        }
    }

    fun adjustSelectedClipLength(deltaBars: Float) {
        val selId = _selectedClipId.value ?: return
        val tracks = _arrangementTracks.value.toMutableList()
        for (i in tracks.indices) {
            val track = tracks[i]
            val clip = track.clips.find { it.id == selId }
            if (clip != null) {
                val newLength = (clip.lengthBars + deltaBars).coerceIn(1f, 32f)
                val updatedClip = clip.copy(lengthBars = newLength)
                val newClips = (track.clips.filter { it.id != selId } + updatedClip).sortedBy { it.startBar }
                tracks[i] = track.copy(clips = newClips)
                _arrangementTracks.value = tracks
                return
            }
        }
    }

    fun deleteSelectedClip() {
        val selId = _selectedClipId.value ?: return
        val tracks = _arrangementTracks.value.toMutableList()
        for (i in tracks.indices) {
            val track = tracks[i]
            if (track.clips.any { it.id == selId }) {
                tracks[i] = track.copy(clips = track.clips.filter { it.id != selId })
                _arrangementTracks.value = tracks
                _selectedClipId.value = null
                showToast("Deleted clip")
                return
            }
        }
    }

    fun toggleSelectedClipMute() {
        val selId = _selectedClipId.value ?: return
        val tracks = _arrangementTracks.value.toMutableList()
        for (i in tracks.indices) {
            val track = tracks[i]
            val clip = track.clips.find { it.id == selId }
            if (clip != null) {
                val updated = clip.copy(isMuted = !clip.isMuted)
                val newClips = track.clips.filter { it.id != selId } + updated
                tracks[i] = track.copy(clips = newClips.sortedBy { it.startBar })
                _arrangementTracks.value = tracks
                return
            }
        }
    }

    fun toggleSelectedClipLoop() {
        val selId = _selectedClipId.value ?: return
        val tracks = _arrangementTracks.value.toMutableList()
        for (i in tracks.indices) {
            val track = tracks[i]
            val clip = track.clips.find { it.id == selId }
            if (clip != null) {
                val updated = clip.copy(isLooping = !clip.isLooping)
                val newClips = track.clips.filter { it.id != selId } + updated
                tracks[i] = track.copy(clips = newClips.sortedBy { it.startBar })
                _arrangementTracks.value = tracks
                return
            }
        }
    }

    // --- Track Controls ---
    fun toggleArrangementTrackMute(trackId: String) {
        val tracks = _arrangementTracks.value.toMutableList()
        val idx = tracks.indexOfFirst { it.id == trackId }
        if (idx != -1) {
            val newMute = !tracks[idx].isMuted
            tracks[idx] = tracks[idx].copy(isMuted = newMute)
            _arrangementTracks.value = tracks
            when (tracks[idx].trackType) {
                SessionTrackType.LEAD -> _engine.isSynthMuted = newMute
                SessionTrackType.BASS -> _engine.isBassMuted = newMute
                SessionTrackType.DRUMS -> _engine.isDrumMuted = newMute
            }
        }
    }

    fun toggleArrangementTrackSolo(trackId: String) {
        val tracks = _arrangementTracks.value.toMutableList()
        val idx = tracks.indexOfFirst { it.id == trackId }
        if (idx != -1) {
            val newSolo = !tracks[idx].isSolo
            tracks[idx] = tracks[idx].copy(isSolo = newSolo)
            _arrangementTracks.value = tracks
        }
    }

    fun toggleArrangementTrackArm(trackId: String) {
        val tracks = _arrangementTracks.value.toMutableList()
        val idx = tracks.indexOfFirst { it.id == trackId }
        if (idx != -1) {
            val newArm = !tracks[idx].isArmed
            tracks[idx] = tracks[idx].copy(isArmed = newArm)
            _arrangementTracks.value = tracks
        }
    }

    fun toggleArrangementTrackAutomation(trackId: String) {
        val tracks = _arrangementTracks.value.toMutableList()
        val idx = tracks.indexOfFirst { it.id == trackId }
        if (idx != -1) {
            tracks[idx] = tracks[idx].copy(isAutomationExpanded = !tracks[idx].isAutomationExpanded)
            _arrangementTracks.value = tracks
        }
    }

    fun setArrangementTrackVolume(trackId: String, volume: Float) {
        val tracks = _arrangementTracks.value.toMutableList()
        val idx = tracks.indexOfFirst { it.id == trackId }
        if (idx != -1) {
            tracks[idx] = tracks[idx].copy(volume = volume)
            _arrangementTracks.value = tracks
            when (tracks[idx].trackType) {
                SessionTrackType.LEAD -> _engine.synthVolume = volume
                SessionTrackType.BASS -> _engine.bassVolume = volume
                SessionTrackType.DRUMS -> _engine.drumVolume = volume
            }
        }
    }

    fun setArrangementTrackPan(trackId: String, pan: Float) {
        val tracks = _arrangementTracks.value.toMutableList()
        val idx = tracks.indexOfFirst { it.id == trackId }
        if (idx != -1) {
            tracks[idx] = tracks[idx].copy(pan = pan)
            _arrangementTracks.value = tracks
            when (tracks[idx].trackType) {
                SessionTrackType.LEAD -> _engine.synthPan = pan
                SessionTrackType.BASS -> _engine.bassPan = pan
                SessionTrackType.DRUMS -> _engine.drumPan = pan
            }
        }
    }

    fun setArrangementTrackSendA(trackId: String, sendA: Float) {
        val tracks = _arrangementTracks.value.toMutableList()
        val idx = tracks.indexOfFirst { it.id == trackId }
        if (idx != -1) {
            tracks[idx] = tracks[idx].copy(sendA = sendA)
            _arrangementTracks.value = tracks
            _engine.reverb.mix = sendA
        }
    }

    fun setArrangementTrackSendB(trackId: String, sendB: Float) {
        val tracks = _arrangementTracks.value.toMutableList()
        val idx = tracks.indexOfFirst { it.id == trackId }
        if (idx != -1) {
            tracks[idx] = tracks[idx].copy(sendB = sendB)
            _arrangementTracks.value = tracks
            _engine.stereoDelay.mix = sendB
        }
    }

    // --- Track Groups (Ableton Foldable Groups) ---
    fun toggleTrackGroupFold(groupId: String) {
        val groups = _trackGroups.value.toMutableList()
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx != -1) {
            groups[idx] = groups[idx].copy(isFolded = !groups[idx].isFolded)
            _trackGroups.value = groups
        }
    }

    fun toggleTrackGroupMute(groupId: String) {
        val groups = _trackGroups.value.toMutableList()
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx != -1) {
            val newMute = !groups[idx].isMuted
            groups[idx] = groups[idx].copy(isMuted = newMute)
            _trackGroups.value = groups
            // Mute all child tracks
            val childIds = groups[idx].trackIds
            val tracks = _arrangementTracks.value.map { t ->
                if (t.id in childIds) t.copy(isMuted = newMute) else t
            }
            _arrangementTracks.value = tracks
        }
    }

    fun toggleTrackGroupSolo(groupId: String) {
        val groups = _trackGroups.value.toMutableList()
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx != -1) {
            val newSolo = !groups[idx].isSolo
            groups[idx] = groups[idx].copy(isSolo = newSolo)
            _trackGroups.value = groups
            val childIds = groups[idx].trackIds
            val tracks = _arrangementTracks.value.map { t ->
                if (t.id in childIds) t.copy(isSolo = newSolo) else t
            }
            _arrangementTracks.value = tracks
        }
    }

    fun setTrackGroupVolume(groupId: String, volume: Float) {
        val groups = _trackGroups.value.toMutableList()
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx != -1) {
            groups[idx] = groups[idx].copy(volume = volume)
            _trackGroups.value = groups
        }
    }

    // ==========================================
    // ABLETON BROWSER & SAMPLE AUDITION
    // ==========================================
    fun selectBrowserCategory(category: BrowserCategory) {
        _selectedBrowserCategory.value = category
    }

    fun setBrowserSearchQuery(query: String) {
        _browserSearchQuery.value = query
    }

    fun auditionSample(sample: BrowserSampleItem) {
        _auditioningSampleId.value = sample.id
        when (sample.previewType) {
            SamplePreviewType.DRUM_HIT -> {
                when {
                    sample.name.contains("Kick", true) -> _engine.triggerDrum(DrumType.KICK, 0.95f)
                    sample.name.contains("Snare", true) -> _engine.triggerDrum(DrumType.SNARE, 0.95f)
                    sample.name.contains("Clap", true) -> _engine.triggerDrum(DrumType.CLAP, 0.95f)
                    sample.name.contains("Tom", true) -> _engine.triggerDrum(DrumType.TOM, 0.95f)
                    sample.name.contains("Open", true) -> _engine.triggerDrum(DrumType.HIHAT_OPEN, 0.85f)
                    else -> _engine.triggerDrum(DrumType.HIHAT_CLOSED, 0.85f)
                }
            }
            SamplePreviewType.SYNTH_CHORD -> {
                viewModelScope.launch {
                    _engine.noteOn(60)
                    _engine.noteOn(63)
                    _engine.noteOn(67)
                    delay(500)
                    _engine.noteOff(60)
                    _engine.noteOff(63)
                    _engine.noteOff(67)
                    _auditioningSampleId.value = null
                }
            }
            SamplePreviewType.BASS_SLAP -> {
                viewModelScope.launch {
                    _engine.bassNoteOn(36)
                    delay(350)
                    _engine.bassNoteOff(36)
                    _auditioningSampleId.value = null
                }
            }
            SamplePreviewType.MELODIC_LOOP -> {
                viewModelScope.launch {
                    val notes = listOf(60, 63, 65, 67, 70, 67, 65, 63)
                    for (p in notes) {
                        _engine.noteOn(p)
                        delay(120)
                        _engine.noteOff(p)
                    }
                    _auditioningSampleId.value = null
                }
            }
            SamplePreviewType.DRUM_LOOP -> {
                viewModelScope.launch {
                    val steps = listOf(
                        listOf(DrumType.KICK, DrumType.HIHAT_CLOSED),
                        listOf(DrumType.HIHAT_CLOSED),
                        listOf(DrumType.SNARE, DrumType.HIHAT_CLOSED),
                        listOf(DrumType.HIHAT_OPEN)
                    )
                    repeat(2) {
                        for (st in steps) {
                            st.forEach { _engine.triggerDrum(it, 0.85f) }
                            delay(140)
                        }
                    }
                    _auditioningSampleId.value = null
                }
            }
            SamplePreviewType.FX_SWEEP -> {
                viewModelScope.launch {
                    _engine.filterCutoff = 0.1f
                    _engine.noteOn(55)
                    for (i in 1..10) {
                        _engine.filterCutoff = i / 10f
                        delay(35)
                    }
                    _engine.noteOff(55)
                    _auditioningSampleId.value = null
                }
            }
        }
    }

    fun stopAudition() {
        _auditioningSampleId.value = null
    }

    fun insertSampleToArrangement(sample: BrowserSampleItem, trackType: SessionTrackType) {
        val targetTrack = _arrangementTracks.value.find { it.trackType == trackType }
        if (targetTrack != null) {
            val startBar = (_playbackPosition.value / 4.0f).toInt().toFloat()
            addClipToTrack(targetTrack.id, startBar, 4.0f, sample.name)
            showToast("Inserted '${sample.name}' into ${targetTrack.name}")
        }
    }

    // ==========================================
    // ABLETON MACRO FX RACK & LFO CONTROLS
    // ==========================================
    fun setMacroValue(macroIndex: Int, value: Float) {
        val currentRack = _macroRack.value
        val updatedMacros = currentRack.macros.map { m ->
            if (m.index == macroIndex) {
                m.copy(value = value.coerceIn(0f, 1f))
            } else m
        }
        _macroRack.value = currentRack.copy(macros = updatedMacros)

        // Apply macro routing to engine
        when (macroIndex) {
            0 -> _engine.filterCutoff = value
            1 -> _engine.filterResonance = value
            2 -> _engine.reverb.mix = value
            3 -> _engine.stereoDelay.timeMs = 100f + (value * 900f)
            4 -> _engine.distortion.drive = value
            5 -> _engine.chorus.depth = value
            6 -> _engine.compressor.thresholdDb = -30f + (value * 25f)
            7 -> _engine.masterVolume = value
        }
    }

    fun loadMacroRackPreset(presetName: String) {
        val rack = when (presetName) {
            "Acid Tweaker 303" -> MacroRack(
                name = presetName,
                macros = listOf(
                    MacroControl(0, "Cutoff", 0.75f, "Filter Cutoff"),
                    MacroControl(1, "Reso Bite", 0.85f, "Filter Res"),
                    MacroControl(2, "Echo Space", 0.4f, "Reverb"),
                    MacroControl(3, "Ping Pong", 0.35f, "Delay"),
                    MacroControl(4, "Acid Drive", 0.65f, "Distortion"),
                    MacroControl(5, "Chorus", 0.15f, "Chorus"),
                    MacroControl(6, "Punch", 0.7f, "Compressor"),
                    MacroControl(7, "Master", 0.85f, "Volume")
                )
            )
            "Ambient Lush Space" -> MacroRack(
                name = presetName,
                macros = listOf(
                    MacroControl(0, "Air Cutoff", 0.45f, "Filter Cutoff"),
                    MacroControl(1, "Warmth", 0.2f, "Filter Res"),
                    MacroControl(2, "Cathedral", 0.85f, "Reverb"),
                    MacroControl(3, "Ethereal Delay", 0.65f, "Delay"),
                    MacroControl(4, "Saturation", 0.1f, "Distortion"),
                    MacroControl(5, "Dimension", 0.75f, "Chorus"),
                    MacroControl(6, "Gentle Glue", 0.35f, "Compressor"),
                    MacroControl(7, "Master", 0.8f, "Volume")
                )
            )
            "Lo-Fi Tape Machine" -> MacroRack(
                name = presetName,
                macros = listOf(
                    MacroControl(0, "Muffled", 0.35f, "Filter Cutoff"),
                    MacroControl(1, "Reso", 0.15f, "Filter Res"),
                    MacroControl(2, "Room", 0.3f, "Reverb"),
                    MacroControl(3, "Slapback", 0.2f, "Delay"),
                    MacroControl(4, "Tape Crunch", 0.45f, "Distortion"),
                    MacroControl(5, "Flutter", 0.55f, "Chorus"),
                    MacroControl(6, "Pumping", 0.6f, "Compressor"),
                    MacroControl(7, "Master", 0.85f, "Volume")
                )
            )
            else -> MacroRack(
                name = presetName,
                macros = listOf(
                    MacroControl(0, "Punch Cutoff", 0.85f, "Filter Cutoff"),
                    MacroControl(1, "Res Peak", 0.45f, "Filter Res"),
                    MacroControl(2, "Club Reverb", 0.3f, "Reverb"),
                    MacroControl(3, "Sync Echo", 0.35f, "Delay"),
                    MacroControl(4, "Hard Drive", 0.4f, "Distortion"),
                    MacroControl(5, "Stereo Spread", 0.4f, "Chorus"),
                    MacroControl(6, "Heavy Glue", 0.8f, "Compressor"),
                    MacroControl(7, "Master Out", 0.9f, "Volume")
                )
            )
        }
        _macroRack.value = rack
        rack.macros.forEach { m -> setMacroValue(m.index, m.value) }
        showToast("Loaded Macro Rack: $presetName")
    }

    fun loadBrowserSampleToActiveTrack(sample: BrowserSampleItem) {
        val target = sample.trackTypeTarget ?: when (sample.category) {
            BrowserCategory.DRUMS -> SessionTrackType.DRUMS
            BrowserCategory.SOUNDS -> SessionTrackType.LEAD
            BrowserCategory.INSTRUMENTS -> SessionTrackType.BASS
            else -> SessionTrackType.LEAD
        }
        insertSampleToArrangement(sample, target)
    }

    fun loadPatch(patch: SynthPatch) {
        patch.applyToEngine(_engine)
        showToast("Loaded Synth Preset: ${patch.name}")
    }

    fun updateLfo(isEnabled: Boolean, waveform: Waveform, rateHz: Float, depth: Float, target: String) {
        _lfoDevice.value = LfoDevice(
            isEnabled = isEnabled,
            waveform = waveform,
            rateHz = rateHz,
            depth = depth,
            target = target
        )
        if (isEnabled) {
            _engine.lfoWaveform = waveform
            _engine.lfoFrequency = rateHz
            _engine.lfoDepth = depth
        }
    }

    // --- Save & Room Database Operations ---
    fun openSaveDialog() {
        _isSaveDialogOpen.value = true
    }

    fun closeSaveDialog() {
        _isSaveDialogOpen.value = false
    }

    fun toggleBrowserDrawer() {
        _isBrowserDrawerOpen.value = !_isBrowserDrawerOpen.value
    }

    fun closeBrowserDrawer() {
        _isBrowserDrawerOpen.value = false
    }

    fun showToast(msg: String) {
        _statusToast.value = msg
        viewModelScope.launch {
            delay(2500)
            if (_statusToast.value == msg) {
                _statusToast.value = null
            }
        }
    }

    fun saveCurrentProject(name: String, genre: String) {
        val projId = _currentProjectId.value
        _currentProjectName.value = name
        _currentGenre.value = genre

        viewModelScope.launch(Dispatchers.IO) {
            val currentPatch = SynthPatch(
                name = name,
                description = "$genre Project Patch",
                vco1Waveform = _engine.vco1Waveform,
                vco1Octave = _engine.vco1Octave,
                vco1Mix = _engine.vco1Mix,
                vco2Waveform = _engine.vco2Waveform,
                vco2Semi = _engine.vco2Semi,
                vco2Detune = _engine.vco2Detune,
                vco2Mix = _engine.vco2Mix,
                fmDepth = _engine.fmDepth,
                ringModMix = _engine.ringModMix,
                lfoWaveform = _engine.lfoWaveform,
                lfoFrequency = _engine.lfoFrequency,
                lfoDepth = _engine.lfoDepth,
                lfoDestination = _engine.lfoDestination,
                filterType = _engine.filterType,
                filterCutoff = _engine.filterCutoff,
                filterResonance = _engine.filterResonance,
                egAmt = _engine.egAmt,
                attackTime = _engine.attackTime,
                decayTime = _engine.decayTime,
                sustainLevel = _engine.sustainLevel,
                releaseTime = _engine.releaseTime,
                glideTime = _engine.glideTime,
                delayTime = _engine.stereoDelay.timeMs / 1000f,
                delayFeedback = _engine.stereoDelay.feedback,
                delayMix = _engine.stereoDelay.mix,
                masterVolume = _engine.masterVolume
            )

            val json = ProjectSerializer.serializeStateToJson(
                name = name,
                genre = genre,
                bpm = _bpm.value,
                swing = _swing.value,
                rootNote = _rootNote.value,
                scale = _currentScale.value,
                keyboardOctave = _keyboardOctave.value,
                patch = currentPatch,
                leadNotes = _leadNotes.value,
                bassNotes = _bassNotes.value,
                drumGrid = _drumGrid.value,
                leadAutomation = _leadAutomation.value,
                bassAutomation = _bassAutomation.value,
                synthVolume = _engine.synthVolume,
                synthPan = _engine.synthPan,
                synthMute = _engine.isSynthMuted,
                bassVolume = _engine.bassVolume,
                bassPan = _engine.bassPan,
                bassMute = _engine.isBassMuted,
                drumVolume = _engine.drumVolume,
                drumPan = _engine.drumPan,
                drumMute = _engine.isDrumMuted,
                masterVolume = _engine.masterVolume,
                rackModules = _rackModules.value,
                scenes = _scenes.value,
                arrangementTracks = _arrangementTracks.value,
                trackGroups = _trackGroups.value,
                macroRack = _macroRack.value,
                lfoDevice = _lfoDevice.value
            )

            val entity = ProjectEntity(
                id = projId ?: 0L,
                name = name,
                genre = genre,
                bpm = _bpm.value,
                keyRoot = _rootNote.value,
                scaleName = _currentScale.value.displayName,
                lastModified = System.currentTimeMillis(),
                projectDataJson = json
            )

            val savedId = projectRepository.saveProject(entity)
            _currentProjectId.value = savedId
            withContext(Dispatchers.Main) {
                _isSaveDialogOpen.value = false
                showToast("Project '$name' saved to Room DB!")
            }
        }
    }

    fun saveProjectAsNew(name: String, genre: String) {
        _currentProjectId.value = null
        saveCurrentProject(name, genre)
    }

    fun loadProjectFromDb(entity: ProjectEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = ProjectSerializer.deserializeStateFromJson(entity.projectDataJson)
            if (state != null) {
                withContext(Dispatchers.Main) {
                    stopTransport()
                    _currentProjectId.value = entity.id
                    _currentProjectName.value = state.name
                    _currentGenre.value = state.genre
                    _bpm.value = state.bpm
                    _swing.value = state.swing
                    _rootNote.value = state.rootNote
                    _currentScale.value = state.scale
                    _keyboardOctave.value = state.keyboardOctave

                    _leadNotes.value = state.leadNotes
                    _bassNotes.value = state.bassNotes
                    _drumGrid.value = state.drumGrid

                    _leadAutomation.value = state.leadAutomation
                    _bassAutomation.value = state.bassAutomation

                    state.synthPatch.applyToEngine(_engine)

                    _engine.synthVolume = state.synthVolume
                    _engine.synthPan = state.synthPan
                    _engine.isSynthMuted = state.synthMute

                    _engine.bassVolume = state.bassVolume
                    _engine.bassPan = state.bassPan
                    _engine.isBassMuted = state.bassMute

                    _engine.drumVolume = state.drumVolume
                    _engine.drumPan = state.drumPan
                    _engine.isDrumMuted = state.drumMute

                    _engine.masterVolume = state.masterVolume

                    if (state.scenes.isNotEmpty()) {
                        _scenes.value = state.scenes
                    }
                    if (state.arrangementTracks.isNotEmpty()) {
                        _arrangementTracks.value = state.arrangementTracks
                    }
                    if (state.trackGroups.isNotEmpty()) {
                        _trackGroups.value = state.trackGroups
                    }
                    state.macroRack?.let { rack ->
                        _macroRack.value = rack
                        rack.macros.forEach { m -> setMacroValue(m.index, m.value) }
                    }
                    state.lfoDevice?.let { lfo ->
                        updateLfo(lfo.isEnabled, lfo.waveform, lfo.rateHz, lfo.depth, lfo.target)
                    }

                    showToast("Loaded '${entity.name}' successfully")
                    closeBrowserDrawer()
                }
            } else {
                withContext(Dispatchers.Main) {
                    showToast("Failed to parse project JSON")
                }
            }
        }
    }

    fun deleteProjectFromDb(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            projectRepository.deleteProjectById(id)
            if (_currentProjectId.value == id) {
                _currentProjectId.value = null
            }
            withContext(Dispatchers.Main) {
                showToast("Project deleted from database")
            }
        }
    }

    fun createNewBlankProject(name: String = "Untitled Session") {
        stopTransport()
        _currentProjectId.value = null
        _currentProjectName.value = name
        _currentGenre.value = "Custom"
        _bpm.value = 120f
        _swing.value = 0f
        _rootNote.value = 0
        _currentScale.value = MusicalScale.NATURAL_MINOR
        _leadNotes.value = emptyList()
        _bassNotes.value = emptyList()
        _drumGrid.value = DrumType.values().associateWith { List(16) { 0f } }
        SynthPatch.PRESETS[0].applyToEngine(_engine)
        resetRackToDefault()
        showToast("Created new project: $name")
    }

    fun exportCurrentProjectJson(): String {
        val currentPatch = SynthPatch(
            name = _currentProjectName.value,
            description = "${_currentGenre.value} Patch",
            vco1Waveform = _engine.vco1Waveform,
            vco1Octave = _engine.vco1Octave,
            vco1Mix = _engine.vco1Mix,
            vco2Waveform = _engine.vco2Waveform,
            vco2Semi = _engine.vco2Semi,
            vco2Detune = _engine.vco2Detune,
            vco2Mix = _engine.vco2Mix,
            fmDepth = _engine.fmDepth,
            ringModMix = _engine.ringModMix,
            lfoWaveform = _engine.lfoWaveform,
            lfoFrequency = _engine.lfoFrequency,
            lfoDepth = _engine.lfoDepth,
            lfoDestination = _engine.lfoDestination,
            filterType = _engine.filterType,
            filterCutoff = _engine.filterCutoff,
            filterResonance = _engine.filterResonance,
            egAmt = _engine.egAmt,
            attackTime = _engine.attackTime,
            decayTime = _engine.decayTime,
            sustainLevel = _engine.sustainLevel,
            releaseTime = _engine.releaseTime,
            glideTime = _engine.glideTime,
            delayTime = _engine.stereoDelay.timeMs / 1000f,
            delayFeedback = _engine.stereoDelay.feedback,
            delayMix = _engine.stereoDelay.mix,
            masterVolume = _engine.masterVolume
        )

        return ProjectSerializer.serializeStateToJson(
            name = _currentProjectName.value,
            genre = _currentGenre.value,
            bpm = _bpm.value,
            swing = _swing.value,
            rootNote = _rootNote.value,
            scale = _currentScale.value,
            keyboardOctave = _keyboardOctave.value,
            patch = currentPatch,
            leadNotes = _leadNotes.value,
            bassNotes = _bassNotes.value,
            drumGrid = _drumGrid.value,
            leadAutomation = _leadAutomation.value,
            bassAutomation = _bassAutomation.value,
            synthVolume = _engine.synthVolume,
            synthPan = _engine.synthPan,
            synthMute = _engine.isSynthMuted,
            bassVolume = _engine.bassVolume,
            bassPan = _engine.bassPan,
            bassMute = _engine.isBassMuted,
            drumVolume = _engine.drumVolume,
            drumPan = _engine.drumPan,
            drumMute = _engine.isDrumMuted,
            masterVolume = _engine.masterVolume,
            rackModules = _rackModules.value,
            scenes = _scenes.value,
            arrangementTracks = _arrangementTracks.value,
            trackGroups = _trackGroups.value,
            macroRack = _macroRack.value,
            lfoDevice = _lfoDevice.value
        )
    }

    fun importProjectFromJson(jsonString: String): Boolean {
        val state = ProjectSerializer.deserializeStateFromJson(jsonString) ?: return false
        viewModelScope.launch(Dispatchers.IO) {
            val entity = ProjectEntity(
                name = state.name,
                genre = state.genre,
                bpm = state.bpm,
                keyRoot = state.rootNote,
                scaleName = state.scale.displayName,
                lastModified = System.currentTimeMillis(),
                projectDataJson = jsonString
            )
            val newId = projectRepository.insertProject(entity)
            loadProjectFromDb(entity.copy(id = newId))
        }
        return true
    }

    fun selectTab(tab: DawTab) {
        _currentTab.value = tab
    }

    fun setPreset(index: Int) {
        if (index in SynthPatch.PRESETS.indices) {
            _selectedPresetIndex.value = index
            SynthPatch.PRESETS[index].applyToEngine(_engine)
        }
    }

    fun loadProject(index: Int) {
        if (index in ProjectSong.DEMO_PROJECTS.indices) {
            _currentProjectIndex.value = index
            val proj = ProjectSong.DEMO_PROJECTS[index]
            _bpm.value = proj.bpm
            _leadNotes.value = proj.leadNotes
            _bassNotes.value = proj.bassNotes
            _drumGrid.value = proj.drumGrid
            proj.patch.applyToEngine(_engine)
        }
    }

    // --- Master Transport ---
    fun togglePlay() {
        if (_isPlaying.value) {
            stopTransport()
        } else {
            startTransport()
        }
    }

    fun stopTransport() {
        _isPlaying.value = false
        transportJob?.cancel()
        transportJob = null

        activePlayingLead.forEach { (_, pitch) -> _engine.noteOff(pitch) }
        activePlayingLead.clear()

        activePlayingBass.forEach { (_, pitch) -> _engine.bassNoteOff(pitch) }
        activePlayingBass.clear()

        _playbackPosition.value = 0f
        _currentStep.value = 0
    }

    private fun startTransport() {
        _isPlaying.value = true
        transportJob = viewModelScope.launch {
            var lastTime = System.currentTimeMillis()
            var lastProcessed16th = -1

            while (isActive && _isPlaying.value) {
                val currentTime = System.currentTimeMillis()
                val deltaMs = currentTime - lastTime
                lastTime = currentTime

                val beatsPerSec = _bpm.value / 60f
                val deltaBeats = (deltaMs / 1000f) * beatsPerSec

                var newPosition = _playbackPosition.value + deltaBeats

                val loopStartBeats = _loopStartBar.value * 4f
                val loopEndBeats = _loopEndBar.value * 4f
                val maxBeats = if (_isLoopEnabled.value && loopEndBeats > loopStartBeats) loopEndBeats else 64f

                if (newPosition >= maxBeats) {
                    newPosition = if (_isLoopEnabled.value) loopStartBeats else 0f
                    activePlayingLead.forEach { (_, pitch) -> _engine.noteOff(pitch) }
                    activePlayingLead.clear()
                    activePlayingBass.forEach { (_, pitch) -> _engine.bassNoteOff(pitch) }
                    activePlayingBass.clear()
                } else if (_isLoopEnabled.value && newPosition < loopStartBeats) {
                    newPosition = loopStartBeats
                }
                _playbackPosition.value = newPosition

                val currentBar = newPosition / 4.0f

                // 16th note step indexing (4 steps per beat, 16 steps per bar)
                val current16th = (newPosition * 4).toInt() % 16
                _currentStep.value = current16th

                // Find active clips on Arrangement Tracks
                val arrTracks = _arrangementTracks.value
                val leadTrack = arrTracks.find { it.trackType == SessionTrackType.LEAD }
                val bassTrack = arrTracks.find { it.trackType == SessionTrackType.BASS }
                val drumTrack = arrTracks.find { it.trackType == SessionTrackType.DRUMS }

                val activeLeadClip = leadTrack?.clips?.find { !it.isMuted && currentBar >= it.startBar && currentBar < it.startBar + it.lengthBars }
                val activeBassClip = bassTrack?.clips?.find { !it.isMuted && currentBar >= it.startBar && currentBar < it.startBar + it.lengthBars }
                val activeDrumClip = drumTrack?.clips?.find { !it.isMuted && currentBar >= it.startBar && currentBar < it.startBar + it.lengthBars }

                if (current16th != lastProcessed16th) {
                    lastProcessed16th = current16th
                    // Trigger Drum Machine step
                    if (activeDrumClip != null && activeDrumClip.drumGrid.isNotEmpty()) {
                        val grid = activeDrumClip.drumGrid
                        for ((drumType, stepsList) in grid) {
                            if (current16th < stepsList.size && stepsList[current16th] > 0.05f) {
                                _engine.triggerDrum(drumType, stepsList[current16th])
                            }
                        }
                    } else {
                        triggerDrumsAtStep(current16th)
                    }

                    // Metronome trigger on quarter notes
                    if (current16th % 4 == 0) {
                        val isDownbeat = (current16th == 0)
                        _engine.triggerMetronome(isDownbeat)
                    }
                }

                // Lead Note Triggering (Arrangement Clip or Global Lead Notes)
                val effectiveLeadNotes = if (activeLeadClip != null && activeLeadClip.leadNotes.isNotEmpty()) {
                    val clipOffsetBeats = activeLeadClip.startBar * 4f
                    activeLeadClip.leadNotes.map { note ->
                        note.copy(startBeat = clipOffsetBeats + note.startBeat)
                    }
                } else {
                    _leadNotes.value
                }

                val toRemoveLead = mutableListOf<String>()
                for ((id, pitch) in activePlayingLead) {
                    val note = effectiveLeadNotes.find { it.id == id }
                    if (note == null || newPosition < note.startBeat || newPosition >= note.startBeat + note.lengthBeats || note.pitch != pitch) {
                        _engine.noteOff(pitch)
                        toRemoveLead.add(id)
                    }
                }
                toRemoveLead.forEach { activePlayingLead.remove(it) }

                if (leadTrack?.isMuted != true) {
                    for (note in effectiveLeadNotes) {
                        if (!activePlayingLead.containsKey(note.id)) {
                            if (newPosition >= note.startBeat && newPosition < note.startBeat + note.lengthBeats) {
                                _engine.noteOn(note.pitch)
                                activePlayingLead[note.id] = note.pitch
                            }
                        }
                    }
                }

                // Bass Note Triggering (Arrangement Clip or Global Bass Notes)
                val effectiveBassNotes = if (activeBassClip != null && activeBassClip.bassNotes.isNotEmpty()) {
                    val clipOffsetBeats = activeBassClip.startBar * 4f
                    activeBassClip.bassNotes.map { note ->
                        note.copy(startBeat = clipOffsetBeats + note.startBeat)
                    }
                } else {
                    _bassNotes.value
                }

                val toRemoveBass = mutableListOf<String>()
                for ((id, pitch) in activePlayingBass) {
                    val note = effectiveBassNotes.find { it.id == id }
                    if (note == null || newPosition < note.startBeat || newPosition >= note.startBeat + note.lengthBeats || note.pitch != pitch) {
                        _engine.bassNoteOff(pitch)
                        toRemoveBass.add(id)
                    }
                }
                toRemoveBass.forEach { activePlayingBass.remove(it) }

                if (bassTrack?.isMuted != true) {
                    for (note in effectiveBassNotes) {
                        if (!activePlayingBass.containsKey(note.id)) {
                            if (newPosition >= note.startBeat && newPosition < note.startBeat + note.lengthBeats) {
                                _engine.bassNoteOn(note.pitch)
                                activePlayingBass[note.id] = note.pitch
                            }
                        }
                    }
                }

                // Apply Track-level Parameter Automation Envelopes
                applyAutomationAtPosition(newPosition)

                delay(12)
            }
        }
    }

    private fun triggerDrumsAtStep(step: Int) {
        val grid = _drumGrid.value
        for ((drumType, stepsList) in grid) {
            if (step < stepsList.size) {
                val vel = stepsList[step]
                if (vel > 0.05f) {
                    _engine.triggerDrum(drumType, vel)
                }
            }
        }
    }

    // --- Drum Pattern Editing ---
    fun selectDrumType(type: DrumType) {
        _selectedDrumType.value = type
    }

    fun toggleDrumStep(type: DrumType, step: Int) {
        val currentGrid = _drumGrid.value.toMutableMap()
        val stepsList = (currentGrid[type] ?: List(16) { 0f }).toMutableList()
        if (step in stepsList.indices) {
            stepsList[step] = if (stepsList[step] > 0.05f) 0.0f else 0.9f
            currentGrid[type] = stepsList
            _drumGrid.value = currentGrid

            // Audition drum hit on tap
            if (stepsList[step] > 0f) {
                _engine.triggerDrum(type, 0.9f)
            }
        }
    }

    fun clearDrumPattern(type: DrumType? = null) {
        val currentGrid = _drumGrid.value.toMutableMap()
        if (type != null) {
            currentGrid[type] = List(16) { 0f }
        } else {
            DrumType.values().forEach { d -> currentGrid[d] = List(16) { 0f } }
        }
        _drumGrid.value = currentGrid
    }

    // --- Piano Roll Operations ---
    fun addLeadNote(note: MidiNote) {
        _leadNotes.value = _leadNotes.value + note
    }

    fun updateLeadNote(note: MidiNote) {
        _leadNotes.value = _leadNotes.value.map { if (it.id == note.id) note else it }
    }

    fun removeLeadNote(id: String) {
        _leadNotes.value = _leadNotes.value.filter { it.id != id }
    }

    fun clearLeadNotes() {
        _leadNotes.value = emptyList()
    }

    fun transposeLead(semitones: Int) {
        _leadNotes.value = _leadNotes.value.map { it.copy(pitch = (it.pitch + semitones).coerceIn(24, 96)) }
    }

    fun addBassNote(note: MidiNote) {
        _bassNotes.value = _bassNotes.value + note
    }

    fun updateBassNote(note: MidiNote) {
        _bassNotes.value = _bassNotes.value.map { if (it.id == note.id) note else it }
    }

    fun removeBassNote(id: String) {
        _bassNotes.value = _bassNotes.value.filter { it.id != id }
    }

    fun clearBassNotes() {
        _bassNotes.value = emptyList()
    }

    fun transposeBass(semitones: Int) {
        _bassNotes.value = _bassNotes.value.map { it.copy(pitch = (it.pitch + semitones).coerceIn(24, 72)) }
    }

    fun generateRandomBassline() {
        val scale = _currentScale.value
        val root = _rootNote.value + 36 // C2
        val availablePitches = scale.intervals.map { root + it }
        val newNotes = mutableListOf<MidiNote>()
        var beat = 0f
        while (beat < 16f) {
            val length = listOf(0.5f, 1.0f).random()
            val pitch = availablePitches.random()
            newNotes.add(MidiNote(pitch = pitch, startBeat = beat, lengthBeats = length, velocity = 0.9f))
            beat += length + listOf(0f, 0.5f, 1.0f).random()
        }
        _bassNotes.value = newNotes
    }

    fun generateRandomMelody() {
        val scale = _currentScale.value
        val root = _rootNote.value + 60 // C4
        val availablePitches = scale.intervals.map { root + it } + scale.intervals.map { root + 12 + it }
        
        val newNotes = mutableListOf<MidiNote>()
        var beat = 0f
        while (beat < 16f) {
            val length = listOf(0.5f, 1.0f, 1.5f).random()
            val pitch = availablePitches.random()
            newNotes.add(MidiNote(pitch = pitch, startBeat = beat, lengthBeats = length, velocity = 0.85f))
            beat += length + listOf(0f, 0.5f).random()
        }
        _leadNotes.value = newNotes
    }

    // --- Scale & Performance ---
    fun setRootNote(root: Int) {
        _rootNote.value = root
    }

    fun setScale(scale: MusicalScale) {
        _currentScale.value = scale
    }

    fun setKeyboardOctave(oct: Int) {
        _keyboardOctave.value = oct.coerceIn(1, 7)
    }

    fun updateBpm(newBpm: Float) {
        _bpm.value = newBpm.coerceIn(40f, 240f)
    }

    fun tapTempo() {
        val now = System.currentTimeMillis()
        if (lastTapTimeMs > 0) {
            val diffMs = now - lastTapTimeMs
            if (diffMs in 200..2000) {
                val calculatedBpm = (60000f / diffMs).coerceIn(40f, 240f)
                _bpm.value = (calculatedBpm * 0.5f + _bpm.value * 0.5f).toInt().toFloat()
            }
        }
        lastTapTimeMs = now
    }

    fun toggleMetronome() {
        val newState = !_isMetronomeOn.value
        _isMetronomeOn.value = newState
        _engine.isMetronomeEnabled = newState
    }

    fun panic() {
        _engine.panic()
        stopTransport()
    }

    // --- Recording & WAV Export ---
    fun toggleRecording() {
        if (_isRecording.value) {
            stopAndSaveRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        _isRecording.value = true
        _engine.startRecording()
        if (!_isPlaying.value) {
            startTransport()
        }
    }

    private fun stopAndSaveRecording() {
        _isRecording.value = false
        val pcm = _engine.stopRecording()
        if (pcm.isNotEmpty()) {
            val app = getApplication<Application>()
            val recordingsDir = File(app.cacheDir, "recordings").apply { mkdirs() }
            val wavFile = File(recordingsDir, "daw_take_${System.currentTimeMillis()}.wav")
            WavWriter.createWavFile(wavFile, pcm, SynthEngine.SAMPLE_RATE, 1)
            _recordedWavFile.value = wavFile
        }
    }

    fun playRecordedAudio() {
        val file = _recordedWavFile.value ?: return
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _isAudioPlaying.value = false
                }
                start()
            }
            _isAudioPlaying.value = true
        } catch (e: Exception) {
            _isAudioPlaying.value = false
        }
    }

    fun stopRecordedAudio() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _isAudioPlaying.value = false
    }

    // --- Master Effects Rack Operations ---
    fun refreshRackModules() {
        _rackModules.value = _engine.effectsRack.getModules()
    }

    fun addEffectToRack(type: EffectType) {
        _engine.effectsRack.addModule(type)
        refreshRackModules()
    }

    fun removeEffectFromRack(id: String) {
        _engine.effectsRack.removeModule(id)
        refreshRackModules()
    }

    fun moveEffectInRack(fromIndex: Int, toIndex: Int) {
        _engine.effectsRack.moveModule(fromIndex, toIndex)
        refreshRackModules()
    }

    fun toggleEffectBypass(id: String) {
        _engine.effectsRack.toggleBypass(id)
        refreshRackModules()
    }

    fun loadRackPreset(presetName: String) {
        _selectedRackPreset.value = presetName
        _engine.effectsRack.loadPreset(presetName)
        refreshRackModules()
    }

    fun resetRackToDefault() {
        _selectedRackPreset.value = "Default Master Chain"
        _engine.effectsRack.resetToDefaultChain()
        refreshRackModules()
    }

    // --- Track-Level Automation Management ---
    fun selectAutomationParameter(param: AutomationParameter) {
        _selectedAutoParam.value = param
    }

    fun toggleAutomationLaneVisibility() {
        _isAutomationLaneVisible.value = !_isAutomationLaneVisible.value
    }

    fun setTrackAutomationParam(trackId: String, param: AutomationParameter) {
        val tracks = _arrangementTracks.value.toMutableList()
        val idx = tracks.indexOfFirst { it.id == trackId }
        if (idx != -1) {
            tracks[idx] = tracks[idx].copy(selectedAutomationParam = param)
            _arrangementTracks.value = tracks
        }
    }

    fun setTrackAutomationPoint(trackId: String, param: AutomationParameter, beat: Float, normalizedValue: Float) {
        val tracks = _arrangementTracks.value.toMutableList()
        val idx = tracks.indexOfFirst { it.id == trackId }
        if (idx != -1) {
            val track = tracks[idx]
            val currentLanes = track.automationLanes.toMutableMap()
            val currentLane = currentLanes[param] ?: AutomationLane.defaultLane(param)
            
            val snapBeat = ((beat * 4).toInt() / 4.0f).coerceIn(0f, 64f)
            val newPoints = currentLane.points.filter { kotlin.math.abs(it.beat - snapBeat) > 0.2f }.toMutableList()
            newPoints.add(AutomationPoint(snapBeat, normalizedValue.coerceIn(0f, 1f)))
            val sorted = newPoints.sortedBy { it.beat }

            currentLanes[param] = currentLane.copy(points = sorted, isEnabled = true)
            tracks[idx] = track.copy(automationLanes = currentLanes)
            _arrangementTracks.value = tracks

            // Also sync to lead/bass automation state if matching
            if (track.trackType == SessionTrackType.LEAD) {
                val leadMap = _leadAutomation.value.toMutableMap()
                leadMap[param] = currentLanes[param]!!
                _leadAutomation.value = leadMap
            } else if (track.trackType == SessionTrackType.BASS) {
                val bassMap = _bassAutomation.value.toMutableMap()
                bassMap[param] = currentLanes[param]!!
                _bassAutomation.value = bassMap
            }
        }
    }

    fun drawContinuousTrackAutomation(trackId: String, param: AutomationParameter, beat: Float, normalizedValue: Float) {
        val tracks = _arrangementTracks.value.toMutableList()
        val idx = tracks.indexOfFirst { it.id == trackId }
        if (idx != -1) {
            val track = tracks[idx]
            val currentLanes = track.automationLanes.toMutableMap()
            val currentLane = currentLanes[param] ?: AutomationLane.defaultLane(param)

            val clampedBeat = beat.coerceIn(0f, 64f)
            // Replace any point within 0.15 beats to avoid excessive density while keeping smooth curve
            val newPoints = currentLane.points.filter { kotlin.math.abs(it.beat - clampedBeat) > 0.15f }.toMutableList()
            newPoints.add(AutomationPoint(clampedBeat, normalizedValue.coerceIn(0f, 1f)))
            val sorted = newPoints.sortedBy { it.beat }

            currentLanes[param] = currentLane.copy(points = sorted, isEnabled = true)
            tracks[idx] = track.copy(automationLanes = currentLanes)
            _arrangementTracks.value = tracks

            if (track.trackType == SessionTrackType.LEAD) {
                val leadMap = _leadAutomation.value.toMutableMap()
                leadMap[param] = currentLanes[param]!!
                _leadAutomation.value = leadMap
            } else if (track.trackType == SessionTrackType.BASS) {
                val bassMap = _bassAutomation.value.toMutableMap()
                bassMap[param] = currentLanes[param]!!
                _bassAutomation.value = bassMap
            }
        }
    }

    fun removeTrackAutomationPoint(trackId: String, param: AutomationParameter, beat: Float) {
        val tracks = _arrangementTracks.value.toMutableList()
        val idx = tracks.indexOfFirst { it.id == trackId }
        if (idx != -1) {
            val track = tracks[idx]
            val currentLanes = track.automationLanes.toMutableMap()
            val currentLane = currentLanes[param] ?: return
            val filtered = currentLane.points.filter { kotlin.math.abs(it.beat - beat) > 0.35f }
            currentLanes[param] = currentLane.copy(points = filtered)
            tracks[idx] = track.copy(automationLanes = currentLanes)
            _arrangementTracks.value = tracks
        }
    }

    fun clearTrackAutomation(trackId: String, param: AutomationParameter) {
        val tracks = _arrangementTracks.value.toMutableList()
        val idx = tracks.indexOfFirst { it.id == trackId }
        if (idx != -1) {
            val track = tracks[idx]
            val currentLanes = track.automationLanes.toMutableMap()
            currentLanes[param] = AutomationLane.defaultLane(param)
            tracks[idx] = track.copy(automationLanes = currentLanes)
            _arrangementTracks.value = tracks

            if (track.trackType == SessionTrackType.LEAD) {
                val leadMap = _leadAutomation.value.toMutableMap()
                leadMap[param] = currentLanes[param]!!
                _leadAutomation.value = leadMap
            } else if (track.trackType == SessionTrackType.BASS) {
                val bassMap = _bassAutomation.value.toMutableMap()
                bassMap[param] = currentLanes[param]!!
                _bassAutomation.value = bassMap
            }
        }
    }

    fun applyTrackAutomationCurve(trackId: String, param: AutomationParameter, curveType: String) {
        val tracks = _arrangementTracks.value.toMutableList()
        val idx = tracks.indexOfFirst { it.id == trackId }
        if (idx != -1) {
            val track = tracks[idx]
            val currentLanes = track.automationLanes.toMutableMap()
            val currentLane = currentLanes[param] ?: AutomationLane.defaultLane(param)

            val points = when (curveType) {
                "Ramp Up" -> AutomationLane.generateRampUp(param)
                "Ramp Down" -> AutomationLane.generateRampDown(param)
                "Sine LFO" -> AutomationLane.generateSineWave(param, 2)
                "Triangle" -> AutomationLane.generateTriangle(param, 2)
                "Random Steps" -> AutomationLane.generateRandomSteps(param)
                else -> listOf(
                    AutomationPoint(0.0f, param.toNormalizedValue(param.defaultValue)),
                    AutomationPoint(16.0f, param.toNormalizedValue(param.defaultValue))
                )
            }

            currentLanes[param] = currentLane.copy(points = points, isEnabled = true)
            tracks[idx] = track.copy(automationLanes = currentLanes)
            _arrangementTracks.value = tracks

            if (track.trackType == SessionTrackType.LEAD) {
                val leadMap = _leadAutomation.value.toMutableMap()
                leadMap[param] = currentLanes[param]!!
                _leadAutomation.value = leadMap
            } else if (track.trackType == SessionTrackType.BASS) {
                val bassMap = _bassAutomation.value.toMutableMap()
                bassMap[param] = currentLanes[param]!!
                _bassAutomation.value = bassMap
            }
        }
    }

    fun setAutomationPoint(track: PianoRollTrack, param: AutomationParameter, beat: Float, normalizedValue: Float) {
        val targetMap = if (track == PianoRollTrack.LEAD) _leadAutomation else _bassAutomation
        val currentLanes = targetMap.value.toMutableMap()
        val currentLane = currentLanes[param] ?: AutomationLane.defaultLane(param)

        // Add or update point near beat (within 0.25 beat snap)
        val snapBeat = (beat * 4).toInt() / 4.0f
        val newPoints = currentLane.points.filter { kotlin.math.abs(it.beat - snapBeat) > 0.2f }.toMutableList()
        newPoints.add(AutomationPoint(snapBeat.coerceIn(0f, 16f), normalizedValue.coerceIn(0f, 1f)))
        val sortedPoints = newPoints.sortedBy { it.beat }

        currentLanes[param] = currentLane.copy(points = sortedPoints)
        targetMap.value = currentLanes
    }

    fun clearAutomationPoints(track: PianoRollTrack, param: AutomationParameter) {
        val targetMap = if (track == PianoRollTrack.LEAD) _leadAutomation else _bassAutomation
        val currentLanes = targetMap.value.toMutableMap()
        val currentLane = currentLanes[param] ?: AutomationLane.defaultLane(param)
        currentLanes[param] = AutomationLane.defaultLane(param)
        targetMap.value = currentLanes
    }

    fun toggleAutomationTrackEnabled(track: PianoRollTrack, param: AutomationParameter) {
        val targetMap = if (track == PianoRollTrack.LEAD) _leadAutomation else _bassAutomation
        val currentLanes = targetMap.value.toMutableMap()
        val currentLane = currentLanes[param] ?: AutomationLane.defaultLane(param)
        currentLanes[param] = currentLane.copy(isEnabled = !currentLane.isEnabled)
        targetMap.value = currentLanes
    }

    fun applyAutomationCurve(track: PianoRollTrack, param: AutomationParameter, curveType: String) {
        val targetMap = if (track == PianoRollTrack.LEAD) _leadAutomation else _bassAutomation
        val currentLanes = targetMap.value.toMutableMap()
        val currentLane = currentLanes[param] ?: AutomationLane.defaultLane(param)

        val points = when (curveType) {
            "Ramp Up" -> AutomationLane.generateRampUp(param)
            "Ramp Down" -> AutomationLane.generateRampDown(param)
            "Sine LFO" -> AutomationLane.generateSineWave(param, 2)
            "Triangle" -> AutomationLane.generateTriangle(param, 2)
            "Random Steps" -> AutomationLane.generateRandomSteps(param)
            else -> listOf(
                AutomationPoint(0.0f, param.toNormalizedValue(param.defaultValue)),
                AutomationPoint(16.0f, param.toNormalizedValue(param.defaultValue))
            )
        }

        currentLanes[param] = currentLane.copy(points = points, isEnabled = true)
        targetMap.value = currentLanes
    }

    private fun applyAutomationAtPosition(position: Float) {
        val liveMap = mutableMapOf<String, Float>()

        // 1. Lead Track Automation
        val leadAuto = _leadAutomation.value
        leadAuto[AutomationParameter.VOLUME]?.let { lane ->
            if (lane.isEnabled) {
                val v = lane.getValueAtBeat(position)
                _engine.synthVolume = v
                liveMap["lead_volume"] = v
            }
        }
        leadAuto[AutomationParameter.PAN]?.let { lane ->
            if (lane.isEnabled) {
                val v = lane.getValueAtBeat(position)
                _engine.synthPan = v
                liveMap["lead_pan"] = v
            }
        }
        leadAuto[AutomationParameter.FILTER_CUTOFF]?.let { lane ->
            if (lane.isEnabled) {
                val v = lane.getValueAtBeat(position)
                _engine.filterCutoff = v
                liveMap["lead_cutoff"] = v
            }
        }
        leadAuto[AutomationParameter.FILTER_RESONANCE]?.let { lane ->
            if (lane.isEnabled) {
                val v = lane.getValueAtBeat(position)
                _engine.filterResonance = v
                liveMap["lead_resonance"] = v
            }
        }
        leadAuto[AutomationParameter.LFO_RATE]?.let { lane ->
            if (lane.isEnabled) {
                val v = lane.getValueAtBeat(position)
                _engine.lfoFrequency = v
                liveMap["lead_lfo_rate"] = v
            }
        }
        leadAuto[AutomationParameter.REVERB_SEND]?.let { lane ->
            if (lane.isEnabled) {
                val v = lane.getValueAtBeat(position)
                _engine.reverb.mix = v
                liveMap["reverb_send"] = v
            }
        }
        leadAuto[AutomationParameter.DELAY_SEND]?.let { lane ->
            if (lane.isEnabled) {
                val v = lane.getValueAtBeat(position)
                _engine.stereoDelay.mix = v
                liveMap["delay_send"] = v
            }
        }

        // 2. Bass Track Automation
        val bassAuto = _bassAutomation.value
        bassAuto[AutomationParameter.VOLUME]?.let { lane ->
            if (lane.isEnabled) {
                val v = lane.getValueAtBeat(position)
                _engine.bassVolume = v
                liveMap["bass_volume"] = v
            }
        }
        bassAuto[AutomationParameter.PAN]?.let { lane ->
            if (lane.isEnabled) {
                val v = lane.getValueAtBeat(position)
                _engine.bassPan = v
                liveMap["bass_pan"] = v
            }
        }
        bassAuto[AutomationParameter.FILTER_CUTOFF]?.let { lane ->
            if (lane.isEnabled) {
                val v = lane.getValueAtBeat(position)
                _engine.bassCutoff = v
                liveMap["bass_cutoff"] = v
            }
        }
        bassAuto[AutomationParameter.FILTER_RESONANCE]?.let { lane ->
            if (lane.isEnabled) {
                val v = lane.getValueAtBeat(position)
                _engine.bassResonance = v
                liveMap["bass_resonance"] = v
            }
        }

        // Additional Lead Synth Parameter Automation
        leadAuto[AutomationParameter.DRIVE_DISTORTION]?.let { lane ->
            if (lane.isEnabled) {
                val v = lane.getValueAtBeat(position)
                _engine.distortion.drive = v
                liveMap["lead_drive"] = v
            }
        }
        leadAuto[AutomationParameter.CHORUS_MIX]?.let { lane ->
            if (lane.isEnabled) {
                val v = lane.getValueAtBeat(position)
                _engine.chorus.depth = v
                liveMap["lead_chorus"] = v
            }
        }
        leadAuto[AutomationParameter.FM_DEPTH]?.let { lane ->
            if (lane.isEnabled) {
                val v = lane.getValueAtBeat(position)
                _engine.fmDepth = v
                liveMap["lead_fm_depth"] = v
            }
        }
        leadAuto[AutomationParameter.WAVETABLE_POS]?.let { lane ->
            if (lane.isEnabled) {
                val v = lane.getValueAtBeat(position)
                _engine.wavetableSynth.tablePosition = v
                _wavetablePosition.value = v
                liveMap["lead_wt_pos"] = v
            }
        }
        leadAuto[AutomationParameter.SAMPLER_START]?.let { lane ->
            if (lane.isEnabled) {
                val v = lane.getValueAtBeat(position)
                _engine.samplerInstrument.startPoint = v
                _samplerStartPoint.value = v
                liveMap["smp_start"] = v
            }
        }

        _liveAutomatedValues.value = liveMap
    }

    // ==========================================
    // REAL-TIME AUTOMATION RECORDING ENGINE
    // ==========================================
    fun toggleAutomationRecordArm() {
        _isAutomationRecordArmed.value = !_isAutomationRecordArmed.value
        if (_isAutomationRecordArmed.value) {
            showToast("Automation Write: ARMED (Tweak any knob)")
        } else {
            showToast("Automation Write: OFF")
        }
    }

    fun recordParameterAutomation(param: AutomationParameter, trackType: SessionTrackType, normalizedValue: Float) {
        if (_isPlaying.value && _isAutomationRecordArmed.value) {
            val targetTrack = _arrangementTracks.value.find { it.trackType == trackType }
            val trackId = targetTrack?.id ?: if (trackType == SessionTrackType.BASS) "track_bass" else "track_lead"
            drawContinuousTrackAutomation(trackId, param, _playbackPosition.value, normalizedValue)
        }
    }

    // ==========================================
    // INSTRUMENT SELECTION & SYNTHESIS WORKSPACE
    // ==========================================
    fun selectInstrument(type: InstrumentType) {
        _activeInstrument.value = type
        _engine.activeInstrument = type
        showToast("Active Instrument: ${type.displayName}")
    }

    // --- Wavetable Engine Controls ---
    fun setWavetableBank(bank: WavetableBank) {
        _wavetableBank.value = bank
        _engine.wavetableSynth.currentBank = bank
    }

    fun setWavetablePosition(pos: Float) {
        val clamped = pos.coerceIn(0f, 1f)
        _wavetablePosition.value = clamped
        _engine.wavetableSynth.tablePosition = clamped
        recordParameterAutomation(AutomationParameter.WAVETABLE_POS, SessionTrackType.LEAD, clamped)
    }

    fun setWavetableWarpMode(mode: WavetableWarpMode) {
        _wavetableWarpMode.value = mode
        _engine.wavetableSynth.warpMode = mode
    }

    fun setWavetableWarpAmount(amount: Float) {
        val clamped = amount.coerceIn(0f, 1f)
        _wavetableWarpAmount.value = clamped
        _engine.wavetableSynth.warpAmount = clamped
    }

    fun setUnisonVoices(voices: Int) {
        val clamped = voices.coerceIn(1, 7)
        _unisonVoices.value = clamped
        _engine.wavetableSynth.unisonVoices = clamped
    }

    fun setUnisonDetune(detune: Float) {
        val clamped = detune.coerceIn(0f, 1f)
        _unisonDetune.value = clamped
        _engine.wavetableSynth.unisonDetune = clamped
    }

    // --- 4-Operator FM Synth Controls ---
    fun setFmAlgorithm(algo: FmAlgorithm) {
        _fmAlgorithm.value = algo
        _engine.fmSynth.algorithm = algo
    }

    fun setFmOperatorRatio(opIndex: Int, ratio: Float) {
        if (opIndex in 0..3) {
            _engine.fmSynth.operators[opIndex].ratio = ratio.coerceIn(0.25f, 16f)
        }
    }

    fun setFmOperatorLevel(opIndex: Int, level: Float) {
        if (opIndex in 0..3) {
            _engine.fmSynth.operators[opIndex].level = level.coerceIn(0f, 1f)
        }
    }

    fun setFmOperatorFeedback(opIndex: Int, feedback: Float) {
        if (opIndex in 0..3) {
            _engine.fmSynth.operators[opIndex].feedback = feedback.coerceIn(0f, 1f)
        }
    }

    fun setFmOperatorFineTune(opIndex: Int, fine: Float) {
        if (opIndex in 0..3) {
            _engine.fmSynth.operators[opIndex].fineTune = fine.coerceIn(-1f, 1f)
        }
    }

    // --- Ableton Simpler / Sampler Controls ---
    fun selectSamplerPreset(index: Int) {
        if (index in 0 until SamplerInstrument.SAMPLE_PRESETS_COUNT) {
            _samplerPresetIndex.value = index
            _engine.samplerInstrument.loadPreset(index)
            showToast("Loaded Sample: ${SamplerInstrument.getPresetName(index)}")
        }
    }

    fun setSamplerPlaybackMode(mode: SamplerPlaybackMode) {
        _samplerMode.value = mode
        _engine.samplerInstrument.mode = mode
    }

    fun setSamplerStartPoint(start: Float) {
        val clamped = start.coerceIn(0f, 0.95f)
        _samplerStartPoint.value = clamped
        _engine.samplerInstrument.startPoint = clamped
        recordParameterAutomation(AutomationParameter.SAMPLER_START, SessionTrackType.LEAD, clamped)
    }

    fun setSamplerEndPoint(end: Float) {
        val clamped = end.coerceIn(_samplerStartPoint.value + 0.05f, 1f)
        _samplerEndPoint.value = clamped
        _engine.samplerInstrument.endPoint = clamped
    }

    fun setSamplerLoopStart(start: Float) {
        val clamped = start.coerceIn(0f, 0.95f)
        _samplerLoopStart.value = clamped
        _engine.samplerInstrument.loopStart = clamped
    }

    fun setSamplerLoopEnd(end: Float) {
        val clamped = end.coerceIn(_samplerLoopStart.value + 0.05f, 1f)
        _samplerLoopEnd.value = clamped
        _engine.samplerInstrument.loopEnd = clamped
    }

    fun toggleSamplerLoop() {
        val newState = !_isSamplerLoopEnabled.value
        _isSamplerLoopEnabled.value = newState
        _engine.samplerInstrument.isLoopEnabled = newState
    }

    fun toggleSamplerReverse() {
        val newState = !_isSamplerReversed.value
        _isSamplerReversed.value = newState
        _engine.samplerInstrument.isReversed = newState
    }

    fun setSamplerTranspose(semitones: Int) {
        val clamped = semitones.coerceIn(-24, 24)
        _samplerTranspose.value = clamped
        _engine.samplerInstrument.transposeSemitones = clamped
        val norm = (clamped + 24f) / 48f
        recordParameterAutomation(AutomationParameter.SAMPLER_PITCH, SessionTrackType.LEAD, norm)
    }

    fun triggerSamplerSlice(sliceIndex: Int) {
        _activeSamplerSlice.value = sliceIndex
        _engine.samplerInstrument.triggerSlice(sliceIndex)
    }

    fun loadRecordedAudioToSampler() {
        val wavFile = _recordedWavFile.value
        if (wavFile != null && wavFile.exists()) {
            val audioBuffer = _engine.loadWavToSampler(wavFile)
            if (audioBuffer != null) {
                showToast("Loaded Live Recording into Simpler!")
                selectTab(DawTab.SAMPLER)
            } else {
                showToast("Could not load recorded file")
            }
        } else {
            showToast("No recorded WAV found. Record audio first!")
        }
    }

    // --- Physical Modeling & Vintage Keys Controls ---
    fun setRhodesTineDecay(decay: Float) {
        _rhodesTineDecay.value = decay.coerceIn(0.5f, 5f)
        _engine.electricPiano.tineDecay = _rhodesTineDecay.value
    }

    fun setRhodesTremolo(rate: Float, depth: Float) {
        _rhodesTremoloRate.value = rate.coerceIn(0.5f, 12f)
        _rhodesTremoloDepth.value = depth.coerceIn(0f, 1f)
        _engine.electricPiano.tremoloRate = _rhodesTremoloRate.value
        _engine.electricPiano.tremoloDepth = _rhodesTremoloDepth.value
    }

    fun setRhodesDrive(drive: Float) {
        _rhodesDrive.value = drive.coerceIn(0f, 1f)
        _engine.electricPiano.drive = _rhodesDrive.value
    }

    fun setStringPadChorus(depth: Float) {
        _stringPadChorus.value = depth.coerceIn(0f, 1f)
        _engine.stringPad.chorusDepth = _stringPadChorus.value
    }

    fun setStringPadSpeed(speed: Float) {
        _stringPadSpeed.value = speed.coerceIn(0.1f, 3f)
        _engine.stringPad.ensembleSpeed = _stringPadSpeed.value
    }

    fun toggleStringPadOctave() {
        val newState = !_stringPadOctave.value
        _stringPadOctave.value = newState
        _engine.stringPad.octaveLayer = if (newState) 0.5f else 0.0f
    }

    fun setFilterCutoff(cutoff: Float) {
        _engine.filterCutoff = cutoff.coerceIn(20f, 20000f)
    }

    fun undoAction() {
        showToast("Undo last edit")
    }

    fun duplicatePattern() {
        showToast("Duplicated pattern")
    }

    fun exportMasterWav() {
        showToast("Exporting project audio to WAV...")
    }

    fun createNewProject() {
        _currentProjectName.value = "New Project"
        showToast("Created new project")
    }

    override fun onCleared() {
        super.onCleared()
        stopTransport()
        mediaPlayer?.release()
        _engine.stop()
    }
}
