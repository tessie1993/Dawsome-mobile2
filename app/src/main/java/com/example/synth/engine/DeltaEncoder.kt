package com.example.synth.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds one ModelDelta bundle: the 8-byte ModelDeltaEnvelope (editSeq)
 * followed by StateCodec entity frames, all little-endian and bit-identical
 * to `cpp/jni/DeltaSchemas.h` / `StateCodec.h`. One edit-model action = one
 * bundle; [EngineController.sendModelDelta] wraps it in a CommandCodec
 * ModelDelta frame and pushes it to the GraphBuilder.
 *
 * The StateCodec header is contract-ordered: version u16@0, kind u16@2,
 * entityId u64@4 (unaligned by design), byteLen u32@12. An empty payload
 * means REMOVE. entityId is always [WireProtocol.makeNodeUid] - the one
 * identity shared with param addressing and graph nodes.
 */
class DeltaEncoder(private val editSeq: Int) {

    data class WireNote(
        val id: Int,          // fnv1a32 of the edit-model note id (or step key)
        val pitch: Int,       // 0..127
        val velocity: Int,    // 0..127
        val startBeat: Double,
        val lengthBeats: Double,
    )

    private val frames = ArrayList<ByteArray>()

    val isEmpty: Boolean get() = frames.isEmpty()

    // ---- upserts (layouts: DeltaSchemas.h) ---------------------------------

    fun upsertTrack(
        uid: Long, type: Int, flags: Int, order: Int,
        volumeDb: Float, pan: Float, sendA: Float, sendB: Float,
    ) {
        val p = ByteArray(20)
        wrap(p).put(type.toByte()).put(flags.toByte()).putShort(order.toShort())
            .putFloat(volumeDb).putFloat(pan).putFloat(sendA).putFloat(sendB)
        frame(WireProtocol.ENTITY_TRACK, uid, p)
    }

    fun upsertClip(
        uid: Long, trackUid: Long, contentUid: Long,
        startBeat: Double, lengthBeats: Double, slotIndex: Int, looping: Boolean,
    ) {
        val p = ByteArray(40)
        wrap(p).putLong(trackUid).putLong(contentUid)
            .putDouble(startBeat).putDouble(lengthBeats)
            .putInt(slotIndex).put((if (looping) 1 else 0).toByte())
        frame(WireProtocol.ENTITY_CLIP, uid, p)
    }

    fun upsertContent(uid: Long, lengthBeats: Double, notes: List<WireNote>) {
        val p = ByteArray(16 + notes.size * 24)
        val b = wrap(p)
        b.putDouble(lengthBeats).putInt(notes.size).putInt(0)
        for (n in notes) {
            b.putInt(n.id)
                .putShort((n.pitch and 0x7F).toShort())
                .putShort((n.velocity and 0x7F).toShort())
                .putDouble(n.startBeat).putDouble(n.lengthBeats)
        }
        frame(WireProtocol.ENTITY_CLIP_CONTENT, uid, p)
    }

    fun upsertDevice(uid: Long, trackUid: Long, type: Int, enabled: Boolean, order: Int) {
        val p = ByteArray(16)
        wrap(p).putLong(trackUid).put(type.toByte())
            .put((if (enabled) 1 else 0).toByte()).putShort(order.toShort())
        frame(WireProtocol.ENTITY_DEVICE, uid, p)
    }

    fun upsertScene(uid: Long, index: Int) {
        val p = ByteArray(8)
        wrap(p).putInt(index)
        frame(WireProtocol.ENTITY_SCENE, uid, p)
    }

    /** Canonical project tempo/meter lists (project-global: entityId 0). */
    fun tempoMap(events: List<Pair<Double, Double>>, sigNumerator: Int, sigDenominator: Int) {
        val p = ByteArray(8 + events.size * 16 + 16)
        val b = wrap(p)
        b.putInt(events.size).putInt(1)
        for ((beat, bpm) in events) b.putDouble(beat).putDouble(bpm)
        b.putDouble(0.0)
            .putShort(sigNumerator.toShort()).putShort(sigDenominator.toShort())
        frame(WireProtocol.ENTITY_TEMPO_MAP, 0L, p)
    }

    // ---- removal (empty payload) -------------------------------------------

    fun remove(entityKind: Int, uid: Long) = frame(entityKind, uid, EMPTY)

    // ---- bundle ------------------------------------------------------------

    fun build(): ByteArray {
        val out = ByteArray(8 + frames.sumOf { it.size })
        val b = wrap(out)
        b.putInt(editSeq).putInt(0)          // ModelDeltaEnvelope
        for (f in frames) b.put(f)
        return out
    }

    private fun frame(kind: Int, entityId: Long, payload: ByteArray) {
        val f = ByteArray(WireProtocol.DELTA_HEADER_BYTES + payload.size)
        wrap(f).putShort(WireProtocol.WIRE_VERSION.toShort())
            .putShort(kind.toShort())
            .putLong(entityId)
            .putInt(payload.size)
            .put(payload)
        frames.add(f)
    }

    private fun wrap(a: ByteArray): ByteBuffer =
        ByteBuffer.wrap(a).order(ByteOrder.LITTLE_ENDIAN)

    private companion object {
        val EMPTY = ByteArray(0)
    }
}
