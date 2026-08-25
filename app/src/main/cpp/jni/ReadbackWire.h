#pragma once

#include <cstddef>
#include <cstdint>
#include <type_traits>

#include "../core/MeterFrame.h"

// C++ -> Kotlin poll-path wire PODs (CONTRACTS.md seam 5, "Readback").
// EngineReadback polls these through long-lived direct ByteBuffers at UI
// rate; both sides are the same little-endian process, so PODs cross as-is.
//
//   - EngineStatusWire: one fixed 80-byte snapshot per poll - transport
//     clock, time anchor (for playhead extrapolation between polls), stream
//     facts and engine counters. Layout frozen below; extend by claiming
//     `reserved` then bumping the wire version.
//   - Meters: MeterFrame (core/MeterFrame.h, 32 bytes, layout asserted
//     there) is copied verbatim, N frames per drain. Kotlin mirror offsets:
//     uid u64@0, peakL f32@8, peakR@12, rmsL@16, rmsR@20, gainReductionDb@24,
//     flags u16@28, seq u16@30.
//
// MidiActivityBus and the framed EngineEventBus (mustDeliver + sequence
// numbers, StateCodec framing) join this header with the hardware-MIDI and
// recording milestones.

namespace daw {

// Status flag bits. The low byte mirrors the AudioEngine clock bits
// (kClockPlaying/Recording/Looping) so the bridge ORs them straight in;
// bridge-level facts live in the second byte.
inline constexpr uint32_t kStatusPlaying     = 1u << 0;
inline constexpr uint32_t kStatusRecording   = 1u << 1;
inline constexpr uint32_t kStatusLooping     = 1u << 2;
inline constexpr uint32_t kStatusRunning     = 1u << 8;   // streams started
inline constexpr uint32_t kStatusNeedsReopen = 1u << 9;   // route/device change (D5)
inline constexpr uint32_t kStatusInputOpen   = 1u << 10;  // duplex input stream live

struct EngineStatusWire {
    uint32_t version;          // offset 0   kWireVersion
    uint32_t flags;            // offset 4   kStatus* bits
    int64_t  samplePos;        // offset 8   transport position (block clock)
    double   beat;             // offset 16
    double   bpm;              // offset 24
    int64_t  anchorFrame;      // offset 32  TimeAnchor.framePosition
    int64_t  anchorNanos;      // offset 40  TimeAnchor.monotonicNanos
    double   sampleRate;       // offset 48
    float    outputLatencyMs;  // offset 56
    float    inputLatencyMs;   // offset 60
    uint32_t xruns;            // offset 64
    uint32_t droppedNotes;     // offset 68  (wrapping 32-bit view of the counter)
    uint32_t panics;           // offset 72
    uint32_t reserved;         // offset 76  = 0 until claimed
};

static_assert(sizeof(EngineStatusWire) == 80, "poll layout is frozen at 80 bytes");
static_assert(offsetof(EngineStatusWire, samplePos) == 8);
static_assert(offsetof(EngineStatusWire, beat) == 16);
static_assert(offsetof(EngineStatusWire, anchorFrame) == 32);
static_assert(offsetof(EngineStatusWire, sampleRate) == 48);
static_assert(offsetof(EngineStatusWire, outputLatencyMs) == 56);
static_assert(offsetof(EngineStatusWire, xruns) == 64);
static_assert(offsetof(EngineStatusWire, reserved) == 76);
static_assert(std::is_trivially_copyable_v<EngineStatusWire>);

inline constexpr size_t kStatusWireBytes = sizeof(EngineStatusWire);
inline constexpr size_t kMeterWireBytes  = sizeof(MeterFrame);

} // namespace daw
