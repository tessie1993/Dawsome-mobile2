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
    val isMetronomeOn: Boolean,
    val bpm: Float,
    val timeSigNum: Int,
    val timeSigDen: Int,
    val keyRoot: Int,
    val scale: MusicalScale,
    val playheadBeat: Float,
    val loopStartBeat: Float,
    val loopEndBeat: Float,
    val barsBeatsFormatted: String,
    val projectName: String
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
    fun toggleMetronome() = store.dispatch(ProjectAction.ToggleMetronome)
    fun setBpm(bpm: Float) = store.dispatch(ProjectAction.SetBpm(bpm))
    fun seekToBeat(beat: Float) = store.dispatch(ProjectAction.SeekToBeat(beat))
    fun setLoopRegion(start: Float, end: Float) = store.dispatch(ProjectAction.SetLoopRegion(start, end))
    fun setScale(root: Int, scale: MusicalScale) = store.dispatch(ProjectAction.SetScale(root, scale))

    companion object {
        fun createUiState(p: com.example.synth.domain.ProjectState): TransportUiState {
            // No min:sec timecode here yet (review cycle-2): deriving wall time
            // from playheadBeat/bpm is wrong once tempo maps land - when a
            // timecode view arrives it reads the engine's sample-position
            // readback instead.
            //
            // Reference readout format: bars.beats.sixteenths (001.03.00).
            // playheadBeat is in QUARTER notes; a bar is num * 4/den quarters
            // (the engine's barBeats() rule) - dividing by the numerator alone
            // is only right for x/4 signatures (review cycle-2 finding).
            val num = if (p.timeSigNum > 0) p.timeSigNum else 4
            val den = if (p.timeSigDen > 0) p.timeSigDen else 4
            val denomBeatQuarters = 4.0f / den
            val barQuarters = num * denomBeatQuarters
            val posInBar = p.playheadBeat % barQuarters
            val beatPos = posInBar / denomBeatQuarters
            val bar = (p.playheadBeat / barQuarters).toInt() + 1
            val beatInBar = beatPos.toInt() + 1
            val sixteenth = ((beatPos % 1.0f) * 4).toInt()
            val barsBeats = String.format(
                java.util.Locale.ROOT, "%03d.%02d.%02d", bar, beatInBar, sixteenth)

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
                barsBeatsFormatted = barsBeats,
                projectName = p.name,
                isMetronomeOn = p.isMetronomeOn
            )
        }
    }
}
