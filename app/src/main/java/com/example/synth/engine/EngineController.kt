package com.example.synth.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lifecycle owner of the native engine session (blueprint Kotlin engine/).
 *
 * One process-lifetime instance. Everything that touches the bridge runs on
 * the single "daw-engine-io" thread ([io]): lifecycle, command pushes (this
 * thread IS the JNI producer of the engine's SPSC channels), and - because
 * [EngineReadback] launches its poll loop on the same dispatcher - the
 * readback natives too. Total serialization on one thread is what makes the
 * native threading contract (NativeAudioBridge.cpp) hold by construction.
 *
 * Command path: [send] appends messages through the confined [CommandEncoder]
 * and flushes. Backpressure (EventRing momentarily full) schedules a short
 * retry; ParamMoveTable overflow surfaces through [onReconcileNeeded] so
 * EngineSync re-sends authoritative model values. Messages sent while IDLE
 * queue up and flush when the engine starts.
 *
 * Route/device loss (D5): EngineReadback sees STATUS_NEEDS_REOPEN and calls
 * [requestReopen]; today that is stop + start (the driver re-opens at the new
 * device's native rate). The full re-prepare sequence - re-keying sample
 * caches, re-priming streams - joins with the media system (M7+), as do the
 * foreground service, audio focus policy, and interruption-finalizes-
 * recording behavior this class will own.
 */
class EngineController {

    enum class EngineState {
        /** libdawcore.so is not in this build (no-compile phase): UI-only. */
        UNAVAILABLE,
        IDLE,
        RUNNING,
        FAILED,
    }

    /** The confined engine-io dispatcher; EngineReadback polls on it too. */
    internal val io: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "daw-engine-io") }
            .asCoroutineDispatcher()

    private val scope = CoroutineScope(io + SupervisorJob())

    private val _state = MutableStateFlow(
        if (NativeAudioBridge.isLoaded) EngineState.IDLE else EngineState.UNAVAILABLE)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    /** Fires on the engine-io thread after a param-channel overflow or drop. */
    var onReconcileNeeded: (() -> Unit)? = null

    // Confined to the engine-io thread.
    private val encoder = CommandEncoder().also {
        it.onBacklogDropped = { onReconcileNeeded?.invoke() }
    }
    private var handle = 0L
    private var prefs = EnginePrefs()
    private var retryScheduled = false
    private var deltaBuffer: ByteBuffer? = null

    init {
        // Create the native session eagerly: the GraphBuilder thread spawns
        // with it, so model deltas apply while audio streams are still
        // closed. The single-thread executor guarantees this runs before any
        // posted send/sendModelDelta.
        if (NativeAudioBridge.isLoaded) scope.launch { ensureHandle() }
    }

    // ---- lifecycle (callable from any thread; work posts to engine-io) -----

    fun start(enginePrefs: EnginePrefs = EnginePrefs()) {
        if (!NativeAudioBridge.isLoaded) return
        scope.launch {
            prefs = enginePrefs
            if (ensureHandle() == 0L) return@launch
            val ok = NativeAudioBridge.nativeStart(
                handle, prefs.enableInput, prefs.bufferBursts)
            _state.value = if (ok) EngineState.RUNNING else EngineState.FAILED
            if (ok) flushNow()
        }
    }

    fun stop() {
        if (!NativeAudioBridge.isLoaded) return
        scope.launch {
            if (handle != 0L) NativeAudioBridge.nativeStop(handle)
            if (_state.value == EngineState.RUNNING) _state.value = EngineState.IDLE
        }
    }

    /** Stop + destroy the native session. Safe with the readback loop: its
     *  ticks share this thread and skip once the handle is gone. */
    fun release() {
        if (!NativeAudioBridge.isLoaded) return
        scope.launch {
            if (handle != 0L) {
                NativeAudioBridge.nativeStop(handle)
                NativeAudioBridge.nativeDestroy(handle)
                handle = 0L
            }
            encoder.clear()
            if (_state.value != EngineState.UNAVAILABLE) _state.value = EngineState.IDLE
        }
    }

    /** D5 route-change recovery; called by EngineReadback on the io thread. */
    fun requestReopen() {
        scope.launch {
            if (handle == 0L || _state.value != EngineState.RUNNING) return@launch
            NativeAudioBridge.nativeStop(handle)
            val ok = NativeAudioBridge.nativeStart(
                handle, prefs.enableInput, prefs.bufferBursts)
            _state.value = if (ok) EngineState.RUNNING else EngineState.FAILED
            if (ok) flushNow()
        }
    }

    // ---- commands ----------------------------------------------------------

    /**
     * Append messages and flush, all on the engine-io thread. No-op when the
     * native library is absent. Example: send { play(); setTempo(128.0) }.
     */
    fun send(block: CommandEncoder.() -> Unit) {
        if (!NativeAudioBridge.isLoaded) return
        scope.launch {
            encoder.block()
            flushNow()
        }
    }

    /**
     * Push one ModelDelta bundle ([DeltaEncoder.build]) to the GraphBuilder.
     * Deltas apply whether or not audio is running (the builder thread lives
     * with the native session); they are idempotent and never backpressured.
     */
    fun sendModelDelta(bundle: ByteArray) {
        if (!NativeAudioBridge.isLoaded || bundle.isEmpty()) return
        scope.launch {
            if (ensureHandle() == 0L) return@launch
            val byteLen = WireProtocol.FRAME_HEADER_BYTES + bundle.size
            val buf = deltaBufferFor(byteLen)
            buf.clear()
            buf.putShort(WireProtocol.WIRE_VERSION.toShort())
                .putShort(WireProtocol.KIND_MODEL_DELTA.toShort())
                .putInt(bundle.size)
                .put(bundle)
            NativeAudioBridge.nativePushCommands(handle, buf, byteLen)
        }
    }

    // ---- engine-io internals -----------------------------------------------

    /** Readback's per-tick handle gate; engine-io thread only. */
    internal fun handleForReadback(): Long =
        if (_state.value == EngineState.RUNNING) handle else 0L

    private fun ensureHandle(): Long {
        if (handle == 0L) {
            handle = NativeAudioBridge.nativeCreate()
            if (handle == 0L) _state.value = EngineState.FAILED
        }
        return handle
    }

    private fun deltaBufferFor(bytes: Int): ByteBuffer {
        val cur = deltaBuffer
        if (cur != null && cur.capacity() >= bytes) return cur
        val grown = ByteBuffer.allocateDirect(maxOf(bytes, 16 * 1024))
            .order(ByteOrder.LITTLE_ENDIAN)
        deltaBuffer = grown
        return grown
    }

    private fun flushNow() {
        if (handle == 0L || _state.value != EngineState.RUNNING) return
        when (encoder.flush(handle)) {
            CommandEncoder.FlushResult.BACKPRESSURE -> scheduleRetry()
            else -> Unit   // IDLE, FLUSHED; ERROR keeps its own counter
        }
        if (NativeAudioBridge.nativeConsumeParamOverflow(handle)) {
            onReconcileNeeded?.invoke()
        }
    }

    private fun scheduleRetry() {
        if (retryScheduled) return
        retryScheduled = true
        scope.launch {
            delay(RETRY_DELAY_MS)   // ~a couple of audio blocks of drain time
            retryScheduled = false
            flushNow()
        }
    }

    private companion object {
        const val RETRY_DELAY_MS = 5L
    }
}
