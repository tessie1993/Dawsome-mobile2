package com.example.synth

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.*

enum class EffectType(val displayName: String, val tag: String, val description: String) {
    REVERB("Reverb", "REV", "Schroeder stereo algorithmic reverb space"),
    DELAY("Tape Delay", "DLY", "Analog stereo ping-pong tape echo"),
    FILTER("Resonant Filter", "VCF", "Chamberlin state-variable LP/HP/BP filter"),
    DISTORTION("Overdrive & Tape", "SAT", "Warm analog saturation & hard overdrive"),
    CHORUS("Chorus Ensemble", "CHO", "Multi-voice lush stereo chorus & flanger"),
    PARAMETRIC_EQ("3-Band EQ", "EQ3", "Studio low shelf, mid peak & high shelf"),
    COMPRESSOR("Master Limiter", "CMP", "Dynamic peak compressor & auto makeup gain")
}

sealed class AudioEffectModule(
    val id: String = UUID.randomUUID().toString(),
    val type: EffectType
) {
    @Volatile var isEnabled: Boolean = true
    abstract fun processStereo(inL: Float, inR: Float): Pair<Float, Float>
    abstract fun clear()
}

// 1. Schroeder Stereo Algorithmic Reverb
class ReverbModule(private val sampleRate: Int = 44100) : AudioEffectModule(type = EffectType.REVERB) {
    @Volatile var roomSize: Float = 0.65f
    @Volatile var damping: Float = 0.35f
    @Volatile var preDelayMs: Float = 15.0f
    @Volatile var mix: Float = 0.25f

    // 4 Comb filters per channel with slight stereo offset
    private val combDelaysL = intArrayOf(
        (sampleRate * 0.0297).toInt(),
        (sampleRate * 0.0371).toInt(),
        (sampleRate * 0.0411).toInt(),
        (sampleRate * 0.0437).toInt()
    )
    private val combDelaysR = intArrayOf(
        (sampleRate * 0.0307).toInt(),
        (sampleRate * 0.0361).toInt(),
        (sampleRate * 0.0421).toInt(),
        (sampleRate * 0.0449).toInt()
    )

    private val combBuffersL = Array(4) { idx -> FloatArray(combDelaysL[idx]) }
    private val combBuffersR = Array(4) { idx -> FloatArray(combDelaysR[idx]) }
    private val combIndicesL = IntArray(4)
    private val combIndicesR = IntArray(4)
    private val combFilterStoreL = FloatArray(4)
    private val combFilterStoreR = FloatArray(4)

    // 2 All-pass filters per channel
    private val allpassDelaysL = intArrayOf((sampleRate * 0.0050).toInt(), (sampleRate * 0.0017).toInt())
    private val allpassDelaysR = intArrayOf((sampleRate * 0.0053).toInt(), (sampleRate * 0.0019).toInt())
    private val allpassBuffersL = Array(2) { idx -> FloatArray(allpassDelaysL[idx]) }
    private val allpassBuffersR = Array(2) { idx -> FloatArray(allpassDelaysR[idx]) }
    private val allpassIndicesL = IntArray(2)
    private val allpassIndicesR = IntArray(2)

    // Pre-delay buffer
    private val maxPreDelaySamples = (sampleRate * 0.15).toInt()
    private val preDelayBufferL = FloatArray(maxPreDelaySamples)
    private val preDelayBufferR = FloatArray(maxPreDelaySamples)
    private var preDelayWriteIdx = 0

    override fun processStereo(inL: Float, inR: Float): Pair<Float, Float> {
        if (!isEnabled || mix <= 0.001f) return Pair(inL, inR)

        // Pre-delay write
        preDelayBufferL[preDelayWriteIdx] = inL
        preDelayBufferR[preDelayWriteIdx] = inR

        val preDelaySamples = ((preDelayMs / 1000f) * sampleRate).toInt().coerceIn(0, maxPreDelaySamples - 1)
        var preReadIdx = preDelayWriteIdx - preDelaySamples
        if (preReadIdx < 0) preReadIdx += maxPreDelaySamples

        val delayedInL = preDelayBufferL[preReadIdx]
        val delayedInR = preDelayBufferR[preReadIdx]
        preDelayWriteIdx = (preDelayWriteIdx + 1) % maxPreDelaySamples

        val feedback = roomSize.coerceIn(0.1f, 0.96f)
        val damp = damping.coerceIn(0.0f, 0.9f)

        // Process Comb Left
        var combSumL = 0.0f
        for (i in 0 until 4) {
            val buf = combBuffersL[i]
            val idx = combIndicesL[i]
            val output = buf[idx]
            combFilterStoreL[i] = output * (1.0f - damp) + combFilterStoreL[i] * damp
            buf[idx] = delayedInL + combFilterStoreL[i] * feedback
            combIndicesL[i] = (idx + 1) % combDelaysL[i]
            combSumL += output
        }

        // Process Comb Right
        var combSumR = 0.0f
        for (i in 0 until 4) {
            val buf = combBuffersR[i]
            val idx = combIndicesR[i]
            val output = buf[idx]
            combFilterStoreR[i] = output * (1.0f - damp) + combFilterStoreR[i] * damp
            buf[idx] = delayedInR + combFilterStoreR[i] * feedback
            combIndicesR[i] = (idx + 1) % combDelaysR[i]
            combSumR += output
        }

        var apL = combSumL * 0.25f
        var apR = combSumR * 0.25f

        // Process All-Pass Series Left
        for (i in 0 until 2) {
            val buf = allpassBuffersL[i]
            val idx = allpassIndicesL[i]
            val bufOut = buf[idx]
            val apFeedback = 0.5f
            val currentIn = apL
            buf[idx] = currentIn + bufOut * apFeedback
            apL = -currentIn * apFeedback + bufOut
            allpassIndicesL[i] = (idx + 1) % allpassDelaysL[i]
        }

        // Process All-Pass Series Right
        for (i in 0 until 2) {
            val buf = allpassBuffersR[i]
            val idx = allpassIndicesR[i]
            val bufOut = buf[idx]
            val apFeedback = 0.5f
            val currentIn = apR
            buf[idx] = currentIn + bufOut * apFeedback
            apR = -currentIn * apFeedback + bufOut
            allpassIndicesR[i] = (idx + 1) % allpassDelaysR[i]
        }

        val outL = inL * (1.0f - mix) + apL * mix
        val outR = inR * (1.0f - mix) + apR * mix
        return Pair(outL, outR)
    }

    override fun clear() {
        for (buf in combBuffersL) buf.fill(0f)
        for (buf in combBuffersR) buf.fill(0f)
        for (buf in allpassBuffersL) buf.fill(0f)
        for (buf in allpassBuffersR) buf.fill(0f)
        combIndicesL.fill(0)
        combIndicesR.fill(0)
        combFilterStoreL.fill(0f)
        combFilterStoreR.fill(0f)
        allpassIndicesL.fill(0)
        allpassIndicesR.fill(0)
        preDelayBufferL.fill(0f)
        preDelayBufferR.fill(0f)
        preDelayWriteIdx = 0
    }
}

// 2. Stereo Ping-Pong Analog Tape Delay
class DelayModule(private val sampleRate: Int = 44100) : AudioEffectModule(type = EffectType.DELAY) {
    @Volatile var timeMs: Float = 280.0f
    @Volatile var feedback: Float = 0.35f
    @Volatile var mix: Float = 0.30f
    @Volatile var pingPong: Boolean = true
    @Volatile var tone: Float = 0.7f // 0.0 (dark tape) to 1.0 (bright digital)

    private val maxBufferSamples = sampleRate * 2 // 2 seconds buffer
    private val bufferL = FloatArray(maxBufferSamples)
    private val bufferR = FloatArray(maxBufferSamples)
    private var writeIdx = 0

    private var filterStateL = 0.0f
    private var filterStateR = 0.0f

    override fun processStereo(inL: Float, inR: Float): Pair<Float, Float> {
        if (!isEnabled || mix <= 0.001f) return Pair(inL, inR)

        val delaySamplesL = ((timeMs / 1000f) * sampleRate).toInt().coerceIn(10, maxBufferSamples - 1)
        val delaySamplesR = if (pingPong) {
            ((timeMs * 0.75f / 1000f) * sampleRate).toInt().coerceIn(10, maxBufferSamples - 1)
        } else {
            delaySamplesL
        }

        var readIdxL = writeIdx - delaySamplesL
        if (readIdxL < 0) readIdxL += maxBufferSamples
        val rawOutL = bufferL[readIdxL]

        var readIdxR = writeIdx - delaySamplesR
        if (readIdxR < 0) readIdxR += maxBufferSamples
        val rawOutR = bufferR[readIdxR]

        // Tape Tone low-pass filter
        val alpha = (0.2f + tone * 0.75f).coerceIn(0.05f, 0.95f)
        filterStateL += alpha * (rawOutL - filterStateL)
        filterStateR += alpha * (rawOutR - filterStateR)

        val filteredL = filterStateL
        val filteredR = filterStateR

        // Feedback calculation with soft analog tape saturation
        val fbL = if (pingPong) filteredR * feedback else filteredL * feedback
        val fbR = if (pingPong) filteredL * feedback else filteredR * feedback

        // Soft-clip feedback to prevent runaway overload
        val satL = tanh((inL + fbL).toDouble()).toFloat()
        val satR = tanh((inR + fbR).toDouble()).toFloat()

        bufferL[writeIdx] = satL
        bufferR[writeIdx] = satR

        writeIdx = (writeIdx + 1) % maxBufferSamples

        val finalL = inL * (1.0f - mix) + filteredL * mix
        val finalR = inR * (1.0f - mix) + filteredR * mix
        return Pair(finalL, finalR)
    }

    override fun clear() {
        bufferL.fill(0f)
        bufferR.fill(0f)
        writeIdx = 0
        filterStateL = 0.0f
        filterStateR = 0.0f
    }
}

// 3. Resonant State-Variable Chamberlin Filter Module
class FilterModule(private val sampleRate: Int = 44100) : AudioEffectModule(type = EffectType.FILTER) {
    @Volatile var filterType: FilterType = FilterType.LOW_PASS
    @Volatile var cutoffHz: Float = 2500.0f
    @Volatile var resonance: Float = 1.5f // 0.5 to 8.0
    @Volatile var drive: Float = 1.0f // 1.0 to 3.0
    @Volatile var mix: Float = 1.0f

    private var lowL = 0.0f
    private var bandL = 0.0f
    private var lowR = 0.0f
    private var bandR = 0.0f

    override fun processStereo(inL: Float, inR: Float): Pair<Float, Float> {
        if (!isEnabled || mix <= 0.001f) return Pair(inL, inR)

        val clampedCutoff = cutoffHz.coerceIn(20.0f, (sampleRate * 0.45f))
        val f = 2.0f * sin(PI * clampedCutoff / sampleRate).toFloat().coerceIn(0.001f, 0.99f)
        val qDamping = 1.0f / resonance.coerceAtLeast(0.5f)

        // Left channel SVF
        val drivenL = (inL * drive).coerceIn(-2.0f, 2.0f)
        val highL = drivenL - lowL - qDamping * bandL
        bandL += f * highL
        lowL += f * bandL

        val filteredL = when (filterType) {
            FilterType.LOW_PASS -> lowL
            FilterType.HIGH_PASS -> highL
            FilterType.BAND_PASS -> bandL
        }

        // Right channel SVF
        val drivenR = (inR * drive).coerceIn(-2.0f, 2.0f)
        val highR = drivenR - lowR - qDamping * bandR
        bandR += f * highR
        lowR += f * bandR

        val filteredR = when (filterType) {
            FilterType.LOW_PASS -> lowR
            FilterType.HIGH_PASS -> highR
            FilterType.BAND_PASS -> bandR
        }

        val outL = inL * (1.0f - mix) + filteredL * mix
        val outR = inR * (1.0f - mix) + filteredR * mix
        return Pair(outL, outR)
    }

    override fun clear() {
        lowL = 0.0f
        bandL = 0.0f
        lowR = 0.0f
        bandR = 0.0f
    }
}

// 4. Analog Overdrive, Tube Saturation & Distortion Module
enum class SaturationMode(val label: String) {
    TAPE("Tape Warmth"),
    TUBE("Asymmetric Tube"),
    HARD_CLIP("Hard Distortion")
}

class DistortionModule : AudioEffectModule(type = EffectType.DISTORTION) {
    @Volatile var drive: Float = 0.35f   // 0.0 to 1.0
    @Volatile var tone: Float = 0.5f    // 0.0 (dark) to 1.0 (bright)
    @Volatile var mix: Float = 0.5f     // 0.0 to 1.0
    @Volatile var mode: SaturationMode = SaturationMode.TAPE

    private var lowpassL = 0.0f
    private var lowpassR = 0.0f

    override fun processStereo(inL: Float, inR: Float): Pair<Float, Float> {
        if (!isEnabled || mix <= 0.001f || drive <= 0.001f) return Pair(inL, inR)

        val gain = 1.0f + drive * 12.0f
        val boostedL = inL * gain
        val boostedR = inR * gain

        val clippedL = when (mode) {
            SaturationMode.TAPE -> tanh(boostedL.toDouble()).toFloat()
            SaturationMode.TUBE -> {
                // Asymmetrical soft saturation
                val x = boostedL
                if (x >= 0.0f) 1.0f - exp(-x.toDouble()).toFloat()
                else -1.0f + exp(x.toDouble()).toFloat()
            }
            SaturationMode.HARD_CLIP -> boostedL.coerceIn(-1.0f, 1.0f)
        }

        val clippedR = when (mode) {
            SaturationMode.TAPE -> tanh(boostedR.toDouble()).toFloat()
            SaturationMode.TUBE -> {
                val x = boostedR
                if (x >= 0.0f) 1.0f - exp(-x.toDouble()).toFloat()
                else -1.0f + exp(x.toDouble()).toFloat()
            }
            SaturationMode.HARD_CLIP -> boostedR.coerceIn(-1.0f, 1.0f)
        }

        // Tone low-pass filter
        val alpha = (0.15f + tone * 0.8f).coerceIn(0.05f, 0.95f)
        lowpassL += alpha * (clippedL - lowpassL)
        lowpassR += alpha * (clippedR - lowpassR)

        val outL = inL * (1.0f - mix) + lowpassL * mix
        val outR = inR * (1.0f - mix) + lowpassR * mix
        return Pair(outL, outR)
    }

    override fun clear() {
        lowpassL = 0.0f
        lowpassR = 0.0f
    }
}

// 5. Stereo Multi-Voice Chorus & Flanger Module
class ChorusModule(private val sampleRate: Int = 44100) : AudioEffectModule(type = EffectType.CHORUS) {
    @Volatile var rateHz: Float = 1.2f     // 0.1 to 6.0 Hz
    @Volatile var depth: Float = 0.6f      // 0.0 to 1.0
    @Volatile var feedback: Float = 0.25f  // 0.0 to 0.75
    @Volatile var mix: Float = 0.4f        // 0.0 to 1.0

    private val maxBufferSamples = (sampleRate * 0.05).toInt() // 50ms buffer
    private val bufferL = FloatArray(maxBufferSamples)
    private val bufferR = FloatArray(maxBufferSamples)
    private var writeIdx = 0
    private var lfoPhase = 0.0f

    override fun processStereo(inL: Float, inR: Float): Pair<Float, Float> {
        if (!isEnabled || mix <= 0.001f) return Pair(inL, inR)

        // Advance LFO
        lfoPhase += rateHz / sampleRate
        if (lfoPhase >= 1.0f) lfoPhase -= 1.0f

        // Stereo modulation: Right channel is 90 degrees out of phase
        val lfoL = sin(2.0 * PI * lfoPhase).toFloat()
        val lfoR = cos(2.0 * PI * lfoPhase).toFloat()

        val baseDelaySamples = sampleRate * 0.012f // 12ms center
        val modDepthSamples = sampleRate * 0.008f * depth

        val delaySamplesL = (baseDelaySamples + lfoL * modDepthSamples).coerceIn(2f, maxBufferSamples - 2f)
        val delaySamplesR = (baseDelaySamples + lfoR * modDepthSamples).coerceIn(2f, maxBufferSamples - 2f)

        // Read with linear interpolation Left
        val readPosL = writeIdx - delaySamplesL
        val realReadPosL = if (readPosL < 0) readPosL + maxBufferSamples else readPosL
        val idxL0 = realReadPosL.toInt() % maxBufferSamples
        val idxL1 = (idxL0 + 1) % maxBufferSamples
        val fracL = realReadPosL - realReadPosL.toInt()
        val delayedL = bufferL[idxL0] * (1.0f - fracL) + bufferL[idxL1] * fracL

        // Read with linear interpolation Right
        val readPosR = writeIdx - delaySamplesR
        val realReadPosR = if (readPosR < 0) readPosR + maxBufferSamples else readPosR
        val idxR0 = realReadPosR.toInt() % maxBufferSamples
        val idxR1 = (idxR0 + 1) % maxBufferSamples
        val fracR = realReadPosR - realReadPosR.toInt()
        val delayedR = bufferR[idxR0] * (1.0f - fracR) + bufferR[idxR1] * fracR

        // Write with feedback
        bufferL[writeIdx] = (inL + delayedL * feedback).coerceIn(-2.0f, 2.0f)
        bufferR[writeIdx] = (inR + delayedR * feedback).coerceIn(-2.0f, 2.0f)
        writeIdx = (writeIdx + 1) % maxBufferSamples

        val outL = inL * (1.0f - mix) + delayedL * mix
        val outR = inR * (1.0f - mix) + delayedR * mix
        return Pair(outL, outR)
    }

    override fun clear() {
        bufferL.fill(0f)
        bufferR.fill(0f)
        writeIdx = 0
        lfoPhase = 0.0f
    }
}

// 6. 3-Band Parametric Studio Equalizer Module
class ParametricEqModule(private val sampleRate: Int = 44100) : AudioEffectModule(type = EffectType.PARAMETRIC_EQ) {
    @Volatile var lowGainDb: Float = 0.0f    // -12dB to +12dB @ 120Hz Low Shelf
    @Volatile var midGainDb: Float = 0.0f    // -12dB to +12dB @ 1.2kHz Mid Peak
    @Volatile var highGainDb: Float = 0.0f   // -12dB to +12dB @ 7kHz High Shelf
    @Volatile var mix: Float = 1.0f

    // Biquad filter state registers
    private var lowX1L = 0f; private var lowX2L = 0f; private var lowY1L = 0f; private var lowY2L = 0f
    private var lowX1R = 0f; private var lowX2R = 0f; private var lowY1R = 0f; private var lowY2R = 0f

    private var midX1L = 0f; private var midX2L = 0f; private var midY1L = 0f; private var midY2L = 0f
    private var midX1R = 0f; private var midX2R = 0f; private var midY1R = 0f; private var midY2R = 0f

    private var highX1L = 0f; private var highX2L = 0f; private var highY1L = 0f; private var highY2L = 0f
    private var highX1R = 0f; private var highX2R = 0f; private var highY1R = 0f; private var highY2R = 0f

    override fun processStereo(inL: Float, inR: Float): Pair<Float, Float> {
        if (!isEnabled || mix <= 0.001f) return Pair(inL, inR)

        // Calculate gains (linear multiplier)
        val lowGain = 10.0.pow((lowGainDb / 20.0)).toFloat()
        val midGain = 10.0.pow((midGainDb / 20.0)).toFloat()
        val highGain = 10.0.pow((highGainDb / 20.0)).toFloat()

        // 1. Low Shelf (approximate RC filter)
        val lowAlpha = 2.0f * PI.toFloat() * 120.0f / sampleRate
        val lowL = lowY1L + lowAlpha * (inL - lowY1L)
        lowY1L = lowL
        val lowR = lowY1R + lowAlpha * (inR - lowY1R)
        lowY1R = lowR

        val lowProcessedL = inL + (lowL * (lowGain - 1.0f))
        val lowProcessedR = inR + (lowR * (lowGain - 1.0f))

        // 2. High Shelf (approximate HP filter)
        val highAlpha = 2.0f * PI.toFloat() * 7000.0f / sampleRate
        val hpBaseL = inL - (highY1L + highAlpha * (inL - highY1L))
        highY1L += highAlpha * (inL - highY1L)
        val hpBaseR = inR - (highY1R + highAlpha * (inR - highY1R))
        highY1R += highAlpha * (inR - highY1R)

        val highProcessedL = lowProcessedL + (hpBaseL * (highGain - 1.0f))
        val highProcessedR = lowProcessedR + (hpBaseR * (highGain - 1.0f))

        // 3. Mid Bell Peak
        val midL = (highProcessedL - lowL - hpBaseL)
        val midR = (highProcessedR - lowR - hpBaseR)
        val eqL = highProcessedL + midL * (midGain - 1.0f)
        val eqR = highProcessedR + midR * (midGain - 1.0f)

        val outL = inL * (1.0f - mix) + eqL * mix
        val outR = inR * (1.0f - mix) + eqR * mix
        return Pair(outL, outR)
    }

    override fun clear() {
        lowY1L = 0f; lowY1R = 0f
        highY1L = 0f; highY1R = 0f
    }
}

// 7. Master Bus Compressor & Limiter Module
class CompressorModule(private val sampleRate: Int = 44100) : AudioEffectModule(type = EffectType.COMPRESSOR) {
    @Volatile var thresholdDb: Float = -12.0f   // -30dB to 0dB
    @Volatile var ratio: Float = 4.0f          // 1.0 to 16.0
    @Volatile var attackMs: Float = 10.0f      // 1ms to 80ms
    @Volatile var releaseMs: Float = 120.0f    // 20ms to 400ms
    @Volatile var makeupGainDb: Float = 3.0f   // 0dB to 12dB
    @Volatile var gainReductionDb: Float = 0.0f // Live telemetry for UI

    private var envelope = 0.0f

    override fun processStereo(inL: Float, inR: Float): Pair<Float, Float> {
        if (!isEnabled) {
            gainReductionDb = 0.0f
            return Pair(inL, inR)
        }

        val peak = max(abs(inL), abs(inR))
        val attackCoeff = exp(-1.0 / (sampleRate * (attackMs / 1000.0))).toFloat()
        val releaseCoeff = exp(-1.0 / (sampleRate * (releaseMs / 1000.0))).toFloat()

        if (peak > envelope) {
            envelope = attackCoeff * envelope + (1.0f - attackCoeff) * peak
        } else {
            envelope = releaseCoeff * envelope + (1.0f - releaseCoeff) * peak
        }

        val envDb = if (envelope > 0.00001f) 20.0f * log10(envelope) else -100.0f
        val overDb = envDb - thresholdDb

        var grDb = 0.0f
        if (overDb > 0.0f) {
            grDb = overDb * (1.0f - 1.0f / ratio)
        }
        gainReductionDb = grDb

        val targetGainDb = -grDb + makeupGainDb
        val linearGain = 10.0.pow((targetGainDb / 20.0)).toFloat()

        val outL = (inL * linearGain).coerceIn(-1.2f, 1.2f)
        val outR = (inR * linearGain).coerceIn(-1.2f, 1.2f)
        return Pair(outL, outR)
    }

    override fun clear() {
        envelope = 0.0f
        gainReductionDb = 0.0f
    }
}

// --- MASTER CHAINABLE EFFECTS RACK CONTAINER ---
class MasterEffectsRack(val sampleRate: Int = 44100) {
    private val modules = CopyOnWriteArrayList<AudioEffectModule>()

    init {
        // Default standard studio master chain
        modules.add(FilterModule(sampleRate).apply {
            cutoffHz = 16000f
            resonance = 1.0f
            isEnabled = false // Bypassed initially
        })
        modules.add(DistortionModule().apply {
            drive = 0.2f
            tone = 0.6f
            mix = 0.0f // Off by default
        })
        modules.add(DelayModule(sampleRate).apply {
            timeMs = 280f
            feedback = 0.35f
            mix = 0.25f
        })
        modules.add(ReverbModule(sampleRate).apply {
            roomSize = 0.65f
            damping = 0.35f
            mix = 0.20f
        })
    }

    fun getModules(): List<AudioEffectModule> = modules.toList()

    fun addModule(type: EffectType): AudioEffectModule {
        val newMod: AudioEffectModule = when (type) {
            EffectType.REVERB -> ReverbModule(sampleRate)
            EffectType.DELAY -> DelayModule(sampleRate)
            EffectType.FILTER -> FilterModule(sampleRate)
            EffectType.DISTORTION -> DistortionModule()
            EffectType.CHORUS -> ChorusModule(sampleRate)
            EffectType.PARAMETRIC_EQ -> ParametricEqModule(sampleRate)
            EffectType.COMPRESSOR -> CompressorModule(sampleRate)
        }
        modules.add(newMod)
        return newMod
    }

    fun removeModule(id: String) {
        modules.removeAll { it.id == id }
    }

    fun moveModule(fromIndex: Int, toIndex: Int) {
        if (fromIndex in 0 until modules.size && toIndex in 0 until modules.size && fromIndex != toIndex) {
            val item = modules.removeAt(fromIndex)
            modules.add(toIndex, item)
        }
    }

    fun toggleBypass(id: String) {
        modules.find { it.id == id }?.let {
            it.isEnabled = !it.isEnabled
        }
    }

    fun clearAll() {
        for (mod in modules) {
            mod.clear()
        }
    }

    fun resetToDefaultChain() {
        modules.clear()
        modules.add(FilterModule(sampleRate).apply { isEnabled = false })
        modules.add(DistortionModule().apply { mix = 0.0f })
        modules.add(DelayModule(sampleRate).apply { mix = 0.25f })
        modules.add(ReverbModule(sampleRate).apply { mix = 0.20f })
    }

    fun loadPreset(presetName: String) {
        modules.clear()
        when (presetName) {
            "Space Echo & Wash" -> {
                modules.add(DelayModule(sampleRate).apply {
                    timeMs = 380f; feedback = 0.6f; mix = 0.45f; pingPong = true
                })
                modules.add(ReverbModule(sampleRate).apply {
                    roomSize = 0.88f; damping = 0.2f; mix = 0.45f
                })
            }
            "Lo-Fi Vintage Cassette" -> {
                modules.add(FilterModule(sampleRate).apply {
                    cutoffHz = 3400f; resonance = 2.2f; filterType = FilterType.LOW_PASS
                })
                modules.add(DistortionModule().apply {
                    drive = 0.45f; tone = 0.35f; mix = 0.65f; mode = SaturationMode.TAPE
                })
                modules.add(ChorusModule(sampleRate).apply {
                    rateHz = 0.4f; depth = 0.7f; mix = 0.35f
                })
            }
            "Acid Overdrive & Punch" -> {
                modules.add(DistortionModule().apply {
                    drive = 0.75f; tone = 0.7f; mix = 0.8f; mode = SaturationMode.HARD_CLIP
                })
                modules.add(DelayModule(sampleRate).apply {
                    timeMs = 190f; feedback = 0.4f; mix = 0.3f
                })
                modules.add(CompressorModule(sampleRate).apply {
                    thresholdDb = -16f; ratio = 6f; makeupGainDb = 4f
                })
            }
            "Club Mastering Bus" -> {
                modules.add(ParametricEqModule(sampleRate).apply {
                    lowGainDb = 2.5f; midGainDb = -1.0f; highGainDb = 2.0f
                })
                modules.add(CompressorModule(sampleRate).apply {
                    thresholdDb = -8f; ratio = 3.5f; makeupGainDb = 2.5f
                })
                modules.add(ReverbModule(sampleRate).apply {
                    roomSize = 0.4f; damping = 0.6f; mix = 0.12f
                })
            }
            else -> resetToDefaultChain()
        }
    }

    // Process stereo audio sequentially through the chain
    fun process(inL: Float, inR: Float): Pair<Float, Float> {
        var curL = inL
        var curR = inR

        for (mod in modules) {
            if (mod.isEnabled) {
                val (nextL, nextR) = mod.processStereo(curL, curR)
                curL = nextL
                curR = nextR
            }
        }
        return Pair(curL, curR)
    }
}
