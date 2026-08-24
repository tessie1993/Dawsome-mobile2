package com.example.ui.state

import com.example.synth.domain.ArrangementClip
import com.example.synth.domain.DrumPadType
import com.example.synth.domain.MidiNote
import com.example.synth.domain.MusicalScale
import com.example.synth.domain.ProjectAction
import com.example.synth.domain.ProjectStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PianoRollUiState(
    val activeTrackId: String?,
    val activeClipId: String?,
    val notes: List<MidiNote>,
    val drumSteps: Map<DrumPadType, List<Float>>,
    val playheadBeat: Float,
    val isPlaying: Boolean,
    val keyRoot: Int,
    val scale: MusicalScale,
    val isFoldToScale: Boolean = false,
    val isFoldToNotes: Boolean = false
)

class PianoRollStateHolder(
    private val store: ProjectStore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) {
    private val _state = MutableStateFlow(createUiState(store.state.value))
    val state: StateFlow<PianoRollUiState> = _state.asStateFlow()

    init {
        scope.launch {
            store.state.collect { projectState ->
                _state.value = createUiState(projectState)
            }
        }
    }

    fun addNote(note: MidiNote) {
        val st = _state.value
        if (st.activeTrackId != null && st.activeClipId != null) {
            store.dispatch(ProjectAction.AddMidiNote(st.activeTrackId, st.activeClipId, note))
        }
    }

    fun deleteNote(noteId: String) {
        val st = _state.value
        if (st.activeTrackId != null && st.activeClipId != null) {
            store.dispatch(ProjectAction.DeleteMidiNote(st.activeTrackId, st.activeClipId, noteId))
        }
    }

    fun quantizeNotes(gridBeat: Float = 0.25f) {
        val st = _state.value
        if (st.activeTrackId != null && st.activeClipId != null) {
            store.dispatch(ProjectAction.QuantizeClipNotes(st.activeTrackId, st.activeClipId, gridBeat))
        }
    }

    fun toggleDrumStep(pad: DrumPadType, stepBeat: Float) {
        val st = _state.value
        if (st.activeTrackId != null && st.activeClipId != null) {
            store.dispatch(ProjectAction.ToggleDrumStep(st.activeTrackId, st.activeClipId, pad, stepBeat))
        }
    }

    private fun createUiState(p: com.example.synth.domain.ProjectState): PianoRollUiState {
        val track = p.tracks.firstOrNull { it.id == p.selectedTrackId } ?: p.tracks.firstOrNull()
        val clip = track?.arrangementClips?.firstOrNull() ?: track?.sessionClips?.firstOrNull()

        return PianoRollUiState(
            activeTrackId = track?.id,
            activeClipId = clip?.id,
            notes = clip?.let {
                when (it) {
                    is ArrangementClip -> it.notes
                    is com.example.synth.domain.SessionClip -> it.notes
                    else -> emptyList()
                }
            } ?: emptyList(),
            drumSteps = clip?.let {
                when (it) {
                    is ArrangementClip -> it.drumSteps
                    is com.example.synth.domain.SessionClip -> it.drumSteps
                    else -> emptyMap()
                }
            } ?: emptyMap(),
            playheadBeat = p.playheadBeat,
            isPlaying = p.isPlaying,
            keyRoot = p.keyRoot,
            scale = p.scale
        )
    }
}
