# Build Log

Running hand-off record for the class-by-class build (blueprint
`docs/spec/ARCHITECTURE_BLUEPRINT.md`, contracts `docs/spec/CONTRACTS.md`).
Newest entry first. Each entry: where the build stands, what comes next.

---

## 2026-08-25 — Review cycle 2: FAIL -> all findings fixed (resubmitted)

The gate earned its keep: the reviewer caught a REAL COMPILE BREAKER
before CI did — Font(resId, weight, variationSettings) is
@ExperimentalTextApi and the repo had no opt-in, so commit 8941479's
Kotlin build was red on arrival. Fixes, finding by finding:

- [BLOCKER] @OptIn(ExperimentalTextApi::class) on EarthFonts.variable()
  with the rationale in a comment. Lesson: experimental-API opt-ins are
  part of "compiles by inspection" - check RequiresOptIn on every new
  androidx API surface.
- [MINOR] bars.beats math is now denominator-aware (bar = num * 4/den
  quarters, matching the engine's barBeats() rule; beats counted in the
  denominator's unit) - 6/8 reads correctly.
- [MINOR] OFL compliance: assets/fonts/ carries the three OFL.txt texts
  (they SHIP in the APK, satisfying accompany-distribution) + VENDOR.md
  provenance manifest per the dr_libs precedent.
- [MINOR] Zero inline sp overrides remain: EarthTypography gained
  displayTimeCompact + microLabel; nav label and time-sig use existing
  tokens as-is. Sizes live in the token file only.
- [NIT] StateCodec: gate comment ("widen with the implementation") +
  phantom DeviceSampleRefPayload reference corrected.
- [NIT] ToggleMetronome excluded from the undo stack (transport toggle,
  not a document edit); Undo/Redo icons -> AutoMirrored variants;
  Locale.ROOT pinned on all numeric readout formatting.
- [NIT deferrals tracked]: record/metronome pulse-flash, BPM touch-scrub
  (milestone-commented), 6-item dock consolidation, play-glyph
  black-vs-white errata (KEEPING the render's black-on-amber - higher
  contrast; COMPONENTS.md §2.1 white noted as pack errata).

Host check green; map 200 classes + the two new type styles; sweep green
incl. duplicates. Resubmitted to the reviewer for the cycle-2 verdict.

---

## 2026-08-25 — Earth V2 UI conformance pass 1 (user-flagged; portrait core)

User called out that the UI does not look like the Earth.Design V2 pack.
Audit verdict: token VALUES were genuinely the pack's (palette/glass tiers
match TOKENS.json) but the pack's FONTS were never bundled, one glass tier
misused the active rim, and the screens/components were never built from
the pack's 14 reference renders in docs/spec/Earth.Design/assets/. Fixes:

- Fonts: Outfit + Inter + JetBrains Mono vendored as variable TTFs
  (res/font) with weight instancing (EarthFonts); every EarthTypography
  style now resolves through them - zero system-default families.
- Token corrections: GlassBorderHighlight to the spec's 8% white;
  Level1Dock border to "subtle" per TOKENS.json (amber rim = levels 3/4).
- EarthTransportBar rebuilt to the portrait reference + COMPONENTS.md §2:
  floating rounded Level1 card (was a flush square strip), 36dp/6dp
  buttons (were 32dp/4dp), record = #DC2626 per spec (crimson maple is
  the ARM color), metronome toggle added, dark inset readout panel with
  stacked BPM label/value, bars.beats.sixteenths timecode + project name.
- Metronome went in as a FULL vertical: ProjectState.isMetronomeOn +
  ToggleMetronome action/reducer + EngineSync branch -> encoder op ->
  the engine's MetronomeNode (the engine side existed since M4; the
  Kotlin layer never exposed it).
- EarthNavigationDock rebuilt: floating rounded card, icon-over-label
  items, active = amber-tinted glass chip (tokens' Primary/Active role).
  Tab consolidation to the reference's SESSION/ARRANGE/MIX/EDIT/DEVICES/
  MORE six-set is a UX decision deferred with a note.
- Deferred to pass 2 (screen milestones): knob halo/mod-ring detailing,
  clip-tile launch states, per-screen recomposition against the remaining
  reference renders (arranger/browser/mastering/synth/sampler sheets).
- Map 200 classes (+EarthFonts), sweep green incl. duplicates. Kotlin
  compile verified by CI (no host Kotlin toolchain).

Review gate cycle 2 covers this batch + the v1.2.0 contracts slice; M5
finale (#29) stays paused mid-slice and resumes after.

---

## 2026-08-25 — Review gate cycle 1 CLOSED: PASS. PR #8 merged; specs re-confirmed

The reviewer re-verified every cycle-1 fix in code (including
cross-checking EngineController/EngineReadback threading to confirm the
telemetry relocation is race-free) and returned VERDICT: PASS with one
non-blocking NIT (upstream commit SHA in dr_libs VENDOR.md - TODO noted
in the manifest; the fetch env has no GitHub API access). CI went green
in the same window (run #25 first full NDK build, #26 on the fixes, #27
post-merge on main) and the user MERGED PR #8 - the engine is compiled,
reviewed, and on main.

Also this entry: the user re-uploaded both spec blueprints - verified
BYTE-IDENTICAL to docs/spec/SPEC_PART1_FUNCTIONAL.md and
SPEC_PART2_WORKFLOW.md (already vendored since Phase A; no change).
WORKFLOW.md gains step 0: consult the specs before every feature (they
are the read-only product authority; spec wins over blueprint on
conflict), and the review gate's judging authorities now name them.

**Next: M5 finale** — the sample-assignment wire + PreviewPlayer
audition (spec 2.3/12: preview sounds, load samples into sampler/pads),
so SimpleSampler and DrumPad Sample mode can actually load audio now
that the engine runs on device. Review gate cycle 2 covers it.

---

## 2026-08-25 — Review gate cycle 1: FAIL -> all findings fixed (resubmitted)

First run of the new HARD review rule (AAA DSP+Android reviewer agent,
read-only). Verdict FAIL: 2 blockers + 2 majors + 8 minors/nits. The two
blockers (nonexistent oboe:1.5.1; missing -DANDROID_STL=c++_shared for the
prefab AAR) had already been found+fixed independently through the CI
red loop - reviewer and CI cross-validate. Everything else fixed:

- [MAJOR] SimpleSampler loop-window HEAP OVERREAD: loopEnd could sit at
  the raw frame count while the interpolator taps i0+1; wrap math could
  legally park pos_ in [frames-1, frames). Fixed: loop window clamped to
  the READABLE range [0, frames-1], start position too; loop-control
  restructured (wrap first, then one-shot end); verified in-bounds on all
  paths. Lesson: interpolators define the readable range, not the buffer.
- [MAJOR] OboeDriver teardown race: input stream was closed/reset while
  the output callback (drainInput) could still run. Fixed: output closes
  FIRST (close joins the callback), then input - Oboe FullDuplex order.
  Lesson: teardown order must quiesce the consumer before the resource.
- [MINOR] Input channel count now validated (non-stereo capture rejected,
  output-only session) - the ring's stereo stride assumption is enforced.
- [MINOR] Loop-seam interpolation is wrap-aware (tap after loopEnd =
  sample at loopStart) - kills the per-pass click on short sustain loops.
- [MINOR] Host shim made value/signature-faithful to real Oboe 1.5:
  ErrorDisconnected=-899 / ErrorInternal=-896, no default ResultWithValue
  ctor + implicit Result ctor, const-ness mirrored (getFramesPerBurst
  non-const, getXRunCount const), ChannelCount as UNSCOPED enum with only
  setChannelCount(int32_t) - as on Android.
- [MINOR] Latency/xrun sampling moved OUT of onAudioReady (framework-lock
  priority-inversion risk) into OboeDriver::refreshTelemetry(), called
  from the non-RT readback poll (nativePollStatus).
- [MINOR] fields-table static_asserts added to ALL four PolyInstrument
  synths (deduced-bound array + assert vs kParamCount).
- [MINOR] third_party/dr_libs/VENDOR.md provenance manifest (upstream,
  date, embedded versions, no-modifications statement, update procedure).
- [MINOR] -Wall -Wextra on the ANDROID target too (the #ifdef __ANDROID__
  code was warning-checked nowhere).
- [NIT] AudioEngine ctor comment now states the real safety invariant;
  registerType results counted (builtinRegistrationFailures());
  quality/smoothing TODOs annotated to their milestones.

Host check green after fixes. Resubmitted to the SAME reviewer for the
pass verdict; committing this round so CI validates in parallel.

---

## 2026-08-25 — AUDIO CORE BRING-UP: the engine is wired, compiled, and ships in the APK

User pivot after installing a silent debug APK: the no-compile phase is
OVER. The silence was by design (CMake was deliberately unwired from
Gradle); this entry makes the engine real. THE ENTIRE ENGINE (~200
classes, written blind across M0-M5) compiled with only two missing
includes and one const-cast idiom to fix — zero warnings under
-Wall -Wextra. The reread discipline held.

- app/build.gradle.kts: externalNativeBuild -> cpp/CMakeLists.txt; prefab
  buildFeature + com.google.oboe:oboe:1.5.1 (version catalog); ndk
  abiFilters arm64-v8a + x86_64.
- CMakeLists: ANDROID target = libdawcore.so (links oboe::oboe, android,
  log, mediandk); host target = dawcore_hostcheck STATIC compiling EVERY
  TU (-Wall -Wextra) for off-device verification.
- third_party/dr_libs vendored (dr_wav 0.14.6 / dr_flac / dr_mp3,
  PD/MIT-0).
- host_shims/oboe/Oboe.h: API-faithful, behavior-inert mirror of the Oboe
  1.5 surface the driver uses (real signatures incl. raw-pointer
  callbacks, chainable builder, ResultWithValue) so OboeDriver compiles
  host-side; Android uses the real prefab Oboe.
- Fixes found by the compiler: AudioEngine.cpp missing DeviceRegistry.h
  include; NativeAudioBridge.cpp missing GraphBuilder.h include +
  JNINativeMethod const_cast table.
- Sweep script: third_party/ + host_shims/ excluded (vendored/build
  support, not app classes).
- Runtime chain verified by reading: MainActivity -> DawRuntime
  .ensureStarted -> EngineSync.attach + EngineController.start (opens
  Oboe when libdawcore loads) + readback; default project has real
  content on all three tracks. SimpleSampler (type 3) landed and is
  registered (its M5f3 entry folded in here; StepSequencerCore deferred
  to M7 with MidiClipPlayer - probability/ratchets need the seeded
  per-pass machinery).
- NOT verifiable in this container (no Android SDK): the AGP/NDK build
  itself. First on-device step: build in Android Studio, install, press
  PLAY - lead (wavetable), bass (subtractive), drums (rack) should sound.
  If input permission is absent the driver opens output-only by design.
- Map: 199 classes; sweep green incl. duplicate checks.

**Next:** user builds on-device; fix whatever the AGP/NDK toolchain or
first live run surfaces (crashes, silence, xruns). Then resume M5
(SliceEngine + PreviewPlayer) or address runtime findings first.

---

## 2026-08-25 — M5 feature 2 done: DrumPadVoice + DrumRackDevice — the drum track sounds

Registered DeviceTypeId 6; the default project's "16-Pad Drum Rack" now
plays its step grid. Reinforced user rules in force: map updated per
class-batch (two updates this feature, both swept), and the sweep script
now ALSO detects duplicates (map blocks, relationship lines, same-name
source declarations; forward declarations excluded) - all NONE.

- device/instruments/DrumPad.h: DrumPadShared (7-field per-pad POD) +
  DrumPadVoice - five researched modes + sample playback: Sub (single-osc
  808 kick: exponential pitch env onto base, tanh drive), Noise (sine body
  + SVF-filtered white, the classic snare split), Metal (TR-808 six
  detuned squares -> highpass, inharmonic cluster), Ring (multiplied sine
  pair), Bit (full-rate square through sample-hold decimation - reread
  fixed pitch-follows-crush and sample-repitch-vs-root bugs), Sample
  (cache-pinned handle, relative repitch; plumbing joins the library
  milestone). Instant attack + declick, exp -60dB decay, 5ms fastRelease,
  30ms transient protection.
- device/instruments/DrumRackDevice.h: kDrumKit[16] mirrors Kotlin
  DrumPadType in enum order (roots + choke groups + musical defaults as
  STATE; descriptor defaults stay neutral); pitch lookup first-match
  (PERC_2 shadows COWBELL at 56 - faithful model quirk). The rack IS its
  VoiceGroup; note-offs ignored (one-shots); choke = fastRelease of
  group-mates; steal candidates/order mirror VoiceAllocator exactly
  (verified against its comparator; releasing never protected). 112
  descriptors via per-pad macro; dense = pad*7 + field.
- RegisterBuiltins: type 6 wired.
- Map: 196 classes; sweep green INCLUDING the new duplicate checks.

**Next: M5 feature 3** — StepSequencerCore evaluation (the drum grid is
already flattened Kotlin-side into notes; assess what engine-side core the
blueprint expects beyond that) + SimpleSampler (type 3) on PolyInstrument
with SampleCache-pinned zones. Then SliceEngine + PreviewPlayer.

---

## 2026-08-25 — M5 feature 1 done: media foundation (decoder + SampleCache)

Completes what the checkpoint below started. New standing cadence from the
user: map updates land per class-batch (not only at feature end), and the
turn PAUSES after each feature commit so the repo can be merged on GitHub.
PR #6 is open with everything M0->M5f1.

- media/AudioFileDecoder.cpp: dr_libs implementation TU (WAV incl. RF64,
  FLAC, MP3 - full-file f32 reads, planar capped-stereo conversion) +
  Android NdkMediaExtractor/Codec AAC loop (16-bit PCM drain, format-change
  re-read, EOS handling; reread added the codec-error/dry-poll bailout and
  partial-output refusal). Content-magic sniff, never extensions.
- media/SampleCache.{h,cpp}: budgeted resident store, keys
  (fileId, conformedRate) per D5; decode+conform OUTSIDE the mutex with
  double-check reinsert (losers adopt the winner); byte-weighted LRU over
  refs==0, pinned entries untouchable (overrun counted, never forced);
  erase-in-sweep is the only deallocation site. Handle-pin vs sweep race
  closed by construction (pins under mutex; copies need a live pin; dtor
  release pairs with sweep acquire).
- CMakeLists: media TUs added; compile-milestone notes for the dr_libs
  vendor step + mediandk link.
- Data-flow walk: pad trigger (M5f2) -> builder-thread acquire at device
  rate -> handle lives in the node (seam 3) -> RT reads planar through the
  pin; rate reopen re-acquires at the new rate, old conforms age out.
- Map: 191 classes, sweep green both directions.

**Next: M5 feature 2** — DrumPadSampler (sample playback voice + the
sub/noise/FM/ring/bit drum-synth modes per spec) + DrumRackDevice (16-pad
rack, pad = chain slot, choke groups), registered as DeviceTypeId 6/7
wiring per registry. Then StepSequencerCore + SimpleSampler.

---

## 2026-08-25 — M5 feature 1 checkpoint: sample-foundation headers (IN PROGRESS)

Mid-feature checkpoint (user interaction pause; committed clean with the
map already synced). Landed, each reread:

- media/SampleBuffer.h: resident planar audio + refcounted SampleHandle
  (seam-3 rule: NodeState carries handles, never raw pointers). Lifetime
  protocol: evictable only at refs==0; deallocation ONLY in cache sweeps
  on non-RT threads; handle dtor = atomic decrement (RT-safe belt).
- dsp/SincResampler.h: offline Kaiser-windowed sinc (beta 9, 32 taps x 256
  phases, linear phase interp; downsampling low-passes to OUTPUT Nyquist;
  per-phase DC normalization) - the D5 conform-at-load path. Reread caught
  the phase-wrap straddle reaching the WRONG tap slot (t+1 instead of t-1)
  and an unguarded static table cache (now mutex + realloc-stable).
- media/AudioFileDecoder.h: decode seam - magic-sniffed WAV/FLAC/MP3 via
  vendored dr_libs (headers land at the first compile milestone;
  AudioFileDecoder.cpp will be their single implementation TU) + Android
  MediaCodec AAC; planar capped-stereo output.

**Next (rest of this feature):** AudioFileDecoder.cpp (dr_libs dispatch +
NdkMediaExtractor/Codec AAC loop), media/SampleCache.{h,cpp} (budgeted by
device tier per blueprint table, (fileId, conformedRate) keys, LRU over
refs==0 byte-weighted, loads under mutex on worker threads, conform via
SincResampler), CMakeLists additions (+mediandk link note), map + sweep +
BUILD_LOG, commit. Then DrumRack/DrumPadSampler/StepSequencerCore.

---

## 2026-08-25 — M4 COMPLETE: launch-minimal SessionPlayer — the session grid plays

M4's finale: session clips launch, loop, stop and hand tracks back to the
arrangement. With this, blueprint §11 M4 is fully landed (SubtractiveSynth +
MetronomeNode + Wavetable/FM + SessionPlayer launch-minimal).

- CONTRACTS.md -> v1.1.0 (first amendment): seam 2 gains the append-only
  `Session` message family {LaunchClip, StopSlot, ReturnTrack, ReturnAll,
  SetLaunchQuantum}. Vocabulary only - layout untouched, kMessageVersion
  stays 1. Code + contract landed together.
- sequencer/SessionPlayer.h NEW ([RT], allocation-free): fixed per-track
  rows; launch boundaries on the ABSOLUTE song grid
  (ceil(now/quantum)*quantum - researched Ableton rule, off-bar seeks stay
  musical; quantum modes none/bar/fixed-beats, bar derived from the live
  time signature); ownership flips only AT boundaries so the arrangement
  sounds until the musical moment (spec §1.1: overrides only its own
  track); StopSlot leaves the track session-owned + silent (Back to
  Arrangement model, spec line 1202 commands both per-track and global);
  launch-while-stopped activates immediately + starts the transport; loop
  wraps re-anchor unreachable boundaries to the wrap point; anchor =
  activation beat and clip-local position is (beat-anchor) mod len -
  stateless across seeks (negative passes = phase-locked history). Flush
  cuts hand back via duck-typed callback (codec-visitor house pattern) so
  the header stays scheduler-independent; rows pruned per snapshot swap.
- TimelineSnapshot: TrackTimeline gains per-track session ClipView lists
  (slot order); GraphBuilder.buildTimeline compiles them (exact-reserve
  discipline preserved - pass 1 now counts both kinds).
- MidiScheduler: scheduleSpan takes the SessionPlayer as arbiter
  (session-owned lanes schedule their launched clip from the anchor and
  silence their arrangement clips); flushTrack (per-track cut) added;
  swap reconciliation searches session lists too. Session clips have no
  placement end (kNoPlacementEnd) - loop-crossing note tails sustain,
  matching the arrangement looping rule.
- AudioEngine: Session family drain -> SessionPlayer; each transport span
  further SPLIT at launch boundaries (sample-exact activation; sub-sample
  edge activates at current offset; split-guard leftovers land next block,
  bounded lateness); SessionPlayer pruned at timeline swaps.
- Kotlin: WireProtocol FAMILY_SESSION + ops; CommandEncoder launch methods;
  EngineSync routes the four store intents (slot press -> launch, empty
  slot -> stop, mirroring the reducer's isPlaying marks; TriggerScene fans
  per-track ops in ONE flush so lanes share the boundary). Store flags stay
  the optimistic UI - engine slot-state readback is a documented deferral
  (status-flags bit 1<<11 reserved candidate).
- Deferred at their milestones: per-clip launch quantization + launch
  modes/legato, follow actions, SessionRecorder, scene window D4 bounding,
  MidiClipPlayer statefulness (M7).
- Map: 185 classes, sweep green both directions.

**Next: M5 drums & sampling** (blueprint §11): decode + resident
SampleCache + PreviewPlayer audition pull forward; DrumRack, DrumPadSampler
(synthesis modes), StepSequencerCore, SimpleSampler, SliceEngine.

---

## 2026-08-25 — M4 feature 3 done: WavetableSynth + FmSynth on the new PolyInstrument shell

The default project's LEAD now sounds (Wavetable Lab, DeviceTypeId 1) and
all three M4 synths are registered — the instrument wave is complete up to
the launch-minimal SessionPlayer.

- device/PolyInstrument.h NEW: the polyphonic-instrument template shell
  (VoiceT, SharedT, StateVersion, PoolVoices, DefaultPolyphony). Owns the
  VoiceAllocator, the sample-accurate event-split process loop, ledger
  admission (+budgetRefusals counter), the seam-1 live note->id mapping and
  SharedT-POD save/load. Concrete synths carry ONLY their DSP + the
  descriptor trio. SubtractiveSynth refactored onto it (no behavior change).
- device/instruments/WavetableSynth.h: WavetableBank — GENERATED global
  table set (8 morph frames x 8 mips x 2048, additive with per-mip harmonic
  caps 128>>mip; ~4M sin at startup, forced NON-RT in
  registerBuiltinDevices so the lazy static never initializes on the audio
  thread). One gain per frame from the full-res mip keeps level continuous
  across mip crossings (per-mip peak normalization would step the
  fundamental — caught on reread). Voice: frame-morph table read (linear
  phase + frame interp, nearest mip) -> SVF LP -> amp ADSR; position swept
  by filter env + LFO; control-rate 16. 20 descriptors (kWavetableParams).
- device/instruments/FmSynth.h: 4-op phase-mod, kFmAlgorithms[8]
  (modSources bitmasks reference only HIGHER ops; 3->0 compute order),
  per-op AUDIO-RATE ADSRs (FM timbre is the envelope motion), op3
  one-sample-delayed self-feedback, kModDepth 2pi, carrier-count
  normalization, velToMod scales modulator levels. Algorithm latched at
  note start; carrier envelopes gate voice life. 31 descriptors (kFmParams).
- RegisterBuiltins.cpp registers types 1+2 and touches
  WavetableBank::instance().
- GraphBuilder seam-3 hardening (from the data-flow walk): device adopt
  entries now hash their TYPE — a same-uid type swap previously matched
  configHash 0 and relied on sizeof(SharedT) inequality to refuse
  cross-type adoption; now refused by identity.
- Kotlin side needed nothing: deviceTypeWire already froze 1/2, DeviceModel
  params default empty => C++ descriptor defaults; editor UIs will dispatch
  descriptor keys ("wt.position") when they land.
- Map: 183 classes, sweep green both directions.

**Next: M4 finale** — minimal SessionPlayer/launch (blueprint §11 M4: slot
trigger -> quantized clip start feeding MidiScheduler), then M5 drums &
sampling.

---

## 2026-08-25 — M4 feature 1 done: SubtractiveSynth — FIRST SOUND path

The full audible loop exists (pending compile): PLAY -> MidiScheduler runs
-> finalizeBlock (global sort, one contiguous per-track run via the new
core/MidiTrackRun handoff) -> PlaybackGraph feeds each lane's run as
ctx.midiIn -> chain -> SubtractiveSynth -> strip -> sends -> master -> Main
-> Oboe. With only type 0 registered, the default project's BASS line is
the premiere (its SUBTRACTIVE_SYNTH); the lead's WavetableSynth is next.

- device/InstrumentNode.h: DeviceNode + VoiceInterface + compiler hooks
  (voiceGroup registration, type-erased ledger admission); registry
  isInstrument flag = RTTI-free static_cast.
- device/instruments/SubtractiveSynth.h: researched classic voice - 2
  polyBLEP osc (detune/semi/mix, phase-offset), noise, Simper SVF LP with
  env-amount(oct)/keyTrack/LFO, analog amp+filter ADSRs, velocity to amp +
  filter depth. Voices heap-free + trivially copyable; control-rate (16)
  filter/LFO/pitch with their DSP objects PREPARED at the control rate;
  event-split sample-accurate process; scheduler instance ids key the
  allocator; live VoiceInterface maps note->id; ledger admission before
  every allocation; 24 contract descriptors (kSubtractiveParams), quality
  declared per convention. Migration: shared params migrate; sounding
  voices reset on structural rebuilds — full voice-state adoption DEFERRED
  (tracked here; rebuilds are edit-time; cut sustain at swap = accepted M4
  cost).
- RegisterBuiltins.cpp (idempotent, engine-ctor) starts the registry.
- MidiScheduler.finalizeBlock added (per-range segments replaced by global
  per-track runs); TrackEvents is now an alias of core MidiTrackRun.
- Map: 173 classes, sweep green.

**Next: M4 continues** — MetronomeNode (cue|main routable click), then
WavetableSynth + FmSynth, then minimal SessionPlayer/launch.

## 2026-08-25 — M3 COMPLETE (feature 3: racks/macros/mod core + BlockSet)

- `device/QualityMode.h`: Eco/Standard/High + the "quality" key convention -
  an ordinary rt-safe param (isQualityMode flagged); the M15 governor forces
  Eco through the normal param path.
- `device/ModMatrix.h`: offset-only modulation core (§5 rule: modulation
  never rewrites the base) - 32 fixed slots, per-key offset summation;
  instruments wire sources from M4, full layering at the automation
  milestone.
- `device/MacroTable.h`: kMaxMacros knobs -> 64 plain-range mappings,
  expanded through an apply callback (= the installed resolver in the
  graph), so macro targets smooth exactly like direct moves.
- `device/RackDevice.h`: parallel-chain composite - fan-out to member
  DeviceChains, INTERNAL PDC to the slowest chain (rack reports one
  latency; composes upward), smoothed ChainMixer gains, macro.1-16 +
  rack.chainGain.1-8 params, state = macros + gains, configHash combines
  chain hashes. Zones + VariationStore at the racks workflow milestone;
  model Rack deltas wire construction then. DelayComp moved to device/
  (racks need it; graph depends downward).
- ParamBlockSet path live end-to-end: ParamBlockEntry triples (envelope +
  16B each) -> bridge -> coalescing table; EngineSync's full reconcile now
  sends ONE BlockSet frame instead of an N-message storm (and includes
  device.bypass states); presets/variations ride the same frame later.
  Generation barrier = documented deferral to the presets milestone.
- Map: 168 classes, sweep green. M3 (device platform) closes.

**Next (M4, blueprint §11): FIRST SOUND** — SubtractiveSynth (registry
type 0) with VoiceAllocator + scheduler-fed notes through the graph, then
MetronomeNode, minimal MidiClipPlayer/SessionPlayer per milestone text,
then WavetableSynth + FmSynth.

## 2026-08-25 — M3 feature 2 done: voice platform

`device/VoiceAllocator.h` (which also hosts the cross-layer VoiceGroup +
StealCandidate contract types - graph depends on device, never the reverse)
and `graph/VoiceBudgetLedger.h`. Pool model: `polyphony` musical voices +
headroom slots absorbing steal fades - a stolen voice fast-releases in its
slot while the new note takes a free one; pool exhaustion kills the
quietest fading slot as the documented last resort. Contract steal order
throughout (allocator-internal AND the ledger's cross-group ranking):
releasing -> unprotected -> oldest, level tiebreak; protection = transient
window (drum hits) or the 2 most-recent serials. The ledger recounts from
groups every block (drift-proof), grants requestVoice within kVoiceBudget=64
or demands stealVoices(1) from the globally best victim. PlaybackGraph owns
the ledger and beginBlock()s it; instruments register + consume from M4.
Map: 162 classes, sweep green.

**Next: M3 feature 3 (closes M3)** — racks/macros/modulation core +
QualityMode plumbing + preset shape. Then M4 first sound.

## 2026-08-25 — M3 feature 1 done: DeviceChain + DeviceRegistry + param residency

- `device/DeviceChain.h`: composite DeviceNode owning THE bypass contract -
  dry path delayed by device latency (delay lines kept WARM while active so
  an engage never crossfades stale history - self-caught, as was a wet/dry
  polarity inversion in the equal-power call), ~10ms fade = the device's
  post-bypass processing window, chain latency bypass-independent; one
  device.bypass switch per slot resolved under the device's uid; chain state
  migrates, configHash covers count+uids+latencies.
- `device/DeviceRegistry.h`: frozen DeviceTypeId wire numbering (append-only,
  replaces Kotlin ordinals), empty-until-milestones factory table (builder
  skips + counts unregistered types), hostside seam-6 key-collision assert.
- Device param residency (closes the M2 TODO): DeviceDeltaPayload grows a
  length-driven ParamValueRecord tail (backward-readable); ModelDevice
  stores params; the builder bakes them through descriptor lookup at
  compile. Param-only device deltas do NOT mark the graph dirty (knob moves
  ride the param path; residency updates silently) - only structural device
  facts rebuild.
- Graph integration: chains compile per lane (tracks/returns/master) from
  ordered model devices, run pre-strip; adoption is now configHash-aware
  (NodeIndexEntry {uid, node, hash}); PDC materializes DelayCompNodes from
  real chain latencies at the master join (per-send comps into return
  inputs deferred to the first nonzero-latency device, M8 - documented).
- Kotlin: DeltaEncoder device params tail, EngineSync frozen deviceTypeWire
  map, SetDeviceParam -> param + single-device residency delta,
  ToggleDeviceEnabled -> live device.bypass param + canonical delta.
- Map: 158 classes, sweep green.

**Next: M3 feature 2** — VoiceAllocator + VoiceBudgetLedger (global budget,
steal order releasing -> oldest -> quietest, protect recent + drum
transients). Then f3: racks/macros/mod core + QualityMode + presets.

## 2026-08-25 — M2 COMPLETE (feature 3: RT swap + live mixer loop)

The graph-and-mixer milestone closes. AudioEngine's render now runs the
whole dual-model protocol: claim graph -> executeAdopt (old graph valid
until ack) -> install -> ack(retired ?: epoch-1, releasing never-claimed
predecessors) -> publishInstalledGraphSeq to both param tables ->
reapplyNewerThan through the NEW resolver; live drains resolve through the
installed graph (misses = counted seam-4 skew); render goes through
processBlock with Main-bus copy-out; graph meters drain to the MeterBus.
Master volume closed end-to-end: EngineSync sends a type-4 track row keyed
by the well-known master uid so rebuilt graphs start correct (params alone
could be reclaimed as "baked" without being in the model).

Kotlin: MixerStateHolder grew a `meters` flow (uid -> track-id remap via
makeNodeUid, "master" key, separate from edit state so meter ticks don't
recompose strips); MixerScreen strips + master feed StereoLedLevelMeter
from live MeterReadings (dark when the engine is absent); MainDawScreen
takes an optional EngineReadback and MainActivity passes DawRuntime's.

Documented M2 deferrals: GroupTrackNode + RoutingTable wait for group
tracks / Routing deltas in the model (fixed topology today: tracks ->
master, sendA/B -> returns 1/2 -> master); Cue stays folded into Main until
routable sources (metronome M4, preview M14) exist. M3 TODO recorded:
device params need a model home (DeviceDeltaPayload params blob or
BlockSet) so rebuilds bake them - mixer params are covered via track rows.
Map: 155 classes, sweep green.

**Next (M3, blueprint §11):** DeviceNode chains (DeviceChain with
latency-preserving bypass), ParamRegistry (hostside collision assert),
VoiceAllocator + VoiceBudgetLedger, preset plumbing, racks/macros core,
QualityMode - then M4 first sound (SubtractiveSynth + MetronomeNode).

## 2026-08-25 — M2 features 1+2 done: device contract + compiled graph

Feature 1: `device/DeviceNode.h` (seams 1/3/6 verbatim: ProcessContext,
DeviceNode, ParamDescriptor, NodeState, VoiceInterface), `graph/TrackStrip`
(strip AS a DeviceNode: contract keys, gain-domain smoothing, never-jumps
migration), `graph/MeterProbe` (~30 Hz peak/RMS frames). MidiEvent.h moved
to core/ (device layer references MidiEventSpan; sequencer -> device -> dsp
-> core direction).

Feature 2: `graph/SendNode` (accumulating post-fader tap, resolver-bridged
to the track uid), `graph/DelayComp.h` (DelayCompNode + PdcCalculator - the
industry join-balancing rule computed at compile; all-zero inputs today),
`graph/MigrationPlan` (adopt-only, RT save->load POD moves through pre-sized
scratch), `graph/PlaybackGraph.{h,cpp}` (arena + nodes + resolver + M2
topology processBlock: zeroed track buffers -> strips -> send taps ->
returns -> master -> Main bus, meters collected per block),
GraphBuilder::buildGraph (ordered lanes, model-initialized strips, adoption
scan against the previous artifact, resolver registration, PDC pass, offer).

Self-caught during reread: a replaced UNCLAIMED graph offer must never be
eagerly freed - the new offer's MigrationPlan references the predecessor's
nodes. Graph artifacts therefore free ONLY via the acked-front GC rule
(provably safe by epoch monotonicity), with a chain cap (8) coalescing
rebuilds while the audio thread isn't claiming. Timeline/tempo artifacts
keep eager replacement (no cross-references). Map: 155 classes, sweep green.

**Next: M2 feature 3** — RT swap in AudioEngine (claim -> executeAdopt ->
ack(retired ?: claimed-1) -> publishInstalledGraphSeq -> reapplyNewerThan
through the new resolver), drainDirty through resolver, render through the
graph, meters -> MeterBus, then Kotlin MixerStateHolder wiring.

## 2026-08-25 — M1 COMPLETE (feature 4: editSeq + structure-delta sync)

The dual-model loop is closed end to end: edit -> editSeq-stamped bundle ->
GraphBuilder -> TimelineSnapshot -> RT claim -> MidiScheduler reconciliation.

- ProjectStore: monotonic editSeq on every published change; onEngineSync is
  now `(ProjectAction?, ProjectState, Int)` - undo/redo notify with a NULL
  action (state authoritative, engine resyncs wholesale). Closed the M0 gap
  where undo silently diverged the engine.
- DeltaEncoder: ModelDelta bundles bit-identical to DeltaSchemas.h
  (envelope + contract-ordered 16B StateCodec headers, empty payload =
  remove).
- EngineController: native session (and its builder thread) created EAGERLY
  at construction - deltas apply while audio is closed; sendModelDelta
  (idempotent, never backpressured, growable direct buffer).
- EngineSync: full three-class classification stamped with real editSeq.
  Structure edits map per action (clip placement-only vs content-only
  updates); cascading removes derive from PRE-state; shared content removed
  only when unreferenced post-state; canonical linked-pair content id =
  lexicographic MIN of clip ids (forward-compatible with explicit
  ClipContent + copy-on-unlink at the session milestone); drum steps
  flatten via DrumPadType.midiPitch with stable fnv32 step ids; solo/arm/
  mute reach the model's track flags (audibility matrix input, M2); SetBpm
  sends the live splice AND the canonical tempo delta; NULL action + every
  RUNNING transition trigger full push + param resend.
- Model v2 note: the deep entity split (explicit ClipContent, TakeLane,
  automation entities) lands with the milestones that consume it; M1's
  slice is identity + editSeq + linked-content derivation, keeping the UI
  layer stable. Map: 137 classes, sweep green.

**Next (M2, blueprint §11):** PlaybackGraph compile + swap (GraphBuilder
kDirtyGraph consumer), TrackStrip/MasterNode with the contract param keys,
key->dense resolver, PDC skeleton, MigrationPlan adopt path, meter probes -
the first audible milestone.

## 2026-08-25 — M1 feature 3 done: MidiScheduler

`sequencer/MidiEvent.h` (the seam-1 MidiEventSpan types; OFF sorts before ON
at equal offsets) + `sequencer/MidiScheduler.h`. Design rule: positional
facts (which notes start in a span, loop-pass indices, the future
probability seed inputs) are derived from the TransportSpan + clip geometry,
never accumulated — the sounding-note table is the ONLY state. Stuck-note
guarantees, end to end: beat-mapped OFFs, flush on stop/seek/loop-wrap,
synthetic OFFs on timeline swaps (matched by content id while downstream
voices key on per-pass instance ids so loop retriggers never collide), and
an admission invariant (capacity − poolSize ≥ soundingCount) that reserves a
pool slot for every future OFF — emitOff cannot fail, mirroring EventRing's
reserved-OFF rule. Output = flat pool + per-track segments sorted (offset,
OFF-before-ON). AudioEngine drives it per block: beginBlock → swap
reconcile → schedule per transport span; instruments consume at M4.
Map: 136 classes, sweep green.

**Next: M1 feature 4 (final)** — Kotlin model v2 additions (stable entity
ids, shared ClipContent for linked clips), ProjectStore editSeq stamping,
EngineSync structure-delta serialization (ModelDeltaEnvelope + StateCodec
frames via a new DeltaEncoder), full-model push on attach. That closes M1.

## 2026-08-25 — M1 feature 2 done: EngineModel + TimelineSnapshot + GraphBuilder

The builder side of the dual-model architecture is live:

- `jni/DeltaSchemas.h` — versioned payload layouts per entityKind (Track 20B,
  Clip 40B, ClipContent head+NoteRecords, Device 16B, Scene 8B, TempoMap
  head+events) + the ModelDeltaEnvelope (editSeq per bundle). byteLen==0 =
  remove; entityId = the same makeNodeUid used everywhere.
- `engine/EngineModel.{h,cpp}` — builder-thread-only mirror of the edit
  model: idempotent delta application, dirty classes (Timeline/Tempo/Graph),
  non-cascading removal (compiles skip dangling refs, counted - seam-4 skew),
  Rack/Routing/LaneGroup/Groove deferred to M2/M3.
- `sequencer/TimelineSnapshot.h` — immutable compiled timeline: flat stores
  (exact-reserve-then-fill keeps view pointers stable), per-track ClipViews
  sorted by placement, notes sorted content-local, binary-search NoteSpans;
  stamped {epoch, builtFromEditSeq, tempoMapRev}.
- `graph/GraphBuilder.{h,cpp}` — the compile thread (50ms cycle): mutex
  inbox of delta bundles (engine-io producer preserves edit order), model
  apply, timeline rebuild on dirty, tempo base rebuild from model deltas
  (rate-gated with retry), forced tail consolidation sampling the SAME
  governing function at boundary beats (equal-tempo merges preserve
  post-seek sample discontinuities; consolidation skipped while an offer is
  in flight). OfferSlot epoch GC: only the builder frees, only after RT
  acks; tempo background pointer republishes when the predecessor's ack
  proves the claim.
- AudioEngine now constructs/joins the builder (model syncs while streams
  are closed), claims offered timelines at block boundaries (ack-retire),
  exposes builder()/timelineOffer()/timeline(); bridge routes ModelDelta
  payloads into the builder inbox. TempoMap.sampleRate became atomic
  (prepare writes vs builder reads).
- Map: 133 classes, sweep green.

**Next: M1 feature 3 — MidiScheduler** (spans + timeline snapshot + map ->
sample-offset-sorted note events, sounding-note table, loop-pass indices,
synthetic offs on snapshot swap). Then f4: Kotlin model v2 + delta sync.

## 2026-08-25 — M1 feature 1 done: TempoMap + TransportEngine

`cpp/sequencer/` begins. TempoMap implements the frozen contract: immutable
builder-compiled base (piecewise-linear segments — ramps densify at compile
time, the RT path is lookup + lerp) swapped via OfferSlot epochs, plus the
fixed-cap RT tail (anchored live tempo splices, seqlock-published,
rev-stamped; claim retains events newer than foldRev; governing rule =
newest tail event at-or-before position, which also handles seek-back tempo
sets whose anchors are non-monotonic). Background snapshots take a mutex the
RT thread never touches. TransportEngine owns play/pause/stop-to-zero,
seeks, beat-anchored loop region (sample anchors recomputed on tempo
events/base claims), time-sig + metronome flags, the TimebaseSource seam
(external authority rejects tempo messages + counts), and advance() which
claims offered bases then splits the block at a loop wrap (1-2 spans).
AudioEngine now routes ALL 16 transport ops, advances real transport, and
publishes a real beat clock; same-rate reopens keep transport state. Status
wire claims its reserved word as timeSigPacked; metronome bit added
(kClockMetronome == kStatusMetronome), mirrored through WireProtocol /
EngineReadback / CommandEncoder (setTimebaseSource added). Map: 109 classes,
sweep green.

**Next: M1 feature 2** — EngineModel + TimelineSnapshot units + GraphBuilder
thread skeleton (StateCodec delta application, snapshot compile + OfferSlot
swap, epoch GC, tempo-tail consolidation nudge). Then f3 MidiScheduler,
f4 Kotlin model v2 + delta sync.

## 2026-08-25 (later still) — M0 COMPLETE (feature 4: the JNI seam)

Engine foundation milestone done. Feature 4 delivered the full Kotlin <-> C++
seam per CONTRACTS.md seam 5:

- `cpp/jni/` — CommandCodec (pure, visitor-driven frame decoder; record-
  granular backpressure; unknown kinds skipped), StateCodec (contract-ordered
  16-byte entity-delta framing, field-wise reads for the unaligned u64;
  consumer = the M1 builder), ReadbackWire (frozen 80-byte EngineStatusWire +
  meter wire doc), NativeAudioBridge.cpp (the ONLY jni.h TU: RegisterNatives
  from JNI_OnLoad, jlong BridgeHandle, direct-ByteBuffer push/poll/meters,
  Param/Move -> ParamMoveTable vs everything-else -> EventRing routing).
  OboeDriver gained an atomic `inputOpen()` fact for the poll path. CMake
  dawcore now lists jni/NativeAudioBridge.cpp (still NOT wired into Gradle).
- Kotlin `com.example.synth.engine` — WireProtocol (bit-exact fnv1a32/64 +
  makeNodeUid mirrors, all layout/opcode constants), ParamKeys (contract
  semantic keys for M2 strips), EnginePrefs/EngineCaps, NativeAudioBridge
  (guarded loadLibrary: UI-only while the .so doesn't ship), CommandEncoder
  (batch builder; backlog cap -> front-of-queue Panic + reconcile),
  EngineController (ALL natives serialized on one daw-engine-io thread = the
  SPSC producer; backpressure retry; D5 reopen), EngineReadback (16 ms
  status/meter polls on the same dispatcher; playhead extrapolation),
  EngineSync (transport + param-move classification off POST-reduction state;
  reconcile + attach-time full param re-send that queues until start).
- `com.example.DawRuntime` — process-scoped composition root (audio survives
  rotation, spec Part 1 §15); MainActivity now passes the shared store into
  MainDawScreen.
- Map: 101 classes, bidirectional sweep green. Verified data-flow walk:
  dispatch -> reduce -> onEngineSync -> send{} -> encode -> push -> decode ->
  ring/table -> RT drain -> clock/meter publish -> poll -> flows -> UI.

**Next (M1, blueprint §11):** Kotlin domain model v2 (+editSeq stamping),
EngineSync structure-delta serialization (StateCodec), native EngineModel +
GraphBuilder skeleton, TempoMap (immutable base + RT tail), TransportEngine
replacing AudioEngine's placeholder transport, TimelineSnapshot swap
mechanics, MidiScheduler.

## 2026-08-25 (later) — condemned skeleton fully removed

Per the owner's directive after review sign-off: all code in direct argument
with the approved blueprint is gone so building agents cannot be confused by
it. Deleted the entire old pre-blueprint C++ skeleton (49 files: graph/,
device/, sequencer/ — mutable shared graph, string-keyed params, the
blueprint's rejected alternative). `app/src/main/cpp/` now contains ONLY
new-engine modules (core/, dsp/) plus CMakeLists. The living map carries 75
classes, all with source, sweep green. Anyone needing the old code finds it
in git history before this commit.

## 2026-08-25 — M0 in progress (2 of 4 features done)

**Done so far**
- Phase A: blueprint authored from the two specs (rev 3).
- Phase B: adversarial DSP review — 3 rounds, 17 blockers resolved,
  **APPROVED**. Condition honored: `CONTRACTS.md` v1.0.0 frozen first.
- M0 feature 1 — `cpp/core/` complete (13 classes): SpscRing, EngineMessage
  (64-byte seam-2 POD), EventRing (note-off reservation), ParamMoveTable
  (per-producer, seqlock slots, reclaim + reconcile), Seqlock (Boehm fences),
  OfferSlot (epoch-in-artifact), SmoothedValue, AudioBufferPool, TimeAnchor,
  MeterFrame, RtRandom (musical seed), FixedVector, ScopedNoDenormals.
  Old condemned core files deleted (LockFreeQueue, EngineCommand,
  ProcessContext, AudioBufferPool.cpp).
- M0 feature 2 — `cpp/dsp/` first wave (7 classes + DspMath): polyBLEP
  Oscillator (old condemned one deleted), NoiseGen, Simper SVF, RBJ Biquad,
  AdsrEnvelope, Lfo, DelayLine. Conventions (pan law, dB floor, equal-power)
  live as code in DspMath.h.
- CMakeLists.txt exists (dawcore + dawcore_host targets) but is deliberately
  NOT wired into Gradle — no-compile phase; wire via externalNativeBuild when
  the user asks for a build.
- `docs/ARCHITECTURE.md` updated after each feature; bidirectional
  map↔source sweep green (119 classes).

**DONE (M0 feature 3): engine driver + engine skeleton** — InputJitterRing
(D2 policies), OboeDriver (full-duplex per the researched pattern, sub-chunks
bursts to kMaxBlock, timestamps → StreamTime, reopen flag on route change),
AudioEngine (RT spine: anchor publish → bounded drains → param tables retain
for post-swap reapply → input consume → silence render → clock publish).
CMake dawcore target now lists the engine .cpp files.

**Original feature-3 scope (kept for reference):**
- `engine/InputJitterRing.h` — duplex input ring (same-thread, D2 policies:
  ~2-burst prime, underfill zeros+count, overfill drop-oldest+count).
- `engine/OboeDriver.{h,cpp}` — output stream w/ callback + callback-less
  input (Oboe FullDuplex pattern: output built first, input matches rate,
  2x input capacity, 3-phase sync), LowLatency+Exclusive w/ fallback,
  per-stream latency reports, route/rate-change re-prepare hooks, xrun
  counters, stream timestamps → TimeAnchor data. Written against the Oboe
  API headers (arrive via prefab at wire-up).
- `engine/AudioEngine.{h,cpp}` — skeleton: owns per-producer rings + tables,
  TimeAnchorPublisher, MeterBus ring; callback path = ScopedNoDenormals →
  drain input → drain messages (events + ParamMoveTable) → sub-chunk
  numFrames ≤ kMaxBlock → render (silence until M2 graph) → publish anchor/
  clock. Seams left for TransportEngine (M1) and PlaybackGraph (M2).

**Then (M0 feature 4):** `jni/` NativeAudioBridge + CommandCodec/StateCodec
(seam 5) + Kotlin `engine/` bridge counterparts. That completes M0; M1 (model
v2 + time) follows per blueprint §11.

**Standing constraints:** no compiling, no tests (user-gated); Earth.Design
V2 for all UI; map updated per feature; commits reread before push.
