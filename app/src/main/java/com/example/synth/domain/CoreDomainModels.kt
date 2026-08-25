package com.example.synth.domain

import java.util.UUID

/**
 * Track Types according to docs/spec/SPEC_PART1_FUNCTIONAL.md & ARCHITECTURE_BLUEPRINT.md
 */
enum class TrackType(val displayName: String) {
    MIDI("MIDI / Instrument"),
    AUDIO("Audio Track"),
    DRUM("Drum Rack"),
    RETURN("Return / Aux"),
    MASTER("Master Bus")
}

/**
 * Musical Scale definitions with intervals.
 */
enum class MusicalScale(val displayName: String, val intervals: List<Int>) {
    CHROMATIC("Chromatic (All)", listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)),
    MAJOR("Major (Ionian)", listOf(0, 2, 4, 5, 7, 9, 11)),
    NATURAL_MINOR("Minor (Aeolian)", listOf(0, 2, 3, 5, 7, 8, 10)),
    DORIAN("Dorian (Cyberpunk)", listOf(0, 2, 3, 5, 7, 9, 10)),
    PENTATONIC_MAJOR("Pentatonic Major", listOf(0, 2, 4, 7, 9)),
    PENTATONIC_MINOR("Pentatonic Minor", listOf(0, 3, 5, 7, 10)),
    BLUES("Blues Scale", listOf(0, 3, 5, 6, 7, 10)),
    HIRAJOSHI("Japanese (Hirajoshi)", listOf(0, 2, 3, 7, 8)),
    ARABIC("Arabic (Double Harmonic)", listOf(0, 1, 4, 5, 7, 8, 11))
}

/**
 * Continuous and Stepped Automation Parameters.
 */
enum class AutomationParameter(
    val id: String,
    val displayName: String,
    val unit: String,
    val minValue: Float,
    val maxValue: Float,
    val defaultValue: Float,
    val isLogarithmic: Boolean = false
) {
    VOLUME("volume", "Volume", "dB", -60.0f, 6.0f, 0.0f),
    PAN("pan", "Pan", "L/R", -1.0f, 1.0f, 0.0f),
    FILTER_CUTOFF("cutoff", "Filter Cutoff", "Hz", 40.0f, 16000.0f, 2500.0f, true),
    FILTER_RESONANCE("resonance", "Resonance", "Q", 0.5f, 8.0f, 1.5f),
    LFO_RATE("lfo_rate", "LFO Speed", "Hz", 0.1f, 20.0f, 3.0f),
    REVERB_SEND("reverb_send", "Reverb Send", "%", 0.0f, 1.0f, 0.2f),
    DELAY_SEND("delay_send", "Delay Send", "%", 0.0f, 1.0f, 0.25f),
    DRIVE_DISTORTION("drive", "Drive Saturation", "%", 0.0f, 1.0f, 0.1f),
    CHORUS_MIX("chorus", "Chorus Depth", "%", 0.0f, 1.0f, 0.25f),
    FM_DEPTH("fm_depth", "FM Cross Mod", "%", 0.0f, 1.0f, 0.0f),
    WAVETABLE_POS("wt_pos", "Wavetable Position", "%", 0.0f, 1.0f, 0.35f),
    SAMPLER_START("smp_start", "Sample Start Trim", "%", 0.0f, 1.0f, 0.0f),
    SAMPLER_PITCH("smp_pitch", "Sample Transpose", "st", -24.0f, 24.0f, 0.0f);

    fun formatValue(normalized: Float): String {
        val actual = toActualValue(normalized)
        return when (this) {
            VOLUME -> String.format("%.1f dB", actual)
            DRIVE_DISTORTION, CHORUS_MIX, FM_DEPTH, WAVETABLE_POS, SAMPLER_START, REVERB_SEND, DELAY_SEND ->
                "${(actual * 100).toInt()}%"
            PAN -> when {
                actual < -0.05f -> "L${(-actual * 100).toInt()}%"
                actual > 0.05f -> "R${(actual * 100).toInt()}%"
                else -> "CENTER"
            }
            FILTER_CUTOFF -> if (actual >= 1000f) "${String.format("%.1f", actual / 1000f)} kHz" else "${actual.toInt()} Hz"
            FILTER_RESONANCE -> String.format("%.2f", actual)
            LFO_RATE -> "${String.format("%.1f", actual)} Hz"
            SAMPLER_PITCH -> "${if (actual > 0) "+" else ""}${actual.toInt()} st"
        }
    }

    fun toActualValue(normalized: Float): Float {
        val normClamped = normalized.coerceIn(0.0f, 1.0f)
        return if (isLogarithmic) {
            val minLog = kotlin.math.ln(minValue.coerceAtLeast(1f).toDouble())
            val maxLog = kotlin.math.ln(maxValue.toDouble())
            kotlin.math.exp(minLog + normClamped * (maxLog - minLog)).toFloat()
        } else {
            minValue + normClamped * (maxValue - minValue)
        }
    }

    fun toNormalizedValue(actual: Float): Float {
        val actualClamped = actual.coerceIn(minValue, maxValue)
        return if (isLogarithmic) {
            val minLog = kotlin.math.ln(minValue.coerceAtLeast(1f).toDouble())
            val maxLog = kotlin.math.ln(maxValue.toDouble())
            ((kotlin.math.ln(actualClamped.toDouble()) - minLog) / (maxLog - minLog)).toFloat().coerceIn(0f, 1f)
        } else {
            ((actualClamped - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
        }
    }
}

/**
 * Breakpoint for automation lines and curves.
 */
data class AutomationPoint(
    val beat: Float,
    val normalizedValue: Float
)

/**
 * Automation Lane supporting linear interpolation and curve evaluation.
 */
data class AutomationLane(
    val parameter: AutomationParameter,
    val isEnabled: Boolean = true,
    val points: List<AutomationPoint> = emptyList()
) {
    fun getValueAtBeat(beat: Float): Float {
        if (!isEnabled || points.isEmpty()) return parameter.defaultValue
        val sorted = points.sortedBy { it.beat }
        if (sorted.size == 1) return parameter.toActualValue(sorted[0].normalizedValue)

        val clampedBeat = beat.coerceIn(0.0f, 128.0f)
        if (clampedBeat <= sorted.first().beat) return parameter.toActualValue(sorted.first().normalizedValue)
        if (clampedBeat >= sorted.last().beat) return parameter.toActualValue(sorted.last().normalizedValue)

        var p0 = sorted.first()
        var p1 = sorted.last()
        for (i in 0 until sorted.size - 1) {
            if (clampedBeat >= sorted[i].beat && clampedBeat <= sorted[i + 1].beat) {
                p0 = sorted[i]
                p1 = sorted[i + 1]
                break
            }
        }
        val span = p1.beat - p0.beat
        val fraction = if (span > 0.0001f) (clampedBeat - p0.beat) / span else 0.0f
        val interpNorm = p0.normalizedValue + fraction * (p1.normalizedValue - p0.normalizedValue)
        return parameter.toActualValue(interpNorm)
    }
}

/**
 * MIDI Note with MPE and probability support.
 */
data class MidiNote(
    val id: String = UUID.randomUUID().toString(),
    val pitch: Int,             // 0..127
    val startBeat: Float,       // Beat position
    val lengthBeats: Float = 1.0f,
    val velocity: Float = 0.9f,  // 0.0 .. 1.0
    val releaseVelocity: Float = 0.5f,
    val probability: Float = 1.0f, // 0.0 .. 1.0
    val slideSemitones: Float = 0.0f
)

/**
 * Drum Pad Type for 16-pad drum rack.
 */
enum class DrumPadType(val displayName: String, val midiPitch: Int, val chokeGroup: Int) {
    KICK("Kick", 36, 0),
    SNARE("Snare", 38, 0),
    CLAP("Clap", 39, 0),
    HIHAT_CLOSED("Closed Hat", 42, 1),
    HIHAT_OPEN("Open Hat", 46, 1),
    TOM_LOW("Low Tom", 41, 0),
    TOM_MID("Mid Tom", 45, 0),
    TOM_HIGH("High Tom", 48, 0),
    CRASH("Crash Cymbal", 49, 0),
    RIDE("Ride Cymbal", 51, 0),
    PERC_1("Percussion 1", 54, 0),
    PERC_2("Percussion 2", 56, 0),
    SUB_BOOM("Sub Boom", 35, 2),
    SHAKER("Shaker", 70, 0),
    COWBELL("Cowbell", 56, 0),
    RIMSHOT("Rimshot", 37, 0)
}

/**
 * Arrangement Clip (Placed on timeline).
 */
data class ArrangementClip(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val trackId: String,
    val startBeat: Float,
    val lengthBeats: Float,
    val notes: List<MidiNote> = emptyList(),
    val drumSteps: Map<DrumPadType, List<Float>> = emptyMap(),
    val audioFilePath: String? = null,
    val linkedSessionClipId: String? = null,
    val isMuted: Boolean = false
)

/**
 * Session Clip (Matrix slot).
 */
data class SessionClip(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val trackId: String,
    val slotIndex: Int,
    val notes: List<MidiNote> = emptyList(),
    val drumSteps: Map<DrumPadType, List<Float>> = emptyMap(),
    val audioFilePath: String? = null,
    val lengthBeats: Float = 16.0f,
    val linkedArrangementClipId: String? = null,
    val isPlaying: Boolean = false,
    val isQueued: Boolean = false,
    val isRecording: Boolean = false,
    val playProgress: Float = 0.0f
)

/**
 * Session Scene (Horizontal row trigger across tracks).
 */
data class SessionScene(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val index: Int,
    val bpm: Float? = null,
    val colorHex: String = "#FF7600"
)

/**
 * Device Model (Instruments & Effects in a Track's chain).
 */
enum class DeviceType(val displayName: String, val isInstrument: Boolean) {
    SUBTRACTIVE_SYNTH("Analog Sub Synth", true),
    WAVETABLE_SYNTH("Wavetable Lab", true),
    FM_SYNTH("4-Op FM Synth", true),
    SAMPLER("Sampler Instrument", true),
    ELECTRIC_PIANO("Electric Piano", true),
    STRING_PAD("String Pad", true),
    DRUM_RACK("16-Pad Drum Rack", true),
    PARAMETRIC_EQ("Parametric EQ+", false),
    COMPRESSOR("Studio Compressor", false),
    REVERB("Crystal Reverb", false),
    DELAY("Ping-Pong Delay", false),
    DISTORTION("Analog Drive", false),
    CHORUS("Ensemble Chorus", false),
    LIMITER("Brickwall Limiter", false)
}

data class DeviceModel(
    val id: String = UUID.randomUUID().toString(),
    val type: DeviceType,
    val name: String = type.displayName,
    val isEnabled: Boolean = true,
    val isFolded: Boolean = false,
    val params: Map<String, Float> = emptyMap()
)

/**
 * Track Model.
 */
data class TrackModel(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TrackType,
    val colorHex: String = "#FF7600",
    val volumeDb: Float = 0.0f,
    val pan: Float = 0.0f,
    val isMuted: Boolean = false,
    val isSoloed: Boolean = false,
    val isArmed: Boolean = false,
    val isOverriddenBySession: Boolean = false,
    val sendLevelA: Float = 0.2f,
    val sendLevelB: Float = 0.25f,
    val devices: List<DeviceModel> = emptyList(),
    val arrangementClips: List<ArrangementClip> = emptyList(),
    val sessionClips: List<SessionClip> = emptyList(),
    val automationLanes: Map<AutomationParameter, AutomationLane> = emptyMap(),
    val peakMeterL: Float = 0.0f,
    val peakMeterR: Float = 0.0f
)

/**
 * Complete Immutable Project State.
 */
data class ProjectState(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Track",
    val bpm: Float = 120.0f,
    val timeSigNum: Int = 4,
    val timeSigDen: Int = 4,
    val keyRoot: Int = 0, // 0 = C
    val scale: MusicalScale = MusicalScale.NATURAL_MINOR,
    val tracks: List<TrackModel> = emptyList(),
    val scenes: List<SessionScene> = emptyList(),
    val masterVolumeDb: Float = 0.0f,
    val masterLimiterCeilingDb: Float = -0.3f,
    val isPlaying: Boolean = false,
    val isRecording: Boolean = false,
    val isLooping: Boolean = true,
    val isMetronomeOn: Boolean = false,
    val loopStartBeat: Float = 0.0f,
    val loopEndBeat: Float = 16.0f,
    val playheadBeat: Float = 0.0f,
    val selectedTrackId: String? = null,
    val selectedClipId: String? = null,
    val activeTab: DawTab = DawTab.ARRANGER
)

/**
 * DAW Navigation Tabs.
 */
enum class DawTab(val title: String) {
    SESSION("Session"),
    ARRANGER("Arranger"),
    MIXER("Mixer"),
    PIANO_ROLL("Piano Roll"),
    SYNTH("Synth Lab"),
    SAMPLER("Sampler"),
    DRUMS("Drums"),
    BROWSER("Browser"),
    MASTERING("Mastering")
}

/**
 * Sealed Interface of all User Actions (UDF).
 */
sealed interface ProjectAction {
    // Transport Actions
    data object Play : ProjectAction
    data object Stop : ProjectAction
    data object TogglePlay : ProjectAction
    data object ToggleRecord : ProjectAction
    data object ToggleLoop : ProjectAction
    data object ToggleMetronome : ProjectAction
    data class SetBpm(val bpm: Float) : ProjectAction
    data class SeekToBeat(val beat: Float) : ProjectAction
    data class SetLoopRegion(val startBeat: Float, val endBeat: Float) : ProjectAction
    data class SetScale(val root: Int, val scale: MusicalScale) : ProjectAction
    data class SelectTab(val tab: DawTab) : ProjectAction

    // Track Actions
    data class AddTrack(val type: TrackType, val name: String) : ProjectAction
    data class DeleteTrack(val trackId: String) : ProjectAction
    data class SelectTrack(val trackId: String) : ProjectAction
    data class SetTrackVolume(val trackId: String, val volumeDb: Float) : ProjectAction
    data class SetTrackPan(val trackId: String, val pan: Float) : ProjectAction
    data class ToggleTrackMute(val trackId: String) : ProjectAction
    data class ToggleTrackSolo(val trackId: String) : ProjectAction
    data class ToggleTrackArm(val trackId: String) : ProjectAction
    data class SetTrackSend(val trackId: String, val sendIndex: Int, val level: Float) : ProjectAction

    // Clip Actions
    data class AddArrangementClip(val clip: ArrangementClip) : ProjectAction
    data class MoveArrangementClip(val clipId: String, val newStartBeat: Float) : ProjectAction
    data class ResizeArrangementClip(val clipId: String, val newLengthBeats: Float) : ProjectAction
    data class DeleteArrangementClip(val clipId: String) : ProjectAction
    data class TriggerSessionClip(val trackId: String, val slotIndex: Int) : ProjectAction
    data class TriggerScene(val sceneIndex: Int) : ProjectAction
    data class ReturnTrackToArrangement(val trackId: String) : ProjectAction
    data object ReturnAllToArrangement : ProjectAction

    // Note & MIDI Actions
    data class AddMidiNote(val trackId: String, val clipId: String, val note: MidiNote) : ProjectAction
    data class DeleteMidiNote(val trackId: String, val clipId: String, val noteId: String) : ProjectAction
    data class QuantizeClipNotes(val trackId: String, val clipId: String, val gridBeat: Float = 0.25f) : ProjectAction
    data class ToggleDrumStep(val trackId: String, val clipId: String, val pad: DrumPadType, val stepBeat: Float) : ProjectAction

    // Device Actions
    data class AddDevice(val trackId: String, val type: DeviceType) : ProjectAction
    data class RemoveDevice(val trackId: String, val deviceId: String) : ProjectAction
    data class SetDeviceParam(val trackId: String, val deviceId: String, val paramName: String, val value: Float) : ProjectAction
    data class ToggleDeviceEnabled(val trackId: String, val deviceId: String) : ProjectAction

    // Master Actions
    data class SetMasterVolume(val volumeDb: Float) : ProjectAction
}
