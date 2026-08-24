package com.example.synth

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

enum class PianoRollTrack(val displayName: String) {
    LEAD("Lead Synth"),
    BASS("Bassline")
}

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

enum class AutomationParameter(
    val id: String,
    val displayName: String,
    val unit: String,
    val minValue: Float,
    val maxValue: Float,
    val defaultValue: Float,
    val isLogarithmic: Boolean = false
) {
    VOLUME("volume", "Volume", "%", 0.0f, 1.0f, 0.85f),
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
            VOLUME, DRIVE_DISTORTION, CHORUS_MIX, FM_DEPTH, WAVETABLE_POS, SAMPLER_START -> "${(actual * 100).toInt()}%"
            PAN -> when {
                actual < -0.05f -> "L${(-actual * 100).toInt()}%"
                actual > 0.05f -> "R${(actual * 100).toInt()}%"
                else -> "CENTER"
            }
            FILTER_CUTOFF -> if (actual >= 1000f) "${String.format("%.1f", actual / 1000f)} kHz" else "${actual.toInt()} Hz"
            FILTER_RESONANCE -> String.format("%.2f", actual)
            LFO_RATE -> "${String.format("%.1f", actual)} Hz"
            REVERB_SEND, DELAY_SEND -> "${(actual * 100).toInt()}%"
            SAMPLER_PITCH -> "${if (actual > 0) "+" else ""}${actual.toInt()} st"
        }
    }

    fun toActualValue(normalized: Float): Float {
        val normClamped = normalized.coerceIn(0.0f, 1.0f)
        return if (isLogarithmic) {
            val minLog = kotlin.math.ln(minValue.toDouble())
            val maxLog = kotlin.math.ln(maxValue.toDouble())
            kotlin.math.exp(minLog + normClamped * (maxLog - minLog)).toFloat()
        } else {
            minValue + normClamped * (maxValue - minValue)
        }
    }

    fun toNormalizedValue(actual: Float): Float {
        val actualClamped = actual.coerceIn(minValue, maxValue)
        return if (isLogarithmic) {
            val minLog = kotlin.math.ln(minValue.toDouble())
            val maxLog = kotlin.math.ln(maxValue.toDouble())
            ((kotlin.math.ln(actualClamped.toDouble()) - minLog) / (maxLog - minLog)).toFloat().coerceIn(0f, 1f)
        } else {
            ((actualClamped - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
        }
    }
}

data class AutomationPoint(
    val beat: Float,       // 0.0 .. 16.0 (total beats)
    val normalizedValue: Float // 0.0 .. 1.0
)

data class AutomationLane(
    val parameter: AutomationParameter,
    val isEnabled: Boolean = true,
    val points: List<AutomationPoint> = emptyList()
) {
    fun getValueAtBeat(beat: Float): Float {
        if (!isEnabled || points.isEmpty()) {
            return parameter.defaultValue
        }
        val sorted = points.sortedBy { it.beat }
        if (sorted.size == 1) {
            return parameter.toActualValue(sorted[0].normalizedValue)
        }

        val clampedBeat = beat.coerceIn(0.0f, 16.0f)
        if (clampedBeat <= sorted.first().beat) {
            return parameter.toActualValue(sorted.first().normalizedValue)
        }
        if (clampedBeat >= sorted.last().beat) {
            return parameter.toActualValue(sorted.last().normalizedValue)
        }

        // Linear interpolation between the two bounding points
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

    companion object {
        fun defaultLane(param: AutomationParameter): AutomationLane {
            val normDefault = param.toNormalizedValue(param.defaultValue)
            return AutomationLane(
                parameter = param,
                isEnabled = true,
                points = listOf(
                    AutomationPoint(0.0f, normDefault),
                    AutomationPoint(16.0f, normDefault)
                )
            )
        }

        fun generateRampUp(param: AutomationParameter): List<AutomationPoint> = listOf(
            AutomationPoint(0.0f, 0.1f),
            AutomationPoint(16.0f, 0.95f)
        )

        fun generateRampDown(param: AutomationParameter): List<AutomationPoint> = listOf(
            AutomationPoint(0.0f, 0.95f),
            AutomationPoint(16.0f, 0.1f)
        )

        fun generateSineWave(param: AutomationParameter, cycles: Int = 2): List<AutomationPoint> {
            val list = mutableListOf<AutomationPoint>()
            val steps = 32
            for (i in 0..steps) {
                val beat = (i / steps.toFloat()) * 16.0f
                val phase = (i / steps.toFloat()) * (2.0 * Math.PI * cycles)
                val norm = ((kotlin.math.sin(phase) + 1.0) / 2.0).toFloat()
                list.add(AutomationPoint(beat, norm.coerceIn(0.05f, 0.95f)))
            }
            return list
        }

        fun generateTriangle(param: AutomationParameter, peaks: Int = 2): List<AutomationPoint> {
            val list = mutableListOf<AutomationPoint>()
            val totalSections = peaks * 2
            for (i in 0..totalSections) {
                val beat = (i / totalSections.toFloat()) * 16.0f
                val norm = if (i % 2 == 0) 0.1f else 0.95f
                list.add(AutomationPoint(beat, norm))
            }
            return list
        }

        fun generateRandomSteps(param: AutomationParameter): List<AutomationPoint> {
            val list = mutableListOf<AutomationPoint>()
            for (beat in 0..16 step 2) {
                val norm = (20..95).random() / 100f
                list.add(AutomationPoint(beat.toFloat(), norm))
            }
            return list
        }
    }
}

data class MidiNote(
    val id: String = UUID.randomUUID().toString(),
    val pitch: Int, // MIDI note number 0..127
    val startBeat: Float, // beat position (e.g. 0.0, 0.5, 1.0)
    val lengthBeats: Float = 1.0f,
    val velocity: Float = 0.9f
)

data class DrumPattern(
    val steps: Int = 16,
    // Map of DrumType to list of step velocities (0.0 = off, >0.0 = on with velocity)
    val grid: Map<DrumType, List<Float>> = DrumType.values().associateWith { List(steps) { 0.0f } }
)

data class ChannelStrip(
    val name: String,
    val volume: Float = 0.85f,
    val pan: Float = 0.0f,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val sendA: Float = 0.2f,
    val sendB: Float = 0.2f
)

enum class SessionTrackType(val displayName: String, val shortName: String) {
    LEAD("Lead Synth", "1 LEAD"),
    BASS("Bassline", "2 BASS"),
    DRUMS("Drum Machine", "3 DRUMS")
}

data class ArrangementClip(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val trackId: String,
    val startBar: Float, // 0.0 = Bar 1, 4.0 = Bar 5
    val lengthBars: Float = 4.0f,
    val colorHex: Long = 0xFFFF764D,
    val leadNotes: List<MidiNote> = emptyList(),
    val bassNotes: List<MidiNote> = emptyList(),
    val drumGrid: Map<DrumType, List<Float>> = emptyMap(),
    val isLooping: Boolean = true,
    val isMuted: Boolean = false
)

data class TrackGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: Long = 0xFFB28DFF,
    val isFolded: Boolean = false,
    val volume: Float = 0.85f,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val trackIds: List<String> = emptyList()
)

data class ArrangementTrack(
    val id: String,
    val name: String,
    val trackType: SessionTrackType,
    val groupId: String? = null,
    val colorHex: Long,
    val volume: Float = 0.85f,
    val pan: Float = 0.0f,
    val sendA: Float = 0.2f,
    val sendB: Float = 0.2f,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val isArmed: Boolean = false,
    val isAutomationExpanded: Boolean = false,
    val selectedAutomationParam: AutomationParameter = AutomationParameter.FILTER_CUTOFF,
    val clips: List<ArrangementClip> = emptyList(),
    val automationLanes: Map<AutomationParameter, AutomationLane> = emptyMap()
)

enum class BrowserCategory(val title: String, val badge: String) {
    SOUNDS("Sounds", "SND"),
    DRUMS("Drums", "DRM"),
    INSTRUMENTS("Instruments", "INS"),
    AUDIO_FX("Audio Effects", "AFX"),
    MIDI_FX("MIDI Effects", "MFX"),
    SAMPLES_LOOPS("Samples & Loops", "SMP"),
    USER_LIBRARY("User Library", "LIB")
}

enum class SamplePreviewType {
    DRUM_HIT,
    SYNTH_CHORD,
    BASS_SLAP,
    MELODIC_LOOP,
    DRUM_LOOP,
    FX_SWEEP
}

data class BrowserSampleItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: BrowserCategory,
    val subCategory: String,
    val bpm: Float? = null,
    val musicalKey: String? = null,
    val previewType: SamplePreviewType,
    val description: String,
    val trackTypeTarget: SessionTrackType? = null
)

data class MacroControl(
    val index: Int,
    val name: String,
    val value: Float = 0.5f,
    val targetParam: String
)

data class MacroRack(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Ableton Audio FX Rack",
    val isEnabled: Boolean = true,
    val macros: List<MacroControl> = listOf(
        MacroControl(0, "Brightness", 0.6f, "Filter Cutoff"),
        MacroControl(1, "Resonance", 0.35f, "Filter Res"),
        MacroControl(2, "Space", 0.4f, "Reverb Mix"),
        MacroControl(3, "Delay Time", 0.3f, "Echo Time"),
        MacroControl(4, "Drive", 0.25f, "Distortion"),
        MacroControl(5, "Chorus", 0.3f, "Modulation"),
        MacroControl(6, "Glue", 0.5f, "Compression"),
        MacroControl(7, "Master Out", 0.85f, "Level")
    )
)

data class LfoDevice(
    val id: String = UUID.randomUUID().toString(),
    val isEnabled: Boolean = true,
    val waveform: Waveform = Waveform.SINE,
    val rateHz: Float = 2.0f,
    val depth: Float = 0.6f,
    val phaseOffset: Float = 0f,
    val target: String = "Filter Cutoff"
)

data class SessionClip(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val trackType: SessionTrackType,
    val isPlaying: Boolean = false,
    val leadNotes: List<MidiNote> = emptyList(),
    val bassNotes: List<MidiNote> = emptyList(),
    val drumGrid: Map<DrumType, List<Float>> = emptyMap()
)

data class SessionScene(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val bpm: Float = 120f,
    val clips: Map<SessionTrackType, SessionClip> = emptyMap()
)

data class ProjectSong(
    val name: String,
    val genre: String,
    val bpm: Float,
    val patch: SynthPatch,
    val leadNotes: List<MidiNote>,
    val bassNotes: List<MidiNote>,
    val drumGrid: Map<DrumType, List<Float>>
) {
    companion object {
        val DEMO_PROJECTS = listOf(
            ProjectSong(
                name = "Synthwave Sunset",
                genre = "Retrowave 80s",
                bpm = 118f,
                patch = SynthPatch.PRESETS[1], // Cosmic Lead
                leadNotes = listOf(
                    MidiNote(pitch = 60, startBeat = 0f, lengthBeats = 1.5f), // C4
                    MidiNote(pitch = 63, startBeat = 2f, lengthBeats = 1.5f), // Eb4
                    MidiNote(pitch = 65, startBeat = 4f, lengthBeats = 1.5f), // F4
                    MidiNote(pitch = 67, startBeat = 6f, lengthBeats = 2.0f), // G4
                    MidiNote(pitch = 70, startBeat = 8f, lengthBeats = 1.5f), // Bb4
                    MidiNote(pitch = 67, startBeat = 10f, lengthBeats = 1.5f),// G4
                    MidiNote(pitch = 65, startBeat = 12f, lengthBeats = 2.0f),// F4
                    MidiNote(pitch = 63, startBeat = 14f, lengthBeats = 2.0f) // Eb4
                ),
                bassNotes = listOf(
                    MidiNote(pitch = 36, startBeat = 0f, lengthBeats = 0.75f),
                    MidiNote(pitch = 36, startBeat = 1f, lengthBeats = 0.75f),
                    MidiNote(pitch = 36, startBeat = 2f, lengthBeats = 0.75f),
                    MidiNote(pitch = 36, startBeat = 3f, lengthBeats = 0.75f),
                    MidiNote(pitch = 39, startBeat = 4f, lengthBeats = 0.75f),
                    MidiNote(pitch = 39, startBeat = 5f, lengthBeats = 0.75f),
                    MidiNote(pitch = 41, startBeat = 6f, lengthBeats = 0.75f),
                    MidiNote(pitch = 43, startBeat = 7f, lengthBeats = 0.75f)
                ),
                drumGrid = mapOf(
                    DrumType.KICK to listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f),
                    DrumType.SNARE to listOf(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f),
                    DrumType.HIHAT_CLOSED to listOf(0.7f, 0.4f, 0.7f, 0.4f, 0.7f, 0.4f, 0.7f, 0.4f, 0.7f, 0.4f, 0.7f, 0.4f, 0.7f, 0.4f, 0.7f, 0.4f),
                    DrumType.HIHAT_OPEN to listOf(0f, 0f, 0.8f, 0f, 0f, 0f, 0.8f, 0f, 0f, 0f, 0.8f, 0f, 0f, 0f, 0.8f, 0f),
                    DrumType.CLAP to listOf(0f, 0f, 0f, 0f, 0.9f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.9f, 0f, 0f, 0.2f),
                    DrumType.TOM to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.8f, 0.9f, 0f)
                )
            ),
            ProjectSong(
                name = "Cyberpunk Acid 303",
                genre = "Industrial Acid",
                bpm = 132f,
                patch = SynthPatch.PRESETS[2], // Acid Sweep
                leadNotes = listOf(
                    MidiNote(pitch = 48, startBeat = 0f, lengthBeats = 0.5f),
                    MidiNote(pitch = 48, startBeat = 0.5f, lengthBeats = 0.5f),
                    MidiNote(pitch = 60, startBeat = 1f, lengthBeats = 0.5f),
                    MidiNote(pitch = 58, startBeat = 1.5f, lengthBeats = 0.5f),
                    MidiNote(pitch = 48, startBeat = 2f, lengthBeats = 0.5f),
                    MidiNote(pitch = 51, startBeat = 2.5f, lengthBeats = 0.5f),
                    MidiNote(pitch = 55, startBeat = 3f, lengthBeats = 0.75f),
                    MidiNote(pitch = 53, startBeat = 3.75f, lengthBeats = 0.25f)
                ),
                bassNotes = listOf(
                    MidiNote(pitch = 36, startBeat = 0f, lengthBeats = 1f),
                    MidiNote(pitch = 36, startBeat = 2f, lengthBeats = 1f)
                ),
                drumGrid = mapOf(
                    DrumType.KICK to listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f),
                    DrumType.SNARE to listOf(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0.4f, 0.6f),
                    DrumType.HIHAT_CLOSED to listOf(1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f),
                    DrumType.HIHAT_OPEN to listOf(0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
                    DrumType.CLAP to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
                    DrumType.TOM to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.8f, 0f, 0f, 0.9f, 1f)
                )
            ),
            ProjectSong(
                name = "Deep House Groove",
                genre = "Deep House",
                bpm = 124f,
                patch = SynthPatch.PRESETS[0], // Fat Sub-Bass
                leadNotes = listOf(
                    MidiNote(pitch = 60, startBeat = 0.5f, lengthBeats = 0.5f),
                    MidiNote(pitch = 63, startBeat = 1.5f, lengthBeats = 0.5f),
                    MidiNote(pitch = 67, startBeat = 2.5f, lengthBeats = 1.0f),
                    MidiNote(pitch = 65, startBeat = 4.5f, lengthBeats = 0.5f),
                    MidiNote(pitch = 63, startBeat = 5.5f, lengthBeats = 0.5f)
                ),
                bassNotes = listOf(
                    MidiNote(pitch = 36, startBeat = 0f, lengthBeats = 0.75f),
                    MidiNote(pitch = 39, startBeat = 1.5f, lengthBeats = 0.75f),
                    MidiNote(pitch = 41, startBeat = 3f, lengthBeats = 1.0f)
                ),
                drumGrid = mapOf(
                    DrumType.KICK to listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f),
                    DrumType.SNARE to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
                    DrumType.HIHAT_CLOSED to listOf(0f, 0f, 0.8f, 0f, 0f, 0f, 0.8f, 0f, 0f, 0f, 0.8f, 0f, 0f, 0f, 0.8f, 0f),
                    DrumType.HIHAT_OPEN to listOf(0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
                    DrumType.CLAP to listOf(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f),
                    DrumType.TOM to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.6f, 0f, 0f, 0f, 0f, 0f, 0f)
                )
            )
        )
    }
}

object WavWriter {
    fun createWavFile(file: File, pcmData: ShortArray, sampleRate: Int = 44100, channels: Int = 1) {
        val totalAudioLen = pcmData.size * 2
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * 2

        val header = ByteArray(44)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        bb.put("RIFF".toByteArray())
        bb.putInt(totalDataLen)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16) // Subchunk1Size (16 for PCM)
        bb.putShort(1.toShort()) // AudioFormat (1 for PCM)
        bb.putShort(channels.toShort())
        bb.putInt(sampleRate)
        bb.putInt(byteRate)
        bb.putShort((channels * 2).toShort()) // BlockAlign
        bb.putShort(16.toShort()) // BitsPerSample
        bb.put("data".toByteArray())
        bb.putInt(totalAudioLen)

        FileOutputStream(file).use { fos ->
            fos.write(header)
            val audioBytes = ByteArray(pcmData.size * 2)
            val audioBb = ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcmData) {
                audioBb.putShort(s)
            }
            fos.write(audioBytes)
        }
    }
}
