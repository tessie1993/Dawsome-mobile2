package com.example.synth.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Decoded engine status snapshot (ReadbackWire.h EngineStatusWire). One per
 * poll; [polledAtNanos] lets the UI extrapolate a smooth playhead between
 * polls via [EngineReadback.estimatedBeat].
 */
data class EngineStatus(
    val running: Boolean = false,
    val playing: Boolean = false,
    val recording: Boolean = false,
    val looping: Boolean = false,
    val metronome: Boolean = false,
    val needsReopen: Boolean = false,
    val inputOpen: Boolean = false,
    val samplePos: Long = 0,
    val beat: Double = 0.0,
    val bpm: Double = 120.0,
    val timeSigNumerator: Int = 4,
    val timeSigDenominator: Int = 4,
    val sampleRate: Double = 0.0,
    val outputLatencyMs: Float = 0f,
    val inputLatencyMs: Float = 0f,
    val xruns: Int = 0,
    val droppedNotes: Int = 0,
    val panics: Int = 0,
    val anchorFrame: Long = 0,      // DAC-clock TimeAnchor (MIDI/recording alignment)
    val anchorNanos: Long = 0,
    val polledAtNanos: Long = 0,
) {
    companion object { val EMPTY = EngineStatus() }
}

/** One track/return/master level frame (core/MeterFrame.h), linear values. */
data class MeterReading(
    val nodeUid: Long,
    val peakL: Float,
    val peakR: Float,
    val rmsL: Float,
    val rmsR: Float,
    val gainReductionDb: Float,
    val clipped: Boolean,
    val truePeakOver: Boolean,
    val seq: Int,
)

/**
 * Polls the engine's readback surfaces (CONTRACTS.md seam 5, "Readback") and
 * republishes them as flows for the UI state holders.
 *
 * The poll loop runs on [EngineController.io] - the same confined thread as
 * every other native call - which is what makes it the single legal consumer
 * of the meter ring and makes destroy-vs-poll races impossible by
 * construction. Ticks are cheap no-ops while the engine isn't running.
 */
class EngineReadback(private val controller: EngineController) {

    private val scope = CoroutineScope(controller.io + SupervisorJob())
    private var job: Job? = null
    private var reopenRequested = false

    private val statusBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(WireProtocol.STATUS_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
    private val meterBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(WireProtocol.METER_FRAME_BYTES * MAX_METERS_PER_TICK)
            .order(ByteOrder.LITTLE_ENDIAN)

    private val _status = MutableStateFlow(EngineStatus.EMPTY)
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val _meters = MutableStateFlow<Map<Long, MeterReading>>(emptyMap())
    val meters: StateFlow<Map<Long, MeterReading>> = _meters.asStateFlow()

    /** Polls that reported an unknown wire version (mirror-drift guard). */
    var wireErrors: Int = 0
        private set

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                tick()
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Smooth UI playhead: last polled beat advanced by wall time at [bpm]. */
    fun estimatedBeat(nowNanos: Long): Double {
        val s = _status.value
        if (!s.playing) return s.beat
        return s.beat + (nowNanos - s.polledAtNanos) * 1e-9 * (s.bpm / 60.0)
    }

    /** Timeline sample position with the same extrapolation. */
    fun estimatedSamplePos(nowNanos: Long): Long {
        val s = _status.value
        if (!s.playing || s.sampleRate <= 0.0) return s.samplePos
        return s.samplePos + ((nowNanos - s.polledAtNanos) * 1e-9 * s.sampleRate).toLong()
    }

    // ---- engine-io thread --------------------------------------------------

    private fun tick() {
        val handle = controller.handleForReadback()
        if (handle == 0L) return

        if (NativeAudioBridge.nativePollStatus(handle, statusBuffer)) {
            if (statusBuffer.getInt(0) != WireProtocol.WIRE_VERSION) {
                wireErrors++
            } else {
                val s = decodeStatus(System.nanoTime())
                _status.value = s
                if (s.needsReopen && !reopenRequested) {
                    reopenRequested = true
                    controller.requestReopen()
                } else if (!s.needsReopen) {
                    reopenRequested = false
                }
            }
        }

        val n = NativeAudioBridge.nativeDrainMeters(handle, meterBuffer, MAX_METERS_PER_TICK)
        if (n > 0) publishMeters(n)
    }

    private fun decodeStatus(nowNanos: Long): EngineStatus {
        val flags = statusBuffer.getInt(4)
        val sig = statusBuffer.getInt(76)
        return EngineStatus(
            running = flags and WireProtocol.STATUS_RUNNING != 0,
            playing = flags and WireProtocol.STATUS_PLAYING != 0,
            recording = flags and WireProtocol.STATUS_RECORDING != 0,
            looping = flags and WireProtocol.STATUS_LOOPING != 0,
            metronome = flags and WireProtocol.STATUS_METRONOME != 0,
            needsReopen = flags and WireProtocol.STATUS_NEEDS_REOPEN != 0,
            inputOpen = flags and WireProtocol.STATUS_INPUT_OPEN != 0,
            samplePos = statusBuffer.getLong(8),
            beat = statusBuffer.getDouble(16),
            bpm = statusBuffer.getDouble(24),
            timeSigNumerator = (sig ushr 16) and 0xFFFF,
            timeSigDenominator = sig and 0xFFFF,
            anchorFrame = statusBuffer.getLong(32),
            anchorNanos = statusBuffer.getLong(40),
            sampleRate = statusBuffer.getDouble(48),
            outputLatencyMs = statusBuffer.getFloat(56),
            inputLatencyMs = statusBuffer.getFloat(60),
            xruns = statusBuffer.getInt(64),
            droppedNotes = statusBuffer.getInt(68),
            panics = statusBuffer.getInt(72),
            polledAtNanos = nowNanos,
        )
    }

    private fun publishMeters(count: Int) {
        val updates = HashMap<Long, MeterReading>(count * 2)
        for (i in 0 until count) {
            val base = i * WireProtocol.METER_FRAME_BYTES
            val flags = meterBuffer.getShort(base + 28).toInt()
            val reading = MeterReading(
                nodeUid = meterBuffer.getLong(base),
                peakL = meterBuffer.getFloat(base + 8),
                peakR = meterBuffer.getFloat(base + 12),
                rmsL = meterBuffer.getFloat(base + 16),
                rmsR = meterBuffer.getFloat(base + 20),
                gainReductionDb = meterBuffer.getFloat(base + 24),
                clipped = flags and WireProtocol.METER_CLIPPED != 0,
                truePeakOver = flags and WireProtocol.METER_TRUE_PEAK_OVER != 0,
                seq = meterBuffer.getShort(base + 30).toInt() and 0xFFFF,
            )
            updates[reading.nodeUid] = reading   // latest per uid wins this tick
        }
        _meters.value = _meters.value + updates
    }

    private companion object {
        const val POLL_MS = 16L               // ~one frame at 60 Hz UI
        const val MAX_METERS_PER_TICK = 128   // tracks + returns + groups + master
    }
}
