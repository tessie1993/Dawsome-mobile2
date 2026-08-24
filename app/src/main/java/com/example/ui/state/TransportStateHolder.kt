package com.example.ui.state

import com.example.synth.domain.MusicalScale
import com.example.synth.domain.ProjectAction
import com.example.synth.domain.ProjectStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

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

class TransportStateHolder(private val store: ProjectStore) {
    val state: StateFlow<TransportUiState> = kotlinx.coroutines.flow.MutableStateFlow(
        createUiState(store.state.value)
    ).apply {
        // Observe store changes
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).let { scope ->
            scope.launchWhenCreated {
                store.state.collect { projectState ->
                    this@apply.value = createUiState(projectState)
                }
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

@Suppress("DEPRECATION")
private fun kotlinx.coroutines.CoroutineScope.launchWhenCreated(block: suspend () -> Unit) {
    launch(kotlinx.coroutines.Dispatchers.Default) { block() }
}
