package com.example.synth

import java.util.UUID
import kotlin.math.*
import kotlin.random.Random

enum class InstrumentType(
    val displayName: String,
    val shortName: String,
    val badge: String,
    val category: String
) {
    ANALOG_SUB("Analog Lead & Sub", "ANALOG", "VCO", "Synthesizers"),
    WAVETABLE("Wavetable Synthesizer", "WAVETABLE", "WT", "Synthesizers"),
    FM_OPERATOR("4-Op FM Synth (Operator)", "FM-4OP", "FM", "Synthesizers"),
    SAMPLER("Ableton Simpler / Sampler", "SIMPLER", "SMP", "Sampling"),
    ELECTRIC_PIANO("Rhodes Stage E-Piano", "E-PIANO", "TINE", "Keyboards"),
    STRING_PAD("Analog String Ensemble", "SOLINA", "PAD", "Acoustic & Strings")
}

// =========================================================================
// 1. WAVETABLE SYNTHESIZER ENGINE
// =========================================================================
enum class WavetableBank(val displayName: String) {
    MODERN_ANALOG("Modern Analog"),
    CYBER_FORMANT("Cyber Formant"),
    METALLIC_GLASS("Metallic Glass"),
    CHIPTUNE_8BIT("Chiptune 8-Bit"),
    ORGAN_HARMONICS("Organ Harmonics")
}

enum class WavetableWarpMode(val displayName: String) {
    NONE("None"),
    PWM("Pulse Width"),
    SYNC("Hard Sync"),
    BEND("Bend +/-"),
    FM("Self FM")
}

class WavetableSynth(private val sampleRate: Int = 44100) {
    @Volatile var bank = WavetableBank.MODERN_ANALOG
    var currentBank: WavetableBank
        get() = bank
        set(value) { bank = value }
    @Volatile var tablePosition = 0.35f // 0.0 to 1.0 (morph)
    @Volatile var warpMode = WavetableWarpMode.PWM
    @Volatile var warpAmount = 0.25f // 0.0 to 1.0
    @Volatile var unisonVoices = 4 // 1 to 7
    @Volatile var unisonDetune = 0.25f // 0 to 1
    @Volatile var subLevel = 0.35f // Sub oscillator level

    // Voice phases (for up to 7 unison voices)
    private val phases = FloatArray(7)
    private var subPhase = 0.0f

    // 5 tables x 4 morph frames x 256 samples
    private val wavetables: Array<Array<FloatArray>> = Array(WavetableBank.values().size) { bankIdx ->
        Array(4) { frameIdx ->
            FloatArray(256) { sampleIdx ->
                val phaseNorm = sampleIdx / 256.0f
                generateTableSample(WavetableBank.values()[bankIdx], frameIdx, phaseNorm)
            }
        }
    }

    private fun generateTableSample(bank: WavetableBank, frame: Int, phase: Float): Float {
        val t = phase * 2.0 * PI
        return when (bank) {
            WavetableBank.MODERN_ANALOG -> when (frame) {
                0 -> sin(t).toFloat()
                1 -> (2.0f * phase - 1.0f) * 0.9f
                2 -> (if (phase < 0.5f) 1.0f else -1.0f) * 0.8f
                else -> (sin(t) * 0.5 + sin(2 * t) * 0.3 + sin(3 * t) * 0.2).toFloat()
            }
            WavetableBank.CYBER_FORMANT -> when (frame) {
                0 -> (sin(t) * sin(5 * t)).toFloat()
                1 -> (sin(t) + 0.6 * sin(3 * t) + 0.4 * sin(7 * t)).toFloat()
                2 -> (sin(2 * t) * (1.0 - phase)).toFloat() * 1.5f
                else -> (sin(t) + sin(4 * t) * 0.5 + sin(8 * t) * 0.3).toFloat()
            }
            WavetableBank.METALLIC_GLASS -> when (frame) {
                0 -> (sin(t) + 0.5 * sin(2.73 * t)).toFloat()
                1 -> (sin(t) + 0.4 * sin(5.1 * t) + 0.3 * sin(9.2 * t)).toFloat()
                2 -> (cos(t * 3.0) * sin(t * 7.0)).toFloat()
                else -> (sin(t * 1.414) + sin(t * 3.141) * 0.5).toFloat()
            }
            WavetableBank.CHIPTUNE_8BIT -> when (frame) {
                0 -> if (phase < 0.125f) 1.0f else -1.0f // 12.5% pulse
                1 -> if (phase < 0.25f) 1.0f else -1.0f // 25% pulse
                2 -> if (phase < 0.5f) 1.0f else -1.0f // 50% square
                else -> ((phase * 16).toInt() % 2 * 2 - 1).toFloat()
            }
            WavetableBank.ORGAN_HARMONICS -> when (frame) {
                0 -> (sin(t) + 0.8 * sin(2 * t) + 0.6 * sin(4 * t)).toFloat() * 0.45f
                1 -> (sin(t) + sin(3 * t) * 0.5 + sin(5 * t) * 0.3 + sin(7 * t) * 0.2).toFloat() * 0.5f
                2 -> (sin(t) * 0.5 + sin(2 * t) * 0.5 + sin(6 * t) * 0.4 + sin(8 * t) * 0.3).toFloat() * 0.45f
                else -> (sin(t) + sin(2 * t) + sin(3 * t) + sin(4 * t)).toFloat() * 0.3f
            }
        }
    }

    fun renderSample(frequency: Float): Float {
        if (frequency <= 1.0f) return 0.0f

        val bankIdx = bank.ordinal.coerceIn(0, wavetables.size - 1)
        val tables = wavetables[bankIdx]

        // Frame morph interpolation (0..3)
        val framePos = (tablePosition.coerceIn(0f, 1f) * (tables.size - 1))
        val frame0 = framePos.toInt().coerceIn(0, tables.size - 1)
        val frame1 = (frame0 + 1).coerceIn(0, tables.size - 1)
        val frameFrac = framePos - frame0

        var sum = 0.0f
        val numVoices = unisonVoices.coerceIn(1, 7)

        for (v in 0 until numVoices) {
            val detuneSpread = if (numVoices > 1) {
                ((v.toFloat() / (numVoices - 1)) - 0.5f) * unisonDetune * 0.06f
            } else 0.0f

            val voiceFreq = frequency * (1.0f + detuneSpread)
            phases[v] += voiceFreq / sampleRate
            if (phases[v] >= 1.0f) phases[v] -= 1.0f

            var p = phases[v]

            // Apply Warp Modifier
            when (warpMode) {
                WavetableWarpMode.PWM -> {
                    val duty = (0.5f + warpAmount * 0.4f).coerceIn(0.1f, 0.9f)
                    p = if (p < duty) (p / duty) * 0.5f else 0.5f + ((p - duty) / (1.0f - duty)) * 0.5f
                }
                WavetableWarpMode.SYNC -> {
                    val syncMult = 1.0f + warpAmount * 4.0f
                    p = (p * syncMult) % 1.0f
                }
                WavetableWarpMode.BEND -> {
                    val bend = (warpAmount * 2.0f - 1.0f) * 0.8f
                    p = if (bend >= 0) p.pow(1.0f + bend * 3.0f) else 1.0f - (1.0f - p).pow(1.0f - bend * 3.0f)
                }
                WavetableWarpMode.FM -> {
                    p = (p + sin(2.0 * PI * p).toFloat() * warpAmount * 0.35f) % 1.0f
                    if (p < 0) p += 1.0f
                }
                WavetableWarpMode.NONE -> {}
            }

            val tableIndexFloat = p.coerceIn(0f, 0.999f) * 256.0f
            val idx0 = tableIndexFloat.toInt().coerceIn(0, 255)
            val idx1 = (idx0 + 1) % 256
            val idxFrac = tableIndexFloat - idx0

            // Interpolate table samples
            val s0 = tables[frame0][idx0] * (1.0f - idxFrac) + tables[frame0][idx1] * idxFrac
            val s1 = tables[frame1][idx0] * (1.0f - idxFrac) + tables[frame1][idx1] * idxFrac
            val morphed = s0 * (1.0f - frameFrac) + s1 * frameFrac

            sum += morphed
        }

        val unisonOut = (sum / sqrt(numVoices.toDouble()).toFloat()).coerceIn(-1.5f, 1.5f)

        // Sub Oscillator (1 Octave Below Sine)
        subPhase += (frequency * 0.5f) / sampleRate
        if (subPhase >= 1.0f) subPhase -= 1.0f
        val subOut = sin(2.0 * PI * subPhase).toFloat() * subLevel

        return (unisonOut * 0.85f + subOut).coerceIn(-1.0f, 1.0f)
    }

    fun getWaveformVisual(resolution: Int = 128): FloatArray {
        val out = FloatArray(resolution)
        val bankIdx = bank.ordinal.coerceIn(0, wavetables.size - 1)
        val tables = wavetables[bankIdx]
        val framePos = (tablePosition.coerceIn(0f, 1f) * (tables.size - 1))
        val frame0 = framePos.toInt().coerceIn(0, tables.size - 1)
        val frame1 = (frame0 + 1).coerceIn(0, tables.size - 1)
        val frameFrac = framePos - frame0

        for (i in 0 until resolution) {
            val p = i / resolution.toFloat()
            val tableIndexFloat = p * 255.0f
            val idx0 = tableIndexFloat.toInt().coerceIn(0, 255)
            val idx1 = (idx0 + 1) % 256
            val idxFrac = tableIndexFloat - idx0

            val s0 = tables[frame0][idx0] * (1.0f - idxFrac) + tables[frame0][idx1] * idxFrac
            val s1 = tables[frame1][idx0] * (1.0f - idxFrac) + tables[frame1][idx1] * idxFrac
            out[i] = s0 * (1.0f - frameFrac) + s1 * frameFrac
        }
        return out
    }
}

// =========================================================================
// 2. 4-OPERATOR FM SYNTHESIZER (ABLETON OPERATOR)
// =========================================================================
enum class FmAlgorithm(val displayName: String, val description: String) {
    CASCADE_STACK("1: D → C → B → A", "Pure serial cascade FM stack for bells & metallic brass"),
    DUAL_STACK("2: (C+D) → B → A", "Branching dual-modulator stack for aggressive bass"),
    PARALLEL_MOD("3: (B+C+D) → A", "Triple parallel modulators into carrier A for rich complex textures"),
    ALL_PARALLEL("4: A + B + C + D", "Additive 4-voice organ / drawbar synthesizer")
}

data class FmOperatorState(
    var ratio: Float = 1.0f, // 0.5, 1, 2, 3, 4, etc.
    var fineTune: Float = 0.0f, // -50 to +50 cents
    var level: Float = 0.7f,
    var feedback: Float = 0.0f, // 0 to 1
    var attackTime: Float = 0.01f,
    var decayTime: Float = 0.4f
)

class FmOperatorSynth(private val sampleRate: Int = 44100) {
    @Volatile var algorithm = FmAlgorithm.CASCADE_STACK
    val opA = FmOperatorState(ratio = 1.0f, level = 0.9f)
    val opB = FmOperatorState(ratio = 2.0f, level = 0.6f)
    val opC = FmOperatorState(ratio = 3.0f, level = 0.4f)
    val opD = FmOperatorState(ratio = 4.0f, level = 0.3f, feedback = 0.2f)

    val operators: Array<FmOperatorState>
        get() = arrayOf(opA, opB, opC, opD)

    private val phases = FloatArray(4)
    private var lastOpDOutput = 0.0f

    fun renderSample(baseFreq: Float, envGate: Float): Float {
        if (baseFreq <= 1.0f || envGate <= 0.001f) return 0.0f

        val ops = arrayOf(opA, opB, opC, opD)

        // Calculate phase increments for all 4 operators
        for (i in 0 until 4) {
            val freq = baseFreq * ops[i].ratio * (1.0f + ops[i].fineTune * 0.005f)
            phases[i] += freq / sampleRate
            if (phases[i] >= 1.0f) phases[i] -= 1.0f
        }

        // Modulator D with Feedback
        val dPhase = phases[3] + (lastOpDOutput * opD.feedback * 0.5f)
        val dOut = sin(2.0 * PI * dPhase).toFloat() * opD.level * envGate
        lastOpDOutput = dOut

        return when (algorithm) {
            FmAlgorithm.CASCADE_STACK -> {
                // D -> C -> B -> A
                val cPhase = phases[2] + dOut * 3.0f
                val cOut = sin(2.0 * PI * cPhase).toFloat() * opC.level * envGate

                val bPhase = phases[1] + cOut * 3.0f
                val bOut = sin(2.0 * PI * bPhase).toFloat() * opB.level * envGate

                val aPhase = phases[0] + bOut * 3.0f
                val aOut = sin(2.0 * PI * aPhase).toFloat() * opA.level
                aOut
            }
            FmAlgorithm.DUAL_STACK -> {
                // (C + D) -> B -> A
                val cOut = sin(2.0 * PI * phases[2]).toFloat() * opC.level * envGate
                val bPhase = phases[1] + (cOut + dOut) * 2.5f
                val bOut = sin(2.0 * PI * bPhase).toFloat() * opB.level * envGate

                val aPhase = phases[0] + bOut * 3.0f
                val aOut = sin(2.0 * PI * aPhase).toFloat() * opA.level
                aOut
            }
            FmAlgorithm.PARALLEL_MOD -> {
                // (B + C + D) -> A
                val bOut = sin(2.0 * PI * phases[1]).toFloat() * opB.level * envGate
                val cOut = sin(2.0 * PI * phases[2]).toFloat() * opC.level * envGate

                val modSum = (bOut + cOut + dOut) * 2.0f
                val aPhase = phases[0] + modSum
                sin(2.0 * PI * aPhase).toFloat() * opA.level
            }
            FmAlgorithm.ALL_PARALLEL -> {
                // A + B + C + D
                val aOut = sin(2.0 * PI * phases[0]).toFloat() * opA.level
                val bOut = sin(2.0 * PI * phases[1]).toFloat() * opB.level
                val cOut = sin(2.0 * PI * phases[2]).toFloat() * opC.level
                ((aOut + bOut + cOut + dOut) * 0.35f).coerceIn(-1.0f, 1.0f)
            }
        }
    }
}

// =========================================================================
// 3. ABLETON SIMPLER / SAMPLER INSTRUMENT
// =========================================================================
enum class SamplerPlaybackMode(val displayName: String, val shortName: String) {
    CLASSIC("Classic Loop", "CLASSIC"),
    ONE_SHOT("One-Shot", "1-SHOT"),
    SLICING("8-Pad Slices", "SLICING")
}

data class SamplePreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String,
    val rootPitch: Int = 60, // C4
    val durationSeconds: Float = 1.0f,
    val generatorType: SampleGeneratorType
)

enum class SampleGeneratorType {
    SUB_808,
    RHODES_CHORD,
    ACOUSTIC_SNARE,
    VOCAL_CHANT,
    GRAND_PIANO,
    CYBER_PLUCK,
    AMEN_BREAK,
    VINYL_CRACKLE
}

class SamplerInstrument(private val sampleRate: Int = 44100) {
    companion object {
        val PRESET_SAMPLES = listOf(
            SamplePreset(name = "808 Deep Sub Boom", category = "Bass & 808", rootPitch = 36, durationSeconds = 1.5f, generatorType = SampleGeneratorType.SUB_808),
            SamplePreset(name = "Lo-Fi Rhodes Major 9", category = "Keys & Chords", rootPitch = 60, durationSeconds = 2.0f, generatorType = SampleGeneratorType.RHODES_CHORD),
            SamplePreset(name = "Acoustic Wood Snare", category = "Drums", rootPitch = 60, durationSeconds = 0.8f, generatorType = SampleGeneratorType.ACOUSTIC_SNARE),
            SamplePreset(name = "Ethereal Vocal Chop", category = "Vocals", rootPitch = 60, durationSeconds = 1.2f, generatorType = SampleGeneratorType.VOCAL_CHANT),
            SamplePreset(name = "Studio Grand Piano C4", category = "Acoustic", rootPitch = 60, durationSeconds = 2.5f, generatorType = SampleGeneratorType.GRAND_PIANO),
            SamplePreset(name = "Cyberpunk Neo Pluck", category = "Synths", rootPitch = 60, durationSeconds = 0.9f, generatorType = SampleGeneratorType.CYBER_PLUCK),
            SamplePreset(name = "Amen Drum Break Loop", category = "Loops & Breaks", rootPitch = 60, durationSeconds = 2.0f, generatorType = SampleGeneratorType.AMEN_BREAK),
            SamplePreset(name = "Dusty Vinyl Texture", category = "Textures & FX", rootPitch = 60, durationSeconds = 2.0f, generatorType = SampleGeneratorType.VINYL_CRACKLE)
        )
        val SAMPLE_PRESETS_COUNT: Int = PRESET_SAMPLES.size
        fun getPresetName(index: Int): String = PRESET_SAMPLES.getOrNull(index)?.name ?: "Preset $index"
    }

    @Volatile var mode = SamplerPlaybackMode.CLASSIC
    @Volatile var selectedPresetIndex = 0
    @Volatile var startPoint = 0.0f // 0.0 to 1.0
    @Volatile var endPoint = 1.0f   // 0.0 to 1.0
    @Volatile var loopStart = 0.2f
    @Volatile var loopEnd = 0.8f
    @Volatile var isLoopEnabled = true
    @Volatile var isReversed = false
    @Volatile var transposeSemitones = 0 // -24 to +24
    @Volatile var fineDetune = 0.0f // -50 to +50 cents
    @Volatile var filterCutoff = 8000.0f
    @Volatile var filterResonance = 1.0f
    @Volatile var attackTime = 0.01f
    @Volatile var releaseTime = 0.35f
    @Volatile var activeSliceIndex = 0 // 0 to 7 for Slicing mode

    // Audio sample buffer
    @Volatile var sampleBuffer: FloatArray = generateSampleAudio(PRESET_SAMPLES[0])
    private val bufferLock = Any()

    // Playback state
    @Volatile var playheadPos = 0.0
    val playheadPosDouble: Double get() = playheadPos
    fun triggerSlice(sliceIndex: Int) {
        triggerNoteOn(60 + sliceIndex, sliceOverride = sliceIndex)
    }
    @Volatile private var isVoiceActive = false
    @Volatile private var currentPitch = 60
    private var envAmp = 0.0f
    private var envStage = 0 // 0=idle, 1=attack, 2=sustain, 3=release
    private var envSamples = 0L

    // Filter state
    private var filterLow = 0.0f
    private var filterBand = 0.0f

    fun loadPreset(index: Int) {
        if (index in PRESET_SAMPLES.indices) {
            selectedPresetIndex = index
            val preset = PRESET_SAMPLES[index]
            synchronized(bufferLock) {
                sampleBuffer = generateSampleAudio(preset)
            }
            startPoint = 0.0f
            endPoint = 1.0f
            loopStart = 0.2f
            loopEnd = 0.8f
        }
    }

    fun loadCustomBuffer(pcmShorts: ShortArray) {
        if (pcmShorts.isEmpty()) return
        val floats = FloatArray(pcmShorts.size)
        for (i in pcmShorts.indices) {
            floats[i] = pcmShorts[i] / 32767.0f
        }
        synchronized(bufferLock) {
            sampleBuffer = floats
        }
        startPoint = 0.0f
        endPoint = 1.0f
        loopStart = 0.1f
        loopEnd = 0.9f
    }

    fun triggerNoteOn(pitch: Int, velocity: Float = 0.9f, sliceOverride: Int? = null) {
        currentPitch = pitch
        val root = PRESET_SAMPLES.getOrNull(selectedPresetIndex)?.rootPitch ?: 60

        val totalLen = sampleBuffer.size.toDouble()

        if (mode == SamplerPlaybackMode.SLICING) {
            val slice = sliceOverride ?: (pitch % 8)
            activeSliceIndex = slice.coerceIn(0, 7)
            val sliceSize = totalLen / 8.0
            playheadPos = if (isReversed) (slice + 1) * sliceSize - 1.0 else slice * sliceSize
        } else {
            val startIdx = (startPoint.coerceIn(0f, 0.99f) * totalLen)
            val endIdx = (endPoint.coerceIn(0.01f, 1f) * totalLen)
            playheadPos = if (isReversed) endIdx - 1.0 else startIdx
        }

        isVoiceActive = true
        envStage = 1 // Attack
        envSamples = 0
    }

    fun triggerNoteOff(pitch: Int) {
        if (currentPitch == pitch && mode != SamplerPlaybackMode.ONE_SHOT) {
            envStage = 3 // Release
            envSamples = 0
        }
    }

    fun renderSample(): Float {
        if (!isVoiceActive || sampleBuffer.isEmpty()) return 0.0f

        val root = PRESET_SAMPLES.getOrNull(selectedPresetIndex)?.rootPitch ?: 60
        val pitchOffset = (currentPitch - root) + transposeSemitones + (fineDetune / 100.0f)
        val speedRatio = 2.0.pow(pitchOffset.toDouble() / 12.0)
        val step = if (isReversed) -speedRatio else speedRatio

        val buf = sampleBuffer
        val totalSize = buf.size.toDouble()

        val startBound = (startPoint * totalSize).coerceIn(0.0, totalSize - 1.0)
        val endBound = (endPoint * totalSize).coerceIn(startBound + 1.0, totalSize)

        // Interpolate sample at playhead position
        val idx0 = playheadPos.toInt().coerceIn(0, buf.size - 1)
        val idx1 = (idx0 + 1).coerceIn(0, buf.size - 1)
        val frac = (playheadPos - idx0).toFloat()
        val rawSample = buf[idx0] * (1.0f - frac) + buf[idx1] * frac

        // Advance playhead
        playheadPos += step

        // Handle loop / boundaries
        when (mode) {
            SamplerPlaybackMode.CLASSIC -> {
                if (isLoopEnabled) {
                    val lStart = (loopStart * totalSize).coerceIn(startBound, endBound)
                    val lEnd = (loopEnd * totalSize).coerceIn(lStart + 1.0, endBound)
                    if (!isReversed && playheadPos >= lEnd) {
                        playheadPos = lStart
                    } else if (isReversed && playheadPos <= lStart) {
                        playheadPos = lEnd
                    }
                } else {
                    if (playheadPos >= endBound || playheadPos < startBound) {
                        isVoiceActive = false
                    }
                }
            }
            SamplerPlaybackMode.ONE_SHOT -> {
                if (playheadPos >= endBound || playheadPos < startBound) {
                    isVoiceActive = false
                }
            }
            SamplerPlaybackMode.SLICING -> {
                val sliceSize = totalSize / 8.0
                val sliceStart = activeSliceIndex * sliceSize
                val sliceEnd = (activeSliceIndex + 1) * sliceSize
                if (playheadPos >= sliceEnd || playheadPos < sliceStart) {
                    isVoiceActive = false
                }
            }
        }

        // Envelope
        envSamples++
        when (envStage) {
            1 -> { // Attack
                val atkSamples = (attackTime * sampleRate).toLong().coerceAtLeast(1)
                envAmp = (envSamples.toFloat() / atkSamples).coerceIn(0f, 1f)
                if (envSamples >= atkSamples) {
                    envStage = 2 // Sustain
                }
            }
            2 -> { // Sustain
                envAmp = 1.0f
            }
            3 -> { // Release
                val relSamples = (releaseTime * sampleRate).toLong().coerceAtLeast(1)
                val relFrac = (envSamples.toFloat() / relSamples).coerceIn(0f, 1f)
                envAmp = 1.0f - relFrac
                if (envAmp <= 0.0f) {
                    envAmp = 0.0f
                    isVoiceActive = false
                    envStage = 0
                }
            }
        }

        // Resonant Auto-Filter
        val f = 2.0f * sin(PI * filterCutoff.coerceIn(40f, 16000f) / sampleRate).toFloat()
        val qDamping = 1.0f / filterResonance.coerceAtLeast(0.5f)
        val highpass = rawSample - filterLow - qDamping * filterBand
        filterBand += f * highpass
        filterLow += f * filterBand

        return filterLow * envAmp
    }

    private fun generateSampleAudio(preset: SamplePreset): FloatArray {
        val lengthSamples = (preset.durationSeconds * sampleRate).toInt().coerceAtLeast(1024)
        val buf = FloatArray(lengthSamples)

        for (i in 0 until lengthSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / lengthSamples

            val sample = when (preset.generatorType) {
                SampleGeneratorType.SUB_808 -> {
                    // Pitch-dropping 808 sub kick
                    val freq = 120.0f * exp(-progress * 8.0f) + 40.0f
                    val phase = 2.0 * PI * freq * t
                    val env = (1.0f - progress).pow(1.5f)
                    (sin(phase) + 0.15 * sin(2 * phase)).toFloat() * env * 1.2f
                }
                SampleGeneratorType.RHODES_CHORD -> {
                    // C Major 9 chord (C4, E4, G4, B4, D5)
                    val env = exp(-progress * 2.5f)
                    val c = sin(2.0 * PI * 261.63 * t)
                    val e = sin(2.0 * PI * 329.63 * t)
                    val g = sin(2.0 * PI * 392.00 * t)
                    val b = sin(2.0 * PI * 493.88 * t)
                    val d = sin(2.0 * PI * 587.33 * t) * 0.7
                    val tine = sin(2.0 * PI * 261.63 * 7 * t) * exp(-progress * 12.0f) * 0.3
                    ((c + e + g + b + d) * 0.2 + tine).toFloat() * env
                }
                SampleGeneratorType.ACOUSTIC_SNARE -> {
                    val tone = sin(2.0 * PI * (180.0 * exp(-progress * 15.0)) * t) * exp(-progress * 10.0f)
                    val noise = (Random.nextFloat() * 2.0f - 1.0f) * exp(-progress * 6.0f)
                    (tone * 0.5f + noise * 0.5f)
                }
                SampleGeneratorType.VOCAL_CHANT -> {
                    val f0 = 261.63f
                    val f1 = 800.0f // Formant 1 "Ah"
                    val f2 = 1200.0f // Formant 2
                    val glottal = (2.0f * (t * f0 % 1.0f) - 1.0f)
                    val formant = sin(2.0 * PI * f1 * t) * 0.6 + sin(2.0 * PI * f2 * t) * 0.4
                    val env = (sin(PI * progress).toFloat()).pow(0.8f)
                    (glottal * 0.3f + formant.toFloat() * 0.7f) * env
                }
                SampleGeneratorType.GRAND_PIANO -> {
                    val f0 = 261.63
                    val env = exp(-progress * 1.8f)
                    val h1 = sin(2.0 * PI * f0 * t)
                    val h2 = sin(2.0 * PI * f0 * 2 * t) * 0.5
                    val h3 = sin(2.0 * PI * f0 * 3 * t) * 0.25
                    val h4 = sin(2.0 * PI * f0 * 4 * t) * 0.12
                    val hammer = (Random.nextFloat() * 2.0f - 1.0f) * exp(-progress * 40.0f) * 0.3f
                    ((h1 + h2 + h3 + h4) * 0.45).toFloat() * env + hammer
                }
                SampleGeneratorType.CYBER_PLUCK -> {
                    val f0 = 261.63
                    val env = exp(-progress * 5.5f)
                    val saw = (2.0f * (t * f0.toFloat() % 1.0f) - 1.0f)
                    val sqr = if ((t * f0.toFloat() % 1.0f) < 0.5f) 1.0f else -1.0f
                    (saw * 0.6f + sqr * 0.4f) * env
                }
                SampleGeneratorType.AMEN_BREAK -> {
                    // Drum break pattern simulation
                    val beatTime = (t * 2.5f) % 1.0f
                    val kick = if (beatTime < 0.2f) sin(2.0 * PI * 60.0 * beatTime).toFloat() * (1.0f - beatTime * 5.0f) else 0f
                    val snare = if (beatTime > 0.5f && beatTime < 0.75f) (Random.nextFloat() * 2.0f - 1.0f) * (1.0f - (beatTime - 0.5f) * 4.0f) else 0f
                    val hat = (Random.nextFloat() * 2.0f - 1.0f) * 0.2f * exp(-((beatTime * 4.0f) % 1.0f) * 8.0f)
                    (kick * 0.7f + snare * 0.6f + hat * 0.3f).coerceIn(-1.0f, 1.0f)
                }
                SampleGeneratorType.VINYL_CRACKLE -> {
                    val crackle = if (Random.nextFloat() < 0.008f) (Random.nextFloat() * 2.0f - 1.0f) * 0.8f else 0.0f
                    val hiss = (Random.nextFloat() * 2.0f - 1.0f) * 0.05f
                    (crackle + hiss)
                }
            }

            buf[i] = sample.toFloat().coerceIn(-1.0f, 1.0f)
        }

        return buf
    }
}

// =========================================================================
// 4. RHODES ELECTRIC PIANO PHYSICAL MODEL
// =========================================================================
class ElectricPianoSynth(private val sampleRate: Int = 44100) {
    @Volatile var tineDecay = 1.8f
    @Volatile var bellHarmonic = 0.45f
    @Volatile var tremoloRate = 4.5f
    @Volatile var tremoloDepth = 0.35f
    @Volatile var drive = 0.2f

    private var phase = 0.0f
    private var tinePhase = 0.0f
    private var tremoloPhase = 0.0f

    fun renderSample(baseFreq: Float, envGate: Float): Float {
        if (baseFreq <= 1.0f || envGate <= 0.001f) return 0.0f

        phase += baseFreq / sampleRate
        if (phase >= 1.0f) phase -= 1.0f

        tinePhase += (baseFreq * 5.04f) / sampleRate // Non-integer bell tine harmonic
        if (tinePhase >= 1.0f) tinePhase -= 1.0f

        tremoloPhase += tremoloRate / sampleRate
        if (tremoloPhase >= 1.0f) tremoloPhase -= 1.0f

        val fundamental = sin(2.0 * PI * phase).toFloat()
        val secondHarmonic = sin(4.0 * PI * phase).toFloat() * 0.35f
        val tine = sin(2.0 * PI * tinePhase).toFloat() * bellHarmonic * envGate

        val raw = (fundamental + secondHarmonic + tine) * envGate

        // Tremolo LFO
        val tremolo = 1.0f - (sin(2.0 * PI * tremoloPhase).toFloat() * 0.5f + 0.5f) * tremoloDepth

        // Tube warm drive
        val driven = tanh(raw * (1.0f + drive * 3.0f))

        return driven * tremolo
    }
}

// =========================================================================
// 5. SOLINA STRING ENSEMBLE PAD MACHINE
// =========================================================================
class StringPadSynth(private val sampleRate: Int = 44100) {
    @Volatile var chorusDepth = 0.65f
    @Volatile var ensembleSpeed = 0.8f
    @Volatile var octaveLayer = 0.5f

    private val oscPhases = FloatArray(6)
    private var lfo1 = 0.0f
    private var lfo2 = 0.0f

    fun renderSample(baseFreq: Float, envGate: Float): Float {
        if (baseFreq <= 1.0f || envGate <= 0.001f) return 0.0f

        lfo1 += (ensembleSpeed * 0.6f) / sampleRate
        if (lfo1 >= 1.0f) lfo1 -= 1.0f

        lfo2 += (ensembleSpeed * 4.8f) / sampleRate
        if (lfo2 >= 1.0f) lfo2 -= 1.0f

        val chorusMod = (sin(2.0 * PI * lfo1) * 0.02 + sin(2.0 * PI * lfo2) * 0.008).toFloat() * chorusDepth

        var sum = 0.0f
        for (i in 0 until 3) {
            val detune = (i - 1) * 0.008f + chorusMod * (if (i % 2 == 0) 1f else -1f)
            oscPhases[i] += (baseFreq * (1.0f + detune)) / sampleRate
            if (oscPhases[i] >= 1.0f) oscPhases[i] -= 1.0f
            // Sawtooth
            sum += (2.0f * oscPhases[i] - 1.0f)
        }

        // Octave Up layer
        for (i in 3 until 6) {
            val detune = (i - 4) * 0.006f - chorusMod
            oscPhases[i] += (baseFreq * 2.0f * (1.0f + detune)) / sampleRate
            if (oscPhases[i] >= 1.0f) oscPhases[i] -= 1.0f
            sum += (2.0f * oscPhases[i] - 1.0f) * octaveLayer
        }

        return (sum * 0.28f * envGate).coerceIn(-1.0f, 1.0f)
    }
}
