package com.example.ui.state

import com.example.synth.domain.ProjectAction
import com.example.synth.domain.ProjectStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MasteringUiState(
    val masterVolumeDb: Float = 0.0f,
    val limiterCeilingDb: Float = -0.3f,
    val momentaryLufs: Float = -14.2f,
    val shortTermLufs: Float = -14.0f,
    val integratedLufs: Float = -14.0f,
    val truePeakDb: Float = -0.3f,
    val dynamicRange: Float = 11.0f,
    val stereoCorrelation: Float = 0.85f,
    val isLimiterActive: Boolean = true,
    val isMultibandActive: Boolean = true
)

class MasteringStateHolder(private val store: ProjectStore) {
    private val _state = MutableStateFlow(MasteringUiState())
    val state: StateFlow<MasteringUiState> = _state.asStateFlow()

    fun setMasterVolume(volumeDb: Float) {
        store.dispatch(ProjectAction.SetMasterVolume(volumeDb))
        _state.value = _state.value.copy(masterVolumeDb = volumeDb)
    }

    fun setLimiterCeiling(ceilingDb: Float) {
        _state.value = _state.value.copy(limiterCeilingDb = ceilingDb)
    }

    fun toggleLimiter() {
        _state.value = _state.value.copy(isLimiterActive = !_state.value.isLimiterActive)
    }

    fun toggleMultiband() {
        _state.value = _state.value.copy(isMultibandActive = !_state.value.isMultibandActive)
    }
}
