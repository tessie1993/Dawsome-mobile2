# Dawsome Architecture Blueprint (rev 3 — post DSP review rounds 1–2)

**Status:** authoritative target architecture for the full product defined by
`docs/spec/SPEC_PART1_FUNCTIONAL.md` (22 functional areas) and
`docs/spec/SPEC_PART2_WORKFLOW.md` (workflow, signal flow, instruments, MIDI tools,
effects, racks, automation, phone translation).
This document is a blueprint: modules, classes, responsibilities, data flow,
threading contracts and seam contracts — **no code**. `docs/ARCHITECTURE.md`
remains the living map of code that exists; classes graduate from this blueprint
into that map as they land.

Authority order: the two spec documents → this blueprint → `CONTRACTS.md` →
the living map.
Rev 3 carries the resolutions of all 17 blocking findings from DSP review
rounds 1–2 (command architecture, duplex I/O, recording alignment, rate
policy, swap/adoption protocol, edit sequencing, timeline change class, sync
placement, bypass/PDC, voice budget, cue bus, warp budget, stable param
identity end-to-end, MP3 removal, contracts layer, and the TempoMap seqlock
design) plus all recommendations and coverage gaps. **Reviewer verdict:
APPROVED (round 3) — implementation proceeds from M0 on the condition that
`docs/spec/CONTRACTS.md` is authored first and contracts change only there.**

Research grounding: Tracktion Engine (model/playback-graph separation, pooled
buffers, latency balancing, background stretch), Google Oboe guidance
(LowLatency+Exclusive/MMAP, device-native rate, non-blocking callbacks, full-
duplex = output callback + callback-less input stream), Ableton Link SDK (RT
capture/commit API; dual GPLv2/proprietary), signalsmith-stretch (MIT), pffft
(BSD-like), WORLD (modified-BSD), dr_libs (PD/MIT-0), libFLAC (BSD), AMidi /
android.media.midi, MediaCodec (AAC encode; **no MP3 encoder exists on Android**).

**Existing `app/src/main/cpp/` skeleton is condemned as reference-only.** It
implements this blueprint's rejected alternative (mutable shared graph,
`std::string` ids on RT nodes, string-keyed setParameter). Nothing may extend it;
its files are replaced module-by-module from M0 onward.

---

## 1. Fixed product & platform decisions

| # | Decision | Value | Rationale |
|---|---|---|---|
| D1 | Platform | Android only, minSdk 24, Kotlin + Compose UI, C++20 NDK engine | Existing repo; Oboe floor |
| D2 | Audio I/O | Oboe output stream with callback, `PerformanceMode::LowLatency` + `SharingMode::Exclusive` (fallback shared), device-native rate; **full-duplex = input stream opened without callback, drained non-blocking inside the output callback through an InputJitterRing** (primed ~2 bursts; underfill → zeros + counter; **overfill/clock-drift: high-water → drop-oldest + counter on the monitoring path; recording truth = the input stream's own clock — long-take duration skew vs output clock is accepted and documented, not resampled**) | Oboe-recommended duplex; one clock master (output) |
| D3 | Internal format | float32, de-interleaved; callback `numFrames` is variable — engine sub-chunks into ≤ `maxBlock` (1024) slices; all RT capacity sized to `maxBlock` | NEON; Oboe reality |
| D4 | Capacities | 64 user tracks, 8 groups, 8 returns, 1 master, 16 devices/chain, 8 chains/rack, rack nesting 3, 16 macros (8 primary), **global voice budget 64 enforced by VoiceBudgetLedger**, scenes unbounded in model; the RT launch window = the visible session page plus all follow-action-reachable targets, capped at 32 scenes/track | RT pre-allocation; phone-honest |
| D5 | Rates & formats | Engine runs at device rate. Originals never resampled in place; SampleCache/proxy caches keyed by `(fileId, targetRate)`; recordings tagged with capture rate and conformed on playback (per-clip resampler) until background re-conform. Export: 44.1/48/96 kHz, 16/24-bit WAV, FLAC, AAC (MediaCodec). **MP3 export dropped** (Android has no MP3 encoder; LAME rejected: LGPL + extra .so vs D13) | Route changes are survivable; license-clean |
| D6 | Plug-ins | No third-party plug-in hosting at launch | Scope |
| D7 | Time-stretch | signalsmith-stretch (MIT); repitch via resampler; **RT stretch budget per device class** (§7.4) with proxy fallback | License + phone CPU |
| D8 | Ableton Link | `SyncAdapter` implemented in C++ `engine/` (RT-safe capture/commit in the callback); Kotlin only enables/configures. OFF by default; GPLv2/commercial decision flagged to the owner before shipping | Phase alignment is RT work |
| D9 | Tuning | 12-TET + named scales (custom tunings deferred) | Scope |
| D10 | Stem separation | Not in core | Scope |
| D11 | Noise reduction | Offline render + preview | CPU realism |
| D12 | Interchange | Own format + audio/stems/MIDI export | Scope |
| D13 | Native lib | Single `libdawcore.so`, single JNI seam; `core/` and `dsp/` stay host-compilable (a desktop CMake test target exists from M0 even while un-run) | One bridge; testability seam |

---

## 2. Concurrency & data-flow model (the load-bearing design)

### 2.1 Dual model, three change classes

- **Edit model (Kotlin)** — immutable `ProjectState`, single source of truth,
  mutated only via `ProjectStore.dispatch`. Every committed edit carries a
  monotonic **editSeq**.
- **Realtime model (C++)** — `PlaybackGraph` (mix topology) + per-clip
  `TimelineSnapshot`s (playable content). Derived, disposable, epoch-managed.

Every change is classified:

1. **Parameter-shaped** (fader, pan, device param, macro, send, mute/solo,
   bypass, simple tempo set/nudge, transport ops, notes from UI input) →
   realtime message paths (§2.2), no rebuild.
2. **Timeline-shaped** (note add/move/delete, automation & envelope points,
   step edits, warp-marker edits, clip gain/fades, groove amount) → background
   rebuild of only the affected **snapshot unit**, atomic pointer swap, epoch
   GC (§2.4). Snapshot units, each independently epoch-swapped: **per-clip
   content** (notes/steps/envelopes/warp), **per-track automation lane group**
   (what AutomationEvaluator iterates — track automation belongs to tracks,
   not clips), and the **marker/tempo lists**. `MidiClipPlayer` keeps a
   sounding-note table and reconciles at swap: notes whose note-off vanished
   get synthetic offs — no stuck notes when editing a playing clip.
3. **Structure-shaped** (add/remove/reorder tracks, devices, chains, zones,
   routing, sends, sidechain edges, rack topology, warp-mode switch, complex
   tempo-map edits) → full graph rebuild + swap (§2.3).

### 2.2 Realtime message architecture (no single magic queue)

Per-producer-thread SPSC rings into RT; RT drains all rings every callback with
per-ring drain caps:

- **JNI ring** (Kotlin main/EngineSync producer): transport ops, structural
  notices, param moves, UI note input.
- **MIDI ring** (MIDI I/O thread producer): timestamped device events; MIDI-learn
  mappings are resolved on the MIDI thread (map hit → param move or note event)
  so learned CCs never round-trip through Kotlin.
- **Builder path** (GraphBuilder producer): **single-slot offers** (graph swap,
  snapshot swap) — a newer unclaimed offer replaces the older one, which is GC'd.

Two message species with different loss semantics:

- **Param moves** are not queued — they land in the **ParamMoveTable**: a fixed
  open-addressing table, latest-wins per key, plus a dirty list the RT thread
  consumes. **Messages and the table carry stable identity only —
  `(NodeUid, semanticKeyHash)` — never graph indices.** The RT thread resolves
  identity → dense index through the *currently installed* graph's resolver
  table at apply time (one hash lookup per applied move, ≤ table capacity per
  block); unresolvable keys are skipped and counted. Producers (Kotlin encoder,
  MIDI thread) never need a resolver copy, and post-swap re-application is
  always index-correct by construction.
  **Overflow reconciles, never silently evicts:** if a block exceeds table
  capacity (256 distinct params), evicted keys raise a reconcile flag and
  EngineSync re-sends their current model values. **Bulk sets** (preset load,
  variation recall: 20–200 params) travel as one atomic param-block event
  applied under a table generation barrier, not as individual moves.
- **Events** (note on/off, transport, panic) ride a **lossless ring** sized for
  worst-case bursts (4096); producer-side high-water → reject + `Panic` event →
  RT executes all-notes-off. A note-on without its note-off is never silently
  possible: note events are enqueued as pairs of guaranteed slots or refused
  atomically.

**Ordering rule (editSeq):** every compiled graph/snapshot is stamped with the
editSeq it was built from. RT applies incoming messages immediately; after a
swap it re-applies the ParamMoveTable dirty set and any pending events with
`seq > graph.seq`, and migrated `SmoothedValue` state (§2.3) carries current
values across — a fader ridden during a rebuild never jumps.

### 2.3 Graph rebuild, swap & state adoption (the protocol)

- `GraphBuilder` (background) owns `EngineModel` (compact C++ mirror of the edit
  model, updated from StateCodec deltas on the builder thread only). On dirty:
  compile a new `PlaybackGraph` — topological node schedule, buffers from
  `AudioBufferPool`, PDC delays, param indices resolved from semantic keys
  (§6) — plus a **MigrationPlan containing adopt entries only**: `{newNode,
  oldNode}` pairs where NodeUid matches **and** configHash matches (same DSP
  topology-relevant config) **and** rate/maxBlock unchanged. Fresh nodes and
  reset-with-fade state (config changed → short fade-in masks the reset) are
  fully pre-installed by the builder off-thread — RT never touches them at swap.
- **Swap (RT side):** callback sees a pending offer, at block boundary walks the
  adopt list executing **pointer/POD moves only** (state blocks are
  self-contained; bounded by the retired graph's node count — no copies, no
  allocation), installs the new graph, publishes **epoch ack** (release-store of
  the retired graph's epoch).
- **GC:** builder frees a retired graph only after observing its epoch ack.
  The audio thread never frees memory.
- **NodeState blocks** are POD or hold only refcounted `SampleHandle`s from
  SampleCache — never raw pointers into graph-owned or cache-evictable memory.
  Voice states, delay lines, reverb tails, stretcher states, envelope positions
  and `SmoothedValue`s migrate; anything whose config changed resets-with-fade
  (a delay-line length change cannot "adopt").
- Rebuilds are debounced; a build in flight absorbs newer dirty marks; offers
  are single-slot (a newer offer replaces an unclaimed older one, which is GC'd).

TimelineSnapshot swaps use the same offer/ack/epoch mechanics per clip slot.

### 2.4 Thread inventory

| Thread | Priority | Role |
|---|---|---|
| Oboe output callback | RT | Drain input ring, drain message rings, apply ParamMoveTable, advance transport, run graph, publish TimeAnchor + meters/clock |
| GraphBuilder | High-normal | EngineModel upkeep, graph/snapshot compiles, MigrationPlans, epoch GC |
| DiskStreamer | High-normal | Clip ring prefetch (playhead + armed quantized launches + stretcher pre-roll) |
| RecordingWriter | Normal | Drain record rings → journaled WAV; storage-budget guard |
| MIDI I/O | High-normal | AMidi read/write, monotonic timestamping, MIDI-learn resolution, clock in/out framing |
| Worker pool | Low | Peaks, analysis, prewarm/proxy renders, offline render, freeze, pack install |
| Kotlin main | UI | Compose, holders, ProjectStore |
| Kotlin IO | BG | Persistence, autosave, Room, packs |

RT rules: no allocation, locks, syscalls, logging, or exceptions; FTZ/DAZ armed;
bounded work per block; variable `numFrames` sub-chunked to ≤ maxBlock.

### 2.5 Time, timestamps & readback

- **TimeAnchor**: each callback publishes `{framePosition, monotonicNanos}`
  atomically (from Oboe stream timestamps). All cross-domain conversion —
  MIDI-in event times, Link phase, recording placement — goes through it.
- **TempoMap**: beat↔sample conversion (tempo + time-sig events), built as an
  **immutable base segment** (compiled offline, swapped via the same epoch
  offer/ack machinery on structure-shaped edits) plus a **fixed-capacity RT
  tail array published through a seqlock (version counter)**: the audio thread
  appends simple tempo events (set BPM, nudge, scene tempo at quantized launch)
  bumping `tempoMapRev` on every append and every base swap; background readers
  (DiskStreamer prefetch, StretchPrewarmer, workers, the Kotlin mirror) take a
  coherent snapshot with seqlock retry and **stamp everything they produce with
  the `tempoMapRev` they read** — stale prefetches/proxies are detectable and
  re-issued, and no reader ever takes a lock the RT thread can block on.
  Precedence: the tail supersedes the base from its first event's position
  until the next structure-shaped map rebuild consolidates it; a full tail
  forces that consolidation. Complex edits (tempo automation curves,
  insert/delete mid-song) are always structure-shaped base rebuilds. Launch
  quantization resolves against the map the RT thread currently holds — never
  a stale async copy.
- **TimebaseSource** abstraction inside TransportEngine from day one:
  `Internal | AbletonLink | MidiClockSlave` (PLL chase for external clock in).
  Tempo authority rule: when Link or clock-slave is active, tempo automation is
  suspended (events reported to UI).
- **Readback:** `MeterBus` (audio meters, SPSC, lossy), `MidiActivityBus`
  (per-MIDI-effect in/out activity counters, lossy), `TransportClock` (atomic
  snapshot per block), `EngineEventBus` **split into two classes**: lossy
  telemetry (xruns, CPU, dropout counters) and **must-deliver events** (take
  finalized, capture materialized, recording error, panic) which carry sequence
  numbers backed by a pull-side reconciliation query — a UI stall can never
  lose a take.
- **CpuLoadMonitor** attributes cost per node (per-block cycle sampling of the
  schedule) so "the expensive device" is nameable (spec P2 §15.4), feeding the
  **DegradationGovernor** ladder: shrink voice budget → force Eco quality on
  flagged devices → demote RT stretchers to proxies (keep-until-ready per
  §7.4) → prompt freeze. All actuation is ordinary commands; audio never just
  stops.

---

## 3. Audio topology & fixed audio semantics

### 3.1 Signal flow per track type (spec P2 §3)

- **MIDI/instrument:** MidiSourceMux(clip | live | step) → MidiEffectChain →
  Instrument → AudioEffectChain → TrackStrip → routing. Post-chain MIDI tap
  (`MidiTapBus`) recordable to other tracks.
- **Audio:** AudioSourceMux(clip | live input via InputJitterRing, monitor
  off/in/auto) → input gain → AudioEffectChain → TrackStrip → routing.
  `RecordTapPoint` = dry input or post-chain print (per track).
- **Drum:** MidiSourceMux → MidiEffectChain → DrumRackDevice (per-pad chains,
  chokes, per-pad strips; pads feed parent, an individual out, or a mixer
  track) → rack AudioEffectChain → TrackStrip.
- **Return:** SendCollector → chain → strip → Main (cycle-checked). **Group:**
  child sum → chain → strip. **External instrument:** MidiOut(port/chan/PC/CC)
  → hardware → AudioReturnIn(+hardware latency offset) → chain → strip.
- **Master:** mix bus → mastering chain → MasterStrip → OutputBusMatrix.

### 3.2 Output buses, cue & preview

`OutputBusMatrix`: N logical stereo buses — **Main** and **Cue** at launch —
mapped to device channels pairs on multichannel interfaces, or Cue folded into
Main when hardware can't provide it (state visible to UI). Routable sources:
MetronomeNode (cue|main|both), `PreviewPlayer` (browser audition — full decode-
preview path, tempo-synced/key-shifted preview via its own stretcher instance,
routed cue or main, live while the project plays), solo-to-cue mode, cue mix
sends per track (pre/post). This exists from M2 — MasterNode and OboeDriver are
designed against buses, not a single output.

### 3.3 PDC & bypass contract

- Devices report `latencySamples()`; PdcCalculator balances all parallel paths
  (dry/wet inside devices, racks' chains, sends/returns, sidechain edges,
  group joins) with DelayCompNodes; recomputed on rebuild.
- **Bypass is a parameter, therefore it must be latency-preserving and
  click-free**: bypassed device = dry delayed by its reported latency,
  crossfaded ~10 ms. A device whose latency depends on a parameter (lookahead,
  oversampling) either reports its worst-case constant latency or declares that
  parameter structure-shaped. This is part of the DeviceNode contract (§10).
- Live-input monitoring paths bypass PDC (low-latency monitoring), flagged.

### 3.4 Fixed numeric conventions (all device/mixer authors bind to these)

- Pan: constant-power sin/cos law, −3 dB center; stereo balance mode for
  stereo sources.
- Crossfader: equal-power curve; per-track assign A/B/none resolved in
  TrackStrip against the global crossfader param.
- Summing: float32 accumulation, no clamping anywhere before MasterStrip; the
  master limiter/soft-clip is the only nonlinearity by default.
- dB mapping: `gain = 10^(dB/20)`; fader taper defined once in ParamDescriptor
  curves; −∞ below −72 dB.
- Default fades: 10 ms equal-power micro-fades guard all clip edges/edits;
  crossfades equal-power.
- Solo semantics: solo-in-place; audibility matrix computed RT-side per block
  from per-track flags; returns stay audible when fed only by soloed tracks;
  soloing inside a group solos the path to master; exclusive-solo is a UI mode.

### 3.5 Recording alignment (owner: `RecordingAligner`)

Placement offset for recorded material =
`capturePosition − (outputLatency + inputLatency + userCalibration)`; a
post-FX RecordTapPoint additionally subtracts that chain's PDC at the tap.
MIDI events convert monotonic→frames via TimeAnchor. OboeDriver supplies
per-stream latency reports; loopback calibration (EnginePrefs) refines. The
"take finalized" event carries the computed offset; comping alignment is
therefore sample-exact by construction.

---

## 4. Module map

One Gradle `:app` + `libdawcore.so`. Dependency direction:

```
Kotlin  ui/* → state holders → domain (+services) → engine bridge → JNI
C++     jni → engine → {graph, sequencer, media, timestretch} → device → dsp → core
```

### 4.1 C++ modules (`app/src/main/cpp/`) — replaces the condemned skeleton

| Module | Key classes (responsibility) |
|---|---|
| `core/` | SpscRing, ParamMoveTable, EventRing (paired-slot note safety), AtomicSnapshot, EpochGc, AudioBufferPool, SmoothedValue, ScopedNoDenormals, FixedVector/FixedFunction, NodeUid, ConfigHash, TimeAnchor, EngineCommand (§10 layout), MeterFrame, RtRandom (seedable) |
| `dsp/` | Oscillator (polyBLEP), WavetableSet/Oscillator, NoiseGen, SvfFilter, LadderFilter, BiquadFilter+design, Comb/Allpass, AdsrEnvelope, AhdEnvelope, MultiStageEnvelope, Lfo, EnvelopeFollower, DelayLine, FftProcessor (pffft), WindowedOverlapAdd, PartitionedConvolver, Resampler, Waveshaper, Oversampler, PitchDetectorYin, TransientDetector, GrainPlayer, FdnReverbCore, PsolaShifter, FormantShifter, DitherTpdf, LoudnessEbuR128, TruePeakDetector, CorrelationMeter, DcBlocker, StereoWidthProcessor — all host-compilable, no Android deps |
| `device/` | DeviceNode (contract §10), InstrumentNode, EffectNode, MidiEffectNode, DeviceChain, ParamDescriptor/ParamRegistry (semantic keys §6), VoiceAllocator (accepts external steal demands), Voice, MpeNoteState, ModMatrix, SidechainInput, QualityMode |
| `device/instruments/` | SubtractiveSynth, WavetableSynth, FmSynth, MacroSynth, SimpleSampler, MultiSampler (SFZ-subset import), DrumPadSampler (synthesis modes: sub/noise/FM/ring/bit — the "drum synth" family), DrumRackDevice, MalletModel, StringModel, EPianoModel, SampleLibraryPlayer, ExternalInstrumentDevice |
| `device/effects/` | Utility, SpectrumAnalyzerNode, TunerNode, LoudnessMeterNode, ChannelEq, ParametricEq8 (LR/MS), PerformanceEq3, AutoFilter, Compressor, BusCompressor, GateExpander, Limiter (lookahead/TP; constant reported latency), DeEsser, MultibandDynamics, TransientShaper, Saturator, MultistageDistortion, PedalDrive, AmpSim, CabSim, Bitcrusher, StereoDelay, EchoDevice, FilterDelay3, GrainDelay, AlgorithmicReverb, HybridReverb, ChorusEnsemble, PhaserFlanger, AutoPanTremolo, PitchCorrector (YIN+PSOLA, scale/MIDI-guided), PitchFreqShifter, Vocoder, ResonatorBank, SpectralProcessor, BeatRepeat, LooperDevice (can set project tempo from loop), NoiseReducer (offline), DeClicker (offline) |
| `device/midi_effects/` | RtArpeggiator, RtChord, RtNoteLength, RtPitch, RtRandom, RtScale, RtVelocity, RtCcControl, RtNoteEcho, RtExpressionControl, RtMidiLfo — MidiEffectNode base with ActiveNoteRegistry (bypass/reorder/stop safe) + MidiActivityBus taps |
| `device/racks/` | RackDevice (Instr/Audio/MidiFx), RackChain (zones: key/velocity/selector/**frequency band** via LR crossovers; crossfaded), MacroTable, VariationStore (snapshots, morph), ChainMixer |
| `graph/` | AudioGraph (compiled schedule), TrackNode/GroupTrackNode/ReturnTrackNode/MasterNode, TrackStrip (conventions §3.4, crossfader assign, cue sends), SendNode/SendCollector, SidechainTap, RoutingTable (cycle-refusing), DelayCompNode, PdcCalculator, OutputBusMatrix, MeterProbe, GraphBuilder, MigrationPlan, NodeStateRegistry, EpochGc use, VoiceBudgetLedger |
| `sequencer/` | TransportEngine (TimebaseSource: internal/Link/clock-slave), TempoMap (RT tail edits + offline rebuilds), TimelineSnapshot (per snapshot unit — clip content / track lane group / marker+tempo lists — epoch-swapped; bounded scene window per D4), ArrangementPlayer, SessionPlayer (launch modes/legato/quant), **SessionRecorder** (record into slots: quantized start/stop, auto-loop on stop, overdub/replace), TrackPlaybackArbiter, FollowActionScheduler (resolves targets — including random choices — at least one quantization period early and feeds PrefetchPlanner, so disk-streamed follow targets never start silent), LaunchQuantizer, MidiClipPlayer (sounding-note table, comp-region lists, MPE, probability/deviation with **seeded RtRandom keyed `(clipId, position, loopPassIndex)` — passes differ musically, offline render replays the same pass indices for parity**), AudioClipPlayer (region lists, warp/repitch, groove micro-offsets applied to beat→sample mapping, ring-underrun → silence+event), StepSequencerCore, GrooveEngine, MidiScheduler, MetronomeNode (bus-routable), PunchController, CaptureBuffer, ArrangementRecorder, AutomationEvaluator (active-lane iteration only), ClipEnvelopeEvaluator, ModulationEngine, ParameterResolver |
| `media/` | AudioFileDecoder (dr_wav/flac/mp3 decode + MediaCodec AAC), AudioFileWriter (WAV, FLAC, AAC), SampleCache (budgeted, `(fileId, rate)` keys, refcounted SampleHandles), DiskStreamRing, PrefetchPlanner, RecordingWriter (journaled, storage guard), PeakFileBuilder, AnalysisService (transients/tempo/key/pitch), SliceEngine, **SampleCaptureService** (record-into-pad/sampler flow off ResampleTap or input, distinct from track recording) |
| `timestretch/` | WarpEngine (budgeted stretcher pool), WarpMap, RepitchPlayhead, StretchPrewarmer (proxies keyed `(tempoMapRev, warpMapRev, rate)`, invalidated on either edit; pre-roll on seek/armed launches via PrefetchPlanner) |
| `engine/` | AudioEngine, OboeDriver (output-driven duplex per D2, per-stream latency reports, route/rate-change re-prepare sequence per D5), SyncAdapter (Link RT capture/commit; MIDI clock in/out), EngineModel, OfflineRenderEngine (deterministic seeds; tails; stems matrix; **realtime-capture mode for ExternalInstrumentDevice tracks**), FreezeEngine, ResampleTap, CpuLoadMonitor (per-node attribution), DegradationGovernor, PanicController |
| `jni/` | NativeAudioBridge, CommandCodec + StateCodec (versioned wire formats §10), CallbackDispatcher (never from RT) |

### 4.2 Kotlin modules (`app/src/main/java/com/example/`)

| Module | Key classes |
|---|---|
| `domain/model/` | Project, TrackModel (kinds/capabilities incl. resampling role), ClipInstance (arr/session), ClipContent (Midi/Audio; contentId sharing, copy-on-unlink per P2 §1.2 property split), Scene, NoteModel (MPE, probability, deviations), AutomationLane (points/curves/shapes/simplify, musical-time lock), ClipEnvelope, ModulationAssignment, DeviceModel tree (racks/zones/macros/variations; **semantic param keys only — never indices**), TakeLane/Take/CompRegion (audio + MIDI), GrooveTemplate, RoutingModel, ScaleContext, MarkerModel, TempoEvent/TimeSigEvent |
| `domain/store/` | ProjectStore (+editSeq stamping), ProjectAction families, UndoManager (gesture transactions, coalescing, bounded), SelectionModel (survives context switches) |
| `domain/midi/` | MidiTransformOps (15 transformations), MidiGeneratorOps (7 generators), StepEditOps (fill N/rotate/randomize) — pure functions, preview-commit contract |
| `engine/` | EngineController (lifecycle, foreground service, focus/route policy, interruption finalize-recording), EngineSync (diff → messages/deltas + change classification), EngineReadback (buses → flows; must-deliver reconciliation), CommandEncoder, EnginePrefs (buffer size, calibration) |
| `data/` | ProjectRepositoryV2 (project dir: project.json + media/ + peaks/ + takes/), ProjectSerializer (versioned migrations), AutosaveJournal (**ops log versioned by schema; on mismatch recover from snapshot, discard tail**), VersionStore, CrashRecovery (journal replay + recording scan), DawDatabase/Room (project + MediaLibrary index incl. **MIDI patterns**, tags, favorites, recents), PackManager, MissingFileResolver, ProjectArchiver, ImportService, **MediaGc** (Kotlin-side — reachability = undo stack ∪ versions ∪ takes ∪ autosave journal, all edit-model concepts; only unreachable media files are ever deleted) |
| `services/` | ExportService (mix/stems/tracks/groups/loop/MIDI; WAV/FLAC/AAC; dither; normalize; tails; loudness targets), MidiDeviceService (USB/BLE; **MidiLearnMap targets: params, macros, mixer, clip/scene launch, transport**), AudioDeviceService (device/bus mapping, cue availability), SyncService (thin UI over SyncAdapter), RenderJobQueue |
| `ui/state/` | Existing 8 + ClipEditorStateHolder, AudioEditorStateHolder, DeviceDetailStateHolder (A/B, presets), AutomationStateHolder (read/write/touch/latch, override/return), TakeCompStateHolder, PerformanceStateHolder (macros, crossfader, momentary FX, edit lock), InputSurfaceStateHolder (keyboard/pads/chords/fretboard/step, scale lock, MPE, note repeat), BrowserStateHolder v2 (preview via PreviewPlayer), SettingsStateHolder, ProjectHubStateHolder |
| `ui/screens/earth/` + `ui/components/earth/` | Existing + ClipEditorScreen, AudioEditorScreen, DeviceDetailScreen, TakeLanesScreen, PerformanceScreen, InputSurfaceScreen, SettingsScreen, ProjectHubScreen; components: WaveformView, PianoRollGrid, StepGrid, AutomationLaneView, EnvelopeEditor, XYMorphPad, MacroKnobRow, TakeLaneRow, SpectrumView, LoudnessMeterView, Keyboard/PadGrid/ChordPad views, ValueSlider (coarse/fine/numeric/reset) — Earth.Design V2 morphic glass |

---

## 5. Parameter system

Resolution per block: `base → track automation → clip envelopes → modulation
(additive bipolar/unipolar offsets) → macro contributions → SmoothedValue →
DSP`. Only *active* lanes/assignments are iterated. Automation touch/override/
return latching per param. Recording modes read/write/touch/latch implemented
Kotlin-side from engine touch reports. Macro moves are plain param moves, so
recording them as automation is free.

## 6. Stable parameter identity (replaces `(NodeUid, paramIndex)`)

- Persisted identity = **semantic param key**: per-device-type stable string id
  (hashed) declared in ParamDescriptor. AutomationLane, ClipEnvelope,
  ModulationAssignment, MacroTable, MidiLearnMap all reference keys.
- GraphBuilder resolves keys → dense indices at compile time; indices exist
  only inside a compiled graph as a builder-internal artifact. All messages and
  the ParamMoveTable carry stable `(NodeUid, semanticKeyHash)` identity; the RT
  thread resolves through the installed graph's resolver table at apply time
  (§2.2) — no producer ever holds indices.
- Device replacement uses a declared **remap table** (per device-type pair) so
  compatible automation/mappings survive replace (P2 §4, §11.1); unmapped
  lanes are kept, flagged orphaned, never silently deleted.

## 7. Media, warp & memory reality

### 7.1 Memory budget table (constructor-injected, tuned per RAM class)

| Cache | 3 GB device | 6 GB | 8 GB+ |
|---|---|---|---|
| SampleCache resident | 256 MB | 512 MB | 768 MB |
| Stream rings (2 s stereo float32 @48 kHz ≈ 0.77 MB each) | 24 (~18 MB) | 32 (~25 MB) | 48 (~37 MB) |
| Prewarm/proxy cache (disk-backed, RAM window) | 64 MB | 128 MB | 192 MB |
| Peak-file cache | 32 MB | 64 MB | 96 MB |

Ring seconds derive from max stretch ratio × prefetch horizon; numbers are
parameters, not constants.

### 7.2 Streaming & recording

RT reads only prefilled rings (underrun → silence + event). RecordingWriter
journals headers (crash-recoverable), refuses to start under a storage floor
and warns approaching it. Auto-naming by track/take/timestamp.

### 7.3 Sample-rate change (D5 sequence)

Pause-safe re-prepare: drain → close streams → reopen at new rate → re-key
caches → re-prime rings → resume; recordings/imports conform via per-clip
resamplers until background re-conform jobs complete. UX state exposed.

### 7.4 Warp budget

N concurrent RT stretchers by device class (baseline 8 stereo mid-range,
measured in M7); beyond N: prewarmed proxy or repitch fallback with UI flag.
Proxies invalidate on tempo-map or warp-map revision change — but a live
tempo event never causes an audible cliff: **a stale proxy keeps playing
through a cheap corrective stretcher at ratio oldTempo/newTempo (≈1.0)**
while the worker pool re-renders against the new `tempoMapRev`; the same
keep-until-ready rule governs DegradationGovernor stretcher demotion (RT
stretcher runs until its proxy exists). This corrective path is part of the
WarpEngine/AudioClipPlayer interface from M6–M7, not a retrofit.

## 8. Linked clips, takes, capture

- `ClipContent` shared by contentId; instances carry position/slot, launch
  mode/quant, follow actions, arrangement fades, per-instance overrides —
  exactly the P2 §1.2 split. Unlink = copy-on-unlink (never duplicates audio
  files). Consolidate/render = explicit new content + file.
- Take lanes for audio **and MIDI** (comp regions; MidiClipPlayer plays MIDI
  comp region lists like AudioClipPlayer does audio).
- SessionRecorder owns slot recording (quantized start/stop, auto-loop,
  overdub/replace); ArrangementRecorder owns session-performance capture
  (launches, scene changes, mixer/param automation, tempo events → linked
  instances + automation); CaptureBuffer owns retrospective MIDI.

## 9. Feature coverage matrix

### 9.1 SPEC_PART1_FUNCTIONAL (22 areas)

| Area | Owners |
|---|---|
| 1 Project system | ProjectRepositoryV2, ProjectSerializer, VersionStore, AutosaveJournal, CrashRecovery, ProjectArchiver, MissingFileResolver, ImportService, MediaGc; ProjectHub UI |
| 2 Transport & song controls | TransportEngine (+TimebaseSource), TempoMap, MetronomeNode (bus-routable), PunchController, LaunchQuantizer, MarkerModel, tap tempo (TransportStateHolder); status via CpuLoadMonitor + EngineEventBus |
| 3 Arrangement View | ArrangementPlayer, TimelineSnapshot, ArrangementRecorder; clip reducers (split/join/ripple/insert-remove time); ArrangerScreen, AutomationLaneView, TakeLaneRow; FreezeEngine |
| 4 Session View | SessionPlayer, **SessionRecorder**, TrackPlaybackArbiter, FollowActionScheduler, LaunchQuantizer, Scene; linked move/copy via ClipContent; SessionViewScreen |
| 5 Track system | TrackModel kinds + reducers; TrackNode family, TrackStrip, RoutingTable; MixerStateHolder; presets |
| 6 Audio recording | OboeDriver duplex, InputJitterRing, RecordRing, RecordingWriter, **RecordingAligner**, monitor modes, latency calibration, take lanes/comping, CrashRecovery |
| 7 MIDI recording & editing | MidiClipPlayer, MidiScheduler, CaptureBuffer, StepSequencerCore, NoteModel; ClipEditorScreen; MIDI file I/O (Import/ExportService); MidiLearnMap |
| 8 Audio clip editor | AudioContent ops, TransientDetector, WarpEngine/WarpMap, SliceEngine, consolidate via OfflineRenderEngine; AudioEditorScreen, WaveformView |
| 9 Drum Rack | DrumRackDevice, DrumPadSampler, chokes, pad routing, **SampleCaptureService** (record into pads); StepSequencerCore + StepEditOps; kit presets |
| 10 Sampler | SimpleSampler, MultiSampler, SliceEngine, AnalysisService pitch detect, SampleCaptureService; DeviceDetailScreen |
| 11 Instruments | device/instruments/* (13); common behavior via DeviceNode contract + VoiceAllocator + VoiceBudgetLedger + ModMatrix + MacroTable + QualityMode |
| 12 Browser & library | MediaLibrary (samples/presets/kits/grooves/**MIDI patterns**), PackManager, **PreviewPlayer** (audition while playing, tempo/key preview), favorites/recents, MissingFileResolver; SoundBrowserScreen |
| 13 Device chain & racks | DeviceChain (+replace-with-remap §6), RackDevice (freq/key/vel/selector zones), MacroTable, VariationStore; freeze/render via RenderJobQueue |
| 14 Mixer | TrackStrip, sends, groups, RoutingTable, SidechainTap, **OutputBusMatrix cue mix**, MeterProbe/MeterBus, track delay offset, phase invert, mono check, channel presets; MixerScreen |
| 15 Effects | device/effects/* catalog |
| 16 Routing | RoutingTable, MidiTapBus, ResampleTap, sidechains, pad routing, cue/monitor mixes, feedback refusal, templates(=projects) |
| 17 Automation & modulation | AutomationLane/Evaluator, ClipEnvelope/Evaluator, ModulationEngine/Assignment, macros, MIDI learn→params |
| 18 Performance | SessionPlayer/scenes, PerformanceScreen (clip pads via **MidiLearnMap clip/scene targets**, macro knobs, **crossfader**, momentary FX via VariationStore, edit lock), CaptureBuffer, SyncAdapter (Link/clock) |
| 19 Mixing & mastering | Mastering chain on MasterNode, LoudnessMeterNode/EbuR128/TruePeak, SpectrumAnalyzerNode, **reference A/B: pre-master-chain tap + loudness-matched compare** in MasteringSuiteScreen, streaming loudness targets + DitherTpdf in ExportService |
| 20 Export & sharing | OfflineRenderEngine + ExportService (WAV/FLAC/AAC per D5), ProjectArchiver, SAF/share |
| 21 Hardware & connectivity | OboeDriver (USB, multi-I/O buses), MidiDeviceService (USB/BLE, **clock in via TimebaseSource** + clock out), SyncAdapter (Link), sustain/expression mapping, AudioDeviceService, background audio (foreground service) |
| 22 Reliability | AutosaveJournal (versioned), CrashRecovery, RecordingWriter journal + storage guard, UndoManager, VersionStore, MissingFileResolver, CPU/thermal via CpuLoadMonitor + DegradationGovernor, offline-first |

### 9.2 SPEC_PART2_WORKFLOW

| Section | Owners |
|---|---|
| §1.1 | TrackPlaybackArbiter |
| §1.2 | ClipContent model (§8) |
| §2 | Areas 1–14 flows; templates in ProjectRepositoryV2 |
| §3 | §3.1 pipelines, taps, PDC, monitoring choice, RecordTapPoint |
| §4 | DeviceNode contract + presets + A/B + QualityMode + preserve-on-replace (§6) |
| §5.1–5.12 | The 13 instrument devices (DrumPadSampler synthesis modes = drum-synth family) + RackDevice |
| §6 | InputSurfaceStateHolder/Screen (all modes incl. fretboard, note repeat, fixed velocity, MPE, simultaneous external MIDI) |
| §7 | Recording modes incl. SessionRecorder + replace/overdub + multi-take MIDI comping + MidiTapBus record; note properties in NoteModel; piano-roll + drum-editor ops (MidiTransformOps/StepEditOps) |
| §8.1/8.2/8.3 | MidiTransformOps (15) / MidiGeneratorOps (7) / device/midi_effects (11, ActiveNoteRegistry, MidiActivityBus) |
| §9 | ScaleContext, GrooveEngine (+extraction via AnalysisService; audio application via AudioClipPlayer micro-offsets), MPE lanes + conversion |
| §10 | Effects catalog + common contract (§3.3 bypass/PDC, sidechain, metering, A/B, oversampling flags, Looper tempo-set) |
| §11 | DeviceChain ops, racks (freq split incl.), macros, variations, nesting cap |
| §12 | §5 layering; envelope loop-independence; Session→Arr automation conversion (ArrangementRecorder) |
| §13 | ArrangementRecorder + consolidate-to-scene op |
| §14 | Takes/comping (§8), bounce matrix (OfflineRenderEngine incl. external-instrument realtime capture), FreezeEngine, ResampleTap |
| §15 | Focused contexts + SelectionModel; audio survives navigation (EngineController); ValueSlider precision; progressive depth panels; Eco/Std/High QualityMode; CpuLoadMonitor attribution + DegradationGovernor; interruption/rotation policies (EngineController + holders) |
| §16 | Scenarios = milestone DoD (§11) |
| §17 | D1–D13 |

## 10. Seam contracts (the no-compile guardrail)

The six seams below get a frozen contract **appendix file
`docs/spec/CONTRACTS.md`, written as the first artifact of M0** (signature-level
declarations, struct layouts with static size assertions, threading annotations,
version fields). Contracts change only by editing that file first. Scope:

1. **DeviceNode**: `prepare(rate, maxBlock)`, `process(ProcessContext&)`,
   `reset()`, `latencySamples()`, bypass behavior (§3.3), `paramCount/
   descriptor(i)`, `saveState/loadState(NodeState&)`, RT-safety annotations.
2. **EngineCommand / message layout**: fixed 64-byte POD, `int64` sample
   positions, `double` beats, editSeq field, enumerated op codes per family;
   param addressing always `(NodeUid, semanticKeyHash)` — indices never cross
   the wire; EventRing paired-slot rule for note on/off; param-block event
   framing for atomic bulk sets.
3. **NodeState block**: header {NodeUid, ConfigHash, size, version}, POD body,
   SampleHandle refcount rules, adopt-vs-reset criteria.
4. **TimelineSnapshot read API**: iterators per snapshot unit — clip content
   (notes, steps, envelope segments, warp segments), **per-track automation
   lane groups**, marker/tempo lists — bounded window guarantees, epoch/swap
   rules, and the **editSeq skew-tolerance rule**: graph@N with snapshot@M may
   transiently disagree; RT skips unresolvable references silently, counts
   them, and converges on the next swap — never a cross-swap barrier.
5. **JNI wire formats**: CommandCodec + StateCodec framing, both with explicit
   format-version fields from day one.
6. **ParamId & ParamDescriptor**: semantic key hashing, descriptor fields
   (range/curve/default/unit/smoothing/rt-flag/randomize-exclusion/quality),
   key→index resolution rules.

Plus the standing rule: `core/` + `dsp/` compile hostside (desktop CMake test
target exists from M0; runs only when the user asks).

## 11. Build order (dependency-corrected)

- **M0 Foundation:** CONTRACTS.md; `core/` (rings, ParamMoveTable, EventRing,
  EpochGc, pools, TimeAnchor); first `dsp/` wave; OboeDriver (duplex per D2);
  AudioEngine skeleton + message drains; JNI codecs (versioned); CMake (device
  + host targets).
- **M1 Model & time:** Kotlin model v2 + store (+editSeq), EngineSync change
  classification, TempoMap + TransportEngine (TimebaseSource), TimelineSnapshot
  + swap mechanics, MidiScheduler.
- **M2 Graph & mixer:** GraphBuilder + MigrationPlan + swap/ack, TrackNode
  family, strips (conventions), sends/returns/groups, RoutingTable, PDC,
  OutputBusMatrix (Main+Cue), meters; MixerScreen wiring.
- **M3 Device platform:** DeviceNode + chains, ParamRegistry/Resolver (semantic
  keys), VoiceAllocator + VoiceBudgetLedger (steal order: releasing → oldest →
  quietest; protect most-recent notes and drum transients), presets,
  racks/macros/modulation core, QualityMode.
- **M4 First sound:** SubtractiveSynth + MidiClipPlayer + SessionPlayer
  (launch minimal) + MetronomeNode. Then WavetableSynth, FmSynth.
- **M5 Drums & sampling** (pulls forward decode + resident SampleCache +
  PreviewPlayer plain audition — tempo-synced/key-shifted preview arrives with
  the M7 stretcher pool; the M6 streaming tier stays put): DrumRack,
  DrumPadSampler, StepSequencerCore, SimpleSampler, SliceEngine,
  SampleCaptureService. *(Scenario 16.1.)*
- **M6 Audio tracks & recording:** DiskStreamRing/PrefetchPlanner tier,
  AudioClipPlayer, duplex recording path + RecordingAligner, takes/comping,
  SessionRecorder. *(Scenario 16.3 core.)*
- **M7 Warp & audio editing:** timestretch/ (budget + proxies), AudioEditor,
  GrooveEngine.
- **M8–M10 Effects waves:** mix (EQs/dynamics/delays/AlgoReverb/utility/meters)
  → color (sat/dist/amp, mod FX, AutoFilter) → advanced (HybridReverb,
  pitch/vocal, spectral, BeatRepeat, Looper, cleanup).
- **M11 MIDI tools + InputSurface.** *(Scenario 16.2.)*
- **M12 Session depth:** follow actions, scene tempo (RT tempo events),
  ArrangementRecorder, PerformanceScreen. *(Scenario 16.4.)*
- **M13 Automation/modulation UI**, clip envelopes, MIDI learn (full targets).
- **M14 Browser/library/packs** + remaining instruments (MacroSynth,
  MultiSampler, physical models, SampleLibraryPlayer, External).
- **M15 Export/mastering** (+DegradationGovernor, freeze/bounce matrix).
  *(Scenario 16.5.)*
- **M16 Hardware/sync/reliability hardening.** *(Scenario 16.6.)*

Each milestone ends: ARCHITECTURE.md updated, data-flow walk, commit.

## 12. Key decisions & rejected alternatives

| Decision | Rejected | Why |
|---|---|---|
| Dual model + compile/swap + MigrationPlan adoption | Locked shared graph; reset-on-rebuild | RT safety; no audible resets on edits |
| Three change classes | Two (param/structure) | Note/automation edits are the most common op; per-clip snapshot swap keeps them cheap and stuck-note-safe |
| Per-producer rings + ParamMoveTable + lossless EventRing | One SPSC "command queue" | Producer discipline, coalescing, and note-loss-impossible semantics |
| editSeq stamping + post-swap re-apply | Hope | Fader rides during rebuilds must not jump |
| Output-driven duplex (callback-less input) | Two independent callbacks | One clock master, defined monitoring latency (Oboe-recommended) |
| RecordingAligner + TimeAnchor | "Calibration setting" | Sample-exact take placement is a design, not a preference |
| Semantic param keys | Persisted indices | Survives device revisions/replacement (spec requirement) |
| SyncAdapter in C++ | Link in Kotlin service | Phase alignment is audio-callback work |
| Rate-keyed caches + conform-on-playback | "Project rate = device rate" absolutism | Route changes and cross-device projects are normal life |
| Drop MP3 export | LAME (LGPL, extra .so) | Android has no MP3 encoder; AAC covers sharing; license posture intact |
| CONTRACTS.md frozen seams + host-compilable core | Prose-only blueprint | Months without a compiler demand pinned interfaces |
| signalsmith-stretch + budget/proxies | RubberBand/SoundTouch; unlimited RT stretch | License; phone CPU honesty |
| Immutable Kotlin model + persistent collections | Mutable listeners | Undo, autosave, hand-off |
| Meters/playhead bypass ProjectStore | Everything through store | 60 Hz × 64 tracks melts reducers |
| Fixed capacities + budget tables | Dynamic everything | RT contract; tunable constructor budgets |
| Single :app + logical modules | Gradle multi-module now | No compile for months; split later mechanical |

## 13. Third-party inventory (license-vetted)

| Library | Use | License |
|---|---|---|
| Oboe | Audio I/O | Apache-2.0 |
| signalsmith-stretch | Warp/pitch | MIT |
| pffft | FFT | BSD-like |
| dr_wav / dr_flac / dr_mp3 | Decode | PD / MIT-0 |
| libFLAC | FLAC encode | BSD |
| MediaCodec | AAC encode/decode | platform |
| WORLD (or derived) | Formant/pitch analysis | modified-BSD |
| Ableton Link | Sync (D8, flagged, isolated in SyncAdapter) | GPLv2 / commercial |
| kotlinx.serialization / collections.immutable | Model | Apache-2.0 |
| AMidi / android.media.midi | MIDI I/O | platform |

No GPL-family code ships unless the Link decision is made explicitly. No MP3
encoder dependency exists (D5).
