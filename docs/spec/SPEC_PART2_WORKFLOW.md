# Mobile DAW Functional Specification Blueprint — Part 2

## DAW workflow, instruments, MIDI tools, audio effects and mobile translation

## Purpose

This document extends Part 1. It explains how music is created inside the DAW, what the instrument and effect systems must do, how MIDI is generated and transformed, and how a desktop-depth workflow becomes usable on a phone.

This remains a functional specification. It does not define visual styling, exact layouts, gestures, software architecture, programming language, framework or data model.

The specification is based primarily on the working model established by Ableton Live, with mobile-production patterns used to determine how the same depth can remain practical on a phone.

No external source list is included in this file.

## Specification language

- **Must** means required for the intended complete product.
- **Should** means strongly recommended but may be scheduled after the first complete release.
- **Open** means a product decision still has to be made.

Device names in this document are functional names. The final app should use its own product names, artwork and sound design rather than copying another DAW's branded devices or visual identity.

# 1. Product decisions carried forward

- The app is a complete standalone DAW.
- A desktop computer is not required to finish a song.
- Phone portrait and phone landscape must both be fully functional.
- Session and Arrangement are equally important creation environments.
- Both views use the same tracks, instruments, effects, routing and mixer.
- A clip can appear in both views as linked content.
- Linked clip content stays synchronized until the user explicitly unlinks it.
- Rotating the phone must preserve playback, selection, editor position, unsaved work and the current task.
- Editing must be non-destructive unless the user deliberately renders or replaces material.

## 1.1 Clarified Session and Arrangement playback rule

“Like Ableton” means Session playback takes over only the affected track.

- Launching a Session clip while the Arrangement plays must stop the Arrangement clip on that same track.
- Arrangement clips on all unaffected tracks continue playing.
- The result can sound like Session material is layered over the Arrangement across the project, but two clips do not play simultaneously on the same track.
- A track can return to its Arrangement playback independently.
- All overridden tracks can return to the Arrangement together.
- The app must clearly track which tracks are currently following Session and which are following Arrangement.
- To hear two clips simultaneously, they must be placed on separate tracks.

This rule avoids doubled signals, duplicated instruments and ambiguous automation while preserving live improvisation over an existing arrangement.

## 1.2 Linked clips across both views

The app extends the traditional Ableton model by linking clip content until the user unlinks it.

Linked properties:

- MIDI notes and note expression.
- Audio source reference.
- Warp markers and transient edits.
- Clip start, end and loop content.
- Clip envelopes and clip modulation.
- Clip-level pitch, gain and timing changes.

Context-specific properties that are not shared:

- Arrangement position.
- Session slot and scene position.
- Session launch mode.
- Session launch quantization.
- Follow actions.
- Arrangement fades and crossfades that depend on neighboring clips.
- Track automation, because it belongs to the track timeline rather than the clip source.

Unlinking a clip must create an independent content version. Future edits then affect only that version. Unlinking must never duplicate the underlying audio file unless a rendered or destructive operation requires a new file.

# 2. The complete in-DAW workflow

The DAW supports several entry points, but all of them feed the same project, tracks and signal flow.

## 2.1 Start a project

The user can:

- Create an empty project.
- Start from a template.
- Start from a drum kit, instrument preset, loop, audio recording or imported file.
- Set tempo, time signature, key and scale immediately or later.
- Begin in Session or Arrangement without losing access to the other view.
- Work offline after required sounds have been downloaded.

## 2.2 Add the first musical source

The user chooses one of these paths:

- Add an instrument track and play or sequence notes.
- Add a drum track and finger-drum or program steps.
- Add an audio track and record a microphone, instrument or line input.
- Import an audio loop or recording.
- Import a MIDI file.
- Add an external instrument track for connected hardware.
- Resample existing project audio into a new audio track.

The selected source determines the default editor and compatible device types, but the user can change the track configuration later.

## 2.3 Find and load a sound

The browser must let the user:

- Search instruments, presets, drum kits, samples, loops, MIDI patterns and effects together or by category.
- Filter by sound character, instrument family, genre, key, tempo, content type and installed location.
- Preview presets by playing the current MIDI input.
- Preview audio at its original tempo or synchronized to the project.
- Preview pitched audio in the project key.
- Mark favorites and use recent items.
- Replace a sound without deleting the MIDI clip that plays it.
- Load a sample directly into a sampler or Drum Rack.
- Load a preset as an instrument plus its associated effect chain.

## 2.4 Capture a performance or create a pattern

The user can:

- Record into a Session clip.
- Record directly into the Arrangement.
- Create an empty MIDI clip and draw notes.
- Step-record notes from an onscreen or external keyboard.
- Program drum steps.
- Overdub notes into a loop.
- Record parameter movement with the performance.
- Capture recently played MIDI even when normal recording was not enabled.
- Record several loop passes as takes.
- Apply recording quantization without permanently removing the unquantized performance from undo history.

## 2.5 Edit the musical idea

For MIDI, the user can edit notes, timing, length, velocity, probability, scale position, articulation and expression.

For audio, the user can trim, split, loop, warp, transpose, fade, reverse, slice and adjust clip gain.

Every editor must support:

- Selection-based editing.
- Undo and redo.
- Duplicate, cut, copy, paste and delete.
- Audition before committing.
- Grid-relative and free timing.
- Numeric precision where appropriate.
- Non-destructive editing.

## 2.6 Develop variations in Session

The user can:

- Duplicate a clip and change the variation.
- Store alternative drum, bass, chord and melody clips vertically within their tracks.
- Arrange compatible clips across a scene.
- Launch clips independently.
- Launch a full scene.
- Change launch quantization per clip.
- Use follow actions to create evolving sequences.
- Capture a spontaneous performance into Arrangement.

## 2.7 Build the finished Arrangement

The user can:

- Record a Session performance into the timeline.
- Copy or link selected Session clips into the Arrangement.
- Build sections directly on the timeline.
- Duplicate, insert, remove and rearrange song sections.
- Edit clips, automation, tempo and time signatures.
- Record or comp final vocal and instrumental takes.
- Replace temporary sounds while keeping musical content.

## 2.8 Design and process sound

Each track can contain a device chain. The user can:

- Place MIDI effects before the instrument.
- Place audio effects after the instrument.
- Reorder devices.
- Bypass devices.
- Create serial and parallel processing.
- Save a device, preset, rack or complete track.
- Map important parameters to macros.
- Automate or modulate eligible parameters.
- Bounce complex processing to audio when needed.

## 2.9 Mix, commit and export

The user can:

- Balance gain, volume, pan and stereo width.
- Route tracks into groups and return effects.
- Add corrective, creative and mastering effects.
- Record or draw automation.
- Freeze or bounce processor-heavy tracks.
- Compare the mix with reference audio.
- Export a stereo master, stems, selected tracks, loops or MIDI.

# 3. Signal flow and device rules

The device model must remain consistent everywhere in the app.

## 3.1 MIDI or instrument track

Signal order:

1. Onscreen performance input, external MIDI input or MIDI clip.
2. Real-time MIDI-effect chain.
3. Instrument or Instrument Rack.
4. Audio-effect chain.
5. Track mixer controls.
6. Group, sends and output routing.
7. Main output.

Rules:

- MIDI effects must appear before an instrument.
- Audio effects can appear only after a sound-generating instrument.
- The processed MIDI output must be recordable to another MIDI track.
- The instrument's audio output must be recordable or resampled to an audio track.

## 3.2 Audio track

Signal order:

1. Audio input or audio clip.
2. Input gain where applicable.
3. Audio-effect chain.
4. Track mixer controls.
5. Group, sends and output routing.
6. Main output.

The user must be able to choose whether monitoring includes the track effects and whether recorded audio captures the dry input or a printed effect signal.

## 3.3 Drum track

Signal order:

1. MIDI input, drum sequencer or MIDI clip.
2. MIDI effects.
3. Drum Rack.
4. Per-pad instrument and effect chains.
5. Per-pad mixer and output routing.
6. Rack-level audio effects.
7. Track mixer.

Each pad can feed the parent track, an individual output, or a separate mixer track.

## 3.4 Return track

- Receives signals through track sends.
- Contains audio effects but no normal clip lane.
- Supports pre-fader and post-fader sends.
- Can route to the Main output or another permitted bus.
- Must prevent accidental feedback routing.

## 3.5 External instrument track

- Sends MIDI notes, clock and controller data to hardware.
- Receives the hardware's returned audio.
- Supports hardware latency compensation.
- Can add audio effects to the returned signal.
- Can freeze or record the returned performance as audio.

# 4. Common instrument behavior

Every built-in instrument must provide a consistent minimum workflow.

- Load, preview, replace and save presets.
- Start from an initialized preset.
- Show the most musically important parameters first while keeping all parameters available.
- Support MIDI note input from clips, onscreen controllers and external controllers.
- Respond to velocity.
- Support sustain where musically relevant.
- Expose pitch bend and modulation.
- Support channel aftertouch and MPE where the sound engine permits it.
- Allow automation and modulation of eligible parameters.
- Allow parameter mapping to rack macros.
- Provide output gain and a clipping warning.
- Provide polyphony and voice-stealing controls where applicable.
- Provide mono, polyphonic, legato and glide behavior where applicable.
- Include CPU quality modes when the instrument can significantly affect performance.
- Preserve notes and automation when the instrument or preset is replaced.
- Allow A/B comparison and preset reversion.
- Allow bouncing to audio without deleting the original source unless explicitly requested.

# 5. Built-in instruments

The complete app must include enough sound sources to make electronic music, recorded music, beat-based music and common acoustic arrangements without third-party plug-ins.

## 5.1 Subtractive synthesizer

Purpose: immediate basses, leads, plucks, pads and classic analog-style sounds with low processor cost.

Functions:

- Two main oscillators plus noise.
- Basic analog waveforms.
- Sub-oscillator.
- Octave, semitone and fine tuning.
- Oscillator sync, detune and pulse-width control.
- Mixer for oscillator and noise balance.
- Multimode filter with drive and resonance.
- Amplifier envelope and modulation envelope.
- At least two LFOs with free and tempo-synchronized rates.
- Velocity, key tracking, aftertouch and MPE modulation.
- Unison, stereo spread and controlled pitch drift.
- Mono/poly modes, legato and glide.
- Modulation matrix or clearly defined source-to-destination routing.

## 5.2 Wavetable synthesizer

Purpose: evolving digital timbres, modern basses, leads, pads and sound effects.

Functions:

- Two wavetable oscillators and a sub-oscillator.
- Factory and user-imported wavetables.
- Wavetable position and oscillator-warp controls.
- Oscillator sync, frequency modulation or phase modulation options.
- Dual filters with serial, parallel and split routing.
- Amplifier envelope plus additional modulation envelopes.
- At least two LFOs.
- Modulation matrix with multiple targets per source.
- Velocity, key, note, aftertouch, slide and per-note pitch input.
- Unison modes with voice amount, detune and stereo spread.
- High-quality and processor-saving modes.

## 5.3 FM synthesizer

Purpose: bells, metallic tones, keys, basses, percussion and complex digital timbres.

Functions:

- Four or more operators.
- Selectable FM algorithms.
- Ratio and fixed-frequency operation.
- Per-operator level, tuning and envelope.
- Operator feedback.
- User-editable or harmonic waveforms.
- Global pitch envelope.
- Filter and filter envelope.
- LFO and MIDI modulation routing.
- Velocity-sensitive operator levels.
- Mono/poly, glide, spread and polyphony controls.
- Processor-saving control through operator disabling and voice limits.

## 5.4 Hybrid or macro synthesizer

Purpose: fast sound creation with deep synthesis available behind a smaller set of musical controls.

Functions:

- Two independent sound engines.
- Multiple synthesis algorithms or oscillator models.
- Morphing between engines.
- Dual filters and routing options.
- Modulation envelopes, LFOs and matrix.
- Macro controls designed for performance.
- Randomize and mutate functions with undo.
- Scale-aware pitch modulation.
- MPE response.

## 5.5 Simple sampler

Purpose: turn one recording or audio file into a playable instrument immediately.

Playback modes:

- Classic pitched playback.
- One-shot playback.
- Slice playback.

Functions:

- Record, import or replace a sample.
- Start, end, loop and loop-crossfade controls.
- Forward, reverse and alternating loop modes.
- Root note, transpose, detune and key tracking.
- Tempo warping for pitched and rhythmic use.
- Slice by transient, beat, marker or equal division.
- Map slices chromatically or across pads.
- Filter, amplifier envelope, pitch envelope and LFO.
- Velocity and MPE modulation.
- Convert slices to a Drum Rack.

## 5.6 Advanced multisampler

Purpose: realistic instruments, layered patches and complex multisampled sound design.

Functions:

- Multiple samples in one instrument.
- Key zones.
- Velocity zones.
- Layer, round-robin, random and selector-based zones.
- Per-zone root note, tuning, gain, pan and playback range.
- Looping and crossfading.
- Global and per-zone modulation.
- Dual filters, envelopes and LFOs.
- Sample-start modulation.
- Import standard non-proprietary multisample mappings where supported.
- Collect all used samples into the project or user library.

## 5.7 Drum Sampler

Purpose: fast, focused editing of one-shot drum sounds inside a pad.

Functions:

- Start, length, fade and playback-direction controls.
- AHD or ADSR amplitude envelope.
- Transpose, fine tune and pitch envelope.
- Multimode filter.
- Velocity-to-volume and velocity-to-filter response.
- Pan and output gain.
- Choke behavior when used in a rack.
- Creative playback modes such as stretch, loop, sub, noise, FM, ring modulation and bit reduction.
- Per-note pitch bend where supported.

## 5.8 Drum Rack

Purpose: create complete drum kits and route each sound independently.

Functions:

- At least 16 immediately playable pads, with access to the full MIDI note range.
- One instrument chain per pad.
- Multiple layered chains on one pad.
- Choke groups.
- Pad copy, move, duplicate, rename and color.
- Per-pad volume, pan, mute, solo and sends.
- Per-pad effects.
- Individual audio outputs.
- Velocity and probability editing.
- Step sequencing with ratchets, repeats, microtiming and automation locks.
- Kit save, load and sample collection.
- Automatic creation of a drum lane editor from used pads.

## 5.9 Physical-model instruments

The app should include processor-efficient physical models for expressive sounds that are difficult to achieve with static samples.

Recommended models:

- Mallet and resonator instrument.
- Plucked or bowed string instrument.
- Electric piano instrument.

Common functions:

- Exciter type and intensity.
- Material or timbre.
- Resonator type, size and decay.
- Strike, pluck or pickup position.
- Damping, stiffness and inharmonicity.
- Velocity, key tracking, aftertouch and MPE response.
- Quality modes.

## 5.10 Sample-library instrument

Purpose: provide immediately usable acoustic and electronic sounds.

Content families:

- Piano and electric piano.
- Organ and keys.
- Bass and guitar.
- Strings, brass and woodwinds.
- Choir and vocal textures.
- Mallets and percussion.
- Pads, atmospheres and sound effects.

Functions:

- Preset browsing by family and character.
- Macro controls for tone, dynamics, envelope, space and expression.
- Velocity layers and round robins where available.
- Sustain and articulation switching where relevant.
- Downloadable content packs with storage management.

## 5.11 External instrument device

Purpose: treat a hardware synthesizer or drum machine like an internal instrument.

Functions:

- Select MIDI output port and channel.
- Send bank, program and MIDI CC values.
- Select audio return input.
- Set hardware latency compensation.
- Save controller mappings as presets.
- Record or bounce the hardware return.

## 5.12 Instrument Rack

Purpose: combine instruments, MIDI effects and audio effects into one playable preset.

Functions:

- Serial and parallel chains.
- Key-range splits.
- Velocity-range splits.
- Chain selector for switching or crossfading sounds.
- Layered instruments.
- Macro controls mapped to multiple parameters.
- Defined minimum, maximum and polarity for macro mappings.
- Macro randomization and variation snapshots.
- Nested racks.
- Preset saving with required samples.

# 6. Onscreen musical input

The phone must work as an instrument without external hardware.

Input modes:

- Chromatic keyboard.
- Scale-locked keyboard.
- Isomorphic grid.
- Drum pads.
- Chord pads.
- Fretboard or string layout where useful.
- Step input.

Functions:

- Select root note and scale.
- Set octave and visible range.
- Sustain and latch.
- Velocity input.
- Pitch bend and modulation.
- Aftertouch or pressure where the device supports it.
- MPE-style per-note slide and pressure where supported.
- Note repeat and rhythmic subdivisions.
- Fixed velocity option.
- External MIDI and onscreen input at the same time.

The exact gestures and layouts remain part of the later UX specification.

# 7. MIDI recording and editor depth

## 7.1 Recording modes

- Arrangement recording.
- Session clip recording.
- Loop overdub.
- Replace recording.
- Step recording.
- Capture recently played MIDI.
- Quantized recording.
- Count-in recording.
- Punch recording.
- Multi-take MIDI recording and comping.
- Record output from a MIDI-effect chain to another track.

## 7.2 Note properties

Each MIDI note can contain:

- Start time.
- Duration.
- Pitch.
- Velocity.
- Release velocity.
- Probability or chance.
- Velocity deviation.
- Timing deviation.
- Per-note pitch bend.
- Per-note slide or timbre.
- Per-note pressure.

## 7.3 Piano-roll functions

- Create, select, move, copy, resize and delete notes.
- Edit one note or multiple notes.
- Edit several clips together.
- Fold to used notes.
- Fold to the current scale.
- Highlight root and scale notes.
- Preview notes through the current instrument.
- Draw notes at the current grid size.
- Duplicate notes or time selections.
- Legato, staccato and fixed-length operations.
- Transpose by semitone, octave or scale degree.
- Invert, reverse and rotate note patterns.
- Quantize note starts, ends or both.
- Apply partial quantization.
- Apply groove and swing.
- Humanize timing and velocity.
- Edit velocities as points, lines, ramps or shapes.
- Edit expression in separate lanes.
- Change grid resolution independently of project tempo.
- Support straight, triplet and dotted divisions.

## 7.4 Drum editor functions

- Show named lanes for used Drum Rack pads.
- Add and remove steps.
- Change velocity per step.
- Add probability per step.
- Add repeats or ratchets per step.
- Move a step before or after the grid with microtiming.
- Set note length.
- Add per-step parameter automation.
- Fill every 2, 3, 4 or selected number of steps.
- Rotate a lane left or right.
- Randomize selected properties with adjustable intensity.
- Duplicate or extend a pattern.

# 8. Three MIDI tool types

The app must clearly distinguish three different ways of working with MIDI.

## 8.1 Transformations

Transformations rewrite selected notes or expression data. They are applied to a note selection, time selection or complete clip. The result becomes editable MIDI data and can be undone.

Required transformations:

| Tool | Function |
| --- | --- |
| Arpeggiate | Split selected chords or notes into an editable arpeggiated sequence with style, rate, gate, distance and steps. |
| Chop | Divide selected notes into repeated parts with gaps, emphasis and timing variation. |
| Connect | Generate editable notes between existing notes, with density, spread, rate and tie controls. |
| Glissando | Connect successive notes with per-note pitch curves. |
| Expression LFO | Write pitch, slide or pressure curves onto selected notes. |
| Ornament | Add flams, grace notes or other short lead-in notes. |
| Quantize | Move note starts and/or ends toward a straight or triplet grid by an adjustable amount. |
| Humanize | Add controlled timing, velocity and duration variation. |
| Recombine | Shuffle, mirror or rotate position, pitch, duration or velocity values among selected notes. |
| Span | Convert durations to legato, tenuto, staccato or a fixed relationship. |
| Strum | Offset chord-note timing and velocity along an adjustable curve. |
| Time Warp | Accelerate or slow note timing across a selection using a curve. |
| Velocity Shaper | Apply a drawn velocity envelope to selected notes. |
| Transpose and Scale | Move notes chromatically or by scale degree and optionally constrain them to the project scale. |
| Invert and Reverse | Invert pitch relationships or reverse musical order within the selected range. |

Transformation behavior:

- Provide live preview before commit.
- Provide Apply, Cancel and Reset.
- Preserve the original through undo.
- Work on all selected notes without hidden exclusions.
- Respect the active scale when scale awareness is enabled.
- Allow repeated application.
- Reveal the resulting editable notes after commit.

## 8.2 Generators

Generators create new editable MIDI material inside a clip or time selection.

Required generators:

| Tool | Function |
| --- | --- |
| Rhythm | Generate a pattern for one pitch or drum pad using steps, density, rotation, accents, splits and duration. |
| Seed | Generate controlled random notes inside pitch, duration, velocity, density and voice ranges. |
| Shape | Draw or select a contour that becomes a melodic sequence inside a pitch range. |
| Chord Stack | Generate chords and chord progressions inside the active scale with root, inversion, duration and voicing controls. |
| Euclidean | Distribute pulses evenly across steps for one or more pitches or drum voices. |
| Bassline | Generate scale-aware bass patterns from rhythm, octave, repetition and approach-note rules. |
| Variation | Produce related alternatives to selected musical material without overwriting the source until accepted. |

Generator behavior:

- Target the current clip loop or selected time range.
- Preview changes while parameters are adjusted.
- Make the result normal editable MIDI notes.
- Allow reseeding random results.
- Preserve the current result until the user deliberately generates another.
- Respect scale, tuning, grid and selected drum pad.
- Warn before replacing overlapping existing notes.

## 8.3 Real-time MIDI effects

Real-time MIDI effects process incoming or clip MIDI during playback. They do not rewrite the source clip unless their output is recorded to a new clip.

Required MIDI effects:

| Effect | Function |
| --- | --- |
| Arpeggiator | Turn held or recorded notes into tempo-synced or free-running patterns with style, rate, gate, steps, retrigger, hold, groove and velocity shaping. |
| Chord | Add interval voices to each incoming note with per-voice velocity, chance and strum. |
| Note Length | Set, gate or trigger note duration and optionally respond to Note On or Note Off. |
| Pitch | Transpose, constrain or block notes by range. |
| Random | Add controlled chance-based pitch variation while respecting the selected scale. |
| Scale | Remap incoming pitches to a scale or a custom note map. |
| Velocity | Compress, expand, limit, randomize or remap velocity. |
| CC Control | Send and automate named MIDI CC, modulation, pitch-bend and pressure values. |
| Note Echo | Create repeated MIDI notes with time, feedback, pitch and velocity decay. |
| Expression Control | Map velocity, note, mod wheel, pressure, slide or randomness to instrument parameters. |
| MIDI LFO | Modulate MIDI CC or mapped parameters using tempo-synced or free-running shapes. |

MIDI-effect chain behavior:

- Effects can be reordered and bypassed.
- Each effect must show incoming and outgoing activity.
- Pitch-based controls can operate in semitones or scale degrees.
- Generated output can be recorded to another MIDI track.
- Stuck notes must be prevented when bypassing, reordering or deleting effects.
- The chain must recover correctly after playback stops, loops or changes position.

# 9. Scale, groove and expression system

## 9.1 Scale awareness

- A project can define a root note and scale.
- A clip can follow the project scale or define its own.
- Piano roll, onscreen keyboard, MIDI tools and compatible instruments can follow the active scale.
- Pitch operations can use semitones or scale degrees.
- Drum tracks bypass melodic scale behavior.
- Notes outside the scale can remain visible and editable.
- Scale locking must never silently delete notes.

## 9.2 Groove and timing

- Apply swing or a saved groove to MIDI and warped audio clips.
- Control groove timing, velocity influence, randomization and overall amount.
- Preview grooves non-destructively.
- Commit a groove when editable note or warp positions are required.
- Extract timing and velocity feel from a MIDI or audio clip where feasible.
- Save user grooves.

## 9.3 MPE and expression

- Record and edit per-note pitch bend, slide, pressure, velocity and release velocity.
- Display one or more expression lanes for selected notes.
- Copy, paste, scale, simplify and delete expression curves.
- Allow built-in instruments and compatible external devices to receive MPE.
- Convert MPE to global MIDI control when required.
- Preserve expression through clip linking, duplication, Arrangement capture and MIDI export where the export format supports it.

# 10. Audio effects

The built-in effects must cover corrective editing, sound design, creative production, mixing, live performance and mastering.

## 10.1 Common audio-effect behavior

Every effect must support the relevant subset of these functions:

- Bypass without clicks or state corruption.
- Reorder within a chain.
- Save and load presets.
- Undo and redo parameter changes.
- Automation and modulation.
- Macro mapping.
- Dry/wet control for effects where parallel blending is meaningful.
- Input and output gain where level changes are expected.
- Tempo-synchronized and free-time modes where applicable.
- Mono and stereo operation.
- Sidechain input where applicable.
- Quality or oversampling control where useful.
- Clear metering of input, output or gain reduction.
- A/B comparison.
- Parameter reset and precise numeric entry.
- Safe feedback limits for delay, resonator and distortion devices.
- Correct latency reporting and compensation.

## 10.2 Utility, analysis and gain tools

### Utility

- Gain.
- Pan or stereo balance.
- Stereo width from mono to expanded stereo.
- Left/right channel selection and swap.
- Phase inversion per channel.
- Mono bass below a selected frequency.
- Mute and DC filtering.

### Spectrum analyser

- Real-time frequency display.
- Peak hold.
- Frequency and note readout.
- Adjustable resolution, range and smoothing.
- Does not change the sound.

### Tuner

- Chromatic note and cents display.
- Adjustable reference pitch.
- Guitar or instrument-oriented alternatives where useful.
- Does not change the sound.

### Loudness and phase meter

- Peak and true-peak metering.
- RMS or equivalent average level.
- Momentary, short-term and integrated loudness.
- Loudness range.
- Stereo correlation and phase warnings.

## 10.3 Equalization and filtering

### Channel EQ

- High-pass and low-pass filters.
- Low, mid and high bands.
- Fast broad tone shaping.
- Output gain.

### Parametric EQ

- Up to eight independently enabled bands.
- Bell, shelf, notch, high-pass and low-pass shapes.
- Frequency, gain and Q for each band.
- Spectrum display.
- Stereo, left/right and mid/side modes.
- Band audition.
- Adaptive Q option.
- Oversampling or high-quality mode.

### Three-band performance EQ

- Low, mid and high gain.
- Adjustable crossover frequencies.
- Full band kill.
- Designed for DJ-style or performance use.

### Auto Filter

- Low-pass, high-pass, band-pass, notch and morphing filters.
- Filter-model or circuit options.
- Resonance and drive.
- LFO modulation.
- Envelope follower.
- Sidechain input.
- Tempo-synchronized and free rates.

## 10.4 Dynamics

### Compressor

- Threshold, ratio, attack, release and knee.
- Automatic and manual release.
- Makeup gain.
- Peak and RMS detection where useful.
- Sidechain input and sidechain EQ.
- Gain-reduction meter.
- Dry/wet for parallel compression.

### Bus compressor

- Simplified musical compression for groups and Main output.
- Threshold, ratio, attack, release and makeup.
- Soft clipping option.
- Sidechain input.
- Dry/wet blend.

### Gate and expander

- Threshold, return or hysteresis, attack, hold and release.
- Gate and expansion modes.
- Sidechain input and filter.
- Gain-reduction display.

### Limiter

- Ceiling.
- Input gain.
- Lookahead.
- Release or automatic release.
- True-peak mode.
- Gain-reduction and clipping indication.

### De-esser

- Frequency range selection.
- Wide-band and split-band modes.
- Threshold and reduction amount.
- Audition of the detected sibilance band.

### Multiband dynamics

- At least three frequency bands.
- Adjustable crossovers.
- Compression and expansion above and below thresholds.
- Per-band solo and bypass.
- Per-band time constants or linked timing.
- Global dry/wet and output gain.

### Transient shaper

- Attack and sustain control.
- Sensitivity and timing.
- Frequency focus where supported.
- Output protection.

## 10.5 Saturation, distortion and amp processing

### Saturator

- Several waveshaping curves.
- Drive and output compensation.
- Tone or color control.
- Soft clipping.
- Dry/wet.
- Oversampling.

### Multistage distortion

- One, two or three processing stages.
- Serial, parallel and multiband routing.
- Multiple distortion algorithms.
- Per-stage drive, tone, feedback and dry/wet.
- Modulation sources and envelope following.
- Output limiting or clipping protection.

### Overdrive and pedal effects

- Overdrive, distortion and fuzz characters.
- Drive, tone, dynamics and output.
- Bass-preservation option.
- Dry/wet blend.

### Amp and cabinet

- Clean, crunch, lead and bass amp models.
- Gain, tone stack, presence and output.
- Cabinet type and microphone position or equivalent tone controls.
- Separate amp and cabinet devices for flexible routing.
- Mono and stereo use.

### Bit and sample-rate reducer

- Bit-depth reduction.
- Sample-rate reduction.
- Jitter, filtering or smoothing.
- Dry/wet.

## 10.6 Delay and time effects

### Stereo Delay

- Independent left and right delay times.
- Tempo-synchronized and millisecond modes.
- Feedback.
- Ping-pong option.
- Input and feedback filtering.
- Modulation.
- Freeze or infinite-hold behavior.
- Dry/wet.

### Echo

- Stereo, ping-pong and mid/side modes.
- Delay-time offset.
- Feedback path filtering.
- Modulation and character controls.
- Ducking.
- Reverb or diffusion in the feedback path.
- Freeze.

### Filter Delay

- Separate low, mid and high delay bands.
- Per-band time, feedback, pan and level.
- Tempo-synchronized and free time.

### Grain Delay

- Grain size and delay time.
- Pitch shifting.
- Random pitch or spray.
- Feedback and dry/wet.

## 10.7 Reverb and space

### Algorithmic Reverb

- Predelay.
- Size and decay time.
- Early-reflection control.
- Diffusion.
- Input and decay filtering.
- Modulation.
- Stereo width.
- Freeze.
- Dry/wet.

### Hybrid Reverb

- Convolution and algorithmic engines.
- Serial and parallel engine routing.
- Factory and user impulse responses.
- Predelay, size, decay and damping.
- Spectral or modulation controls where appropriate.
- Input EQ and output EQ.
- CPU quality controls.

## 10.8 Modulation and movement

### Chorus and ensemble

- Rate, amount, delay and feedback.
- Multiple voice or ensemble modes.
- Stereo width.
- High-pass filtering.
- Dry/wet.

### Phaser and flanger

- Phaser and flanger modes.
- Rate, depth, feedback and center frequency or delay.
- Tempo synchronization.
- Stereo phase offset.
- Envelope following where useful.

### Auto Pan and Tremolo

- Panning and amplitude-modulation modes.
- LFO shape, rate, phase and amount.
- Tempo-synchronized and free rates.
- Random or sample-and-hold option.

## 10.9 Pitch, vocal and spectral processing

### Pitch correction

- Detect incoming pitch.
- Select key and scale or follow the project scale.
- Correction amount and speed.
- Note-transition and humanization controls.
- Formant preservation.
- MIDI-guided target notes where supported.
- Low-latency monitoring mode.

### Pitch and frequency shifter

- Semitone and cent pitch shifting.
- Frequency shifting in Hertz.
- Fine delay between channels.
- LFO and envelope modulation.
- Feedback.
- Formant control where applicable.

### Vocoder

- External or internal carrier.
- Adjustable band count.
- Frequency range and formant shift.
- Attack, release and bandwidth.
- Unvoiced or noise control.
- Dry/wet.

### Resonator

- Multiple tuned resonant filters.
- Root note, interval and decay controls.
- Scale-aware tuning.
- Input filtering.
- Pitch following or MIDI control.

### Spectral and granular processor

- Freeze or hold spectral content.
- Delay or smear frequency regions.
- Shift, blur and modulate partials.
- Tempo-aware time controls where appropriate.
- Processor quality modes.

## 10.10 Rhythmic and performance effects

### Beat Repeat

- Capture and repeat rhythmic slices.
- Interval, offset, grid and variation.
- Chance, gate, pitch decay and mix behavior.
- Momentary performance triggering.

### Looper

- Record, overdub, play, stop and clear.
- Project-synchronized or free recording.
- Set project tempo from the loop where appropriate.
- Undo last overdub.
- Feedback.
- Reverse and speed changes.
- Drag or commit the loop into a normal audio clip.

### Performance rack

- Combine several effects behind macros.
- Map momentary and continuous controls.
- Store named variations.
- Crossfade parallel chains.
- Reset safely to a neutral state.

## 10.11 Recording cleanup

### Noise reduction

- Learn or detect a noise profile.
- Reduction amount and sensitivity.
- Artifact control.
- Voice-focused mode where useful.
- Preview removed noise.
- Offline high-quality render option.

### De-click and de-plosive tools

- Detect short clicks, pops and mouth noises.
- Reduce plosive low-frequency bursts.
- Preview before rendering.
- Preserve the original recording.

# 11. Device chains and racks

## 11.1 Chain operations

- Add a device at a chosen insertion point.
- Replace a device while preserving compatible automation or mappings when possible.
- Reorder devices.
- Duplicate, copy and move devices between tracks.
- Bypass one device or a selected group.
- Fold or expand device detail without changing sound.
- Save one device, a chain, a rack or a complete track preset.
- Collect samples required by a preset.
- Search presets without leaving the musical context.

## 11.2 Parallel chains

- Add multiple chains inside an Instrument, MIDI Effect or Audio Effect Rack.
- Process chains in parallel and sum their output.
- Set per-chain volume, pan and activation.
- Define key, velocity and chain-selector zones.
- Crossfade overlapping zones.
- Solo and audition a chain.
- Nest racks where processor limits permit it.

## 11.3 Macros and variations

- Map one macro to several parameters.
- Map several macros to one parameter.
- Define each mapping's minimum, maximum, direction and range.
- Rename and color macros.
- Store named macro variations.
- Randomize selected macros or all macros.
- Exclude safety-critical parameters from randomization.
- Morph or crossfade between stored variations where feasible.
- Record macro movement as automation.

# 12. Automation and modulation workflow

## 12.1 Track automation

Track automation belongs to the Arrangement timeline.

- Record mixer and device movements.
- Draw breakpoint envelopes.
- Add curves and predefined shapes.
- Move, copy, duplicate, scale and stretch automation.
- Simplify dense recorded automation.
- Temporarily override automation.
- Return a parameter to automated playback.
- Lock automation to musical time when clips move, or keep it fixed when deliberately unlocked.

## 12.2 Clip envelopes

Clip envelopes travel with a clip and can exist in Session or Arrangement.

- Automate or modulate mixer, instrument, effect and MIDI CC parameters.
- Loop independently from the clip's note or audio loop where supported.
- Link or unlink envelope length from the clip.
- Copy envelopes with linked clips.
- Convert Session clip automation into Arrangement automation when recording a Session performance.

## 12.3 Modulation

Modulation changes a parameter around its current value without replacing the underlying automation.

Sources:

- LFO.
- Envelope follower.
- Shaper or custom envelope.
- Step modulator.
- Random or sample-and-hold.
- Note, velocity, pressure, slide and key tracking.
- Rack macros.

Functions:

- Map one source to multiple targets.
- Set bipolar or unipolar amount.
- Set smoothing.
- Tempo-sync or free-run modulation.
- Retrigger per note, clip, bar or transport start where relevant.
- Preserve modulation mappings in presets and racks.

# 13. Session-to-Arrangement workflow

## 13.1 Improvisation

- Session clips can launch in any order.
- Scene launches can change several tracks together.
- Global and per-clip launch quantization keep changes musical.
- The Arrangement timeline continues advancing while Session clips play.
- Session clips override Arrangement playback only on their own tracks.
- Per-track and global Return to Arrangement commands restore timeline playback.

## 13.2 Capture into Arrangement

When Arrangement Record is active, the app records:

- Which Session clips launched.
- Exact launch and stop times after quantization.
- Scene changes.
- Clip-property changes made during performance.
- Mixer movements.
- Instrument and effect automation.
- Tempo and time-signature changes triggered by scenes.

The capture creates Arrangement clip instances and automation. It does not duplicate underlying audio unless a render operation is requested.

## 13.3 Editing after capture

- Captured clips remain linked to their Session source until explicitly unlinked.
- Arrangement placement can change without moving the Session source.
- The user can edit captured timing and automation.
- The user can replace a captured passage with another Session variation.
- The user can consolidate an Arrangement section into a new Session scene.
- The user can perform another pass without destroying the earlier Arrangement version.

# 14. Recording, comping, bounce and resampling

## 14.1 Comping

- Loop or repeated recording creates take lanes.
- Audio and MIDI takes are supported.
- The newest take is immediately auditionable.
- Any range from a take can be promoted to the main lane.
- Comp selections can crossfade where audio boundaries meet.
- Original takes remain available until deliberately deleted.
- Take lanes can be renamed, reordered, muted and auditioned.

## 14.2 Bounce and freeze

The app must support:

- Bounce clip to new audio.
- Bounce time selection to new audio.
- Bounce track in place.
- Bounce track to a new track while preserving the source.
- Bounce group to audio.
- Freeze and unfreeze a processor-heavy track.
- Include instrument and insert effects in the render.
- Choose whether mixer volume, pan, sends and Main effects are included.
- Preserve render tails from reverb and delay.

## 14.3 Resampling

- Record the output of one track.
- Record the output of a group.
- Record a return track.
- Record the Main output.
- Record a performance-effect pass.
- Immediately use the new recording as a clip, sampler source or Drum Rack slice source.

# 15. Translation to a phone

The mobile version must preserve functional depth without pretending that a phone is a small desktop monitor.

## 15.1 Functional adaptation principles

- Every desktop-class operation must have a phone-complete path.
- No essential feature may require a tablet, mouse or hardware keyboard.
- Each major task must be possible in a focused working context: arranging, clip editing, instrument editing, effect editing, mixing or browsing.
- Moving between focused contexts must not stop audio.
- The current track, clip, device, parameter and time selection must remain selected when moving between contexts.
- Portrait and landscape must expose the same project capabilities.
- Rotation must not dismiss edits, reset zoom, stop recording or move the playhead.
- The user must be able to return to the previous musical context without searching for it again.

## 15.2 Precision on a small screen

Every continuously adjustable parameter should support:

- Fast broad adjustment.
- Fine adjustment.
- Exact numeric entry where meaningful.
- Reset to default.
- Undo.
- Clear display of the current value and unit.
- Automation and mapping access.

Selection-based editors must provide enough separation between selecting, moving, resizing, drawing and deleting that accidental destructive edits are uncommon. Exact gestures remain a UX decision.

## 15.3 Progressive depth

Each complex instrument or effect should have:

- Essential musical controls.
- Full editing depth.
- Preset access.
- Modulation and automation access.
- Input/output or analysis information where relevant.

Simplifying the first level must never remove parameters from saved projects or make advanced editing impossible.

## 15.4 Mobile performance and processor management

- Audio has priority over visual refresh and background analysis.
- Instruments and effects can expose Eco, Standard and High quality where needed.
- High-quality offline export can exceed real-time playback quality.
- The app monitors processor load, thermal pressure, memory and storage.
- Processor warnings identify the expensive track or device where possible.
- Freeze and bounce are available without leaving the project workflow.
- Disabling an unused oscillator, voice, oversampling mode or effect section should reduce processor use.
- The engine must avoid changing sound quality automatically during export.
- Playback should degrade gracefully before audio is interrupted.

## 15.5 Mobile interruption handling

- Incoming calls, alarms, audio-route changes and app backgrounding must not corrupt a project.
- Recording must be finalized or recoverable after interruption.
- Bluetooth, headphones and USB interfaces can be connected or disconnected safely.
- The app must report when the active audio input or output changes.
- Autosave must capture recent edits without interrupting audio.
- Unsaved recorded audio must be recoverable after a crash.

## 15.6 Mobile hardware integration

- Onscreen keyboard, pads and controls are first-class inputs.
- External USB and Bluetooth MIDI can be used at the same time as touch input.
- MIDI learn maps hardware controls to macros, mixer and device parameters.
- USB audio interfaces support available inputs and outputs.
- Latency and buffer settings are understandable but remain adjustable.
- External hardware mappings are saved per device and project.

# 16. Required acceptance scenarios

These scenarios describe the complete behavior the app must eventually support.

## 16.1 Beat from scratch

1. Create a project.
2. Add a Drum Rack.
3. Preview and load samples.
4. Finger-drum or generate a rhythm.
5. Edit velocity, probability, ratchets and timing.
6. Duplicate the clip into variations.
7. Launch variations in Session.
8. Record the performance into Arrangement.
9. Mix individual pads and export the result.

## 16.2 Synth composition

1. Add a synth preset.
2. Record or draw a chord clip.
3. Add a scale-aware arpeggiator.
4. Record the processed MIDI to a new clip.
5. Transform the notes with strum and velocity shaping.
6. Automate macros.
7. Bounce the result to audio while preserving the source.

## 16.3 Vocal production

1. Add an audio track and choose an input.
2. Set monitoring, count-in and recording level.
3. Record several loop takes.
4. Comp the best phrases.
5. Add fades and timing corrections.
6. Add cleanup, pitch correction, EQ, compression, de-essing and reverb send.
7. Automate vocal level.
8. Bounce or export the finished vocal stem.

## 16.4 Session over Arrangement

1. Play an existing Arrangement.
2. Launch a Session clip on one track.
3. Confirm that the Session clip replaces only that track's Arrangement clip.
4. Confirm that all other Arrangement tracks continue.
5. Launch additional Session clips and scenes.
6. Return one track to Arrangement.
7. Return all tracks to Arrangement.
8. Record the Session performance into a new Arrangement pass.

## 16.5 Complete phone mix

1. Group drums, music and vocals.
2. Create reverb and delay returns.
3. Add track, group and Main effects.
4. Configure sidechain compression.
5. Draw and record automation.
6. Check loudness, true peak, phase and mono compatibility.
7. Freeze or bounce tracks if processor load is high.
8. Export a master and stems without a desktop computer.

## 16.6 Orientation and recovery

1. Start playback and select a detailed edit.
2. Rotate between portrait and landscape.
3. Confirm that playback, selection, zoom, editor and undo history persist.
4. Background and resume the app.
5. Recover the same project state without duplicated notes, lost automation or missing recorded audio.

# 17. Open product decisions

These decisions should be answered before the implementation specification is finalized.

- Target platforms at launch: Android, iPhone or both.
- Maximum supported track count, device count and send count.
- Supported project sample rates and recording bit depths.
- Whether third-party instrument and effect plug-ins are supported at launch.
- Which mobile plug-in standards are supported on each platform.
- Whether custom tuning systems beyond standard scales are required.
- Whether stem separation is part of the core app or a later expansion.
- Whether high-quality noise reduction is real-time, offline or both.
- Whether advanced physical-model instruments are included at launch.
- Whether advanced spectral effects are included at launch.
- Whether MIDI generators can be extended by users or third parties.
- Whether project exchange with desktop DAWs is required in addition to audio, stems and MIDI export.
- Exact boundaries between the first complete release and later expansion packs.

# 18. Part 2 completion statement

Part 2 defines the target creative workflow and functional depth for:

- In-DAW music creation.
- Session and Arrangement cooperation.
- Linked clips.
- Signal flow.
- Built-in instruments.
- Drum and sampler systems.
- Onscreen musical input.
- MIDI recording and editing.
- MIDI transformations, generators and real-time effects.
- Scale, groove and MPE behavior.
- Audio effects.
- Device chains and racks.
- Automation and modulation.
- Recording, comping, bounce and resampling.
- Phone-specific functional adaptation.

The next specification stage should decide the launch scope and then walk through the app screen by screen without changing the functional rules established here.
