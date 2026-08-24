package com.example.ui.state

import com.example.synth.domain.DeviceModel
import com.example.synth.domain.DeviceType
import com.example.synth.domain.ProjectAction
import com.example.synth.domain.ProjectStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceRackUiState(
    val selectedTrackId: String?,
    val selectedTrackName: String,
    val devices: List<DeviceModel>
)

class DeviceRackStateHolder(
    private val store: ProjectStore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) {
    private val _state = MutableStateFlow(createUiState(store.state.value))
    val state: StateFlow<DeviceRackUiState> = _state.asStateFlow()

    init {
        scope.launch {
            store.state.collect { projectState ->
                _state.value = createUiState(projectState)
            }
        }
    }

    fun addDevice(trackId: String, type: DeviceType) = store.dispatch(ProjectAction.AddDevice(trackId, type))
    fun removeDevice(trackId: String, deviceId: String) = store.dispatch(ProjectAction.RemoveDevice(trackId, deviceId))
    fun toggleDevice(trackId: String, deviceId: String) = store.dispatch(ProjectAction.ToggleDeviceEnabled(trackId, deviceId))
    fun setParam(trackId: String, deviceId: String, paramName: String, value: Float) =
        store.dispatch(ProjectAction.SetDeviceParam(trackId, deviceId, paramName, value))

    private fun createUiState(p: com.example.synth.domain.ProjectState): DeviceRackUiState {
        val selTrack = p.tracks.firstOrNull { it.id == p.selectedTrackId } ?: p.tracks.firstOrNull()
        return DeviceRackUiState(
            selectedTrackId = selTrack?.id,
            selectedTrackName = selTrack?.name ?: "No Track Selected",
            devices = selTrack?.devices ?: emptyList()
        )
    }
}
