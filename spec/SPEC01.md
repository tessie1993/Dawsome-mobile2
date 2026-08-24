# Mobile DAW Functional Specification Blueprint

## Functional scope only

This describes what the app does and contains—without deciding layouts, gestures, styling, or screen design.

The recurring mobile-DAW baseline includes multitrack audio/MIDI production, instruments, recording, editing, mixing, automation and export.

## Features appearing in most mobile DAWs

- Multitrack projects
- Linear Arrangement timeline
- Audio tracks
- MIDI/instrument tracks
- Drum tracks
- Audio recording
- MIDI recording
- Audio and MIDI importing
- Piano-roll editor
- Quantization and swing
- Audio region editing
- Built-in instruments
- Drum machine or sampler
- Built-in effects
- Mixer with volume, pan, mute and solo
- Automation
- Loop and sample browser
- Tempo, metronome and count-in
- Loop/cycle playback
- Undo and redo
- Project saving and autosave
- Stereo mix export
- Track or stem export
- External MIDI-controller support
- Audio-interface support
- Time-stretching and pitch-shifting
- Track freezing, bouncing or rendering
- Cloud or file-based project transfer

Session-style clip launching appears in several major apps, but it is not universal.

## English description of our app

The app is a complete, standalone digital audio workstation for phones.

It lets musicians create an entire song without needing a computer. A user can record vocals and instruments, program drums, play synthesizers, edit samples, compose MIDI, arrange a track, mix it, master it and export the finished result.

It combines two equally important production methods:

- Session View for experimenting with clips, loops, patterns and scenes.
- Arrangement View for building a complete song on a linear timeline.

Both views belong to the same project and use the same tracks, instruments, effects and mixer.

The app is intended to be a serious production tool rather than a restricted sketchpad.

## Why someone would use it

- Produce a complete song anywhere.
- Work without owning a desktop DAW.
- Capture musical ideas immediately.
- Record vocals or instruments directly.
- Create drums, melodies, harmonies and sound design.
- Experiment non-linearly before arranging a song.
- Perform with clips and scenes.
- Mix and master inside the same app.
- Connect MIDI keyboards, pad controllers and audio interfaces.
- Export a finished track, stems, loops or MIDI.
- Continue working offline.
- Avoid transferring unfinished projects between several apps.

# Complete functional walkthrough

## 1. Project system

The project system manages songs and their associated files.

Functions:

- Create an empty project.
- Create from a template.
- Open recent projects.
- Search projects.
- Rename, duplicate and delete projects.
- Organize projects into folders.
- Save automatically.
- Save named versions and snapshots.
- Restore previous versions.
- Recover after a crash.
- Collect imported samples into the project.
- Detect and replace missing files.
- Archive a complete portable project.
- Import projects.
- Export transferable projects.
- Display project duration, tempo, key and storage use.

## 2. Transport and song controls

The transport controls the entire project.

Functions:

- Play.
- Pause.
- Stop.
- Record.
- Return to the beginning.
- Move to a specific bar or time.
- Set tempo.
- Tap tempo.
- Set time signature.
- Set musical key and scale.
- Enable metronome.
- Configure count-in.
- Enable loop/cycle playback.
- Set loop start and end.
- Add song markers.
- Enable punch-in and punch-out recording.
- Control global quantization.
- Undo and redo.
- Display CPU, audio and recording status.

## 3. Arrangement View

Arrangement View builds the finished song along a timeline.

Functions:

- Display all tracks against bars, beats or time.
- Record audio and MIDI directly into the timeline.
- Add, move, copy and delete clips.
- Split and join clips.
- Resize and loop clips.
- Duplicate sections.
- Create song markers and named sections.
- Select and move multiple clips.
- Ripple-edit later material.
- Insert or remove time.
- Change tempo and time signature during a song.
- Display automation lanes.
- Display take lanes.
- Record a Session performance into the Arrangement.
- Freeze or bounce tracks.
- Export selected sections.

## 4. Session View

Session View is used for improvisation, loop-based creation and live performance.

Functions:

- Store multiple clips on every track.
- Launch individual clips.
- Launch complete scenes.
- Quantize clip and scene launching.
- Record audio into an empty clip.
- Record MIDI into an empty clip.
- Overdub an existing clip.
- Set independent clip lengths.
- Set clip launch modes.
- Configure one-shot or looping playback.
- Add follow actions.
- Assign scene tempo and time signature.
- Duplicate clips and scenes.
- Capture a performance into Arrangement View.
- Move or copy clips between Session and Arrangement.
- Link clip copies until the user explicitly unlinks them.
- Stop one track, one scene or all Session clips.

The precise rule for simultaneous Session and Arrangement playback remains unresolved because “layering” and “behaving like Ableton” describe different models.

## 5. Track system

Tracks contain musical material and determine how it is processed.

Track types:

- Audio track
- MIDI track
- Instrument track
- Drum track
- Sampler track
- Group track
- Return/auxiliary track
- Master track
- External instrument track
- Resampling track

Track functions:

- Add, rename, duplicate and delete.
- Change color and icon.
- Arm for recording.
- Select audio or MIDI input.
- Enable input monitoring.
- Mute, solo and isolate.
- Adjust volume and pan.
- Route audio and MIDI.
- Add sends.
- Add instruments and effects.
- Freeze, flatten, bounce or resample.
- Group and ungroup.
- Save a track as a reusable preset.
- Export an individual track.

## 6. Audio recording

Audio recording captures microphones, instruments and external hardware.

Functions:

- Record through the phone microphone.
- Record through a USB audio interface.
- Select mono or stereo input.
- Record multiple inputs where supported.
- Monitor the input.
- Apply low-latency monitoring.
- Record with or without effects.
- Set recording gain.
- Calibrate recording latency.
- Use count-in and metronome.
- Punch in and out.
- Loop-record multiple takes.
- Create take lanes.
- Comp the best parts of multiple takes.
- Record while other tracks play.
- Automatically name recordings.
- Recover interrupted recordings.

## 7. MIDI recording and editing

The MIDI system records and edits musical notes and controller data.

Functions:

- Record from an onscreen instrument.
- Record from an external MIDI controller.
- Step-record notes.
- Overdub MIDI.
- Edit notes in a piano roll.
- Move, copy, resize and delete notes.
- Edit velocity.
- Edit note probability.
- Edit pitch bend, modulation, aftertouch and MIDI CC.
- Quantize note position and length.
- Add swing.
- Humanize timing and velocity.
- Transpose notes.
- Fold to used notes.
- Constrain notes to a scale.
- Generate chords.
- Apply legato, strum and arpeggiation.
- Edit multiple MIDI clips together.
- Import and export MIDI files.
- Support MIDI learn and controller mapping.

## 8. Audio clip editor

The audio editor changes recorded or imported sound without requiring another app.

Functions:

- Trim and crop.
- Split and join.
- Duplicate and loop.
- Reverse.
- Normalize.
- Change clip gain.
- Add fades and crossfades.
- Remove silence.
- Detect transients.
- Stretch audio to tempo.
- Add and edit warp markers.
- Transpose audio.
- Preserve or change formants.
- Change playback speed.
- Slice audio into samples.
- Convert slices into a drum or sampler instrument.
- Consolidate several edits into one clip.
- Render destructive edits as a new file while preserving the original.

## 9. Drum Rack

Drum Rack creates kits and rhythmic patterns.

Functions:

- Load one sample or instrument per pad.
- Record samples directly into pads.
- Drag samples into pads.
- Replace, copy and move pads.
- Set choke groups.
- Layer several samples on a pad.
- Configure velocity layers.
- Tune, trim and fade each pad.
- Reverse individual sounds.
- Set one-shot or loop playback.
- Add per-pad effects.
- Route pads separately to the mixer.
- Program beats in a step sequencer.
- Edit velocity, probability, repeats and timing.
- Save and load complete kits.

## 10. Sampler

The sampler converts recordings and files into playable instruments.

Functions:

- Record or import a sample.
- Automatically detect pitch.
- Set root note and playable range.
- Trim start and end.
- Create forward, reverse and alternating loops.
- Add loop crossfades.
- Slice by transient, beat or equal division.
- Map slices chromatically or to pads.
- Create multisampled instruments.
- Add filter, envelopes and LFOs.
- Control pitch, volume and panning.
- Time-stretch samples.
- Save sampler presets.

## 11. Instruments

The app includes enough instruments to create complete productions without third-party plug-ins.

Instrument families:

- Subtractive synthesizer
- Wavetable synthesizer
- FM synthesizer
- Sample-based instrument
- Drum synthesizer
- Bass instrument
- Piano and electric piano
- Organ
- Strings and orchestral sounds
- Guitar and bass instruments
- Pads and atmospheric instruments

Common functions:

- Preset browser.
- Oscillators and sample sources.
- Filters.
- Envelopes.
- LFOs.
- Modulation matrix.
- Arpeggiator.
- Unison and voice controls.
- Glide and legato.
- Macro controls.
- Save custom presets.

## 12. Browser and sound library

The browser finds everything that can be added to a project.

Functions:

- Browse instruments.
- Browse effects.
- Browse presets.
- Browse samples and loops.
- Browse drum kits.
- Browse project templates.
- Search by name.
- Filter by instrument, genre, mood, key, tempo or type.
- Preview sounds before loading.
- Preview loops synchronized to project tempo.
- Mark favorites.
- Show recently used items.
- Import user samples and folders.
- Download optional sound packs.
- Manage storage used by packs.
- Locate missing project files.

## 13. Device chain and racks

The device chain processes each track.

Functions:

- Add instruments, MIDI effects and audio effects.
- Reorder devices.
- Bypass individual devices.
- Adjust dry/wet balance.
- Save a complete chain as a preset.
- Create parallel processing chains.
- Split by frequency, velocity or note range.
- Map parameters to macros.
- Randomize selected parameters.
- Compare alternative settings.
- Copy devices between tracks.
- Freeze or render expensive chains.

## 14. Mixer

The mixer combines all tracks into the final output.

Functions:

- Volume faders.
- Pan and stereo balance.
- Mute and solo.
- Record arm.
- Input monitoring.
- Peak and RMS meters.
- Gain reduction meters.
- Insert effects.
- Sends and returns.
- Pre-fader and post-fader sends.
- Groups and submixes.
- Track routing.
- Sidechain routing.
- Cue/headphone mix.
- Master channel.
- Track delay compensation.
- Mono checking.
- Phase inversion.
- Channel presets.

## 15. Effects

Included audio effects should cover recording, production, mixing and mastering.

Categories:

- Equalizer
- Compressor
- Limiter
- Gate
- De-esser
- Multiband dynamics
- Reverb
- Delay
- Chorus
- Flanger
- Phaser
- Distortion and saturation
- Bitcrusher
- Filter
- Auto-filter
- Tremolo and autopan
- Pitch correction
- Pitch shifting
- Vocal processing
- Guitar and bass amplifiers
- Cabinet simulation
- Transient shaper
- Stereo-width utility
- Frequency analyser
- Tuner
- Noise reduction
- Creative glitch and repeat effects

## 16. Routing

Routing controls how audio and MIDI travel through the project.

Functions:

- Route one track into another.
- Route several tracks into a group.
- Create send effects.
- Create parallel processing.
- Sidechain one track from another.
- Route individual drum pads.
- Route MIDI between tracks.
- Control external MIDI hardware.
- Record the output of another track.
- Resample the master output.
- Create cue and monitor mixes.
- Prevent feedback loops.
- Save routing configurations in templates.

## 17. Automation and modulation

Automation records deliberate parameter changes over time.

Functions:

- Automate volume, pan, sends and mute.
- Automate instrument parameters.
- Automate effect parameters.
- Draw automation points and curves.
- Record parameter movement.
- Use read, write, touch and latch modes.
- Edit automation inside clips.
- Edit automation across the Arrangement.
- Copy and scale automation.
- Temporarily override automation.
- Map an LFO to parameters.
- Use envelope followers.
- Map macros to multiple parameters.
- Map hardware MIDI controls.
- Create reusable modulation assignments.

## 18. Performance functions

Performance functions turn the project into a playable instrument.

Functions:

- Launch clips and scenes.
- Quantize launches.
- Map clips to MIDI pads.
- Map effects to knobs.
- Crossfade between track groups.
- Use momentary performance effects.
- Capture unrecorded MIDI performances.
- Record the performance into Arrangement.
- Prevent accidental project edits.
- Continue audio when changing sections.
- Synchronize with other apps or devices using Ableton Link or MIDI clock.

## 19. Mixing and mastering

The mastering section prepares the finished release.

Functions:

- Master EQ.
- Bus compression.
- Saturation.
- Stereo control.
- Limiting.
- Loudness metering.
- Spectrum analysis.
- True-peak detection.
- Reference-track comparison.
- Mono and phase checks.
- Mastering presets.
- Loudness targets for streaming.
- Dither for lower bit-depth exports.

## 20. Export and sharing

Export turns the project into usable files.

Functions:

- Export the complete stereo mix.
- Export every track as stems.
- Export selected tracks.
- Export groups.
- Export loop regions.
- Export MIDI.
- Export at different sample rates and bit depths.
- Export WAV, FLAC, AAC or MP3.
- Include or exclude master effects.
- Normalize on export.
- Render tails from delay and reverb.
- Export project archives.
- Share through the device file system.
- Save directly to external or cloud storage.

Stem and track export are already prominent in several mobile DAWs.

## 21. Hardware and connectivity

Functions:

- USB audio-interface input and output.
- USB MIDI.
- Bluetooth MIDI.
- MIDI clock input and output.
- Ableton Link synchronization.
- Multiple audio inputs where supported.
- Multiple audio outputs where supported.
- External instrument monitoring.
- Controller mapping.
- Sustain pedal and expression input.
- Headphone and cue routing.
- Background audio operation.

## 22. Reliability and data protection

Functions:

- Continuous autosave.
- Crash recovery.
- Recording recovery.
- Undo history.
- Named versions.
- Non-destructive editing.
- Missing-file detection.
- Storage warnings.
- CPU-overload warnings.
- Safe recording when storage is low.
- Project backup.
- Offline operation.
- No desktop dependency.

This is the candidate functional inventory. It describes the complete app before we decide which features are mandatory, optional or excluded.
