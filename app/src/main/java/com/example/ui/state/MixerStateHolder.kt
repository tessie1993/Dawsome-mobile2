package com.example.ui.state

import com.example.synth.domain.ProjectAction
import com.example.synth.domain.ProjectStore
import com.example.synth.domain.TrackModel
import com.example.synth.engine.EngineReadback
import com.example.synth.engine.MeterReading
import com.example.synth.engine.WireProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MixerUiState(
    val tracks: List<TrackModel>,
    val masterVolumeDb: Float,
    val selectedTrackId: String?
)

class MixerStateHolder(
    private val store: ProjectStore,
    readback: EngineReadback? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) {
    private val _state = MutableStateFlow(createUiState(store.state.value))
    val state: StateFlow<MixerUiState> = _state.asStateFlow()

    /**
     * Live engine meters keyed by track id (plus [MASTER_METER_KEY]) - the
     * first UI consumer of the MeterBus readback path. Kept separate from
     * [state] so ~30 Hz meter ticks never recompose the edit-driven strips.
     * Empty while the engine is unavailable (no-compile phase): the meters
     * simply rest dark.
     */
    val meters: StateFlow<Map<String, MeterReading>> =
        if (readback == null) {
            MutableStateFlow(emptyMap<String, MeterReading>()).asStateFlow()
        } else {
            combine(store.state, readback.meters) { project, byUid ->
                val idsByUid = HashMap<Long, String>(project.tracks.size * 2)
                for (t in project.tracks) {
                    idsByUid[WireProtocol.makeNodeUid(
                        WireProtocol.NODE_KIND_TRACK, t.id)] = t.id
                }
                idsByUid[WireProtocol.masterNodeUid] = MASTER_METER_KEY
                buildMap {
                    for ((uid, reading) in byUid) {
                        idsByUid[uid]?.let { put(it, reading) }
                    }
                }
            }.stateIn(scope, SharingStarted.Eagerly, emptyMap())
        }

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

    companion object {
        const val MASTER_METER_KEY = "master"
    }
}
