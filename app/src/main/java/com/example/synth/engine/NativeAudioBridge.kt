package com.example.synth.engine

import java.nio.ByteBuffer

/**
 * JNI surface of libdawcore (cpp/jni/NativeAudioBridge.cpp). The C++ side
 * registers these natives from JNI_OnLoad against this exact class name -
 * renaming or moving this object requires updating the FindClass string.
 *
 * Loading is guarded: during the no-compile build phase the .so does not
 * ship, and the app runs UI-only. Every caller gates on [isLoaded];
 * [EngineController] surfaces the absence as EngineState.Unavailable.
 *
 * Threading contract (the native side depends on it):
 *  - create/destroy/start/stop/pushCommands/consumeParamOverflow are called
 *    only from EngineController's single engine-io thread - that thread IS
 *    the JNI producer of the engine's SPSC channels.
 *  - pollStatus/drainMeters are serialized by EngineReadback (the single
 *    meter-ring consumer).
 *  - destroy only after readback has stopped.
 *
 * Buffers are long-lived direct ByteBuffers (zero-copy via
 * GetDirectBufferAddress), always ByteOrder.LITTLE_ENDIAN.
 */
object NativeAudioBridge {

    val isLoaded: Boolean = try {
        System.loadLibrary("dawcore")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    /** @return native handle, 0 on allocation failure. */
    external fun nativeCreate(): Long

    external fun nativeDestroy(handle: Long)

    /** Opens + starts the Oboe session. Idempotent while running. */
    external fun nativeStart(handle: Long, enableInput: Boolean, bufferBursts: Int): Boolean

    external fun nativeStop(handle: Long)

    /**
     * Decodes seam-5 frames from [buffer] (position 0..[byteLen]) into the
     * engine. @return records consumed (>= 0; fewer than sent = backpressure,
     * re-send the tail), or a negative WireProtocol.RESULT_* code.
     */
    external fun nativePushCommands(handle: Long, buffer: ByteBuffer, byteLen: Int): Int

    /** Fills WireProtocol.STATUS_BYTES at offset 0. @return false on bad args. */
    external fun nativePollStatus(handle: Long, buffer: ByteBuffer): Boolean

    /** Copies up to [maxFrames] MeterFrames to offset 0. @return frame count. */
    external fun nativeDrainMeters(handle: Long, buffer: ByteBuffer, maxFrames: Int): Int

    /** Test-and-clear the ParamMoveTable reconcile flag (seam-2 overflow rule). */
    external fun nativeConsumeParamOverflow(handle: Long): Boolean
}
