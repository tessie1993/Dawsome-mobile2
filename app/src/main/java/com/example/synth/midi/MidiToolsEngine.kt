package com.example.synth.midi

import com.example.synth.domain.MidiNote
import com.example.synth.domain.MusicalScale
import java.util.UUID
import kotlin.math.*
import kotlin.random.Random

/**
 * MIDI Transformations Suite according to SPEC01.md Section 8.1.
 * Non-destructive transformations on selections or clips.
 */
object MidiTransformations {

    /**
     * Quantize note start times toward a straight or triplet grid.
     */
    fun quantize(
        notes: List<MidiNote>,
        gridBeat: Float = 0.25f, // 1/16th note
        amount: Float = 1.0f    // 0.0 to 1.0 partial quantization
    ): List<MidiNote> {
        if (gridBeat <= 0.001f || amount <= 0f) return notes
        return notes.map { note ->
            val targetBeat = (round(note.startBeat / gridBeat) * gridBeat)
            val newBeat = note.startBeat + (targetBeat - note.startBeat) * amount.coerceIn(0f, 1f)
            note.copy(startBeat = newBeat)
        }
    }

    /**
     * Humanize timing, velocity, and duration with controlled randomization.
     */
    fun humanize(
        notes: List<MidiNote>,
        timingDevBeats: Float = 0.03f,
        velocityDev: Float = 0.10f
    ): List<MidiNote> {
        return notes.map { note ->
            val timeOffset = (Random.nextFloat() * 2f - 1f) * timingDevBeats
            val velOffset = (Random.nextFloat() * 2f - 1f) * velocityDev
            note.copy(
                startBeat = (note.startBeat + timeOffset).coerceAtLeast(0f),
                velocity = (note.velocity + velOffset).coerceIn(0.1f, 1.0f)
            )
        }
    }

    /**
     * Strum offset: Stagger notes of chords across time and apply velocity tapering.
     */
    fun strum(
        notes: List<MidiNote>,
        delayPerNoteBeats: Float = 0.04f,
        isUpward: Boolean = true
    ): List<MidiNote> {
        // Group notes starting around the same beat (within 0.05 beats)
        val chordGroups = notes.groupBy { round(it.startBeat * 20) / 20f }

        return chordGroups.flatMap { (_, chordNotes) ->
            val sorted = if (isUpward) chordNotes.sortedBy { it.pitch } else chordNotes.sortedByDescending { it.pitch }
            sorted.mapIndexed { index, note ->
                val offset = index * delayPerNoteBeats
                val velTaper = 1.0f - (index * 0.05f)
                note.copy(
                    startBeat = note.startBeat + offset,
                    velocity = (note.velocity * velTaper).coerceIn(0.2f, 1.0f)
                )
            }
        }
    }

    /**
     * Chop notes into rhythmic subdivisions.
     */
    fun chop(
        notes: List<MidiNote>,
        divisions: Int = 2 // Split into 2, 3, or 4 parts
    ): List<MidiNote> {
        if (divisions <= 1) return notes
        return notes.flatMap { note ->
            val subLen = note.lengthBeats / divisions
            (0 until divisions).map { i ->
                note.copy(
                    id = UUID.randomUUID().toString(),
                    startBeat = note.startBeat + (i * subLen),
                    lengthBeats = subLen * 0.9f, // slight gap
                    velocity = note.velocity * (if (i == 0) 1.0f else 0.85f)
                )
            }
        }
    }

    /**
     * Scale Constrain: Shift notes to the nearest scale degree.
     */
    fun scaleConstrain(
        notes: List<MidiNote>,
        rootNote: Int,
        scale: MusicalScale
    ): List<MidiNote> {
        return notes.map { note ->
            val relativePitch = (note.pitch - rootNote + 120) % 12
            if (scale.intervals.contains(relativePitch)) {
                note
            } else {
                // Find closest scale degree
                val closestInterval = scale.intervals.minByOrNull { abs(it - relativePitch) } ?: 0
                val octave = (note.pitch - rootNote) / 12
                val newPitch = rootNote + (octave * 12) + closestInterval
                note.copy(pitch = newPitch.coerceIn(0, 127))
            }
        }
    }

    /**
     * Transpose notes by semitones.
     */
    fun transpose(notes: List<MidiNote>, semitones: Int): List<MidiNote> {
        return notes.map { it.copy(pitch = (it.pitch + semitones).coerceIn(0, 127)) }
    }

    /**
     * Invert pitch relationships within a selection.
     */
    fun invert(notes: List<MidiNote>): List<MidiNote> {
        if (notes.isEmpty()) return notes
        val centerPitch = notes.map { it.pitch }.average().roundToInt()
        return notes.map { note ->
            val diff = note.pitch - centerPitch
            note.copy(pitch = (centerPitch - diff).coerceIn(0, 127))
        }
    }

    /**
     * Reverse notes in time.
     */
    fun reverse(notes: List<MidiNote>): List<MidiNote> {
        if (notes.isEmpty()) return notes
        val minBeat = notes.minOf { it.startBeat }
        val maxBeat = notes.maxOf { it.startBeat + it.lengthBeats }
        return notes.map { note ->
            val offsetFromStart = note.startBeat - minBeat
            val newStart = (maxBeat - offsetFromStart - note.lengthBeats).coerceAtLeast(minBeat)
            note.copy(startBeat = newStart)
        }
    }
}

/**
 * MIDI Generators Suite according to SPEC01.md Section 8.2.
 * Algorithmic creation of new musical patterns inside clips.
 */
object MidiGenerators {

    /**
     * Euclidean Rhythm Generator (Bjorklund Algorithm).
     * Distributes k pulses evenly across n steps.
     */
    fun generateEuclidean(
        steps: Int = 16,
        pulses: Int = 5,
        pitch: Int = 36, // default kick pitch
        stepBeat: Float = 0.25f, // 1/16th note
        velocity: Float = 0.9f
    ): List<MidiNote> {
        if (pulses <= 0 || steps <= 0) return emptyList()
        val pattern = BooleanArray(steps)
        var bucket = 0
        for (i in 0 until steps) {
            bucket += pulses
            if (bucket >= steps) {
                bucket -= steps
                pattern[i] = true
            }
        }

        val notes = mutableListOf<MidiNote>()
        for (i in 0 until steps) {
            if (pattern[i]) {
                notes.add(
                    MidiNote(
                        pitch = pitch,
                        startBeat = i * stepBeat,
                        lengthBeats = stepBeat * 0.8f,
                        velocity = if (i % 4 == 0) velocity else velocity * 0.8f
                    )
                )
            }
        }
        return notes
    }

    /**
     * Chord Progression Generator inside active scale.
     */
    fun generateChordProgression(
        rootNote: Int = 60, // C4
        scale: MusicalScale = MusicalScale.NATURAL_MINOR,
        progressionDegrees: List<Int> = listOf(1, 6, 3, 7), // i - VI - III - VII
        beatsPerChord: Float = 4.0f
    ): List<MidiNote> {
        val notes = mutableListOf<MidiNote>()

        progressionDegrees.forEachIndexed { index, degree ->
            val chordStart = index * beatsPerChord
            // Triad degrees (1st, 3rd, 5th from degree)
            val d1 = (degree - 1) % scale.intervals.size
            val d2 = (d1 + 2) % scale.intervals.size
            val d3 = (d1 + 4) % scale.intervals.size

            val p1 = rootNote + scale.intervals[d1]
            val p2 = rootNote + scale.intervals[d2]
            val p3 = rootNote + scale.intervals[d3]

            notes.add(MidiNote(pitch = p1, startBeat = chordStart, lengthBeats = beatsPerChord * 0.95f, velocity = 0.85f))
            notes.add(MidiNote(pitch = p2, startBeat = chordStart, lengthBeats = beatsPerChord * 0.95f, velocity = 0.80f))
            notes.add(MidiNote(pitch = p3, startBeat = chordStart, lengthBeats = beatsPerChord * 0.95f, velocity = 0.78f))
        }

        return notes
    }

    /**
     * Scale-Aware Bassline Generator.
     */
    fun generateBassline(
        rootNote: Int = 36, // C2
        scale: MusicalScale = MusicalScale.NATURAL_MINOR,
        totalBars: Int = 4,
        density: Float = 0.7f
    ): List<MidiNote> {
        val notes = mutableListOf<MidiNote>()
        val totalBeats = totalBars * 4f
        var beat = 0.0f

        while (beat < totalBeats) {
            if (Random.nextFloat() < density) {
                val scaleDegree = if (beat % 4f == 0f) 0 else scale.intervals.indices.random()
                val pitch = rootNote + scale.intervals[scaleDegree]
                val len = if (Random.nextBoolean()) 0.5f else 1.0f

                notes.add(
                    MidiNote(
                        pitch = pitch,
                        startBeat = beat,
                        lengthBeats = len * 0.85f,
                        velocity = if (beat % 4f == 0f) 0.95f else 0.80f
                    )
                )
            }
            beat += 0.5f // 8th note grid
        }

        return notes
    }
}
