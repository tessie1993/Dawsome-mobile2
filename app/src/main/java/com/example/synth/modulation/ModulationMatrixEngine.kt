package com.example.synth.modulation

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class ModSource(val displayName: String) {
    LFO_1("LFO 1"),
    LFO_2("LFO 2"),
    LFO_3("LFO 3"),
    ENV_AMP("Amp Envelope"),
    ENV_FILTER("Filter Envelope"),
    ENV_MOD("Mod Envelope"),
    VELOCITY("Note Velocity"),
    MOD_WHEEL("Mod Wheel (CC1)"),
    KEY_TRACK("Key Tracking"),
    RANDOM_SH("Random S&H")
}

enum class ModDestination(val displayName: String) {
    OSC1_PITCH("Osc 1 Pitch"),
    OSC2_PITCH("Osc 2 Pitch"),
    WT_POSITION("Wavetable Position"),
    FM_DEPTH("FM Mod Depth"),
    FILTER_CUTOFF("Filter Cutoff"),
    FILTER_RESO("Filter Resonance"),
    DRIVE("Distortion Drive"),
    PAN("Stereo Pan"),
    REVERB_SEND("Reverb Send")
}

data class ModMatrixRoute(
    val id: String,
    val source: ModSource,
    val destination: ModDestination,
    val depth: Float = 0.0f, // -1.0 to +1.0 (bipolar)
    val isBipolar: Boolean = true,
    val isEnabled: Boolean = true
)

/**
 * 16x8 Modulation Matrix Real-Time Processing Engine according to SPEC01.md Section 9.1.
 */
class ModulationMatrixEngine {
    val routes = mutableListOf<ModMatrixRoute>()

    // Real-Time LFO States
    private var lfo1Phase = 0.0f
    private var lfo2Phase = 0.0f
    private var lfo3Phase = 0.0f

    fun addRoute(source: ModSource, destination: ModDestination, depth: Float = 0.5f) {
        if (routes.size < 16) {
            routes.add(ModMatrixRoute(java.util.UUID.randomUUID().toString(), source, destination, depth))
        }
    }

    fun removeRoute(id: String) {
        routes.removeAll { it.id == id }
    }

    /**
     * Compute aggregate modulation offset for a specific target destination.
     */
    fun computeDestinationModulation(
        destination: ModDestination,
        sampleRate: Int = 44100,
        lfo1RateHz: Float = 2.0f,
        lfo2RateHz: Float = 0.5f,
        lfo3RateHz: Float = 6.0f,
        velocity: Float = 1.0f,
        modWheel: Float = 0.0f
    ): Float {
        // Advance LFO phases
        lfo1Phase += lfo1RateHz / sampleRate
        if (lfo1Phase >= 1.0f) lfo1Phase -= 1.0f

        lfo2Phase += lfo2RateHz / sampleRate
        if (lfo2Phase >= 1.0f) lfo2Phase -= 1.0f

        lfo3Phase += lfo3RateHz / sampleRate
        if (lfo3Phase >= 1.0f) lfo3Phase -= 1.0f

        var totalMod = 0.0f

        val relevantRoutes = routes.filter { it.destination == destination && it.isEnabled }
        for (route in relevantRoutes) {
            val sourceVal = when (route.source) {
                ModSource.LFO_1 -> sin(2.0 * PI * lfo1Phase).toFloat()
                ModSource.LFO_2 -> (2.0f * lfo2Phase - 1.0f) // Triangle/Saw
                ModSource.LFO_3 -> sin(2.0 * PI * lfo3Phase).toFloat()
                ModSource.ENV_AMP -> 0.85f
                ModSource.ENV_FILTER -> 0.65f
                ModSource.ENV_MOD -> 0.50f
                ModSource.VELOCITY -> velocity * 2.0f - 1.0f
                ModSource.MOD_WHEEL -> modWheel
                ModSource.KEY_TRACK -> 0.0f
                ModSource.RANDOM_SH -> (Random.nextFloat() * 2f - 1f)
            }

            totalMod += sourceVal * route.depth
        }

        return totalMod.coerceIn(-1.0f, 1.0f)
    }
}
