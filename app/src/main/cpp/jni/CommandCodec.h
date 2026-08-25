#pragma once

#include <bit>
#include <cstddef>
#include <cstdint>
#include <cstring>

#include "../core/EngineMessage.h"

// Kotlin -> C++ command wire format (CONTRACTS.md seam 5, v1).
//
// A push buffer is a sequence of frames, each:
//
//     offset 0  uint16  version   (= kWireVersion)
//     offset 2  uint16  kind      (FrameKind)
//     offset 4  uint32  byteLen   (payload bytes, excludes this 8-byte header)
//     offset 8  payload
//
// EngineMessageBatch payload is N x the seam-2 EngineMessage POD, little-
// endian, as-is. ControlOp payload is one ControlOpPayload. ParamBlockSet
// and ModelDelta payloads are consumed by the GraphBuilder / EngineModel
// (StateCodec framing inside ModelDelta); the codec hands them to the
// visitor opaquely.
//
// The codec is policy-free and pure: no JNI, no engine types beyond the
// message POD, all loads via memcpy (alignment- and aliasing-safe), fully
// host-compilable. Policy (where records route, what is deferred until the
// builder exists) lives in NativeAudioBridge's visitor.
//
// Version discipline (seam 5): unknown VERSION refuses the rest of the
// buffer - count + surface, never crash. Unknown KIND within a known
// version is forward-compatible: frames are length-delimited, so the codec
// skips and counts them.
//
// Consumption contract: `recordsConsumed` counts EngineMessages + ControlOps
// accepted by the visitor, in order. When the visitor refuses one
// (backpressure - e.g. EventRing full), decoding stops and the producer
// re-sends everything after the first `recordsConsumed` records. Records are
// fixed-size, so the producer can re-frame the tail without re-encoding.

namespace daw {

static_assert(std::endian::native == std::endian::little,
              "seam-5 wire is little-endian as-is; big-endian hosts need a swap layer");

inline constexpr uint16_t kWireVersion = 1;

enum class FrameKind : uint16_t {
    EngineMessageBatch = 0,
    ParamBlockSet      = 1,
    ModelDelta         = 2,
    ControlOp          = 3,
};

inline constexpr size_t kFrameHeaderBytes = 8;

struct FrameHeader {
    uint16_t version;
    uint16_t kind;
    uint32_t byteLen;
};
static_assert(sizeof(FrameHeader) == kFrameHeaderBytes);

// Non-realtime control-plane operation (kind = ControlOp).
struct ControlOpPayload {
    uint32_t op;      // ControlOpCode
    uint32_t arg;
};
inline constexpr size_t kControlOpBytes = 8;
static_assert(sizeof(ControlOpPayload) == kControlOpBytes);

enum class ControlOpCode : uint32_t {
    Nop = 0,
    // Future: RequestEventReplay(sinceSeq), RequestStateDump, ...
};

class CommandCodec {
public:
    enum class Status : int32_t {
        Ok           = 0,   // whole buffer decoded
        Backpressure = 1,   // visitor refused a record; resend after recordsConsumed
        BadVersion   = 2,   // frame version != kWireVersion
        Truncated    = 3,   // buffer ends inside a header or payload
        BadLength    = 4,   // payload length invalid for its kind
    };

    struct Result {
        Status   status = Status::Ok;
        uint32_t recordsConsumed = 0;  // messages + control ops accepted
        uint32_t unknownFrames = 0;    // skipped forward-compatibly
        size_t   stopOffset = 0;       // byte offset where decoding stopped
    };

    // Visitor shape (duck-typed, must be noexcept-friendly):
    //   bool onMessage(const EngineMessage&)      false = backpressure
    //   bool onControl(const ControlOpPayload&)   false = backpressure
    //   void onBlockSet(const uint8_t* p, uint32_t len)
    //   void onModelDelta(const uint8_t* p, uint32_t len)
    template <typename Visitor>
    static Result decode(const uint8_t* data, size_t len, Visitor&& v) noexcept {
        Result r;
        size_t off = 0;
        while (off < len) {
            if (len - off < kFrameHeaderBytes) {
                r.status = Status::Truncated;
                r.stopOffset = off;
                return r;
            }
            FrameHeader h;
            std::memcpy(&h, data + off, kFrameHeaderBytes);
            if (h.version != kWireVersion) {
                r.status = Status::BadVersion;
                r.stopOffset = off;
                return r;
            }
            const size_t payloadOff = off + kFrameHeaderBytes;
            if (h.byteLen > len - payloadOff) {
                r.status = Status::Truncated;
                r.stopOffset = off;
                return r;
            }
            const uint8_t* payload = data + payloadOff;

            switch (static_cast<FrameKind>(h.kind)) {
                case FrameKind::EngineMessageBatch: {
                    if (h.byteLen % sizeof(EngineMessage) != 0) {
                        r.status = Status::BadLength;
                        r.stopOffset = off;
                        return r;
                    }
                    const size_t n = h.byteLen / sizeof(EngineMessage);
                    for (size_t i = 0; i < n; ++i) {
                        EngineMessage m;
                        std::memcpy(&m, payload + i * sizeof(EngineMessage), sizeof m);
                        if (!v.onMessage(m)) {
                            r.status = Status::Backpressure;
                            r.stopOffset = payloadOff + i * sizeof(EngineMessage);
                            return r;
                        }
                        ++r.recordsConsumed;
                    }
                    break;
                }
                case FrameKind::ControlOp: {
                    if (h.byteLen != kControlOpBytes) {
                        r.status = Status::BadLength;
                        r.stopOffset = off;
                        return r;
                    }
                    ControlOpPayload c;
                    std::memcpy(&c, payload, kControlOpBytes);
                    if (!v.onControl(c)) {
                        r.status = Status::Backpressure;
                        r.stopOffset = payloadOff;
                        return r;
                    }
                    ++r.recordsConsumed;
                    break;
                }
                case FrameKind::ParamBlockSet:
                    v.onBlockSet(payload, h.byteLen);
                    break;
                case FrameKind::ModelDelta:
                    v.onModelDelta(payload, h.byteLen);
                    break;
                default:
                    ++r.unknownFrames;   // length-delimited: skippable
                    break;
            }
            off = payloadOff + h.byteLen;
            r.stopOffset = off;
        }
        return r;
    }

    // Encode-side helper (hostside tests, and the C++ -> Kotlin event path
    // which frames identically). Writes the 8-byte header; returns bytes
    // written (0 if the destination is too small).
    static size_t writeFrameHeader(uint8_t* dst, size_t dstLen,
                                   FrameKind kind, uint32_t payloadLen) noexcept {
        if (dstLen < kFrameHeaderBytes) return 0;
        const FrameHeader h{kWireVersion, static_cast<uint16_t>(kind), payloadLen};
        std::memcpy(dst, &h, kFrameHeaderBytes);
        return kFrameHeaderBytes;
    }
};

} // namespace daw
