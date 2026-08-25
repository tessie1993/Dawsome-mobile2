package com.example.synth.engine

import com.example.synth.domain.ProjectAction
import com.example.synth.domain.ProjectState
import com.example.synth.domain.ProjectStore
import java.util.concurrent.atomic.AtomicInteger

/**
 * Translates edit-model changes into engine traffic (blueprint Kotlin
 * engine/EngineSync): the change-classification seam of the dual-model
 * architecture.
 *
 * M0 scope - the two realtime change classes:
 *  - transport intents  -> Transport messages
 *  - parameter moves    -> Param/Move addressed (nodeUid, paramKeyHash)
 * Structure-shaped changes (tracks, clips, devices, scenes) become StateCodec
 * ModelDeltas for the GraphBuilder from M1 on; until then they are engine-
 * silent, which is correct for an engine that renders silence pre-graph.
 *
 * Values are read from the POST-reduction [ProjectState] handed to
 * [ProjectStore.onEngineSync], never from the raw action - the reducer's
 * coercions (pan clamps, bpm range) are authoritative.
 *
 * Reconcile duty (seam-2 overflow rule + encoder backlog drops): when the
 * engine side reports a dropped move, [resendAuthoritativeParams] re-sends
 * every addressable value from the current model, restoring convergence.
 * The same full re-send runs at [attach]: commands queue while the engine is
 * idle and flush on start, so the param tables hold model truth before the
 * first graph install (their post-swap re-apply memory).
 *
 * editSeq is a local monotonic counter until the model v2 store stamps real
 * edit sequences (M1).
 */
class EngineSync(
    private val store: ProjectStore,
    private val controller: EngineController,
) {
    private val editSeq = AtomicInteger(0)

    fun attach() {
        controller.onReconcileNeeded = { resendAuthoritativeParams() }
        store.onEngineSync = ::onAction
        resendAuthoritativeParams()
    }

    fun detach() {
        store.onEngineSync = null
        controller.onReconcileNeeded = null
    }

    // Runs on the store's dispatcher; controller.send posts to engine-io.
    private fun onAction(action: ProjectAction, state: ProjectState) {
        when (action) {
            // ---- transport ----------------------------------------------------
            is ProjectAction.Play -> controller.send { play() }
            is ProjectAction.Stop -> controller.send { stop() }
            is ProjectAction.TogglePlay -> controller.send { togglePlay() }
            is ProjectAction.ToggleRecord -> controller.send { record(state.isRecording) }
            is ProjectAction.ToggleLoop -> controller.send { loop(state.isLooping) }
            is ProjectAction.SetBpm -> controller.send { setTempo(state.bpm.toDouble()) }
            is ProjectAction.SeekToBeat ->
                controller.send { seekBeat(state.playheadBeat.toDouble()) }
            is ProjectAction.SetLoopRegion -> controller.send {
                setLoopRegion(state.loopStartBeat.toDouble(), state.loopEndBeat.toDouble())
            }

            // ---- parameter moves ----------------------------------------------
            is ProjectAction.SetTrackVolume ->
                sendTrackParam(state, action.trackId, ParamKeys.MIXER_VOLUME)
            is ProjectAction.SetTrackPan ->
                sendTrackParam(state, action.trackId, ParamKeys.MIXER_PAN)
            is ProjectAction.ToggleTrackMute ->
                sendTrackParam(state, action.trackId, ParamKeys.MIXER_MUTE)
            is ProjectAction.SetTrackSend -> sendTrackParam(
                state, action.trackId,
                if (action.sendIndex == 0) ParamKeys.MIXER_SEND_A else ParamKeys.MIXER_SEND_B)
            is ProjectAction.SetDeviceParam -> sendParam(
                WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_DEVICE, action.deviceId),
                action.paramName, action.value.toDouble())
            is ProjectAction.SetMasterVolume -> sendParam(
                WireProtocol.masterNodeUid, ParamKeys.MIXER_VOLUME,
                state.masterVolumeDb.toDouble())

            // ---- structure-shaped: ModelDelta path from M1 --------------------
            // (Solo is not a param at all: the RT audibility matrix computes
            // solo-in-place from Routing deltas at M2.)
            else -> Unit
        }
    }

    /** Re-send every addressable value from the current model (reconcile). */
    fun resendAuthoritativeParams() {
        val state = store.state.value
        for (track in state.tracks) {
            sendTrackParam(state, track.id, ParamKeys.MIXER_VOLUME)
            sendTrackParam(state, track.id, ParamKeys.MIXER_PAN)
            sendTrackParam(state, track.id, ParamKeys.MIXER_MUTE)
            sendTrackParam(state, track.id, ParamKeys.MIXER_SEND_A)
            sendTrackParam(state, track.id, ParamKeys.MIXER_SEND_B)
            for (device in track.devices) {
                val uid = WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_DEVICE, device.id)
                for ((name, value) in device.params) sendParam(uid, name, value.toDouble())
            }
        }
        sendParam(WireProtocol.masterNodeUid, ParamKeys.MIXER_VOLUME,
            state.masterVolumeDb.toDouble())
    }

    private fun sendTrackParam(state: ProjectState, trackId: String, key: String) {
        val track = state.tracks.firstOrNull { it.id == trackId } ?: return
        val plain = when (key) {
            ParamKeys.MIXER_VOLUME -> track.volumeDb.toDouble()
            ParamKeys.MIXER_PAN -> track.pan.toDouble()
            ParamKeys.MIXER_MUTE -> if (track.isMuted) 1.0 else 0.0
            ParamKeys.MIXER_SEND_A -> track.sendLevelA.toDouble()
            ParamKeys.MIXER_SEND_B -> track.sendLevelB.toDouble()
            else -> return
        }
        sendParam(WireProtocol.makeNodeUid(WireProtocol.NODE_KIND_TRACK, trackId), key, plain)
    }

    private fun sendParam(nodeUid: Long, key: String, plain: Double) {
        val seq = editSeq.incrementAndGet()
        val keyHash = WireProtocol.paramKey(key)
        controller.send { paramMove(nodeUid, keyHash, plain, seq) }
    }
}
