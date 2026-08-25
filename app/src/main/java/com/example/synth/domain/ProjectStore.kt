package com.example.synth.domain

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Centralized Unidirectional Data Flow Store for DAW Project State.
 * Handles thread-safe state reduction, undo/redo history, and engine notifications.
 *
 * Every state change carries a monotonic [editSeq] (blueprint 2.2 ordering
 * rule): the engine sync layer stamps messages and model-delta bundles with
 * it, and compiled engine artifacts record the editSeq they were built from.
 * Undo/redo notify the sync listener with a null action - the state is
 * authoritative and the engine resyncs wholesale.
 */
class ProjectStore(
    initialState: ProjectState = createDefaultProject(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<ProjectState> = _state.asStateFlow()

    private val undoStack = ArrayDeque<ProjectState>()
    private val redoStack = ArrayDeque<ProjectState>()

    private val editSeqCounter = AtomicInteger(0)

    /** The sequence number of the latest published state change. */
    val editSeq: Int get() = editSeqCounter.get()

    /** Engine synchronization listener. Null action = undo/redo/full resync. */
    var onEngineSync: ((ProjectAction?, ProjectState, Int) -> Unit)? = null

    fun dispatch(action: ProjectAction) {
        scope.launch {
            val currentState = _state.value
            val nextState = reduce(currentState, action)

            if (isStateChangeUndoable(action) && nextState != currentState) {
                undoStack.addLast(currentState)
                if (undoStack.size > 50) undoStack.removeFirst()
                redoStack.clear()
            }

            _state.value = nextState
            onEngineSync?.invoke(action, nextState, editSeqCounter.incrementAndGet())
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeLast()
            redoStack.addLast(_state.value)
            _state.value = prev
            onEngineSync?.invoke(null, prev, editSeqCounter.incrementAndGet())
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeLast()
            undoStack.addLast(_state.value)
            _state.value = next
            onEngineSync?.invoke(null, next, editSeqCounter.incrementAndGet())
        }
    }

    private fun reduce(current: ProjectState, action: ProjectAction): ProjectState {
        return when (action) {
            is ProjectAction.Play -> current.copy(isPlaying = true)
            is ProjectAction.Stop -> current.copy(isPlaying = false, playheadBeat = 0.0f)
            is ProjectAction.TogglePlay -> current.copy(isPlaying = !current.isPlaying)
            is ProjectAction.ToggleRecord -> current.copy(isRecording = !current.isRecording)
            is ProjectAction.ToggleLoop -> current.copy(isLooping = !current.isLooping)
            is ProjectAction.SetBpm -> current.copy(bpm = action.bpm.coerceIn(20f, 300f))
            is ProjectAction.SeekToBeat -> current.copy(playheadBeat = action.beat.coerceAtLeast(0f))
            is ProjectAction.SetLoopRegion -> current.copy(
                loopStartBeat = action.startBeat,
                loopEndBeat = action.endBeat.coerceAtLeast(action.startBeat + 1f)
            )
            is ProjectAction.SetScale -> current.copy(keyRoot = action.root, scale = action.scale)
            is ProjectAction.SelectTab -> current.copy(activeTab = action.tab)

            is ProjectAction.AddTrack -> {
                val newTrack = TrackModel(
                    name = action.name,
                    type = action.type,
                    colorHex = getTrackDefaultColor(action.type)
                )
                current.copy(
                    tracks = current.tracks + newTrack,
                    selectedTrackId = current.selectedTrackId ?: newTrack.id
                )
            }
            is ProjectAction.DeleteTrack -> {
                current.copy(
                    tracks = current.tracks.filterNot { it.id == action.trackId },
                    selectedTrackId = if (current.selectedTrackId == action.trackId) current.tracks.firstOrNull { it.id != action.trackId }?.id else current.selectedTrackId
                )
            }
            is ProjectAction.SelectTrack -> current.copy(selectedTrackId = action.trackId)
            is ProjectAction.SetTrackVolume -> current.copy(
                tracks = current.tracks.map {
                    if (it.id == action.trackId) it.copy(volumeDb = action.volumeDb) else it
                }
            )
            is ProjectAction.SetTrackPan -> current.copy(
                tracks = current.tracks.map {
                    if (it.id == action.trackId) it.copy(pan = action.pan.coerceIn(-1f, 1f)) else it
                }
            )
            is ProjectAction.ToggleTrackMute -> current.copy(
                tracks = current.tracks.map {
                    if (it.id == action.trackId) it.copy(isMuted = !it.isMuted) else it
                }
            )
            is ProjectAction.ToggleTrackSolo -> current.copy(
                tracks = current.tracks.map {
                    if (it.id == action.trackId) it.copy(isSoloed = !it.isSoloed) else it
                }
            )
            is ProjectAction.ToggleTrackArm -> current.copy(
                tracks = current.tracks.map {
                    if (it.id == action.trackId) it.copy(isArmed = !it.isArmed) else it
                }
            )
            is ProjectAction.SetTrackSend -> current.copy(
                tracks = current.tracks.map {
                    if (it.id == action.trackId) {
                        if (action.sendIndex == 0) it.copy(sendLevelA = action.level.coerceIn(0f, 1f))
                        else it.copy(sendLevelB = action.level.coerceIn(0f, 1f))
                    } else it
                }
            )

            is ProjectAction.AddArrangementClip -> current.copy(
                tracks = current.tracks.map { track ->
                    if (track.id == action.clip.trackId) track.copy(arrangementClips = track.arrangementClips + action.clip)
                    else track
                }
            )
            is ProjectAction.MoveArrangementClip -> current.copy(
                tracks = current.tracks.map { track ->
                    track.copy(
                        arrangementClips = track.arrangementClips.map { clip ->
                            if (clip.id == action.clipId) clip.copy(startBeat = action.newStartBeat) else clip
                        }
                    )
                }
            )
            is ProjectAction.ResizeArrangementClip -> current.copy(
                tracks = current.tracks.map { track ->
                    track.copy(
                        arrangementClips = track.arrangementClips.map { clip ->
                            if (clip.id == action.clipId) clip.copy(lengthBeats = action.newLengthBeats.coerceAtLeast(0.25f)) else clip
                        }
                    )
                }
            )
            is ProjectAction.DeleteArrangementClip -> current.copy(
                tracks = current.tracks.map { track ->
                    track.copy(arrangementClips = track.arrangementClips.filterNot { it.id == action.clipId })
                }
            )

            is ProjectAction.TriggerSessionClip -> current.copy(
                tracks = current.tracks.map { track ->
                    if (track.id == action.trackId) {
                        track.copy(
                            isOverriddenBySession = true,
                            sessionClips = track.sessionClips.map { clip ->
                                clip.copy(isPlaying = clip.slotIndex == action.slotIndex)
                            }
                        )
                    } else track
                }
            )
            is ProjectAction.TriggerScene -> current.copy(
                tracks = current.tracks.map { track ->
                    track.copy(
                        isOverriddenBySession = true,
                        sessionClips = track.sessionClips.map { clip ->
                            clip.copy(isPlaying = clip.slotIndex == action.sceneIndex)
                        }
                    )
                }
            )
            is ProjectAction.ReturnTrackToArrangement -> current.copy(
                tracks = current.tracks.map { track ->
                    if (track.id == action.trackId) {
                        track.copy(
                            isOverriddenBySession = false,
                            sessionClips = track.sessionClips.map { it.copy(isPlaying = false) }
                        )
                    } else track
                }
            )
            is ProjectAction.ReturnAllToArrangement -> current.copy(
                tracks = current.tracks.map { track ->
                    track.copy(
                        isOverriddenBySession = false,
                        sessionClips = track.sessionClips.map { it.copy(isPlaying = false) }
                    )
                }
            )

            is ProjectAction.AddMidiNote -> current.copy(
                tracks = current.tracks.map { track ->
                    if (track.id == action.trackId) {
                        track.copy(
                            arrangementClips = track.arrangementClips.map { clip ->
                                if (clip.id == action.clipId) clip.copy(notes = clip.notes + action.note) else clip
                            },
                            sessionClips = track.sessionClips.map { clip ->
                                if (clip.id == action.clipId) clip.copy(notes = clip.notes + action.note) else clip
                            }
                        )
                    } else track
                }
            )
            is ProjectAction.DeleteMidiNote -> current.copy(
                tracks = current.tracks.map { track ->
                    if (track.id == action.trackId) {
                        track.copy(
                            arrangementClips = track.arrangementClips.map { clip ->
                                if (clip.id == action.clipId) clip.copy(notes = clip.notes.filterNot { it.id == action.noteId }) else clip
                            },
                            sessionClips = track.sessionClips.map { clip ->
                                if (clip.id == action.clipId) clip.copy(notes = clip.notes.filterNot { it.id == action.noteId }) else clip
                            }
                        )
                    } else track
                }
            )
            is ProjectAction.QuantizeClipNotes -> current.copy(
                tracks = current.tracks.map { track ->
                    if (track.id == action.trackId) {
                        val quantizeFn: (MidiNote) -> MidiNote = { n ->
                            val grid = action.gridBeat
                            val quantizedStart = Math.round(n.startBeat / grid) * grid
                            n.copy(startBeat = quantizedStart)
                        }
                        track.copy(
                            arrangementClips = track.arrangementClips.map { clip ->
                                if (clip.id == action.clipId) clip.copy(notes = clip.notes.map(quantizeFn)) else clip
                            },
                            sessionClips = track.sessionClips.map { clip ->
                                if (clip.id == action.clipId) clip.copy(notes = clip.notes.map(quantizeFn)) else clip
                            }
                        )
                    } else track
                }
            )
            is ProjectAction.ToggleDrumStep -> current.copy(
                tracks = current.tracks.map { track ->
                    if (track.id == action.trackId) {
                        val toggleFn: (Map<DrumPadType, List<Float>>) -> Map<DrumPadType, List<Float>> = { steps ->
                            val currentSteps = steps[action.pad] ?: emptyList()
                            val updatedSteps = if (currentSteps.contains(action.stepBeat)) {
                                currentSteps.filterNot { it == action.stepBeat }
                            } else {
                                (currentSteps + action.stepBeat).sorted()
                            }
                            steps + (action.pad to updatedSteps)
                        }
                        track.copy(
                            arrangementClips = track.arrangementClips.map { clip ->
                                if (clip.id == action.clipId) clip.copy(drumSteps = toggleFn(clip.drumSteps)) else clip
                            },
                            sessionClips = track.sessionClips.map { clip ->
                                if (clip.id == action.clipId) clip.copy(drumSteps = toggleFn(clip.drumSteps)) else clip
                            }
                        )
                    } else track
                }
            )

            is ProjectAction.AddDevice -> current.copy(
                tracks = current.tracks.map { track ->
                    if (track.id == action.trackId) track.copy(devices = track.devices + DeviceModel(type = action.type))
                    else track
                }
            )
            is ProjectAction.RemoveDevice -> current.copy(
                tracks = current.tracks.map { track ->
                    if (track.id == action.trackId) track.copy(devices = track.devices.filterNot { it.id == action.deviceId })
                    else track
                }
            )
            is ProjectAction.SetDeviceParam -> current.copy(
                tracks = current.tracks.map { track ->
                    if (track.id == action.trackId) {
                        track.copy(
                            devices = track.devices.map { dev ->
                                if (dev.id == action.deviceId) dev.copy(params = dev.params + (action.paramName to action.value))
                                else dev
                            }
                        )
                    } else track
                }
            )
            is ProjectAction.ToggleDeviceEnabled -> current.copy(
                tracks = current.tracks.map { track ->
                    if (track.id == action.trackId) {
                        track.copy(
                            devices = track.devices.map { dev ->
                                if (dev.id == action.deviceId) dev.copy(isEnabled = !dev.isEnabled) else dev
                            }
                        )
                    } else track
                }
            )

            is ProjectAction.SetMasterVolume -> current.copy(masterVolumeDb = action.volumeDb)
        }
    }

    private fun isStateChangeUndoable(action: ProjectAction): Boolean {
        return when (action) {
            is ProjectAction.Play, is ProjectAction.Stop, is ProjectAction.TogglePlay,
            is ProjectAction.SeekToBeat, is ProjectAction.SelectTab, is ProjectAction.SelectTrack -> false
            else -> true
        }
    }

    companion object {
        fun getTrackDefaultColor(type: TrackType): String {
            return when (type) {
                TrackType.DRUM -> "#D96B27"     // Autumn Rust
                TrackType.MIDI -> "#FF7600"     // Earth Amber
                TrackType.AUDIO -> "#2E7D4E"    // Nature Emerald
                TrackType.RETURN -> "#6B8E23"   // Nature Moss Sage
                TrackType.MASTER -> "#D4AF37"   // Autumn Harvest Gold
            }
        }

        fun createDefaultProject(): ProjectState {
            val leadTrackId = "track_lead"
            val bassTrackId = "track_bass"
            val drumTrackId = "track_drums"

            val initialTracks = listOf(
                TrackModel(
                    id = leadTrackId,
                    name = "Lead Synth",
                    type = TrackType.MIDI,
                    colorHex = "#FF7600",
                    volumeDb = -2.0f,
                    devices = listOf(
                        DeviceModel(type = DeviceType.WAVETABLE_SYNTH, name = "Wavetable Lab"),
                        DeviceModel(type = DeviceType.DELAY, name = "Ping-Pong Delay")
                    ),
                    arrangementClips = listOf(
                        ArrangementClip(
                            id = "clip_lead_arr",
                            name = "Synth Lead Hook",
                            trackId = leadTrackId,
                            startBeat = 0.0f,
                            lengthBeats = 16.0f,
                            notes = listOf(
                                MidiNote(pitch = 60, startBeat = 0.0f, lengthBeats = 1.0f),
                                MidiNote(pitch = 63, startBeat = 1.0f, lengthBeats = 1.0f),
                                MidiNote(pitch = 67, startBeat = 2.0f, lengthBeats = 2.0f),
                                MidiNote(pitch = 70, startBeat = 4.0f, lengthBeats = 2.0f)
                            )
                        )
                    ),
                    sessionClips = (0..7).map { slot ->
                        SessionClip(
                            name = "Lead Riff ${slot + 1}",
                            trackId = leadTrackId,
                            slotIndex = slot,
                            notes = if (slot == 0) listOf(
                                MidiNote(pitch = 60, startBeat = 0.0f, lengthBeats = 1.0f),
                                MidiNote(pitch = 63, startBeat = 1.0f, lengthBeats = 1.0f),
                                MidiNote(pitch = 67, startBeat = 2.0f, lengthBeats = 2.0f)
                            ) else emptyList()
                        )
                    }
                ),
                TrackModel(
                    id = bassTrackId,
                    name = "Analog Bass",
                    type = TrackType.MIDI,
                    colorHex = "#C85A32",
                    volumeDb = -1.0f,
                    devices = listOf(
                        DeviceModel(type = DeviceType.SUBTRACTIVE_SYNTH, name = "Moog Sub Bass"),
                        DeviceModel(type = DeviceType.DISTORTION, name = "Analog Saturation")
                    ),
                    arrangementClips = listOf(
                        ArrangementClip(
                            id = "clip_bass_arr",
                            name = "Rolling 808 Bass",
                            trackId = bassTrackId,
                            startBeat = 0.0f,
                            lengthBeats = 16.0f,
                            notes = listOf(
                                MidiNote(pitch = 36, startBeat = 0.0f, lengthBeats = 2.0f),
                                MidiNote(pitch = 36, startBeat = 2.0f, lengthBeats = 2.0f),
                                MidiNote(pitch = 39, startBeat = 4.0f, lengthBeats = 2.0f),
                                MidiNote(pitch = 41, startBeat = 6.0f, lengthBeats = 2.0f)
                            )
                        )
                    ),
                    sessionClips = (0..7).map { slot ->
                        SessionClip(
                            name = "Bass Groove ${slot + 1}",
                            trackId = bassTrackId,
                            slotIndex = slot
                        )
                    }
                ),
                TrackModel(
                    id = drumTrackId,
                    name = "808 Drum Rack",
                    type = TrackType.DRUM,
                    colorHex = "#D96B27",
                    volumeDb = 0.0f,
                    devices = listOf(
                        DeviceModel(type = DeviceType.DRUM_RACK, name = "16-Pad Drum Rack"),
                        DeviceModel(type = DeviceType.COMPRESSOR, name = "Drum Bus Glue")
                    ),
                    arrangementClips = listOf(
                        ArrangementClip(
                            id = "clip_drums_arr",
                            name = "Main Beat",
                            trackId = drumTrackId,
                            startBeat = 0.0f,
                            lengthBeats = 16.0f,
                            drumSteps = mapOf(
                                DrumPadType.KICK to listOf(0f, 4f, 8f, 12f),
                                DrumPadType.SNARE to listOf(4f, 12f),
                                DrumPadType.HIHAT_CLOSED to (0..15).map { it.toFloat() }
                            )
                        )
                    ),
                    sessionClips = (0..7).map { slot ->
                        SessionClip(
                            name = "Drum Pattern ${slot + 1}",
                            trackId = drumTrackId,
                            slotIndex = slot,
                            drumSteps = if (slot == 0) mapOf(
                                DrumPadType.KICK to listOf(0f, 4f, 8f, 12f),
                                DrumPadType.SNARE to listOf(4f, 12f)
                            ) else emptyMap()
                        )
                    }
                )
            )

            val initialScenes = (0..7).map { i ->
                SessionScene(
                    name = listOf("Intro", "Build Up", "Main Drop", "Breakdown", "Verse 2", "Chorus", "Bridge", "Outro")[i],
                    index = i
                )
            }

            return ProjectState(
                name = "Cyberpunk Obsidian",
                bpm = 120.0f,
                tracks = initialTracks,
                scenes = initialScenes,
                selectedTrackId = leadTrackId
            )
        }
    }
}
