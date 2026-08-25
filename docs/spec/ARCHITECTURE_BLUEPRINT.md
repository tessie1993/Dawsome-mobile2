# Dawsome Architecture Blueprint

**Status:** authoritative target architecture for the full product defined by
`docs/spec/SPEC_PART1_FUNCTIONAL.md` (22 functional areas) and
`docs/spec/SPEC_PART2_WORKFLOW.md` (workflow, signal flow, instruments, MIDI tools,
effects, racks, automation, phone translation).
This document is a blueprint: modules, classes, responsibilities, data flow and
threading contracts — **no code**. `docs/ARCHITECTURE.md` remains the living map of
code that exists; classes graduate from this blueprint into that map as they land.

Authority order: the two spec documents → this blueprint → the living map.

Research grounding: Tracktion Engine (model/playback-graph separation, pooled
buffers, latency balancing, background stretch threads), Google Oboe guidance
(LowLatency + Exclusive/MMAP, device-native rate, non-blocking callbacks),
Ableton Link SDK (dual GPLv2/proprietary), signalsmith-stretch (MIT),
pffft (BSD-like), WORLD (modified-BSD), dr_libs (public-domain/MIT-0),
libFLAC (BSD), Android AMidi / android.media.midi, MediaCodec for AAC/MP3.

---

## 1. Fixed product & platform decisions

These resolve the specs' "Open decisions" for the build. Each is changeable, but code
assumes them until changed here.

| # | Decision | Value | Rationale |
|---|---|---|---|
| D1 | Platform | Android only, minSdk 24, Kotlin + Compose UI, C++20 NDK engine | Existing repo; Oboe floor |
| D2 | Audio I/O | Oboe; `PerformanceMode::LowLatency` + `SharingMode::Exclusive`, device-native sample rate (typically 48 kHz), float32 processing | Lowest latency path (MMAP where available) |
| D3 | Internal format | 32-bit float, de-interleaved channel pointers, block size = burst size (with engine-side fixed maxBlock 1024 for allocation) | NEON-friendly, Oboe native |
| D4 | Capacities | 64 user tracks, 8 group tracks, 8 return tracks, 1 master, 16 devices/chain, 8 chains/rack, rack nesting depth 3, 8 scenes visible/∞ stored, 16 macro slots (8 primary), 64-voice global polyphony budget | Fixed-capacity RT structures; phone-realistic |
| D5 | Sample rates | Project = device rate; import resampled on load path; export 44.1/48/96 kHz, 16/24-bit WAV, FLAC, AAC/MP3 (MediaCodec) | Avoid RT resampling of the whole graph |
| D6 | Plug-ins | No third-party plug-in hosting at launch | Spec leaves open; huge scope |
| D7 | Time-stretch | signalsmith-stretch (MIT) as WarpEngine core; repitch mode via resampler | License-safe, high quality |
| D8 | Ableton Link | Behind `SyncAdapter` interface; OFF by default; GPLv2/commercial decision flagged to the owner before shipping | License isolation |
| D9 | Tuning | 12-TET + named scales only (spec's custom-tuning question: deferred) | Scope |
| D10 | Stem separation | Not in core (spec open question: deferred) | Scope |
| D11 | Noise reduction | Offline render + preview (not RT at launch) | CPU realism |
| D12 | Project interchange | Own format + audio/stem/MIDI export; no desktop-DAW project export at launch | Spec open question |
| D13 | Native lib | Single `libdawcore.so`, single JNI seam | One bridge, one codec |

---

## 2. Concurrency & data-flow model (the load-bearing design)

### 2.1 Dual-model architecture

Two representations of the same project, never shared mutably:

- **Edit model (Kotlin)** — `ProjectState`: immutable, undoable, serializable. The
  single source of truth for *what the project is*. Mutated only via
  `ProjectStore.dispatch(ProjectAction)` reducers.
- **Realtime model (C++)** — `PlaybackGraph` + `TimelineSnapshot`: allocation-free,
  lock-free-read structures the audio thread walks. Derived, disposable, rebuilt.

Bridging rule: *parameter-shaped* changes (volume, pan, device param, macro, send
level, mute/solo, tempo nudge) flow as **EngineCommands** through a lock-free SPSC
queue and are applied by the audio thread between blocks. *Structure-shaped* changes
(add/remove/reorder track/device/clip/scene, routing, warp map edits, chain zones)
mark the realtime model dirty and trigger an asynchronous **graph rebuild**.

### 2.2 Graph rebuild & swap (Tracktion-informed)

- `GraphBuilder` (background thread) compiles the latest `EngineModel` (a compact
  C++ mirror of ProjectState, owned by the builder) into a new `PlaybackGraph`:
  topologically ordered node list, pre-allocated buffers from `AudioBufferPool`,
  PDC delays inserted, per-node prepared state.
- Swap is an atomic pointer exchange the audio thread performs at a block boundary
  (`GraphSwapLatch`). The retired graph is pushed to a garbage queue and destroyed
  on the builder thread — **the audio thread never frees memory**.
- **Node state migration:** every node carries a stable `NodeUid` derived from the
  edit-model entity id. On swap, stateful DSP (filter states, delay lines, reverb
  tails, voice states, stretcher state, envelope positions) is *adopted* from the
  retired graph via `NodeStateRegistry` (uid → state block, move-only). This fixes
  the classic rebuild artifact (Tracktion's "stateful buffers lost" problem):
  reordering an EQ must not cut a reverb tail.
- Rebuilds are debounced/coalesced; a rebuild in flight absorbs newer dirty marks.

### 2.3 Thread inventory

| Thread | Priority | Role | Talks to |
|---|---|---|---|
| Oboe RT callback | RT (inherited MMAP/FIFO) | Pull commands, advance transport, run PlaybackGraph, write output, capture input taps | SPSC in: commands, MIDI-in, disk rings. SPSC out: meters, playhead, events |
| Oboe input callback (or duplex pull) | RT | Deinterleave input → monitoring tap + record ring | Record ring → writer |
| GraphBuilder | High-normal | EngineModel upkeep, graph compile, retired-graph destruction, state registry | Command staging in, graph swap out |
| DiskStreamer | High-normal | Prefetch audio-clip rings ahead of playhead & scheduled launches; peak-file reads | SampleCache, rings |
| RecordingWriter | Normal | Drain record rings → WAV with journaled headers (crash-safe) | Files, events out |
| MIDI I/O | High-normal | AMidi read/write loops, timestamping, clock in/out | SPSC → RT; MidiDeviceService |
| Worker pool | Low | Waveform/peak gen, transient & tempo analysis, offline render, freeze, noise-learn, pack install | Media, events |
| Kotlin main | UI | Compose, state holders, ProjectStore | JNI bridge |
| Kotlin IO coroutines | BG | Persistence, autosave journal, Room, packs | Files/DB |

Rules on the RT threads: no allocation, no locks, no syscalls, no logging, no
exceptions across the boundary, FTZ/DAZ armed (`ScopedNoDenormals`), bounded work
per block. All cross-thread traffic is SPSC rings or atomics; nothing blocks RT.

### 2.4 State readback (engine → UI)

- `MeterBus`: per-track peak/RMS + gain-reduction + master true-peak/LUFS frames,
  SPSC, UI polls at frame rate. Bypasses ProjectStore (too hot for state reducers).
- `TransportClock`: atomic {samplePos, beat, bpm, playing, recording, loop} snapshot
  each block; Kotlin exposes it as a Flow tick for playheads.
- `EngineEventBus`: low-rate events (xrun count, CPU load, recorded-file grew,
  take finalized, clip launched/quantize-pending, stuck-note panic, route change).
  These *do* reach ProjectStore where they change project state (e.g. new take).

### 2.5 Timebase

`TempoMap` (tempo + time-signature change points) provides bidirectional
beat↔sample conversion, owned by the realtime side, mirrored in Kotlin. All RT
scheduling is in samples; all musical data is in double beats. Loop, launch
quantization, groove, Link phase all resolve through TempoMap. Tempo automation
edits rebuild the map (structure-shaped change).

---

## 3. Signal flow per track type (spec Part 2 §3)

Fixed per-block track pipeline (each stage a node or fused into TrackNode):

- **MIDI/instrument track:** MidiSourceMux(clip player | live input | step input)
  → MidiEffectChain → InstrumentDevice → AudioEffectChain → TrackStrip(vol/pan/
  mute/solo, sends pre|post) → routing (group|master). Processed-MIDI tap after
  MidiEffectChain is recordable to another track (MidiTapBus).
- **Audio track:** AudioSourceMux(clip player | live input w/ monitor mode) →
  input gain → AudioEffectChain → TrackStrip → routing. Monitoring modes:
  off / in / auto; record taps dry or post-chain (RecordTapPoint decision per
  track, spec §3.2).
- **Drum track:** MidiSourceMux → MidiEffectChain → DrumRackDevice (pad chains:
  per-pad instrument + per-pad effects + per-pad strip with choke groups,
  individual outs to mixer) → rack-level AudioEffectChain → TrackStrip.
- **Return track:** SendCollector → AudioEffectChain → TrackStrip → master (or
  permitted bus; feedback-cycle detection at edit time refuses illegal routes).
- **Group track:** child sum → AudioEffectChain → TrackStrip → master/group.
- **External instrument track:** MidiOutDevice(port/chan/PC/CC) → hardware →
  AudioReturnIn(+latency offset) → AudioEffectChain → TrackStrip.
- **Master:** mix bus → AudioEffectChain (mastering) → MasterStrip(volume, final
  TP limiter option) → output + resample tap.

Sidechain: any dynamics/filter device exposes a sidechain input fed by a
`SidechainTap` on any track/group/return (pre/post fader), routed through the
graph as an extra edge (PDC-compensated).

**PDC:** every DeviceNode reports `latencySamples()`. Chain latency sums; the
graph computes per-path latency to master and inserts `DelayCompNode`s on shorter
parallel paths, including send/return and sidechain edges. Live-input monitoring
paths bypass PDC (low-latency monitoring), flagged in UI. PDC recomputes only on
rebuild.

---

## 4. Module map

One Gradle `:app` + one native `libdawcore.so`. Logical modules with one-way
dependencies (→ = may depend on):

```
Kotlin  ui/* → state holders → domain (+services) → engine bridge → JNI
        services → domain, data;  data → domain
C++     jni → engine → {graph, sequencer, media, timestretch} → device → dsp → core
```

### 4.1 C++ modules (`app/src/main/cpp/`)

| Module | Owns | Key classes |
|---|---|---|
| `core/` | RT primitives | LockFreeQueue (SPSC), RingBuffer, AtomicSnapshot, AudioBufferPool, SmoothedValue, ScopedNoDenormals, XorShift RNG, FixedVector/FixedString, RtAssert, EngineCommand, MeterFrame, NodeUid, ObjectHandle tables |
| `dsp/` | Pure DSP, no engine deps | Oscillator (polyBLEP), WavetableSet+WavetableOscillator, NoiseGen, SvfFilter, LadderFilter, BiquadFilter(+design), CombFilter, AllpassFilter, AdsrEnvelope, AhdEnvelope, MultiStageEnvelope, Lfo (shapes, S&H), EnvelopeFollower, DelayLine (interp), FftProcessor (pffft wrap), WindowedOverlapAdd, PartitionedConvolver, Resampler (windowed-sinc), Waveshaper, Upsampler2x/Oversampler, PitchDetectorYin, TransientDetector (spectral flux), GrainPlayer, FdnReverbCore, PsolaShifter, FormantShifter (WORLD-informed), DitherTpdf, LoudnessEbuR128, TruePeakDetector, CorrelationMeter, DcBlocker, StereoWidthProcessor |
| `device/` | Device platform | DeviceNode (abstract: prepare/process/reset/latency/params/save-state), InstrumentNode, EffectNode, MidiEffectNode, DeviceChain, ParamDescriptor+ParamRegistry, VoiceAllocator (poly/mono/legato/glide/steal), Voice base, MpeNoteState, ModMatrix (per-device), SidechainInput |
| `device/instruments/` | Spec §5 | SubtractiveSynth, WavetableSynth, FmSynth, MacroSynth (dual-engine morph), SimpleSampler (pitch/one-shot/slice), MultiSampler (zones/RR/vel layers), DrumPadSampler, DrumRackDevice, MalletModel, StringModel, EPianoModel, SampleLibraryPlayer (SFZ-subset import), ExternalInstrumentDevice |
| `device/effects/` | Spec §10 | Utility, SpectrumAnalyzerNode, TunerNode, LoudnessMeterNode, ChannelEq, ParametricEq8, PerformanceEq3, AutoFilter, Compressor, BusCompressor, GateExpander, Limiter (lookahead+TP), DeEsser, MultibandDynamics (3-band Linkwitz-Riley), TransientShaper, Saturator, MultistageDistortion, PedalDrive, AmpSim, CabSim, Bitcrusher, StereoDelay, EchoDevice (ducking, diffusion), FilterDelay3, GrainDelay, AlgorithmicReverb (FDN), HybridReverb (conv+algo), ChorusEnsemble, PhaserFlanger, AutoPanTremolo, PitchCorrector (YIN+PSOLA, scale-aware, MIDI-guided), PitchFreqShifter, Vocoder, ResonatorBank, SpectralProcessor (freeze/smear/shift), BeatRepeat, LooperDevice, NoiseReducer (offline+preview), DeClicker (offline) |
| `device/midi_effects/` | Spec §8.3 | RtArpeggiator, RtChord, RtNoteLength, RtPitch, RtRandom, RtScale, RtVelocity, RtCcControl, RtNoteEcho, RtExpressionControl, RtMidiLfo — all through MidiEffectNode with ActiveNoteRegistry (stuck-note prevention on bypass/reorder/stop) |
| `device/racks/` | Spec §11 | RackDevice (Instrument/Audio/MidiEffect variants), RackChain (zones: key/velocity/selector/**frequency band** — Linkwitz-Riley crossover split for multiband racks, Part 1 §13; crossfaded overlaps), MacroTable (mappings min/max/curve/polarity), VariationStore (macro snapshots, morph), ChainMixer |
| `graph/` | Mix topology | AudioGraph (node schedule), TrackNode, GroupTrackNode, ReturnTrackNode, MasterNode, TrackStrip (gain/pan laws, mute/solo matrix, **crossfader A/B assign** resolved against the global crossfader param — performance crossfading, Part 1 §18), SendNode/SendCollector, SidechainTap, RoutingTable (validated, cycle-free), DelayCompNode, PdcCalculator, MeterProbe, GraphBuilder, GraphSwapLatch, NodeStateRegistry, AudioGraphGc |
| `sequencer/` | Time & playback | TransportEngine, TempoMap, TimelineSnapshot (arr clips, session slots, notes, automation, warp refs — RT-readable), ArrangementPlayer, SessionPlayer (launch modes, legato, quantization), TrackPlaybackArbiter (per-track Session-overrides-Arrangement, return-to-arrangement; spec §1.1), FollowActionScheduler, LaunchQuantizer, MidiClipPlayer (notes+MPE+prob/deviation), AudioClipPlayer (warped/repitch, gain, fades), StepSequencerCore (ratchets, microtiming, per-step automation locks), GrooveEngine (templates, extract, amount), MidiScheduler (sample-offset event lists), MetronomeNode, PunchController, CaptureBuffer (retrospective MIDI capture ring), ArrangementRecorder (session-performance capture: launches, scene changes, mixer/param automation → edit ops), AutomationEvaluator, ClipEnvelopeEvaluator, ModulationEngine (LFO/follower/step/random/note sources), ParameterResolver (see §6) |
| `media/` | Files & samples | AudioFileDecoder (dr_wav/dr_flac/dr_mp3 + MediaCodec AAC), AudioFileWriter (WAV; FLAC via libFLAC; AAC/MP3 via MediaCodec), SampleCache (LRU, resident vs streamed), DiskStreamRing, PrefetchPlanner (playhead + armed launches), RecordingWriter (journaled WAV, recovery scan), PeakFileBuilder (multi-res waveform), AnalysisService (transient grid, tempo/key guess, pitch detect), SliceEngine (transient/beat/equal → slices) |
| `timestretch/` | Warp | WarpEngine (signalsmith-stretch adapter, per-clip instances), WarpMap (marker sample↔beat, linear segments), RepitchPlayhead, StretchPrewarmer (background large-ratio pre-render, Tracktion-style) |
| `engine/` | Facade | AudioEngine (owns everything, lifecycle), OboeDriver (out+in streams, route/rate change recovery, xrun counters), EngineCommandRouter, EngineModel (C++ mirror of edit state for builder), OfflineRenderEngine (faster-than-RT, tails, stems solo/mute matrix, quality mode), FreezeEngine, ResampleTap (track/group/return/master), CpuLoadMonitor, PanicController (all-notes-off, engine reset) |
| `jni/` | Boundary | NativeAudioBridge (JNI_OnLoad, method table), CommandCodec (flat binary command encoding), StateCodec (TimelineSnapshot/EngineModel deltas from Kotlin), CallbackDispatcher (event bus → JVM via pre-registered method ids, never from RT thread) |

### 4.2 Kotlin modules (`app/src/main/java/com/example/`)

| Module | Owns | Key classes |
|---|---|---|
| `domain/model/` | Edit model v2 (immutable, `kotlinx.collections.immutable`) | Project, TrackModel hierarchy (Audio/Midi/Drum/Group/Return/Master/External/Resampling roles as TrackKind + capabilities), ClipInstance (arr/session variants: position vs slot, launch mode/quant/follow, fades), ClipContent (MidiContent: notes+expression lanes+drumSteps; AudioContent: fileRef, warpMap, gain, pitch, slices) with contentId sharing + copy-on-unlink (spec §1.2 linked/unlinked property split), Scene (tempo/timeSig, follow), NoteModel (+MPE fields, probability, deviations), AutomationLane (points, curves, shapes, simplify/thin-out op, musical-time lock flag), ClipEnvelope, ModulationAssignment, DeviceModel tree (params, racks: chains/zones/macros/variations), TakeLane/Take/CompRegion, GrooveTemplate, RoutingModel (io/sends/sidechains), ScaleContext (project + per-clip), MarkerModel, TempoEvent/TimeSigEvent |
| `domain/store/` | UDF | ProjectStore (reducers per action family), ProjectAction (sealed families: Transport/Track/Clip/Note/Device/Rack/Automation/Modulation/Routing/Take/Scene/Groove/Project), UndoManager (gesture-scoped transactions, coalescing, bounded history), SelectionModel (track/clip/note/device/time — survives context switches, spec §15.1) |
| `engine/` | Bridge & sync | EngineController (lifecycle, foreground service + audio focus/route handling), EngineSync (ProjectState diff → commands or model-push + rebuild triggers), EngineReadback (meters/clock/events → flows), CommandEncoder (Kotlin side of codec), EnginePrefs (buffer size, latency calibration) |
| `data/` | Persistence & library | ProjectRepositoryV2 (project folder: project.json + media/ + peaks/ + takes/), ProjectSerializer (kotlinx.serialization, versioned migrations), AutosaveJournal (debounced ops log + snapshot), VersionStore (named versions/snapshots), CrashRecovery (journal replay + recording scan), DawDatabase/Room (project index, MediaLibrary index: samples/presets/kits/grooves, tags, favorites, recents), PackManager (download, verify, storage budget), MissingFileResolver, ProjectArchiver (portable bundle) |
| `services/` | App services | ExportService (mix/stems/tracks/loop/MIDI; format+dither+normalize+tails options), MidiDeviceService (USB/BLE discovery, input routing, clock/MTC out, MidiLearnMap), AudioDeviceService (interface/in-out selection, route-change policy), SyncService (Ableton Link adapter — D8, MIDI clock), ImportService (audio/MIDI/project import, sample collect), RenderJobQueue (freeze/bounce/noise-learn jobs → worker pool) |
| `ui/state/` | Per-surface holders | Existing 8 + ClipEditorStateHolder (piano roll deep: fold/scale/expression lanes/multi-clip), AudioEditorStateHolder (trim/warp/slice/fades), DeviceDetailStateHolder (per-device panels, A/B, presets), AutomationStateHolder (lane edit, modes: read/write/touch/latch, override), TakeCompStateHolder, PerformanceStateHolder (macros, xfader, momentary FX, lock), InputSurfaceStateHolder (keyboard/pads/chord/step modes, scale lock, MPE), BrowserStateHolder v2 (search/filter/preview-in-key/tempo), SettingsStateHolder (audio/MIDI/sync/storage), ProjectHubStateHolder (projects, versions, recovery) |
| `ui/screens/earth/` | Screens | Existing 8 + ClipEditorScreen, AudioEditorScreen, DeviceDetailScreen, TakeLanesScreen, PerformanceScreen, InputSurfaceScreen (overlay), SettingsScreen, ProjectHubScreen — all Earth.Design V2 morphic glass |
| `ui/components/earth/` | Reusable | Existing + WaveformView (peak-file backed), PianoRollGrid, StepGrid, AutomationLaneView, EnvelopeEditor, XYMorphPad, MacroKnobRow, TakeLaneRow, SpectrumView, LoudnessMeterView, KeyboardView/PadGridView/ChordPadView, ValueSlider (coarse/fine/numeric per spec §15.2) |

---

## 5. Parameter system (spec §11.3, §12, Part1 §17)

- Every automatable value is a `ParamId` = (NodeUid, paramIndex) with a static
  `ParamDescriptor` (range, curve, default, unit, smoothing-ms, rt-safe flag,
  randomize-excluded flag).
- Per-block resolution order (ParameterResolver):
  `base (edit model) → track automation (AutomationEvaluator) → clip envelopes
  (ClipEnvelopeEvaluator) → modulation offsets (ModulationEngine, additive
  bipolar/unipolar, never rewrites) → macro contributions (MacroTable) →
  SmoothedValue → DSP`.
- Automation override: touching a param sets override until "return to automation"
  (per-param latch state in TransportEngine).
- Automation recording modes read/write/touch/latch implemented in Kotlin
  (AutomationStateHolder + ArrangementRecorder events), engine only reports
  touched params with timestamps.
- Macro mapping/randomization/variation-morph resolve to plain param moves so
  recording macro movement as automation is free (spec §11.3).

## 6. Linked clips & content model (spec Part 2 §1.2)

- `ClipContent` is the shared entity (contentId). Session and Arrangement
  `ClipInstance`s reference it. Shared: notes/expression, audio ref, warp
  markers, loop content, clip envelopes, clip gain/pitch. Per-instance: position
  or slot, launch mode/quantization, follow actions, arrangement fades,
  per-instance overrides explicitly listed — nothing else.
- Unlink = deep-copy content with new contentId (copy-on-unlink; never duplicates
  audio files). ArrangementRecorder creates linked instances (spec §13.2).
- Consolidate/render creates new AudioContent + file (explicit destructive ops
  only, originals kept per spec).

## 7. Recording, takes, comping (spec Part 2 §14, Part 1 §6)

- RT input path: input callback → monitoring tap (per-track monitor mode) +
  `RecordRing` → RecordingWriter (journaled WAV: header fixed up on close;
  recovery scan finalizes orphans on next launch).
- Loop recording → Take per pass (audio: file regions; MIDI: note buffers).
  `TakeLane`/`CompRegion` live in the edit model; comp audio playback = region
  list with crossfades (AudioClipPlayer supports region-list sources).
- Retrospective MIDI capture: `CaptureBuffer` ring always records armed-track
  input; "Capture" materializes it as a clip with inferred tempo/grid.
- Punch in/out via PunchController; count-in via MetronomeNode; recording
  quantization applied as a non-destructive first edit (undoable).

## 8. Feature coverage matrix

### 8.1 SPEC_PART1_FUNCTIONAL (22 areas)

| Area | Owning modules / classes |
|---|---|
| 1 Project system | data/: ProjectRepositoryV2, ProjectSerializer, VersionStore, AutosaveJournal, CrashRecovery, ProjectArchiver, MissingFileResolver, ImportService; ProjectHubStateHolder/Screen |
| 2 Transport & song controls | sequencer/TransportEngine, TempoMap, MetronomeNode, PunchController, LaunchQuantizer (global quant), MarkerModel; TransportStateHolder, EarthTransportBar; tap tempo in TransportStateHolder |
| 3 Arrangement View | ArrangementPlayer, TimelineSnapshot, ArrangementRecorder; domain clip ops (split/join/ripple/insert-remove time reducers); ArrangementStateHolder/ArrangerScreen, AutomationLaneView, TakeLaneRow; FreezeEngine |
| 4 Session View | SessionPlayer, TrackPlaybackArbiter, FollowActionScheduler, LaunchQuantizer, Scene model; SessionStateHolder/SessionViewScreen; linked move/copy via ClipContent |
| 5 Track system | TrackModel hierarchy + reducers; graph/TrackNode family, TrackStrip, RoutingTable; MixerStateHolder; track presets via data/ presets |
| 6 Audio recording | OboeDriver input, RecordRing, RecordingWriter, monitoring modes, latency calibration (EnginePrefs), TakeLane/comping, CrashRecovery |
| 7 MIDI recording & editing | MidiClipPlayer, MidiScheduler, CaptureBuffer, StepSequencerCore; NoteModel+expression; ClipEditorStateHolder/Screen, PianoRollGrid; MIDI file I/O in ImportService/ExportService; MidiLearnMap |
| 8 Audio clip editor | AudioContent ops (trim/split/reverse/normalize/gain/fades/silence), TransientDetector, WarpEngine+WarpMap, SliceEngine, consolidate/render via OfflineRenderEngine; AudioEditorStateHolder/Screen, WaveformView |
| 9 Drum Rack | DrumRackDevice, DrumPadSampler, choke groups, pad routing; StepSequencerCore (velocity/prob/ratchet/microtiming/step automation); SamplerDrumLabScreen + StepGrid; kit save via presets |
| 10 Sampler | SimpleSampler, MultiSampler, SliceEngine, pitch detect (AnalysisService), loop crossfades, time-stretch; DeviceDetailScreen panels |
| 11 Instruments | device/instruments/* (13 devices, §4.1); common behavior via DeviceNode+VoiceAllocator+ModMatrix+MacroTable (spec Part2 §4 checklist). The "drum synthesizer" family is served by DrumPadSampler's synthesis playback modes (sub, noise, FM, ring-mod, bit-reduce — Part 2 §5.7); LooperDevice can set project tempo from a recorded loop (Part 2 §10.10) |
| 12 Browser & library | MediaLibrary (Room), PackManager, preview (in-key/tempo-synced via WarpEngine preview path), favorites/recents, MissingFileResolver; BrowserStateHolder v2/SoundBrowserScreen |
| 13 Device chain & racks | DeviceChain, RackDevice family, MacroTable, VariationStore; DeviceRackStateHolder, DeviceDetailStateHolder; copy/save presets; freeze/render via RenderJobQueue |
| 14 Mixer | TrackStrip, SendNode, groups, RoutingTable, SidechainTap, cue bus (AudioDeviceService outputs), MeterProbe/MeterBus, track delay (per-track offset in strip), phase invert, mono check (Utility on master); MixerStateHolder/MixerScreen |
| 15 Effects | device/effects/* full catalog (§4.1) |
| 16 Routing | RoutingTable (validated graphs, cycle refusal), MidiTapBus, ResampleTap, sidechains, pad routing, cue mix; routing UI in MixerScreen detail |
| 17 Automation & modulation | AutomationLane/Evaluator, ClipEnvelope/Evaluator, ModulationEngine + ModulationAssignment, macro mapping, MIDI-learn to params; AutomationStateHolder, EnvelopeEditor |
| 18 Performance | SessionPlayer + scenes, PerformanceStateHolder/Screen (clip pads, macro knobs, xfader groups, momentary FX via VariationStore, edit lock), CaptureBuffer, SyncService (Link/MIDI clock) |
| 19 Mixing & mastering | Mastering chain on MasterNode (ParametricEq8, BusCompressor, Saturator, StereoWidth, Limiter TP), LoudnessMeterNode/LoudnessEbuR128, SpectrumAnalyzerNode, reference compare (A/B source in MasteringSuiteScreen via AudioClipPlayer), streaming loudness targets + DitherTpdf in ExportService |
| 20 Export & sharing | OfflineRenderEngine + ExportService (mix/stems/tracks/groups/loop/MIDI, WAV/FLAC/AAC/MP3, SR/bit-depth, normalize, tails, master on/off), ProjectArchiver, SAF/share intents |
| 21 Hardware & connectivity | OboeDriver (USB interfaces, multi-I/O where supported), MidiDeviceService (USB/BLE, clock in/out), SyncService (Link), sustain/expression via MIDI mapping, AudioDeviceService (routes, cue), background audio via foreground service (EngineController) |
| 22 Reliability | AutosaveJournal, CrashRecovery, RecordingWriter journaling, UndoManager, VersionStore, MissingFileResolver, storage/CPU warnings (CpuLoadMonitor + EngineEventBus), offline-first (no network deps in core) |

### 8.2 SPEC_PART2_WORKFLOW key sections

| Section | Owning design |
|---|---|
| §1.1 Session/Arrangement rule | TrackPlaybackArbiter (per-track override, return per-track/global) |
| §1.2 Linked clips | ClipContent model (§6) |
| §2 Workflow paths | Covered by areas 1–14 above; entry templates in ProjectRepositoryV2 |
| §3 Signal flow | §3 of this blueprint (per-track pipelines, taps, PDC) |
| §4 Common instrument behavior | DeviceNode contract + VoiceAllocator + preset system + A/B (DeviceDetailStateHolder) + CPU quality modes (per-device QualityMode param) |
| §5 Instruments 5.1–5.12 | SubtractiveSynth, WavetableSynth, FmSynth, MacroSynth, SimpleSampler, MultiSampler, DrumPadSampler, DrumRackDevice, Mallet/String/EPiano models, SampleLibraryPlayer, ExternalInstrumentDevice, RackDevice |
| §6 Onscreen input | InputSurfaceStateHolder/Screen (keyboard/scale-lock/isomorphic/pads/chords/fretboard/step; velocity, bend, MPE, note repeat, fixed velocity, simultaneous external MIDI) |
| §7 MIDI recording/editor | Area 7 above + note properties (NoteModel MPE fields), piano-roll ops list in ClipEditorStateHolder reducers, drum editor via StepSequencerCore |
| §8.1 Transformations (15) | MidiTransformOps (pure Kotlin domain functions on NoteModel selections, preview via temporary clip content; Arpeggiate/Chop/Connect/Glissando/ExpressionLFO/Ornament/Quantize/Humanize/Recombine/Span/Strum/TimeWarp/VelocityShaper/TransposeScale/InvertReverse) + StepEditOps for the drum editor (fill every 2/3/4/N, rotate lane, randomize selected properties with intensity — spec §7.4) |
| §8.2 Generators (7) | MidiGeneratorOps (Rhythm/Seed/Shape/ChordStack/Euclidean/Bassline/Variation) — same preview/commit contract |
| §8.3 Realtime MIDI effects (11) | device/midi_effects/* with ActiveNoteRegistry; output recordable via MidiTapBus |
| §9 Scale, groove, MPE | ScaleContext (project/clip), GrooveEngine + GrooveTemplate + extraction, MPE record/edit lanes + conversion (MpeNoteState, expression lanes) |
| §10 Audio effects (all) | device/effects/* catalog incl. common-behavior contract (bypass ramps, latency reporting, sidechain, oversampling flags, A/B, metering) |
| §11 Chains & racks | DeviceChain ops, RackDevice zones/macros/variations, nested racks (D4 depth 3) |
| §12 Automation & modulation | §5 parameter system; clip envelopes loop-independent lengths; Session→Arrangement automation conversion in ArrangementRecorder |
| §13 Session→Arrangement | ArrangementRecorder (launch/scene/mixer/tempo capture → linked instances + automation), consolidate-section-to-scene op |
| §14 Comping/bounce/resample | §7 + OfflineRenderEngine bounce matrix (clip/selection/track in-place/track new/group), FreezeEngine, ResampleTap targets |
| §15 Phone translation | Focused contexts = screens sharing SelectionModel; audio never stops on navigation (engine lifecycle ≠ UI); ValueSlider coarse/fine/numeric; progressive depth = essential vs full panels per device; Eco/Standard/High QualityMode params; CpuLoadMonitor + thermal hooks; interruption handling in EngineController (focus loss → graceful stop, route change events, autosave); rotation-safe state (holders + SavedState) |
| §16 Acceptance scenarios | Adopted as milestone definitions-of-done (§9) |
| §17 Open decisions | Resolved as D1–D12 |

Gap check: every numbered spec section above has at least one named owner; drum
editor lane auto-creation → StepSequencerCore + SamplerDrumLab UI; "record with
or without effects" → RecordTapPoint; "compare with reference audio" →
MasteringSuiteScreen A/B path; "prevent feedback" → RoutingTable validation;
"safe recording when storage low" → RecordingWriter budget check + events.

## 9. Build order (milestones; scenario-mapped)

- **M0 Engine foundation:** core/, dsp/ first wave (osc/filters/envelopes/LFO/
  delay line/FFT), OboeDriver, AudioEngine skeleton, command queue, JNI codec,
  CMake + build wiring. *(No sound features yet; foundation only.)*
- **M1 Model v2 + timeline:** Kotlin domain/model + store rewrite (keeping UDF
  shape), TempoMap, TimelineSnapshot, EngineModel/EngineSync, TransportEngine.
- **M2 Graph & mixer:** graph/* (tracks, strips, sends/returns/groups, routing,
  PDC, meters), MixerScreen wiring.
- **M3 Device platform:** DeviceNode/chains, ParamRegistry/Resolver, VoiceAllocator,
  presets, racks/macros/modulation core.
- **M4 First sound:** SubtractiveSynth + MidiClipPlayer + SessionPlayer minimal +
  metronome (scenario: play a clip). Then WavetableSynth, FmSynth.
- **M5 Drums & sampling:** DrumRack + DrumPadSampler + StepSequencerCore +
  SimpleSampler + SliceEngine (scenario 16.1 beat-from-scratch).
- **M6 Audio tracks & recording:** media/ (decode/cache/stream), AudioClipPlayer,
  recording path, takes/comping (scenario 16.3 vocal minus FX polish).
- **M7 Warp & audio editing:** timestretch/, AudioEditor, groove.
- **M8 Effects wave 1 (mix):** EQs, dynamics, delays, AlgorithmicReverb, utility,
  meters. **M9 wave 2 (color):** saturation/dist/amp, modulation FX, AutoFilter.
  **M10 wave 3 (advanced):** HybridReverb, pitch/vocal, spectral, BeatRepeat,
  Looper, cleanup.
- **M11 MIDI tools:** transformations + generators + realtime MIDI effects +
  InputSurface (scenario 16.2).
- **M12 Session depth:** follow actions, scenes tempo, ArrangementRecorder
  (scenario 16.4), PerformanceScreen.
- **M13 Automation/modulation UI**, clip envelopes, MIDI learn.
- **M14 Browser/library/packs**, remaining instruments (MacroSynth, MultiSampler,
  physical models, SampleLibraryPlayer, External).
- **M15 Export/mastering** (scenario 16.5), freeze/bounce matrix.
- **M16 Hardware/sync/reliability hardening** (scenario 16.6), interruption/
  rotation audits, storage/CPU guards.

Each milestone ends: ARCHITECTURE.md updated, data-flow walk, commit.

## 10. Key decisions & rejected alternatives

| Decision | Rejected alternative | Why |
|---|---|---|
| Dual model + rebuild/swap | Mutating one shared graph under locks | RT safety; proven by Tracktion/Live-class engines |
| NodeStateRegistry migration on swap | Reset state on rebuild | Audible tail/voice drops on every edit — unacceptable |
| Commands for params, rebuild for structure | Everything as commands | Structural edits under RT constraints breed heisenbugs; rebuild is simpler and bounded |
| Immutable Kotlin model + persistent collections | In-place mutable model with listeners | Undo/redo, autosave diffing, thread hand-off all become trivial |
| Meters/playhead bypass ProjectStore | All state through the store | 60 Hz × 64 tracks through reducers melts UI; store stays semantic |
| signalsmith-stretch | RubberBand / SoundTouch | MIT vs GPL/commercial; quality vs artifacts at extremes |
| Single :app + logical modules | Multi-module Gradle now | No compile step for months; split later is mechanical |
| Fixed capacities (D4) | Dynamic everything | Pre-allocation is the RT contract; caps are phone-honest |
| SFZ-subset for multisample import | Custom-only format | Spec asks for "standard non-proprietary mappings" |
| Offline noise reduction (D11) | RT NR at launch | Phone CPU budget; spec allows offline render |

## 11. Third-party inventory (license-vetted)

| Library | Use | License |
|---|---|---|
| Oboe | Audio I/O | Apache-2.0 |
| signalsmith-stretch | Warp/pitch | MIT |
| pffft | FFT | BSD-like |
| dr_wav / dr_flac / dr_mp3 | Decode | public domain / MIT-0 |
| libFLAC | FLAC encode | BSD |
| Android MediaCodec | AAC/MP3 encode, AAC decode | platform |
| WORLD (or derived formant code) | Formant/pitch analysis | modified-BSD |
| Ableton Link | Sync (D8, flagged) | GPLv2 / commercial |
| kotlinx.serialization / collections.immutable | Model | Apache-2.0 |
| AMidi / android.media.midi | MIDI I/O | platform |

No GPL code ships unless the Link decision is made explicitly.
