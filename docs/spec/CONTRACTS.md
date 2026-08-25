# Dawsome Seam Contracts — v1.2.0

> v1.2.0 (M5 sample assignment + preview): seam 5's StateCodec gains two
> append-only entity kinds. `SampleRef = 10` (entityId = device uid;
> payload `{u32 slot, u32 pad0, u64 fileId, char path[byteLen-16]}` —
> slot 0 for single-sample devices, pad index for DrumRack; fileId 0
> clears the slot; byteLen 0 removes every ref of the device). `Preview
> = 11` (entityId = fileId; payload = UTF-8 path; entityId 0 or byteLen 0
> stops the audition). Both are structural [builder] facts: sample refs
> mark kDirtyGraph (the builder pins cache handles at compile and hands
> them to nodes; handles NEVER ride POD migration), preview never touches
> the graph. Vocabulary addition only — frames with these kinds are
> skipped+counted by older readers; `kWireVersionState` stays 1.
>
> v1.1.0 (M4 SessionPlayer): seam 2 gains the append-only `Session` message
> family (launch/stop/return/quantum ops). Vocabulary addition only — no
> layout change, `kMessageVersion` stays 1.

Frozen interface contracts for the six seams every class codes against while
the project builds without a compiler (`ARCHITECTURE_BLUEPRINT.md` §10).
**Contracts change only by editing this file first**; every change bumps the
version above and the per-seam version constants below. Declarations are
C++20 unless marked Kotlin. Threading annotations:

- `[RT]` — callable on the audio thread: no allocation, locks, syscalls,
  logging, exceptions; bounded work.
- `[builder]` — GraphBuilder thread only. `[any]` — any thread.
- `[ui]` / `[io]` — Kotlin main / IO dispatchers.

Global constants (in `core/EngineConfig.h`, mirrored in Kotlin `EnginePrefs`):

```cpp
namespace daw {
inline constexpr int    kMaxBlock          = 1024;   // frames; callbacks sub-chunked to this
inline constexpr int    kMaxTracks         = 64;
inline constexpr int    kMaxGroups         = 8;
inline constexpr int    kMaxReturns        = 8;
inline constexpr int    kMaxDevicesPerChain= 16;
inline constexpr int    kMaxChainsPerRack  = 8;
inline constexpr int    kMaxRackDepth      = 3;
inline constexpr int    kMaxMacros         = 16;
inline constexpr int    kVoiceBudget       = 64;     // global, VoiceBudgetLedger-enforced
inline constexpr int    kParamTableCap     = 256;    // distinct params per block
inline constexpr int    kEventRingCap      = 4096;   // messages
inline constexpr int    kTempoTailCap      = 64;     // RT tempo tail events
inline constexpr int    kLaunchWindowScenes= 32;     // per track (blueprint D4)
inline constexpr int    kCorrectiveStretch = 4;      // corrective stretchers, OUTSIDE the RT stretch budget
}
```

---

## Seam 1 — DeviceNode (v1)

```cpp
namespace daw {

struct ProcessContext {                 // [RT] read-only during process()
  float* const* inputs;                 // deinterleaved, numChannels pointers
  float* const* outputs;                // may alias inputs (in-place allowed)
  int            numChannels;           // 1 or 2 at this node
  int            numFrames;             // <= kMaxBlock
  double         sampleRate;
  int64_t        blockStartSample;      // engine timeline position
  double         blockStartBeat;        // via installed TempoMap
  double         bpm;
  bool           isPlaying;
  bool           isRecording;
  bool           isOfflineRender;       // deterministic-seed mode
  const class MidiEventSpan* midiIn;    // sample-offset-sorted, may be null
  class MidiEventSink*       midiOut;   // instruments/midi-fx only, may be null
  class SidechainBus*        sidechain; // resolved taps, may be null
};

class DeviceNode {
public:
  virtual ~DeviceNode() = default;                       // [builder]
  virtual void prepare(double sampleRate, int maxBlock) = 0;   // [builder]
  virtual void process(ProcessContext& ctx) = 0;         // [RT]
  virtual void reset() = 0;                              // [RT] silence tails, keep params
  virtual int  latencySamples() const = 0;               // [any] CONSTANT between prepares;
                                                         // param-dependent latency reports worst case
                                                         // or declares the param structure-shaped
  virtual int  paramCount() const = 0;                   // [any]
  virtual const ParamDescriptor& paramDescriptor(int i) const = 0; // [any]
  virtual void setParamImmediate(int denseIndex, float plain) = 0; // [RT] post-resolution
  // State migration (Seam 3): POD in/out, sizes bounded and declared up front.
  virtual size_t stateBytes() const = 0;                 // [any]
  virtual void   saveState(NodeState& out) const = 0;    // [RT at swap] pointer/POD only
  virtual bool   loadState(const NodeState& in) = 0;     // [builder pre-install / RT adopt]
};
}
```

Binding rules (blueprint §3.3, §3.4): bypass is implemented by the chain, not
the device — dry path delayed by `latencySamples()`, ~10 ms equal-power
crossfade, device keeps processing one crossfade-length after bypass engages.
`process()` must handle `numFrames < maxBlock` every call. Denormals are
globally FTZ/DAZ — devices never test for them. QualityMode is an ordinary
enum param (`quality`: Eco/Standard/High) on devices that declare it.
Instruments additionally implement the voice interface:
`noteOn(note, velocity, MpeState)`, `noteOff(note, releaseVelocity)`,
`allNotesOff()`, `stealVoices(int count)` `[RT]` — the ledger's demand
(steal order: releasing → oldest → quietest; protect most-recent notes and
drum transients).

## Seam 2 — Engine messages (v1)

One POD message type for all realtime traffic (rings defined in blueprint
§2.2). **Param addressing is always `(nodeUid, paramKeyHash)` — dense indices
never cross any thread boundary.**

```cpp
namespace daw {

enum class MsgFamily : uint8_t { Transport, Param, Note, Structure, System,
                                 Session /* v1.1, append-only */ };

enum class SessionOp : uint8_t {        // family Session (v1.1)
  LaunchClip = 0,   // nodeUid = trackUid, b = clipUid, a = slotIndex
  StopSlot,         // quantized stop; the track STAYS session-owned (silent)
  ReturnTrack,      // immediate back-to-arrangement for nodeUid's track
  ReturnAll,        // every track back to arrangement
  SetLaunchQuantum, // a = mode (0 none, 1 bar, 2 fixed), v0 = beats for fixed
};

struct EngineMessage {                  // exactly 64 bytes, trivially copyable
  MsgFamily family;                     // 1
  uint8_t   op;                         // 1  per-family opcode enum
  uint16_t  flags;                      // 2
  uint32_t  editSeq;                    // 4  monotonic edit sequence
  uint64_t  nodeUid;                    // 8  0 = engine-global
  uint32_t  paramKeyHash;               // 4  FNV-1a-32 of semantic key ("" = n/a)
  uint32_t  a;                          // 4  family-defined
  int64_t   samplePos;                  // 8  timeline sample position (or -1)
  double    beat;                       // 8  musical position (or NaN)
  double    v0;                         // 8  primary value (plain units)
  double    v1;                         // 8  secondary value
  uint64_t  b;                          // 8  family-defined / pointer payload
};                                      // = 64
static_assert(sizeof(EngineMessage) == 64);
}
```

- **EventRing** (lossless): Note family messages enqueue on/off as paired
  guaranteed slots or are refused atomically; producer high-water → refuse +
  System/Panic → RT all-notes-off. Transport/System ride the same ring.
- **ParamMoveTable**: open addressing, capacity `kParamTableCap`, key =
  `(nodeUid, paramKeyHash)`, latest-wins, dirty list consumed per block;
  eviction under overflow raises a reconcile flag → EngineSync re-sends the
  evicted keys' current model values.
- **Bulk sets** (preset load, variation recall): `Param/BlockSet` message whose
  `b` carries a builder-owned, epoch-GC'd buffer of `{nodeUid, keyHash, plain}`
  triples; applied atomically under a table generation barrier.
- **Post-swap rule**: RT re-applies the dirty set and pending events with
  `editSeq > installedGraph.editSeq`; resolution happens against the installed
  graph's resolver at apply time, so re-application is always index-correct.
- Codec/wire version: `kMessageVersion = 1` (bump on any layout change).

## Seam 3 — NodeState & migration (v1)

```cpp
namespace daw {
struct NodeStateHeader {
  uint64_t nodeUid;
  uint64_t configHash;    // DSP-topology-relevant config (see rule below)
  uint32_t sizeBytes;     // body size
  uint16_t version;       // per-device-type state version
  uint16_t flags;
};
struct NodeState { NodeStateHeader hdr; std::byte* body; };  // body: POD only

struct MigrationEntry { DeviceNode* newNode; DeviceNode* oldNode; };
// MigrationPlan = adopt entries ONLY (uid match && configHash match &&
// rate/maxBlock unchanged). Fresh/reset-with-fade state is pre-installed by
// the builder; RT executes pointer/POD moves for adopt entries at swap and
// publishes the retired graph's epoch ack. Builder frees only after ack.
}
```

Rules: state bodies are self-contained PODs; sample references are refcounted
`SampleHandle`s (SampleCache), never raw pointers into graph- or cache-owned
memory. `configHash` covers anything that changes buffer sizes/topology
(delay length class, oversampling factor, FDN size, wavetable set identity,
voice count); it excludes continuously variable params. Version mismatch →
reset-with-fade, never partial adoption.

## Seam 4 — TimelineSnapshot read API & epochs (v1)

Snapshot units, each independently built `[builder]`, swapped via single-slot
offer + epoch ack, read `[RT]` through plain-struct iterators:

- **Clip content** (per clip): `NoteSpan notes(fromBeat, toBeat)`,
  `StepSpan steps(pad)`, `EnvelopeSegSpan envelope(paramKeyHash)`,
  `WarpSegSpan warp()` — spans are pointer+count over immutable arrays.
- **Track lane group** (per track): `LaneSpan automationLanes()`, each lane
  `{paramKeyHash, PointSpan}` — what AutomationEvaluator iterates.
- **Marker/tempo lists**: `MarkerSpan markers()`, plus the TempoMap contract
  below.
- Bounded windows: session snapshot carries the visible page plus
  follow-action-reachable targets, ≤ `kLaunchWindowScenes` per track; **random
  follow-action draws are taken from the windowed set** (the cap defines the
  draw population, never silently truncates it).
- **Skew tolerance**: graph@N may transiently pair with snapshot@M; RT skips
  unresolvable references silently and counts them; convergence at the next
  swap. No cross-swap barriers, ever.
- Sounding-note reconciliation: MidiClipPlayer emits synthetic note-offs for
  sounding notes absent from a newly installed clip snapshot.

**TempoMap contract** (blueprint §2.5): immutable base (epoch-swapped) +
seqlock-published RT tail (capacity `kTempoTailCap`); `tempoMapRev` bumps on
every tail append and base swap; background readers snapshot with seqlock
retry and stamp outputs with the rev they read. **Base-claim retention rule:**
when RT claims a consolidated base, it retains (re-appends) tail events whose
rev is greater than the base's `foldRev` — `claimBase()` never clears the
whole tail. Tail full → forced structure-shaped consolidation.

## Seam 5 — JNI wire formats (v1)

- **CommandCodec** (Kotlin → C++): frame `{u16 version=1, u16 kind, u32
  byteLen, payload}`; kind ∈ {EngineMessageBatch, ParamBlockSet,
  ModelDelta, ControlOp}. EngineMessageBatch payload is N × the Seam-2 POD,
  little-endian, as-is.
- **StateCodec** (model deltas → EngineModel `[builder]`): framed entity
  deltas `{u16 version=1, u16 entityKind, u64 entityId, u32 byteLen,
  payload}` — entityKind ∈ {Track, Clip, ClipContent, Device, Rack, Routing,
  Scene, TempoMap, LaneGroup, Groove, SampleRef (v1.2), Preview (v1.2)}.
  Deltas are idempotent upserts/removes; the builder owns application order
  by editSeq.
- **Readback**: MeterBus/TransportClock/MidiActivityBus are shared-memory POD
  rings polled from Kotlin; EngineEventBus events are framed like StateCodec
  with `mustDeliver` flag + sequence number; a pull query
  (`fetchEventsSince(seq)`) backs reconciliation for must-deliver events.
- Both codecs refuse frames with unknown version (count + surface, never
  crash); version bumps are backward-readable for one version.

## Seam 6 — ParamId & ParamDescriptor (v1)

```cpp
namespace daw {
struct ParamDescriptor {
  const char* key;          // stable semantic id, e.g. "filter.cutoff" — NEVER reindexed
  const char* displayName;
  float minPlain, maxPlain, defaultPlain;
  enum class Curve : uint8_t { Linear, Log, Exp, Db, Switch } curve;
  const char* unit;         // "Hz", "dB", "%", "st", ""
  float smoothingMs;        // 0 = stepped
  bool  rtSafe;             // false => structure-shaped when changed
  bool  excludeFromRandomize;
  bool  isQualityMode;
};
// paramKeyHash = FNV-1a-32(key). Collisions within one device type are a
// build-time (hostside) assertion. Persisted forms store the string key;
// hashes live only in messages and tables.
}
```

Key→dense-index resolution exists only inside a compiled graph (resolver
table owned by the graph, consulted `[RT]` at apply time). Device replacement
consults a per-`(fromType, toType)` remap table of key pairs; unmapped lanes
are kept and flagged orphaned.

---

## Cross-cutting binding constants (blueprint §3.4, §7.4)

- Pan: constant-power sin/cos, −3 dB center; balance mode for stereo sources.
- Crossfader: equal-power; per-track assign A/B/none in TrackStrip.
- Summing: float32, no clamping before MasterStrip.
- dB: `gain = 10^(dB/20)`; −∞ below −72 dB; fader taper via descriptor curve.
- Edit guards: 10 ms equal-power micro-fades on clip edges and bypass ramps.
- Solo: solo-in-place; RT-computed audibility matrix; returns fed only by
  soloed tracks stay audible; exclusive-solo is UI-level.
- Voice steal order: releasing → oldest → quietest; protect most-recent notes
  and drum transients (ledger demand → `stealVoices`).
- Corrective stretchers (stale-proxy tempo bridge): **separate allowance
  `kCorrectiveStretch = 4`, outside the N-stretcher RT budget** — a tempo
  nudge may bridge up to 4 proxied clips simultaneously; beyond that, oldest
  bridges fall back to repitch until re-renders land.
- Probability/humanize RNG seed: `(clipId, position, loopPassIndex)`; offline
  render replays pass indices for parity.
- Host-compilability: `core/` and `dsp/` include no Android/Oboe/JNI headers;
  the desktop CMake test target must always configure (run only on request).
