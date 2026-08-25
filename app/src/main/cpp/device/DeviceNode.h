#pragma once

#include <cstddef>
#include <cstdint>

#include "../core/MidiEvent.h"
#include "../core/NodeUid.h"

// The device platform contract (CONTRACTS.md seams 1, 3 and 6), verbatim as
// frozen. Every instrument, effect, MIDI effect, rack - and the graph's own
// TrackStrip - implements DeviceNode; the graph compiler, MigrationPlan and
// param resolver only ever see this interface.
//
// Binding rules (blueprint 3.3/3.4): bypass is implemented by the CHAIN, not
// the device (dry path delayed by latencySamples(), ~10 ms equal-power
// crossfade, device keeps processing one crossfade-length after bypass
// engages). process() must handle numFrames < maxBlock on every call.
// Denormals are globally FTZ/DAZ - devices never test for them.
// latencySamples() is CONSTANT between prepares; param-dependent latency
// reports the worst case or declares that param structure-shaped.

namespace daw {

class MidiEventSink;   // instruments/midi-fx output tap; lands with MidiTapBus
class SidechainBus;    // resolved sidechain taps; lands with dynamics (M8)

// [RT] read-only during process().
struct ProcessContext {
    float* const* inputs = nullptr;    // deinterleaved, numChannels pointers
    float* const* outputs = nullptr;   // may alias inputs (in-place allowed)
    int      numChannels = 2;          // 1 or 2 at this node
    int      numFrames = 0;            // <= kMaxBlock
    double   sampleRate = 0.0;
    int64_t  blockStartSample = 0;     // engine timeline position
    double   blockStartBeat = 0.0;     // via installed TempoMap
    double   bpm = 120.0;
    bool     isPlaying = false;
    bool     isRecording = false;
    bool     isOfflineRender = false;  // deterministic-seed mode
    const MidiEventSpan* midiIn = nullptr;   // sample-offset-sorted, may be null
    MidiEventSink* midiOut = nullptr;        // instruments/midi-fx only
    SidechainBus*  sidechain = nullptr;      // resolved taps, may be null
};

// Seam 6: stable semantic parameter identity. Persisted forms store the
// string key; hashes (FNV-1a-32) travel in messages and tables. Collisions
// within one device type are a hostside build-time assertion.
struct ParamDescriptor {
    const char* key = "";              // "filter.cutoff" - NEVER reindexed
    const char* displayName = "";
    float minPlain = 0.0f;
    float maxPlain = 1.0f;
    float defaultPlain = 0.0f;
    enum class Curve : uint8_t { Linear, Log, Exp, Db, Switch } curve = Curve::Linear;
    const char* unit = "";             // "Hz", "dB", "%", "st", ""
    float smoothingMs = 0.0f;          // 0 = stepped
    bool  rtSafe = true;               // false => structure-shaped when changed
    bool  excludeFromRandomize = false;
    bool  isQualityMode = false;
};

// Seam 3: state migration blocks. Bodies are self-contained PODs; sample
// references are refcounted SampleHandles, never raw pointers into graph- or
// cache-owned memory. configHash covers anything that changes buffer sizes /
// topology; version mismatch => reset-with-fade, never partial adoption.
struct NodeStateHeader {
    uint64_t nodeUid = 0;
    uint64_t configHash = 0;
    uint32_t sizeBytes = 0;            // body size
    uint16_t version = 0;              // per-device-type state version
    uint16_t flags = 0;
};

struct NodeState {
    NodeStateHeader hdr;
    std::byte* body = nullptr;         // POD only
};

class DeviceNode {
public:
    virtual ~DeviceNode() = default;                              // [builder]
    virtual void prepare(double sampleRate, int maxBlock) = 0;    // [builder]
    virtual void process(ProcessContext& ctx) = 0;                // [RT]
    virtual void reset() = 0;                    // [RT] silence tails, keep params
    virtual int  latencySamples() const = 0;     // [any] constant between prepares
    virtual int  paramCount() const = 0;                          // [any]
    virtual const ParamDescriptor& paramDescriptor(int i) const = 0;  // [any]
    virtual void setParamImmediate(int denseIndex, float plain) = 0;  // [RT] post-resolution
    // State migration (seam 3): POD in/out, sizes bounded, declared up front.
    virtual size_t stateBytes() const = 0;                        // [any]
    virtual void   saveState(NodeState& out) const = 0;  // [RT at swap] pointer/POD only
    virtual bool   loadState(const NodeState& in) = 0;   // [builder pre-install / RT adopt]
};

// Instruments additionally implement the voice interface (seam 1); the
// VoiceBudgetLedger's steal demand (releasing -> oldest -> quietest, protect
// most-recent notes and drum transients) arrives through stealVoices.
struct MpeNoteState {
    float pressure = 0.0f;             // 0..1
    float pitchBendSemitones = 0.0f;
    float slide = 0.0f;                // 0..1 (CC74 / MPE timbre)
};

class VoiceInterface {
public:
    virtual ~VoiceInterface() = default;
    virtual void noteOn(int note, float velocity, const MpeNoteState& mpe) = 0;  // [RT]
    virtual void noteOff(int note, float releaseVelocity) = 0;                   // [RT]
    virtual void allNotesOff() = 0;                                              // [RT]
    virtual void stealVoices(int count) = 0;                                     // [RT]
};

} // namespace daw
