package com.example.synth.engine

/**
 * Kotlin mirror of the seam-5 wire protocol (docs/spec/CONTRACTS.md) and the
 * identity hashes (cpp/core/NodeUid.h). Single source of truth on this side:
 * CommandEncoder, EngineReadback and EngineSync all read from here, and every
 * value must stay bit-identical to its C++ counterpart:
 *
 *   - frame kinds / versions        -> cpp/jni/CommandCodec.h, StateCodec.h
 *   - message families + opcodes    -> cpp/core/EngineMessage.h
 *   - status / meter layouts        -> cpp/jni/ReadbackWire.h
 *   - fnv1a32/64, makeNodeUid       -> cpp/core/NodeUid.h
 *
 * Change the C++ side (and CONTRACTS.md) first, then this mirror.
 * All multi-byte values are little-endian; buffers crossing the bridge are
 * direct ByteBuffers with ByteOrder.LITTLE_ENDIAN.
 */
object WireProtocol {

    // ---- frame envelope (CommandCodec.h) -----------------------------------
    const val WIRE_VERSION = 1
    const val FRAME_HEADER_BYTES = 8       // u16 version, u16 kind, u32 byteLen
    const val KIND_ENGINE_MESSAGE_BATCH = 0
    const val KIND_PARAM_BLOCK_SET = 1
    const val KIND_MODEL_DELTA = 2
    const val KIND_CONTROL_OP = 3
    const val CONTROL_OP_BYTES = 8         // u32 op, u32 arg

    // ---- EngineMessage (EngineMessage.h; 64 bytes, offsets frozen) ---------
    // family u8@0, op u8@1, flags u16@2, editSeq u32@4, nodeUid u64@8,
    // paramKeyHash u32@16, a u32@20, samplePos i64@24, beat f64@32,
    // v0 f64@40, v1 f64@48, b u64@56.
    const val MESSAGE_BYTES = 64

    const val FAMILY_TRANSPORT = 0
    const val FAMILY_PARAM = 1
    const val FAMILY_NOTE = 2
    const val FAMILY_STRUCTURE = 3
    const val FAMILY_SYSTEM = 4

    const val TRANSPORT_PLAY = 0
    const val TRANSPORT_STOP = 1
    const val TRANSPORT_TOGGLE_PLAY = 2
    const val TRANSPORT_RECORD_ON = 3
    const val TRANSPORT_RECORD_OFF = 4
    const val TRANSPORT_SEEK_SAMPLE = 5
    const val TRANSPORT_SEEK_BEAT = 6
    const val TRANSPORT_SET_LOOP_REGION = 7
    const val TRANSPORT_LOOP_ON = 8
    const val TRANSPORT_LOOP_OFF = 9
    const val TRANSPORT_SET_TEMPO = 10
    const val TRANSPORT_NUDGE_TEMPO = 11
    const val TRANSPORT_SET_TIME_SIG = 12
    const val TRANSPORT_METRONOME_ON = 13
    const val TRANSPORT_METRONOME_OFF = 14
    const val TRANSPORT_SET_TIMEBASE_SOURCE = 15

    const val PARAM_MOVE = 0
    const val PARAM_BLOCK_SET = 1
    const val PARAM_TOUCH = 2
    const val PARAM_RELEASE = 3

    const val NOTE_ON = 0
    const val NOTE_OFF = 1
    const val NOTE_POLY_PRESSURE = 2
    const val NOTE_PITCH_BEND = 3
    const val NOTE_SLIDE = 4
    const val NOTE_ALL_NOTES_OFF = 5

    const val SYSTEM_PANIC = 0
    const val SYSTEM_REQUEST_METER_FLUSH = 1

    // ---- StateCodec entity kinds (StateCodec.h; deltas flow from M1) -------
    const val DELTA_HEADER_BYTES = 16      // u16 ver, u16 kind, u64 id @4, u32 len @12
    const val ENTITY_TRACK = 0
    const val ENTITY_CLIP = 1
    const val ENTITY_CLIP_CONTENT = 2
    const val ENTITY_DEVICE = 3
    const val ENTITY_RACK = 4
    const val ENTITY_ROUTING = 5
    const val ENTITY_SCENE = 6
    const val ENTITY_TEMPO_MAP = 7
    const val ENTITY_LANE_GROUP = 8
    const val ENTITY_GROOVE = 9

    // ---- readback (ReadbackWire.h) -----------------------------------------
    // Status: version u32@0, flags u32@4, samplePos i64@8, beat f64@16,
    // bpm f64@24, anchorFrame i64@32, anchorNanos i64@40, sampleRate f64@48,
    // outputLatencyMs f32@56, inputLatencyMs f32@60, xruns u32@64,
    // droppedNotes u32@68, panics u32@72, timeSigPacked u32@76 (num<<16|den).
    const val STATUS_BYTES = 80
    const val STATUS_PLAYING = 1 shl 0
    const val STATUS_RECORDING = 1 shl 1
    const val STATUS_LOOPING = 1 shl 2
    const val STATUS_METRONOME = 1 shl 3
    const val STATUS_RUNNING = 1 shl 8
    const val STATUS_NEEDS_REOPEN = 1 shl 9
    const val STATUS_INPUT_OPEN = 1 shl 10

    // Meters: uid u64@0, peakL f32@8, peakR f32@12, rmsL f32@16, rmsR f32@20,
    // gainReductionDb f32@24, flags u16@28, seq u16@30.
    const val METER_FRAME_BYTES = 32
    const val METER_CLIPPED = 1
    const val METER_TRUE_PEAK_OVER = 2

    // ---- nativePushCommands result codes (NativeAudioBridge.cpp) -----------
    const val RESULT_BAD_VERSION = -2
    const val RESULT_TRUNCATED = -3
    const val RESULT_BAD_LENGTH = -4
    const val RESULT_BAD_HANDLE = -100
    const val RESULT_BAD_BUFFER = -101
    const val RESULT_BAD_RANGE = -102

    // ---- identity hashes (NodeUid.h; bit-exact mirrors) --------------------
    // Kotlin Int/Long arithmetic wraps two's-complement = the same bits as the
    // C++ unsigned math; >> on unsigned maps to ushr. Keys hash over UTF-8
    // bytes, matching the C++ char iteration for the ASCII keys we use.

    /** FNV-1a-32 of a semantic param key ("filter.cutoff") - seam 6. */
    fun paramKey(key: String): Int {
        var h = 0x811c9dc5L.toInt()
        for (b in key.toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toInt() and 0xFF)
            h *= 0x01000193
        }
        return h
    }

    fun fnv1a64(s: String): Long {
        var h = 0xcbf29ce484222325uL.toLong()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toLong() and 0xFF)
            h *= 0x100000001b3L
        }
        return h
    }

    /**
     * Deterministic node identity from (entity kind, edit-model id) - the
     * exact combine in NodeUid.h makeNodeUid, so a graph rebuilt by the C++
     * builder derives the same uid this side addressed messages to.
     */
    fun makeNodeUid(kind: String, entityId: String): Long {
        var h = fnv1a64(kind)
        h = h xor (fnv1a64(entityId) + 0x9e3779b97f4a7c15uL.toLong() +
                (h shl 6) + (h ushr 2))
        return if (h == 0L) 1L else h
    }

    // Entity-kind strings for makeNodeUid. The C++ GraphBuilder derives node
    // uids with these same strings from M1 on - contract, do not rename.
    const val NODE_KIND_TRACK = "track"
    const val NODE_KIND_DEVICE = "device"
    const val NODE_KIND_MASTER = "master"

    /** The master strip's node uid (single well-known instance). */
    val masterNodeUid: Long = makeNodeUid(NODE_KIND_MASTER, "master")
}

/**
 * Semantic parameter keys the Kotlin side addresses today. These strings are
 * the persisted contract form (seam 6): the C++ TrackStrip / MasterStrip
 * ParamDescriptors (M2) declare exactly these keys.
 */
object ParamKeys {
    const val MIXER_VOLUME = "mixer.volume"   // plain dB
    const val MIXER_PAN = "mixer.pan"         // -1..+1
    const val MIXER_SEND_A = "mixer.sendA"    // 0..1
    const val MIXER_SEND_B = "mixer.sendB"    // 0..1
    const val MIXER_MUTE = "mixer.mute"       // 0/1 switch
}
