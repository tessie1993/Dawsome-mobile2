package com.example.synth.engine

import com.example.synth.domain.ArrangementClip
import com.example.synth.domain.ProjectAction
import com.example.synth.domain.ProjectState
import com.example.synth.domain.ProjectStore
import com.example.synth.domain.SessionClip
import com.example.synth.domain.TrackModel
import com.example.synth.domain.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The change-classification seam of the dual-model architecture (blueprint
 * Kotlin engine/EngineSync). Three change classes:
 *
 *  - transport intents  -> Transport messages (RT-applied immediately)
 *  - parameter moves    -> Param/Move addressed (nodeUid, paramKeyHash)
 *  - structure edits    -> ModelDelta bundles ([DeltaEncoder]) applied by
 *                          the GraphBuilder's EngineModel off-thread
 *
 * Everything is stamped with the store's monotonic editSeq. A null action
 * from the store (undo/redo) and every RUNNING transition trigger a FULL
 * model push + param re-send - deltas are idempotent upserts, so wholesale
 * resync is always safe. Cascading removals (track delete) derive from the
 * PRE-change state; shared ClipContent is removed only when no clip in the
 * post-change state still references it.
 *
 * Linked clips: the canonical content id of a linked arrangement/session
 * pair is the lexicographic MIN of the two clip ids - symmetric and
 * deterministic on both sides of the link. (The explicit ClipContent entity
 * with copy-on-unlink materializes with the session workflow milestone;
 * this derivation is forward-compatible with it.)
 *
 * Values are read from the POST-reduction [ProjectState] - the reducer's
 * coercions are authoritative.
 */
class EngineSync(
    private val store: ProjectStore,
    private val controller: EngineController,
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var lastState: ProjectState = store.state.value
    private var stateJob: Job? = null

    fun attach() {
        controller.onReconcileNeeded = { resendAuthoritativeParams() }
        store.onEngineSync = ::onChange
        lastState = store.state.value
        pushFullModel(lastState, store.editSeq)
        resendAuthoritativeParams()
        // Re-run the full sync whenever the engine (re)starts: covers
        // release->start recreation and D5 reopens. Idempotent by design.
        stateJob = scope.launch {
            controller.state.collect { s ->
                if (s == EngineController.EngineState.RUNNING) {
                    pushFullModel(store.state.value, store.editSeq)
                    resendAuthoritativeParams()
                }
            }
        }
    }

    fun detach() {
        stateJob?.cancel()
        stateJob = null
        store.onEngineSync = null
        controller.onReconcileNeeded = null
    }

    // Runs on the store's dispatcher; controller calls post to engine-io.
    private fun onChange(action: ProjectAction?, state: ProjectState, editSeq: Int) {
        if (action == null) {                    // undo/redo: state is authoritative
            pushFullModel(state, editSeq)
            resendAuthoritativeParams()
            lastState = state
            return
        }
        when (action) {
            // ---- transport ----------------------------------------------------
            is ProjectAction.Play -> controller.send { play() }
            is ProjectAction.Stop -> controller.send { stop() }
            is ProjectAction.TogglePlay -> controller.send { togglePlay() }
            is ProjectAction.ToggleRecord -> controller.send { record(state.isRecording) }
            is ProjectAction.ToggleLoop -> controller.send { loop(state.isLooping) }
            is ProjectAction.SeekToBeat ->
                controller.send { seekBeat(state.playheadBeat.toDouble()) }
            is ProjectAction.SetLoopRegion -> controller.send {
                setLoopRegion(state.loopStartBeat.toDouble(), state.loopEndBeat.toDouble())
            }
            is ProjectAction.SetBpm -> {
                controller.send { setTempo(state.bpm.toDouble()) }   // live splice
                val d = DeltaEncoder(editSeq)                        // canonical map
                d.tempoMap(listOf(0.0 to state.bpm.toDouble()),
                    state.timeSigNum, state.timeSigDen)
                controller.sendModelDelta(d.build())
            }

            // ---- parameter moves ----------------------------------------------
            is ProjectAction.SetTrackVolume ->
                sendTrackParam(state, action.trackId, ParamKeys.MIXER_VOLUME, editSeq)
            is ProjectAction.SetTrackPan ->
                sendTrackParam(state, action.trackId, ParamKeys.MIXER_PAN, editSeq)
            is ProjectAction.ToggleTrackMute -> {
                // Param drives the RT strip; the track delta keeps the model
                // flags (audibility matrix input, M2) in step.
                sendTrackParam(state, action.trackId, ParamKeys.MIXER_MUTE, editSeq)
                sendTrackUpsert(state, editSeq, action.trackId)
            }
            is ProjectAction.SetTrackSend -> sendTrackParam(
                state, action.trackId,
                if (action.sendIndex == 0) ParamKeys.MIXER_SEND_A else ParamKeys.MIXER_SEND_B,
                editSeq)
            is ProjectAction.SetDeviceParam -> {
                sendParam(
                    WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_DEVICE, action.deviceId),
                    action.paramName, action.value.toDouble(), editSeq)
                // Model residency for rebuilds: refresh this one device row.
                // Param-only device deltas never mark the graph dirty.
                sendSingleDevice(state, editSeq, action.trackId, action.deviceId)
            }
            is ProjectAction.SetMasterVolume -> {
                // Param for the live strip; the type-4 track delta bakes the
                // value into the model so rebuilt graphs start correct.
                sendParam(WireProtocol.masterNodeUid, ParamKeys.MIXER_VOLUME,
                    state.masterVolumeDb.toDouble(), editSeq)
                sendMasterUpsert(state, editSeq)
            }

            // ---- structure edits -> ModelDelta bundles ------------------------
            is ProjectAction.AddTrack -> {
                val added = state.tracks.filter { t -> lastState.tracks.none { it.id == t.id } }
                if (added.isNotEmpty()) {
                    val d = DeltaEncoder(editSeq)
                    for (t in added) encodeTrackFull(d, state, t)
                    controller.sendModelDelta(d.build())
                }
            }
            is ProjectAction.DeleteTrack -> {
                lastState.tracks.firstOrNull { it.id == action.trackId }?.let { gone ->
                    val d = DeltaEncoder(editSeq)
                    encodeTrackRemoval(d, state, gone)
                    controller.sendModelDelta(d.build())
                }
            }
            is ProjectAction.AddArrangementClip ->
                sendClipAndContent(state, editSeq, action.clip.id)
            is ProjectAction.MoveArrangementClip ->
                sendClipPlacement(state, editSeq, action.clipId)
            is ProjectAction.ResizeArrangementClip ->
                sendClipPlacement(state, editSeq, action.clipId)
            is ProjectAction.DeleteArrangementClip -> {
                val d = DeltaEncoder(editSeq)
                encodeClipRemoval(d, state, action.clipId)
                if (!d.isEmpty) controller.sendModelDelta(d.build())
            }
            is ProjectAction.AddMidiNote ->
                sendContentOnly(state, editSeq, action.clipId)
            is ProjectAction.DeleteMidiNote ->
                sendContentOnly(state, editSeq, action.clipId)
            is ProjectAction.QuantizeClipNotes ->
                sendContentOnly(state, editSeq, action.clipId)
            is ProjectAction.ToggleDrumStep ->
                sendContentOnly(state, editSeq, action.clipId)
            is ProjectAction.AddDevice -> sendDeviceChain(state, editSeq, action.trackId)
            is ProjectAction.RemoveDevice -> sendDeviceChain(state, editSeq, action.trackId)
            is ProjectAction.ToggleDeviceEnabled -> {
                // Live click-free bypass rides the param path (chain-owned
                // device.bypass); the delta keeps the model's enabled flag
                // canonical for rebuilds.
                val enabled = state.tracks.firstOrNull { it.id == action.trackId }
                    ?.devices?.firstOrNull { it.id == action.deviceId }?.isEnabled
                if (enabled != null) {
                    sendParam(
                        WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_DEVICE, action.deviceId),
                        ParamKeys.DEVICE_BYPASS, if (enabled) 0.0 else 1.0, editSeq)
                }
                sendDeviceChain(state, editSeq, action.trackId)
            }
            // Solo/arm are track-structure facts (the M2 audibility matrix and
            // the recording system read them from Track deltas, not params).
            is ProjectAction.ToggleTrackSolo -> sendTrackUpsert(state, editSeq, action.trackId)
            is ProjectAction.ToggleTrackArm -> sendTrackUpsert(state, editSeq, action.trackId)

            // ---- session playback intents (SessionPlayer, M5) + UI-only ------
            is ProjectAction.TriggerSessionClip, is ProjectAction.TriggerScene,
            is ProjectAction.ReturnTrackToArrangement, is ProjectAction.ReturnAllToArrangement,
            is ProjectAction.SetScale, is ProjectAction.SelectTab,
            is ProjectAction.SelectTrack -> Unit
        }
        lastState = state
    }

    // ---- full model push ------------------------------------------------------

    fun pushFullModel(state: ProjectState, editSeq: Int) {
        val d = DeltaEncoder(editSeq)
        d.tempoMap(listOf(0.0 to state.bpm.toDouble()), state.timeSigNum, state.timeSigDen)
        for (scene in state.scenes) {
            d.upsertScene(WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_SCENE, scene.id),
                scene.index)
        }
        for (t in state.tracks) encodeTrackFull(d, state, t)
        encodeMaster(d, state)
        controller.sendModelDelta(d.build())
    }

    private fun sendMasterUpsert(state: ProjectState, editSeq: Int) {
        val d = DeltaEncoder(editSeq)
        encodeMaster(d, state)
        controller.sendModelDelta(d.build())
    }

    /** The master lane as a type-4 track row keyed by the well-known uid. */
    private fun encodeMaster(d: DeltaEncoder, state: ProjectState) {
        d.upsertTrack(WireProtocol.masterNodeUid, type = 4, flags = 0, order = 0xFFFF,
            volumeDb = state.masterVolumeDb, pan = 0f, sendA = 0f, sendB = 0f)
    }

    /**
     * Re-send every addressable value from the current model (reconcile) as
     * ONE ParamBlockSet frame - the bulk path presets/variations also ride.
     */
    fun resendAuthoritativeParams() {
        val state = store.state.value
        val editSeq = store.editSeq
        val entries = ArrayList<Triple<Long, Int, Float>>(state.tracks.size * 6)
        for (track in state.tracks) {
            val uid = WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_TRACK, track.id)
            entries.add(Triple(uid, WireProtocol.paramKey(ParamKeys.MIXER_VOLUME), track.volumeDb))
            entries.add(Triple(uid, WireProtocol.paramKey(ParamKeys.MIXER_PAN), track.pan))
            entries.add(Triple(uid, WireProtocol.paramKey(ParamKeys.MIXER_MUTE),
                if (track.isMuted) 1f else 0f))
            entries.add(Triple(uid, WireProtocol.paramKey(ParamKeys.MIXER_SEND_A), track.sendLevelA))
            entries.add(Triple(uid, WireProtocol.paramKey(ParamKeys.MIXER_SEND_B), track.sendLevelB))
            for (device in track.devices) {
                val duid = WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_DEVICE, device.id)
                entries.add(Triple(duid, WireProtocol.paramKey(ParamKeys.DEVICE_BYPASS),
                    if (device.isEnabled) 0f else 1f))
                for ((name, value) in device.params)
                    entries.add(Triple(duid, WireProtocol.paramKey(name), value))
            }
        }
        entries.add(Triple(WireProtocol.masterNodeUid,
            WireProtocol.paramKey(ParamKeys.MIXER_VOLUME), state.masterVolumeDb))
        controller.sendParamBlockSet(editSeq, entries)
    }

    // ---- delta encoding helpers ----------------------------------------------

    private fun encodeTrackFull(d: DeltaEncoder, state: ProjectState, t: TrackModel) {
        val trackUid = WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_TRACK, t.id)
        d.upsertTrack(trackUid, trackTypeWire(t.type), trackFlags(t), state.tracks.indexOf(t),
            t.volumeDb, t.pan, t.sendLevelA, t.sendLevelB)
        t.devices.forEachIndexed { order, dev ->
            d.upsertDevice(WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_DEVICE, dev.id),
                trackUid, deviceTypeWire(dev.type), dev.isEnabled, order,
                deviceWireParams(dev))
        }
        for (c in t.arrangementClips) encodeArrangementClip(d, trackUid, c)
        for (c in t.sessionClips) encodeSessionClip(d, trackUid, c)
    }

    private fun encodeArrangementClip(d: DeltaEncoder, trackUid: Long, c: ArrangementClip) {
        val contentId = contentIdOf(c.id, c.linkedSessionClipId)
        val contentUid = WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_CONTENT, contentId)
        d.upsertClip(WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_CLIP, c.id),
            trackUid, contentUid, c.startBeat.toDouble(), c.lengthBeats.toDouble(),
            slotIndex = -1, looping = true)
        d.upsertContent(contentUid, c.lengthBeats.toDouble(),
            wireNotes(c.notes, c.drumSteps))
    }

    private fun encodeSessionClip(d: DeltaEncoder, trackUid: Long, c: SessionClip) {
        val contentId = contentIdOf(c.id, c.linkedArrangementClipId)
        val contentUid = WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_CONTENT, contentId)
        d.upsertClip(WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_CLIP, c.id),
            trackUid, contentUid, 0.0, c.lengthBeats.toDouble(),
            slotIndex = c.slotIndex, looping = true)
        d.upsertContent(contentUid, c.lengthBeats.toDouble(),
            wireNotes(c.notes, c.drumSteps))
    }

    private fun encodeTrackRemoval(d: DeltaEncoder, postState: ProjectState, gone: TrackModel) {
        for (c in gone.arrangementClips) {
            d.remove(WireProtocol.ENTITY_CLIP,
                WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_CLIP, c.id))
            maybeRemoveContent(d, postState, contentIdOf(c.id, c.linkedSessionClipId))
        }
        for (c in gone.sessionClips) {
            d.remove(WireProtocol.ENTITY_CLIP,
                WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_CLIP, c.id))
            maybeRemoveContent(d, postState, contentIdOf(c.id, c.linkedArrangementClipId))
        }
        for (dev in gone.devices) {
            d.remove(WireProtocol.ENTITY_DEVICE,
                WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_DEVICE, dev.id))
        }
        d.remove(WireProtocol.ENTITY_TRACK,
            WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_TRACK, gone.id))
    }

    private fun encodeClipRemoval(d: DeltaEncoder, postState: ProjectState, clipId: String) {
        val old = lastState.tracks.asSequence()
            .flatMap { it.arrangementClips.asSequence() }
            .firstOrNull { it.id == clipId } ?: return
        d.remove(WireProtocol.ENTITY_CLIP,
            WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_CLIP, clipId))
        maybeRemoveContent(d, postState, contentIdOf(old.id, old.linkedSessionClipId))
    }

    /** Shared content is removed only when nothing in postState references it. */
    private fun maybeRemoveContent(d: DeltaEncoder, postState: ProjectState, contentId: String) {
        val referenced = postState.tracks.any { t ->
            t.arrangementClips.any { contentIdOf(it.id, it.linkedSessionClipId) == contentId } ||
                t.sessionClips.any { contentIdOf(it.id, it.linkedArrangementClipId) == contentId }
        }
        if (!referenced) {
            d.remove(WireProtocol.ENTITY_CLIP_CONTENT,
                WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_CONTENT, contentId))
        }
    }

    private fun sendTrackUpsert(state: ProjectState, editSeq: Int, trackId: String) {
        val t = state.tracks.firstOrNull { it.id == trackId } ?: return
        val d = DeltaEncoder(editSeq)
        d.upsertTrack(WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_TRACK, t.id),
            trackTypeWire(t.type), trackFlags(t), state.tracks.indexOf(t),
            t.volumeDb, t.pan, t.sendLevelA, t.sendLevelB)
        controller.sendModelDelta(d.build())
    }

    private fun trackFlags(t: TrackModel): Int {
        var flags = 0
        if (t.isMuted) flags = flags or 0x1
        if (t.isSoloed) flags = flags or 0x2
        if (t.isArmed) flags = flags or 0x4
        if (t.isOverriddenBySession) flags = flags or 0x8
        return flags
    }

    private fun sendClipAndContent(state: ProjectState, editSeq: Int, clipId: String) {
        val d = forClipInState(state, clipId, editSeq) { enc, trackUid, arr, sess ->
            if (arr != null) encodeArrangementClip(enc, trackUid, arr)
            if (sess != null) encodeSessionClip(enc, trackUid, sess)
        } ?: return
        if (!d.isEmpty) controller.sendModelDelta(d.build())
    }

    private fun sendClipPlacement(state: ProjectState, editSeq: Int, clipId: String) {
        // Placement changed, content untouched: upsert the clip frame only.
        for (t in state.tracks) {
            val trackUid = WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_TRACK, t.id)
            val c = t.arrangementClips.firstOrNull { it.id == clipId } ?: continue
            val contentUid = WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_CONTENT,
                contentIdOf(c.id, c.linkedSessionClipId))
            val d = DeltaEncoder(editSeq)
            d.upsertClip(WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_CLIP, c.id),
                trackUid, contentUid, c.startBeat.toDouble(), c.lengthBeats.toDouble(),
                slotIndex = -1, looping = true)
            controller.sendModelDelta(d.build())
            return
        }
    }

    private fun sendContentOnly(state: ProjectState, editSeq: Int, clipId: String) {
        val d = forClipInState(state, clipId, editSeq) { enc, _, arr, sess ->
            if (arr != null) {
                enc.upsertContent(
                    WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_CONTENT,
                        contentIdOf(arr.id, arr.linkedSessionClipId)),
                    arr.lengthBeats.toDouble(), wireNotes(arr.notes, arr.drumSteps))
            }
            if (sess != null) {
                enc.upsertContent(
                    WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_CONTENT,
                        contentIdOf(sess.id, sess.linkedArrangementClipId)),
                    sess.lengthBeats.toDouble(), wireNotes(sess.notes, sess.drumSteps))
            }
        } ?: return
        if (!d.isEmpty) controller.sendModelDelta(d.build())
    }

    private fun sendSingleDevice(
        state: ProjectState, editSeq: Int, trackId: String, deviceId: String,
    ) {
        val t = state.tracks.firstOrNull { it.id == trackId } ?: return
        val order = t.devices.indexOfFirst { it.id == deviceId }
        if (order < 0) return
        val dev = t.devices[order]
        val d = DeltaEncoder(editSeq)
        d.upsertDevice(WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_DEVICE, dev.id),
            WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_TRACK, trackId),
            deviceTypeWire(dev.type), dev.isEnabled, order, deviceWireParams(dev))
        controller.sendModelDelta(d.build())
    }

    private fun sendDeviceChain(state: ProjectState, editSeq: Int, trackId: String) {
        val t = state.tracks.firstOrNull { it.id == trackId } ?: return
        val trackUid = WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_TRACK, trackId)
        val d = DeltaEncoder(editSeq)
        // Removed devices (in lastState, not in state).
        lastState.tracks.firstOrNull { it.id == trackId }?.devices
            ?.filter { old -> t.devices.none { it.id == old.id } }
            ?.forEach { gone ->
                d.remove(WireProtocol.ENTITY_DEVICE,
                    WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_DEVICE, gone.id))
            }
        // Upsert the whole chain (order is positional).
        t.devices.forEachIndexed { order, dev ->
            d.upsertDevice(WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_DEVICE, dev.id),
                trackUid, deviceTypeWire(dev.type), dev.isEnabled, order,
                deviceWireParams(dev))
        }
        controller.sendModelDelta(d.build())
    }

    /** Find clipId in any track's arrangement or session lists (post-state). */
    private inline fun forClipInState(
        state: ProjectState, clipId: String, editSeq: Int,
        block: (DeltaEncoder, Long, ArrangementClip?, SessionClip?) -> Unit,
    ): DeltaEncoder? {
        for (t in state.tracks) {
            val arr = t.arrangementClips.firstOrNull { it.id == clipId }
            val sess = t.sessionClips.firstOrNull { it.id == clipId }
            if (arr == null && sess == null) continue
            val d = DeltaEncoder(editSeq)
            block(d, WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_TRACK, t.id), arr, sess)
            return d
        }
        return null
    }

    // ---- value conversion -----------------------------------------------------

    private fun contentIdOf(clipId: String, linkedId: String?): String =
        if (linkedId != null && linkedId < clipId) linkedId else clipId

    private fun trackTypeWire(t: TrackType): Int = when (t) {
        TrackType.MIDI -> 0
        TrackType.AUDIO -> 1
        TrackType.DRUM -> 2
        TrackType.RETURN -> 3
        TrackType.MASTER -> 4
    }

    // FROZEN wire numbering - mirrors cpp/device/DeviceRegistry.h
    // DeviceTypeId. Never derived from enum ordinals.
    private fun deviceTypeWire(t: com.example.synth.domain.DeviceType): Int = when (t) {
        com.example.synth.domain.DeviceType.SUBTRACTIVE_SYNTH -> 0
        com.example.synth.domain.DeviceType.WAVETABLE_SYNTH -> 1
        com.example.synth.domain.DeviceType.FM_SYNTH -> 2
        com.example.synth.domain.DeviceType.SAMPLER -> 3
        com.example.synth.domain.DeviceType.ELECTRIC_PIANO -> 4
        com.example.synth.domain.DeviceType.STRING_PAD -> 5
        com.example.synth.domain.DeviceType.DRUM_RACK -> 6
        com.example.synth.domain.DeviceType.PARAMETRIC_EQ -> 7
        com.example.synth.domain.DeviceType.COMPRESSOR -> 8
        com.example.synth.domain.DeviceType.REVERB -> 9
        com.example.synth.domain.DeviceType.DELAY -> 10
        com.example.synth.domain.DeviceType.DISTORTION -> 11
        com.example.synth.domain.DeviceType.CHORUS -> 12
        com.example.synth.domain.DeviceType.LIMITER -> 13
    }

    private fun deviceWireParams(
        dev: com.example.synth.domain.DeviceModel,
    ): List<Pair<Int, Float>> =
        dev.params.map { (name, value) -> WireProtocol.paramKey(name) to value }

    private fun wireNotes(
        notes: List<com.example.synth.domain.MidiNote>,
        drumSteps: Map<com.example.synth.domain.DrumPadType, List<Float>>,
    ): List<DeltaEncoder.WireNote> {
        val out = ArrayList<DeltaEncoder.WireNote>(notes.size + drumSteps.values.sumOf { it.size })
        for (n in notes) {
            out.add(DeltaEncoder.WireNote(
                id = WireProtocol.fnv1a32(n.id),
                pitch = n.pitch,
                velocity = (n.velocity * 127f).toInt().coerceIn(1, 127),
                startBeat = n.startBeat.toDouble(),
                lengthBeats = n.lengthBeats.toDouble()))
        }
        for ((pad, beats) in drumSteps) {
            for (beat in beats) {
                out.add(DeltaEncoder.WireNote(
                    id = WireProtocol.fnv1a32("step:${pad.name}:$beat"),
                    pitch = pad.midiPitch,
                    velocity = 100,
                    startBeat = beat.toDouble(),
                    lengthBeats = 0.25))
            }
        }
        return out
    }

    // ---- param sending --------------------------------------------------------

    private fun sendTrackParam(state: ProjectState, trackId: String, key: String, editSeq: Int) {
        val track = state.tracks.firstOrNull { it.id == trackId } ?: return
        val plain = when (key) {
            ParamKeys.MIXER_VOLUME -> track.volumeDb.toDouble()
            ParamKeys.MIXER_PAN -> track.pan.toDouble()
            ParamKeys.MIXER_MUTE -> if (track.isMuted) 1.0 else 0.0
            ParamKeys.MIXER_SEND_A -> track.sendLevelA.toDouble()
            ParamKeys.MIXER_SEND_B -> track.sendLevelB.toDouble()
            else -> return
        }
        sendParam(WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_TRACK, trackId),
            key, plain, editSeq)
    }

    private fun sendParam(nodeUid: Long, key: String, plain: Double, editSeq: Int) {
        val keyHash = WireProtocol.paramKey(key)
        controller.send { paramMove(nodeUid, keyHash, plain, editSeq) }
    }
}
