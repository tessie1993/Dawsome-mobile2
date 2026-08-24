package com.example.ui.state

import com.example.synth.domain.ProjectAction
import com.example.synth.domain.ProjectStore
import com.example.synth.domain.TrackModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MixerUiState(
    val tracks: List<TrackModel>,
    val masterVolumeDb: Float,
    val selectedTrackId: String?
)

class MixerStateHolder(
    private val store: ProjectStore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) {
    private val _state = MutableStateFlow(createUiState(store.state.value))
    val state: StateFlow<MixerUiState> = _state.asStateFlow()

    init {
        scope.launch {
            store.state.collect { projectState ->
                _state.value = createUiState(projectState)
            }
        }
    }

    fun selectTrack(trackId: String) = store.dispatch(ProjectAction.SelectTrack(trackId))
    fun setTrackVolume(trackId: String, volumeDb: Float) = store.dispatch(ProjectAction.SetTrackVolume(trackId, volumeDb))
    fun setTrackPan(trackId: String, pan: Float) = store.dispatch(ProjectAction.SetTrackPan(trackId, pan))
    fun toggleMute(trackId: String) = store.dispatch(ProjectAction.ToggleTrackMute(trackId))
    fun toggleSolo(trackId: String) = store.dispatch(ProjectAction.ToggleTrackSolo(trackId))
    fun toggleArm(trackId: String) = store.dispatch(ProjectAction.ToggleTrackArm(trackId))
    fun setSend(trackId: String, sendIndex: Int, level: Float) = store.dispatch(ProjectAction.SetTrackSend(trackId, sendIndex, level))
    fun setMasterVolume(volumeDb: Float) = store.dispatch(ProjectAction.SetMasterVolume(volumeDb))

    private fun createUiState(p: com.example.synth.domain.ProjectState): MixerUiState {
        return MixerUiState(
            tracks = p.tracks,
            masterVolumeDb = p.masterVolumeDb,
            selectedTrackId = p.selectedTrackId
        )
    }
}
