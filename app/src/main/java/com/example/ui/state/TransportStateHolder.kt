package com.example.ui.state

import com.example.synth.domain.MusicalScale
import com.example.synth.domain.ProjectAction
import com.example.synth.domain.ProjectStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TransportUiState(
    val isPlaying: Boolean,
    val isRecording: Boolean,
    val isLooping: Boolean,
    val bpm: Float,
    val timeSigNum: Int,
    val timeSigDen: Int,
    val keyRoot: Int,
    val scale: MusicalScale,
    val playheadBeat: Float,
    val loopStartBeat: Float,
    val loopEndBeat: Float,
    val timecodeFormatted: String
)

class TransportStateHolder(
    private val store: ProjectStore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) {
    private val _state = MutableStateFlow(createUiState(store.state.value))
    val state: StateFlow<TransportUiState> = _state.asStateFlow()

    init {
        scope.launch {
            store.state.collect { projectState ->
                _state.value = createUiState(projectState)
            }
        }
    }

    fun play() = store.dispatch(ProjectAction.Play)
    fun stop() = store.dispatch(ProjectAction.Stop)
    fun togglePlay() = store.dispatch(ProjectAction.TogglePlay)
    fun toggleRecord() = store.dispatch(ProjectAction.ToggleRecord)
    fun toggleLoop() = store.dispatch(ProjectAction.ToggleLoop)
    fun setBpm(bpm: Float) = store.dispatch(ProjectAction.SetBpm(bpm))
    fun seekToBeat(beat: Float) = store.dispatch(ProjectAction.SeekToBeat(beat))
    fun setLoopRegion(start: Float, end: Float) = store.dispatch(ProjectAction.SetLoopRegion(start, end))
    fun setScale(root: Int, scale: MusicalScale) = store.dispatch(ProjectAction.SetScale(root, scale))

    companion object {
        fun createUiState(p: com.example.synth.domain.ProjectState): TransportUiState {
            val totalSeconds = (p.playheadBeat / (p.bpm / 60.0f)).toInt()
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val millis = ((p.playheadBeat % 1.0f) * 100).toInt()
            val formatted = String.format("%02d:%02d:%02d", minutes, seconds, millis)

            return TransportUiState(
                isPlaying = p.isPlaying,
                isRecording = p.isRecording,
                isLooping = p.isLooping,
                bpm = p.bpm,
                timeSigNum = p.timeSigNum,
                timeSigDen = p.timeSigDen,
                keyRoot = p.keyRoot,
                scale = p.scale,
                playheadBeat = p.playheadBeat,
                loopStartBeat = p.loopStartBeat,
                loopEndBeat = p.loopEndBeat,
                timecodeFormatted = formatted
            )
        }
    }
}
