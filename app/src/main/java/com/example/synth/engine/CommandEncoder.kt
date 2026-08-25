package com.example.synth.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds seam-5 EngineMessageBatch frames and pushes them over the bridge.
 *
 * NOT thread-safe: confined to EngineController's engine-io thread, which is
 * the JNI producer of the engine's SPSC channels. Messages append to a
 * pending queue and [flush] frames them in order; when the native side
 * reports backpressure (EventRing momentarily full - it drains every audio
 * block), the consumed prefix is dropped from the queue and the tail is
 * re-framed on the next flush. Records are fixed 64-byte PODs, so the
 * consumed count maps exactly onto queue entries.
 *
 * Stuck-note safety (EventRing contract): the queue never silently loses
 * note traffic. If the backlog exceeds [maxPending] (an engine wedged for
 * seconds), the whole backlog is replaced by a front-of-queue System/Panic -
 * the engine answers with all-notes-off - and [onBacklogDropped] fires so
 * EngineSync re-sends current model values (the same reconcile path that
 * answers ParamMoveTable overflow).
 */
class CommandEncoder(
    private val maxBatch: Int = 256,
    private val maxPending: Int = 1024,
) {
    enum class FlushResult { IDLE, FLUSHED, BACKPRESSURE, ERROR }

    /** Set by EngineSync: re-send authoritative model values after a drop. */
    var onBacklogDropped: (() -> Unit)? = null

    val pendingCount: Int
        get() = pending.size

    var codecErrors: Int = 0
        private set
    var droppedBacklogs: Int = 0
        private set

    private val pending = ArrayDeque<ByteArray>()

    // The frame always starts at buffer offset 0; the bridge reads via
    // GetDirectBufferAddress + byteLen and ignores position/limit.
    private val frameBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(WireProtocol.FRAME_HEADER_BYTES +
                maxBatch * WireProtocol.MESSAGE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)

    // ---- transport ---------------------------------------------------------

    fun play() = enqueue(message(WireProtocol.FAMILY_TRANSPORT, WireProtocol.TRANSPORT_PLAY))
    fun stop() = enqueue(message(WireProtocol.FAMILY_TRANSPORT, WireProtocol.TRANSPORT_STOP))
    fun togglePlay() = enqueue(message(WireProtocol.FAMILY_TRANSPORT, WireProtocol.TRANSPORT_TOGGLE_PLAY))

    fun record(on: Boolean) = enqueue(message(WireProtocol.FAMILY_TRANSPORT,
        if (on) WireProtocol.TRANSPORT_RECORD_ON else WireProtocol.TRANSPORT_RECORD_OFF))

    fun seekSample(samplePos: Long) = enqueue(message(WireProtocol.FAMILY_TRANSPORT,
        WireProtocol.TRANSPORT_SEEK_SAMPLE, samplePos = samplePos))

    fun seekBeat(beat: Double) = enqueue(message(WireProtocol.FAMILY_TRANSPORT,
        WireProtocol.TRANSPORT_SEEK_BEAT, beat = beat))

    fun setTempo(bpm: Double) = enqueue(message(WireProtocol.FAMILY_TRANSPORT,
        WireProtocol.TRANSPORT_SET_TEMPO, v0 = bpm))

    fun nudgeTempo(bpmDelta: Double) = enqueue(message(WireProtocol.FAMILY_TRANSPORT,
        WireProtocol.TRANSPORT_NUDGE_TEMPO, v0 = bpmDelta))

    fun setLoopRegion(startBeat: Double, endBeat: Double) = enqueue(message(
        WireProtocol.FAMILY_TRANSPORT, WireProtocol.TRANSPORT_SET_LOOP_REGION,
        v0 = startBeat, v1 = endBeat))

    fun loop(on: Boolean) = enqueue(message(WireProtocol.FAMILY_TRANSPORT,
        if (on) WireProtocol.TRANSPORT_LOOP_ON else WireProtocol.TRANSPORT_LOOP_OFF))

    fun metronome(on: Boolean) = enqueue(message(WireProtocol.FAMILY_TRANSPORT,
        if (on) WireProtocol.TRANSPORT_METRONOME_ON else WireProtocol.TRANSPORT_METRONOME_OFF))

    fun setTimeSig(numerator: Int, denominator: Int) = enqueue(message(
        WireProtocol.FAMILY_TRANSPORT, WireProtocol.TRANSPORT_SET_TIME_SIG,
        a = (numerator shl 16) or (denominator and 0xFFFF)))

    /** 0 = internal, 1 = Ableton Link, 2 = MIDI clock slave. */
    fun setTimebaseSource(source: Int) = enqueue(message(
        WireProtocol.FAMILY_TRANSPORT, WireProtocol.TRANSPORT_SET_TIMEBASE_SOURCE,
        a = source))

    // ---- params (addressed (nodeUid, paramKeyHash) - seam 2 rule) ----------

    fun paramMove(nodeUid: Long, paramKeyHash: Int, plain: Double, editSeq: Int) =
        enqueue(message(WireProtocol.FAMILY_PARAM, WireProtocol.PARAM_MOVE,
            editSeq = editSeq, nodeUid = nodeUid, paramKeyHash = paramKeyHash, v0 = plain))

    fun paramTouch(nodeUid: Long, paramKeyHash: Int, editSeq: Int) =
        enqueue(message(WireProtocol.FAMILY_PARAM, WireProtocol.PARAM_TOUCH,
            editSeq = editSeq, nodeUid = nodeUid, paramKeyHash = paramKeyHash))

    fun paramRelease(nodeUid: Long, paramKeyHash: Int, editSeq: Int) =
        enqueue(message(WireProtocol.FAMILY_PARAM, WireProtocol.PARAM_RELEASE,
            editSeq = editSeq, nodeUid = nodeUid, paramKeyHash = paramKeyHash))

    // ---- notes (live input; clip playback is engine-internal) --------------

    fun noteOn(nodeUid: Long, noteId: Int, pitchSemitones: Double, velocity: Double) =
        enqueue(message(WireProtocol.FAMILY_NOTE, WireProtocol.NOTE_ON,
            nodeUid = nodeUid, a = noteId, v0 = pitchSemitones, v1 = velocity))

    fun noteOff(nodeUid: Long, noteId: Int, releaseVelocity: Double = 0.0) =
        enqueue(message(WireProtocol.FAMILY_NOTE, WireProtocol.NOTE_OFF,
            nodeUid = nodeUid, a = noteId, v0 = releaseVelocity))

    fun allNotesOff(nodeUid: Long = 0L) =
        enqueue(message(WireProtocol.FAMILY_NOTE, WireProtocol.NOTE_ALL_NOTES_OFF,
            nodeUid = nodeUid))

    // ---- session launch (contracts v1.1; quantization happens engine-side) --

    fun launchClip(trackUid: Long, clipUid: Long, slotIndex: Int = -1) =
        enqueue(message(WireProtocol.FAMILY_SESSION, WireProtocol.SESSION_LAUNCH_CLIP,
            nodeUid = trackUid, a = slotIndex, b = clipUid))

    fun stopSlot(trackUid: Long) =
        enqueue(message(WireProtocol.FAMILY_SESSION, WireProtocol.SESSION_STOP_SLOT,
            nodeUid = trackUid))

    fun returnTrackToArrangement(trackUid: Long) =
        enqueue(message(WireProtocol.FAMILY_SESSION, WireProtocol.SESSION_RETURN_TRACK,
            nodeUid = trackUid))

    fun returnAllToArrangement() =
        enqueue(message(WireProtocol.FAMILY_SESSION, WireProtocol.SESSION_RETURN_ALL))

    fun setLaunchQuantum(mode: Int, beats: Double = 0.0) =
        enqueue(message(WireProtocol.FAMILY_SESSION,
            WireProtocol.SESSION_SET_LAUNCH_QUANTUM, a = mode, v0 = beats))

    /** Front-of-queue: outruns everything already pending. */
    fun panic() {
        pending.addFirst(message(WireProtocol.FAMILY_SYSTEM, WireProtocol.SYSTEM_PANIC))
    }

    // ---- flush -------------------------------------------------------------

    /**
     * Push pending records until empty, backpressure, or error. On ERROR
     * (malformed frame - an encoder bug, surfaced via [codecErrors]) the
     * framed records are dropped: which of them applied is unknowable, and
     * re-sending risks double-fired notes.
     */
    fun flush(handle: Long): FlushResult {
        var didFlush = false
        while (pending.isNotEmpty()) {
            val n = minOf(pending.size, maxBatch)
            frameBuffer.clear()
            frameBuffer.putShort(WireProtocol.WIRE_VERSION.toShort())
            frameBuffer.putShort(WireProtocol.KIND_ENGINE_MESSAGE_BATCH.toShort())
            frameBuffer.putInt(n * WireProtocol.MESSAGE_BYTES)
            for (i in 0 until n) frameBuffer.put(pending[i])

            val byteLen = WireProtocol.FRAME_HEADER_BYTES + n * WireProtocol.MESSAGE_BYTES
            val ret = NativeAudioBridge.nativePushCommands(handle, frameBuffer, byteLen)

            if (ret < 0) {
                repeat(n) { pending.removeFirst() }
                codecErrors++
                return FlushResult.ERROR
            }
            repeat(minOf(ret, n)) { pending.removeFirst() }
            if (ret > 0) didFlush = true
            if (ret < n) return FlushResult.BACKPRESSURE
        }
        return if (didFlush) FlushResult.FLUSHED else FlushResult.IDLE
    }

    fun clear() = pending.clear()

    // ---- internals ---------------------------------------------------------

    private fun enqueue(record: ByteArray) {
        if (pending.size >= maxPending) {
            // Engine wedged: replace the backlog with Panic + the new intent,
            // then let EngineSync restore authoritative values.
            pending.clear()
            droppedBacklogs++
            panic()
            pending.addLast(record)
            onBacklogDropped?.invoke()
            return
        }
        pending.addLast(record)
    }

    /** One seam-2 EngineMessage, little-endian at the frozen offsets. */
    private fun message(
        family: Int, op: Int, flags: Int = 0, editSeq: Int = 0,
        nodeUid: Long = 0L, paramKeyHash: Int = 0, a: Int = 0,
        samplePos: Long = -1L, beat: Double = Double.NaN,
        v0: Double = 0.0, v1: Double = 0.0, b: Long = 0L,
    ): ByteArray {
        val arr = ByteArray(WireProtocol.MESSAGE_BYTES)
        ByteBuffer.wrap(arr).order(ByteOrder.LITTLE_ENDIAN)
            .put(family.toByte())        // 0
            .put(op.toByte())            // 1
            .putShort(flags.toShort())   // 2
            .putInt(editSeq)             // 4
            .putLong(nodeUid)            // 8
            .putInt(paramKeyHash)        // 16
            .putInt(a)                   // 20
            .putLong(samplePos)          // 24
            .putDouble(beat)             // 32
            .putDouble(v0)               // 40
            .putDouble(v1)               // 48
            .putLong(b)                  // 56
        return arr
    }
}
