package com.example.synth.engine

/**
 * Audio session configuration handed to [EngineController.start]. Persisted
 * user choices (buffer size preference, latency calibration) land here as the
 * settings UI grows; the defaults are the blueprint's phone-first choices.
 */
data class EnginePrefs(
    /**
     * Open the duplex input stream. Off by default: recording workflows (M6)
     * own the RECORD_AUDIO permission flow; the engine itself never asks.
     */
    val enableInput: Boolean = false,
    /** Output buffer size in bursts - the latency vs stability knob (D2). */
    val bufferBursts: Int = 2,
    /**
     * Manual round-trip calibration offset applied by RecordingAligner (M6)
     * on top of the driver-reported stream latencies. 0 = trust the reports.
     */
    val manualLatencyOffsetMs: Float = 0f,
)

/**
 * Kotlin mirror of the contractual engine capacities in cpp/core/EngineConfig.h
 * (CONTRACTS.md global constants). Change the C++ side and the contract first.
 */
object EngineCaps {
    const val MAX_BLOCK = 1024
    const val MAX_CHANNELS = 2
    const val MAX_TRACKS = 64
    const val MAX_GROUPS = 8
    const val MAX_RETURNS = 8
    const val MAX_DEVICES_PER_CHAIN = 16
    const val MAX_CHAINS_PER_RACK = 8
    const val MAX_RACK_DEPTH = 3
    const val MAX_MACROS = 16
    const val VOICE_BUDGET = 64
    const val PARAM_TABLE_CAP = 256
    const val EVENT_RING_CAP = 4096
    const val TEMPO_TAIL_CAP = 64
    const val LAUNCH_WINDOW_SCENES = 32
    const val CORRECTIVE_STRETCH = 4
}
