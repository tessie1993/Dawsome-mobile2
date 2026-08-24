package com.example.synth

import com.example.synth.domain.DeviceType
import com.example.synth.domain.DrumPadType
import com.example.synth.domain.ProjectAction
import com.example.synth.domain.ProjectState
import com.example.synth.domain.ProjectStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real-Time Bridge connecting the UDF ProjectStore with the SynthEngine audio generation thread.
 */
class SynthEngineBridge(
    val engine: SynthEngine = SynthEngine(),
    private val store: ProjectStore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private var playbackJob: Job? = null

    init {
        // Start engine audio stream
        engine.start()

        // Attach listener to ProjectStore
        store.onEngineSync = { action, state ->
            handleAction(action, state)
        }

        // Launch sequencer clock loop
        startSequencerLoop()
    }

    private fun handleAction(action: ProjectAction, state: ProjectState) {
        when (action) {
            is ProjectAction.Play -> {
                // Ensure playback loop is running
                if (playbackJob == null || playbackJob?.isActive == false) {
                    startSequencerLoop()
                }
            }
            is ProjectAction.Stop -> {
                engine.panic()
            }
            is ProjectAction.SetBpm -> {
                // BPM updated in state
            }
            is ProjectAction.SetMasterVolume -> {
                val linearVol = Math.pow(10.0, (action.volumeDb / 20.0)).toFloat().coerceIn(0f, 1.5f)
                engine.masterVolume = linearVol
            }
            is ProjectAction.SetDeviceParam -> {
                updateDeviceParam(action.deviceId, action.paramName, action.value)
            }
            else -> {
                // Other actions
            }
        }
    }

    private fun updateDeviceParam(deviceId: String, paramName: String, value: Float) {
        when (paramName.lowercase()) {
            "cutoff" -> engine.filterCutoff = 40f + value * 16000f
            "reso", "resonance" -> engine.filterResonance = 0.5f + value * 7.5f
            "wt_pos", "wtpos" -> engine.wavetableSynth.tablePosition = value
            "fm_depth", "fmdepth" -> engine.fmSynth.opA.level = value
            "drive" -> engine.distortion.drive = value
            "reverb_mix", "reverb" -> engine.reverb.mix = value
            "delay_mix", "delay" -> engine.stereoDelay.mix = value
        }
    }

    private fun startSequencerLoop() {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            var currentBeat = 0.0f
            val stepIntervalMs = 20L // ~50Hz clock resolution for sequencing

            while (isActive) {
                val state = store.state.value
                if (state.isPlaying) {
                    val bpm = state.bpm.coerceIn(20f, 300f)
                    val beatsPerSec = bpm / 60.0f
                    val beatIncrement = beatsPerSec * (stepIntervalMs / 1000.0f)

                    val nextBeat = currentBeat + beatIncrement
                    val loopEnd = state.loopEndBeat.coerceAtLeast(state.loopStartBeat + 1f)

                    val clampedBeat = if (state.isLooping && nextBeat >= loopEnd) {
                        state.loopStartBeat
                    } else {
                        nextBeat
                    }

                    // Advance playhead in store
                    store.dispatch(ProjectAction.SeekToBeat(clampedBeat))
                    currentBeat = clampedBeat
                }
                delay(stepIntervalMs)
            }
        }
    }

    fun triggerNoteOn(pitch: Int, velocity: Float = 0.9f) {
        engine.noteOn(pitch)
    }

    fun triggerNoteOff(pitch: Int) {
        engine.noteOff(pitch)
    }

    fun triggerDrumPad(pad: DrumPadType, velocity: Float = 1.0f) {
        val drumType = when (pad) {
            DrumPadType.KICK, DrumPadType.SUB_BOOM -> DrumType.KICK
            DrumPadType.SNARE, DrumPadType.RIMSHOT -> DrumType.SNARE
            DrumPadType.HIHAT_CLOSED, DrumPadType.SHAKER -> DrumType.HIHAT_CLOSED
            DrumPadType.HIHAT_OPEN, DrumPadType.RIDE, DrumPadType.CRASH -> DrumType.HIHAT_OPEN
            DrumPadType.CLAP -> DrumType.CLAP
            else -> DrumType.TOM
        }
        engine.triggerDrum(drumType, velocity)
    }

    fun release() {
        playbackJob?.cancel()
        engine.stop()
    }
}
