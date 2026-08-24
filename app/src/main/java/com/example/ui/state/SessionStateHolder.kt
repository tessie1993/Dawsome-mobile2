package com.example.ui.state

import com.example.synth.domain.ProjectAction
import com.example.synth.domain.ProjectStore
import com.example.synth.domain.SessionScene
import com.example.synth.domain.TrackModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionUiState(
    val tracks: List<TrackModel>,
    val scenes: List<SessionScene>,
    val isPlaying: Boolean,
    val selectedTrackId: String?
)

class SessionStateHolder(
    private val store: ProjectStore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) {
    private val _state = MutableStateFlow(createUiState(store.state.value))
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    init {
        scope.launch {
            store.state.collect { projectState ->
                _state.value = createUiState(projectState)
            }
        }
    }

    fun triggerClip(trackId: String, slotIndex: Int) = store.dispatch(ProjectAction.TriggerSessionClip(trackId, slotIndex))
    fun triggerScene(sceneIndex: Int) = store.dispatch(ProjectAction.TriggerScene(sceneIndex))
    fun returnTrackToArrangement(trackId: String) = store.dispatch(ProjectAction.ReturnTrackToArrangement(trackId))
    fun returnAllToArrangement() = store.dispatch(ProjectAction.ReturnAllToArrangement)

    private fun createUiState(p: com.example.synth.domain.ProjectState): SessionUiState {
        return SessionUiState(
            tracks = p.tracks,
            scenes = p.scenes,
            isPlaying = p.isPlaying,
            selectedTrackId = p.selectedTrackId
        )
    }
}
