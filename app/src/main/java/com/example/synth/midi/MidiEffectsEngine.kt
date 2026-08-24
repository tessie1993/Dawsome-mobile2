package com.example.synth.midi

import com.example.synth.domain.MidiNote
import com.example.synth.domain.MusicalScale
import kotlin.random.Random

/**
 * Real-Time MIDI Effect Device Base and Implementations according to SPEC01.md Section 8.3.
 */
sealed interface MidiEffectDevice {
    val id: String
    val name: String
    var isEnabled: Boolean

    fun process(notes: List<MidiNote>, currentBeat: Float): List<MidiNote>
}

/**
 * Real-Time Arpeggiator Effect.
 */
enum class ArpStyle { UP, DOWN, UP_DOWN, RANDOM }

class RealtimeArpeggiator(
    override val id: String = "midi_fx_arp",
    override val name: String = "Arpeggiator",
    override var isEnabled: Boolean = true,
    var style: ArpStyle = ArpStyle.UP,
    var rateBeat: Float = 0.25f, // 1/16th
    var octaveRange: Int = 1,
    var gate: Float = 0.8f // note length ratio
) : MidiEffectDevice {

    override fun process(notes: List<MidiNote>, currentBeat: Float): List<MidiNote> {
        if (!isEnabled || notes.isEmpty()) return notes

        val sortedPitches = notes.map { it.pitch }.distinct().sorted()
        val allPitches = mutableListOf<Int>()
        for (oct in 0 until octaveRange) {
            sortedPitches.forEach { p -> allPitches.add(p + oct * 12) }
        }

        val stepIndex = ((currentBeat / rateBeat).toInt()) % allPitches.size
        val pitch = when (style) {
            ArpStyle.UP -> allPitches[stepIndex]
            ArpStyle.DOWN -> allPitches[allPitches.size - 1 - stepIndex]
            ArpStyle.UP_DOWN -> {
                val cycle = (allPitches.size * 2) - 2
                val pos = if (cycle > 0) stepIndex % cycle else 0
                if (pos < allPitches.size) allPitches[pos] else allPitches[cycle - pos]
            }
            ArpStyle.RANDOM -> allPitches.random()
        }

        return listOf(
            MidiNote(
                pitch = pitch,
                startBeat = currentBeat,
                lengthBeats = rateBeat * gate,
                velocity = 0.9f
            )
        )
    }
}

/**
 * Real-Time Chord Device. Adds interval harmonies to incoming notes.
 */
class RealtimeChordDevice(
    override val id: String = "midi_fx_chord",
    override val name: String = "Chord Generator",
    override var isEnabled: Boolean = true,
    var intervals: List<Int> = listOf(3, 7) // Minor triad default (root + minor 3rd + 5th)
) : MidiEffectDevice {

    override fun process(notes: List<MidiNote>, currentBeat: Float): List<MidiNote> {
        if (!isEnabled || notes.isEmpty()) return notes
        return notes.flatMap { baseNote ->
            listOf(baseNote) + intervals.map { interval ->
                baseNote.copy(
                    pitch = (baseNote.pitch + interval).coerceIn(0, 127),
                    velocity = baseNote.velocity * 0.85f
                )
            }
        }
    }
}

/**
 * Real-Time Scale Remapper Device.
 */
class RealtimeScaleDevice(
    override val id: String = "midi_fx_scale",
    override val name: String = "Scale Remapper",
    override var isEnabled: Boolean = true,
    var rootNote: Int = 0,
    var scale: MusicalScale = MusicalScale.NATURAL_MINOR
) : MidiEffectDevice {

    override fun process(notes: List<MidiNote>, currentBeat: Float): List<MidiNote> {
        if (!isEnabled || notes.isEmpty()) return notes
        return MidiTransformations.scaleConstrain(notes, rootNote, scale)
    }
}

/**
 * Real-Time Velocity Processor (Compress, Expand, Randomize).
 */
class RealtimeVelocityDevice(
    override val id: String = "midi_fx_velocity",
    override val name: String = "Velocity Shaper",
    override var isEnabled: Boolean = true,
    var gain: Float = 1.0f,
    var randomizeAmount: Float = 0.1f
) : MidiEffectDevice {

    override fun process(notes: List<MidiNote>, currentBeat: Float): List<MidiNote> {
        if (!isEnabled) return notes
        return notes.map { note ->
            val randomOffset = (Random.nextFloat() * 2f - 1f) * randomizeAmount
            val newVel = (note.velocity * gain + randomOffset).coerceIn(0.1f, 1.0f)
            note.copy(velocity = newVel)
        }
    }
}

/**
 * Real-Time Note Echo Device (Repeated MIDI notes with decaying velocity).
 */
class RealtimeNoteEchoDevice(
    override val id: String = "midi_fx_echo",
    override val name: String = "Note Echo",
    override var isEnabled: Boolean = true,
    var repeats: Int = 3,
    var delayBeats: Float = 0.5f,
    var decay: Float = 0.7f,
    var pitchShiftPerRepeat: Int = 0
) : MidiEffectDevice {

    override fun process(notes: List<MidiNote>, currentBeat: Float): List<MidiNote> {
        if (!isEnabled || notes.isEmpty()) return notes
        return notes.flatMap { baseNote ->
            (0..repeats).map { r ->
                baseNote.copy(
                    pitch = (baseNote.pitch + r * pitchShiftPerRepeat).coerceIn(0, 127),
                    startBeat = baseNote.startBeat + (r * delayBeats),
                    velocity = (baseNote.velocity * Math.pow(decay.toDouble(), r.toDouble())).toFloat().coerceIn(0.1f, 1.0f)
                )
            }
        }
    }
}
