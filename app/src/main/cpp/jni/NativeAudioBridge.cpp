#include <jni.h>

#include <atomic>
#include <cstdint>
#include <cstring>
#include <new>

#include "../engine/AudioEngine.h"
#include "CommandCodec.h"
#include "ReadbackWire.h"

// The one JNI translation unit (blueprint jni/NativeAudioBridge). Everything
// Android-runtime-specific stops here: Kotlin talks to the engine through a
// jlong handle, long-lived direct ByteBuffers and the seam-5 codecs; no other
// engine file includes <jni.h>.
//
// Registration: RegisterNatives from JNI_OnLoad against the Kotlin object
// com.example.synth.engine.NativeAudioBridge (Android JNI-tips guidance:
// up-front symbol checking, nothing exported but JNI_OnLoad). If minification
// is ever enabled, that class needs a keep rule.
//
// Threading contract (enforced by the Kotlin side, documented here because
// the native side depends on it):
//   - nativeCreate/Destroy/Start/Stop/PushCommands/ConsumeParamOverflow are
//     confined to EngineController's single engine-io thread - the JNI
//     producer of jniEvents/jniParams (SPSC single-producer guarantee).
//   - nativePollStatus/DrainMeters are serialized by EngineReadback (the
//     single consumer of the meter ring). Status reads are seqlock/atomic
//     and safe alongside the producer thread.
//   - nativeDestroy comes only after readback has stopped.
//
// No callbacks into Java exist here - and never will from the RT thread; the
// push-style CallbackDispatcher (EngineEventBus) arrives with the recording/
// export milestones and dispatches from a normal thread.

namespace {

using namespace daw;

// The status wire's low flag byte mirrors the engine clock bits verbatim.
static_assert(kStatusPlaying == kClockPlaying);
static_assert(kStatusRecording == kClockRecording);
static_assert(kStatusLooping == kClockLooping);
static_assert(kStatusMetronome == kClockMetronome);

// Error returns shared with Kotlin (WireProtocol.RESULT_*). Codec failures
// map to -Status; bridge-level failures start at -100.
constexpr jint kErrBadVersion = -2;   // -CommandCodec::Status::BadVersion
constexpr jint kErrTruncated  = -3;
constexpr jint kErrBadLength  = -4;
constexpr jint kErrBadHandle  = -100;
constexpr jint kErrBadBuffer  = -101; // null or non-direct ByteBuffer
constexpr jint kErrBadRange   = -102; // byteLen exceeds buffer capacity

// One native engine session; the jlong handle is a pointer to this.
struct BridgeHandle {
    AudioEngine engine;
    std::atomic<bool>     running{false};
    std::atomic<uint32_t> codecErrors{0};     // malformed push buffers (encoder bug)
    std::atomic<uint32_t> deferredFrames{0};  // BlockSet/ModelDelta before the builder exists
};

BridgeHandle* fromHandle(jlong h) noexcept {
    return reinterpret_cast<BridgeHandle*>(h);
}

uint8_t* directBytes(JNIEnv* env, jobject buffer) noexcept {
    if (buffer == nullptr) return nullptr;
    return static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
}

// Routes decoded records into the engine's JNI producer channels.
struct PushVisitor {
    AudioEngine& engine;
    uint32_t deferred = 0;

    bool onMessage(const EngineMessage& m) noexcept {
        if (m.family == MsgFamily::Param &&
            m.op == static_cast<uint8_t>(ParamOp::Move)) {
            // Coalescing latest-wins table. Overflow raises the reconcile
            // flag (nativeConsumeParamOverflow) - never backpressure, the
            // move is intentionally dropped and re-sent by EngineSync.
            engine.jniParams().set(m.nodeUid, m.paramKeyHash, m.v0, m.editSeq);
            return true;
        }
        // Everything else is lossless-ring traffic. Refusal = backpressure:
        // decode stops, Kotlin retains and re-sends the tail. If the Kotlin
        // side ever has to DROP retained note traffic (its cap), it sends a
        // System/Panic first - the EventRing stuck-note contract.
        return engine.jniEvents().tryPush(m);
    }

    bool onControl(const ControlOpPayload&) noexcept {
        return true;   // ControlOpCode::Nop is the only op today
    }

    // Bulk-set buffers ride the builder (M2 graph) and model deltas the
    // EngineModel (M1); until those exist the frames are counted so the
    // Kotlin side can see them not landing.
    void onBlockSet(const uint8_t*, uint32_t) noexcept { ++deferred; }
    void onModelDelta(const uint8_t*, uint32_t) noexcept { ++deferred; }
};

// ---- natives (registered below; signatures must match NativeAudioBridge.kt)

jlong nativeCreate(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new (std::nothrow) BridgeHandle());
}

void nativeDestroy(JNIEnv*, jobject, jlong handle) {
    BridgeHandle* b = fromHandle(handle);
    if (b == nullptr) return;
    b->engine.stop();
    delete b;
}

jboolean nativeStart(JNIEnv*, jobject, jlong handle,
                     jboolean enableInput, jint bufferBursts) {
    BridgeHandle* b = fromHandle(handle);
    if (b == nullptr) return JNI_FALSE;
    if (b->running.load(std::memory_order_relaxed)) return JNI_TRUE;

    OboeDriver::Config cfg;
    cfg.enableInput = enableInput == JNI_TRUE;
    if (bufferBursts > 0) cfg.requestedBufferBursts = bufferBursts;

    const bool ok = b->engine.start(cfg);   // open() clears the reopen flag
    b->running.store(ok, std::memory_order_release);
    return ok ? JNI_TRUE : JNI_FALSE;
}

void nativeStop(JNIEnv*, jobject, jlong handle) {
    BridgeHandle* b = fromHandle(handle);
    if (b == nullptr) return;
    b->engine.stop();
    b->running.store(false, std::memory_order_release);
}

jint nativePushCommands(JNIEnv* env, jobject, jlong handle,
                        jobject buffer, jint byteLen) {
    BridgeHandle* b = fromHandle(handle);
    if (b == nullptr) return kErrBadHandle;
    const uint8_t* data = directBytes(env, buffer);
    if (data == nullptr) return kErrBadBuffer;
    if (byteLen < 0 || byteLen > env->GetDirectBufferCapacity(buffer))
        return kErrBadRange;

    PushVisitor v{b->engine};
    const CommandCodec::Result r =
        CommandCodec::decode(data, static_cast<size_t>(byteLen), v);
    if (v.deferred != 0)
        b->deferredFrames.fetch_add(v.deferred, std::memory_order_relaxed);

    switch (r.status) {
        case CommandCodec::Status::Ok:
        case CommandCodec::Status::Backpressure:
            // Consumed count; the producer re-sends records after it.
            return static_cast<jint>(r.recordsConsumed);
        case CommandCodec::Status::BadVersion:
            b->codecErrors.fetch_add(1, std::memory_order_relaxed);
            return kErrBadVersion;
        case CommandCodec::Status::Truncated:
            b->codecErrors.fetch_add(1, std::memory_order_relaxed);
            return kErrTruncated;
        default:
            b->codecErrors.fetch_add(1, std::memory_order_relaxed);
            return kErrBadLength;
    }
}

jboolean nativePollStatus(JNIEnv* env, jobject, jlong handle, jobject buffer) {
    BridgeHandle* b = fromHandle(handle);
    if (b == nullptr) return JNI_FALSE;
    uint8_t* dst = directBytes(env, buffer);
    if (dst == nullptr ||
        env->GetDirectBufferCapacity(buffer) < static_cast<jlong>(kStatusWireBytes))
        return JNI_FALSE;

    const TransportClockData clk = b->engine.clock();
    const TimeAnchor anchor = b->engine.anchor();
    const OboeDriver& drv = b->engine.driver();

    EngineStatusWire w{};
    w.version = kWireVersion;
    w.flags = clk.flags;
    if (b->running.load(std::memory_order_acquire)) w.flags |= kStatusRunning;
    if (drv.needsReopen()) w.flags |= kStatusNeedsReopen;
    if (drv.inputOpen())   w.flags |= kStatusInputOpen;
    w.samplePos = clk.samplePos;
    w.beat = clk.beat;
    w.bpm = clk.bpm;
    w.anchorFrame = anchor.framePosition;
    w.anchorNanos = anchor.monotonicNanos;
    w.sampleRate = drv.sampleRate();
    w.outputLatencyMs = static_cast<float>(drv.outputLatencyMs());
    w.inputLatencyMs = static_cast<float>(drv.inputLatencyMs());
    w.xruns = static_cast<uint32_t>(drv.xrunCount());
    w.droppedNotes = static_cast<uint32_t>(b->engine.droppedNotes());
    w.panics = static_cast<uint32_t>(b->engine.panics());
    w.timeSigPacked =
        (uint32_t(b->engine.transport().timeSigNumerator()) << 16) |
        uint32_t(b->engine.transport().timeSigDenominator());

    std::memcpy(dst, &w, sizeof w);
    return JNI_TRUE;
}

jint nativeDrainMeters(JNIEnv* env, jobject, jlong handle,
                       jobject buffer, jint maxFrames) {
    BridgeHandle* b = fromHandle(handle);
    if (b == nullptr) return kErrBadHandle;
    uint8_t* dst = directBytes(env, buffer);
    if (dst == nullptr) return kErrBadBuffer;

    const jlong capFrames =
        env->GetDirectBufferCapacity(buffer) / static_cast<jlong>(kMeterWireBytes);
    jint limit = maxFrames < capFrames ? maxFrames : static_cast<jint>(capFrames);
    if (limit < 0) limit = 0;

    MeterFrame frame;
    jint count = 0;
    while (count < limit && b->engine.popMeter(frame)) {
        std::memcpy(dst + static_cast<size_t>(count) * kMeterWireBytes,
                    &frame, kMeterWireBytes);
        ++count;
    }
    return count;
}

jboolean nativeConsumeParamOverflow(JNIEnv*, jobject, jlong handle) {
    BridgeHandle* b = fromHandle(handle);
    if (b == nullptr) return JNI_FALSE;
    return b->engine.jniParams().consumeOverflowFlag() ? JNI_TRUE : JNI_FALSE;
}

const JNINativeMethod kMethods[] = {
    {"nativeCreate", "()J", reinterpret_cast<void*>(nativeCreate)},
    {"nativeDestroy", "(J)V", reinterpret_cast<void*>(nativeDestroy)},
    {"nativeStart", "(JZI)Z", reinterpret_cast<void*>(nativeStart)},
    {"nativeStop", "(J)V", reinterpret_cast<void*>(nativeStop)},
    {"nativePushCommands", "(JLjava/nio/ByteBuffer;I)I",
     reinterpret_cast<void*>(nativePushCommands)},
    {"nativePollStatus", "(JLjava/nio/ByteBuffer;)Z",
     reinterpret_cast<void*>(nativePollStatus)},
    {"nativeDrainMeters", "(JLjava/nio/ByteBuffer;I)I",
     reinterpret_cast<void*>(nativeDrainMeters)},
    {"nativeConsumeParamOverflow", "(J)Z",
     reinterpret_cast<void*>(nativeConsumeParamOverflow)},
};

} // namespace

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK)
        return JNI_ERR;
    jclass cls = env->FindClass("com/example/synth/engine/NativeAudioBridge");
    if (cls == nullptr) return JNI_ERR;
    const jint n = sizeof(kMethods) / sizeof(kMethods[0]);
    if (env->RegisterNatives(cls, kMethods, n) != JNI_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}
