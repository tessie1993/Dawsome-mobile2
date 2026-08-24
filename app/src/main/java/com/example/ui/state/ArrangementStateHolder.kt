package com.example.ui.state

import com.example.synth.domain.ArrangementClip
import com.example.synth.domain.ProjectAction
import com.example.synth.domain.ProjectStore
import com.example.synth.domain.TrackModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArrangementUiState(
    val tracks: List<TrackModel>,
    val playheadBeat: Float,
    val isPlaying: Boolean,
    val loopStartBeat: Float,
    val loopEndBeat: Float,
    val isLooping: Boolean,
    val selectedTrackId: String?,
    val selectedClipId: String?,
    val zoomLevel: Float = 1.0f
)

class ArrangementStateHolder(
    private val store: ProjectStore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) {
    private val _state = MutableStateFlow(createUiState(store.state.value))
    val state: StateFlow<ArrangementUiState> = _state.asStateFlow()

    init {
        scope.launch {
            store.state.collect { projectState ->
                _state.value = createUiState(projectState)
            }
        }
    }

    fun selectTrack(trackId: String) = store.dispatch(ProjectAction.SelectTrack(trackId))
    fun moveClip(clipId: String, newStartBeat: Float) = store.dispatch(ProjectAction.MoveArrangementClip(clipId, newStartBeat))
    fun resizeClip(clipId: String, newLengthBeats: Float) = store.dispatch(ProjectAction.ResizeArrangementClip(clipId, newLengthBeats))
    fun deleteClip(clipId: String) = store.dispatch(ProjectAction.DeleteArrangementClip(clipId))
    fun addClip(clip: ArrangementClip) = store.dispatch(ProjectAction.AddArrangementClip(clip))
    fun seekToBeat(beat: Float) = store.dispatch(ProjectAction.SeekToBeat(beat))

    private fun createUiState(p: com.example.synth.domain.ProjectState): ArrangementUiState {
        return ArrangementUiState(
            tracks = p.tracks,
            playheadBeat = p.playheadBeat,
            isPlaying = p.isPlaying,
            loopStartBeat = p.loopStartBeat,
            loopEndBeat = p.loopEndBeat,
            isLooping = p.isLooping,
            selectedTrackId = p.selectedTrackId,
            selectedClipId = p.selectedClipId
        )
    }
}
