package com.example.synth

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class DrumType(val displayName: String, val shortName: String, val colorHex: Long) {
    KICK("808 Bass Kick", "BD", 0xFFFF0055),
    SNARE("Snappy Snare", "SD", 0xFF00E5FF),
    HIHAT_CLOSED("Closed Hat", "CH", 0xFFFFD600),
    HIHAT_OPEN("Open Sizzle Hat", "OH", 0xFFFF9100),
    CLAP("Hand Clap", "CP", 0xFF00FF66),
    TOM("Resonant Tom", "TM", 0xFFB388FF)
}

class DrumVoice(val type: DrumType) {
    @Volatile var isTriggered = false
    @Volatile var velocity = 1.0f
    
    // Per-voice parameters
    @Volatile var tune = 1.0f       // 0.5 to 2.0 pitch scale
    @Volatile var decay = 1.0f      // 0.2 to 3.0 decay length multiplier
    @Volatile var volume = 0.85f    // 0.0 to 1.0
    @Volatile var pan = 0.0f        // -1.0 to 1.0

    private var sampleCounter = 0
    private var phase = 0.0f
    private var clapBurstCounter = 0
    private var clapBurstTimer = 0

    fun trigger(vel: Float = 1.0f) {
        velocity = vel.coerceIn(0.1f, 1.0f)
        sampleCounter = 0
        phase = 0.0f
        clapBurstCounter = 0
        clapBurstTimer = 0
        isTriggered = true
    }

    fun renderSample(sampleRate: Int): Float {
        if (!isTriggered) return 0.0f

        val tSec = sampleCounter.toFloat() / sampleRate
        val effectiveDecay = decay.coerceIn(0.1f, 5.0f)
        var sample = 0.0f

        when (type) {
            DrumType.KICK -> {
                // 808 Style Pitch-Swept Sub Kick with Transient Click
                val baseFreq = 48.0f * tune
                val startFreq = 160.0f * tune
                val sweepTime = 0.045f * effectiveDecay
                val currentFreq = if (tSec < sweepTime) {
                    val sweepProgress = tSec / sweepTime
                    startFreq + (baseFreq - startFreq) * (sweepProgress * sweepProgress)
                } else {
                    baseFreq
                }
                
                phase += currentFreq / sampleRate
                if (phase >= 1.0f) phase -= 1.0f
                
                val body = sin(2.0 * PI * phase).toFloat()
                // Exponential amplitude decay
                val env = Math.exp(-tSec * 8.0 / effectiveDecay).toFloat()
                // Initial punch click
                val click = if (sampleCounter < 60) (Random.nextFloat() * 2.0f - 1.0f) * 0.4f else 0.0f
                
                sample = (body + click) * env
                if (env < 0.001f || tSec > 1.5f * effectiveDecay) isTriggered = false
            }

            DrumType.SNARE -> {
                // Dual Body Tone + Filtered Noise Burst
                val bodyFreq = 175.0f * tune
                phase += bodyFreq / sampleRate
                if (phase >= 1.0f) phase -= 1.0f
                
                val body = sin(2.0 * PI * phase).toFloat() * Math.exp(-tSec * 22.0 / effectiveDecay).toFloat()
                val noise = (Random.nextFloat() * 2.0f - 1.0f) * Math.exp(-tSec * 14.0 / effectiveDecay).toFloat()
                
                sample = (body * 0.5f + noise * 0.75f)
                if (tSec > 0.6f * effectiveDecay) isTriggered = false
            }

            DrumType.HIHAT_CLOSED -> {
                // Metallic Crisp High-Passed Noise with fast decay
                val noise = Random.nextFloat() * 2.0f - 1.0f
                val env = Math.exp(-tSec * 50.0 / effectiveDecay).toFloat()
                // Add metallic ring tone
                phase += (7500.0f * tune) / sampleRate
                if (phase >= 1.0f) phase -= 1.0f
                val metallic = sin(2.0 * PI * phase).toFloat() * 0.4f
                
                sample = (noise * 0.7f + metallic) * env
                if (env < 0.001f || tSec > 0.25f * effectiveDecay) isTriggered = false
            }

            DrumType.HIHAT_OPEN -> {
                // Long Sizzle Metallic Hi-Hat
                val noise = Random.nextFloat() * 2.0f - 1.0f
                val env = Math.exp(-tSec * 8.5 / effectiveDecay).toFloat()
                phase += (8200.0f * tune) / sampleRate
                if (phase >= 1.0f) phase -= 1.0f
                val ring = (sin(2.0 * PI * phase) * sin(2.0 * PI * phase * 1.35)).toFloat() * 0.5f
                
                sample = (noise * 0.65f + ring) * env
                if (env < 0.001f || tSec > 1.2f * effectiveDecay) isTriggered = false
            }

            DrumType.CLAP -> {
                // Multi-trigger burst (3 micro clicks) followed by reverberant tail
                val burstInterval = (0.012f * sampleRate).toInt()
                val noise = Random.nextFloat() * 2.0f - 1.0f
                var env = 0.0f
                
                if (clapBurstCounter < 3) {
                    clapBurstTimer++
                    if (clapBurstTimer >= burstInterval) {
                        clapBurstTimer = 0
                        clapBurstCounter++
                    }
                    env = 0.8f * (1.0f - clapBurstTimer.toFloat() / burstInterval)
                } else {
                    val tailSec = (sampleCounter - 3 * burstInterval).toFloat() / sampleRate
                    if (tailSec > 0) {
                        env = 0.9f * Math.exp(-tailSec * 16.0 / effectiveDecay).toFloat()
                    }
                }
                
                sample = noise * env
                if (tSec > 0.8f * effectiveDecay) isTriggered = false
            }

            DrumType.TOM -> {
                // Pitch dropping resonant Tom
                val startFreq = 220.0f * tune
                val endFreq = 90.0f * tune
                val dropTime = 0.08f * effectiveDecay
                val currentFreq = if (tSec < dropTime) {
                    startFreq - (startFreq - endFreq) * (tSec / dropTime)
                } else {
                    endFreq
                }
                phase += currentFreq / sampleRate
                if (phase >= 1.0f) phase -= 1.0f
                
                val body = sin(2.0 * PI * phase).toFloat()
                val env = Math.exp(-tSec * 10.0 / effectiveDecay).toFloat()
                sample = body * env
                if (env < 0.001f || tSec > 0.8f * effectiveDecay) isTriggered = false
            }
        }

        sampleCounter++
        return sample * volume * velocity
    }
}

class DrumEngine {
    val voices = DrumType.values().associateWith { DrumVoice(it) }

    fun trigger(type: DrumType, velocity: Float = 1.0f) {
        voices[type]?.trigger(velocity)
    }

    fun render(sampleRate: Int): Float {
        var mix = 0.0f
        for (voice in voices.values) {
            mix += voice.renderSample(sampleRate)
        }
        return mix.coerceIn(-1.5f, 1.5f)
    }

    fun stopAll() {
        for (voice in voices.values) {
            voice.isTriggered = false
        }
    }
}
