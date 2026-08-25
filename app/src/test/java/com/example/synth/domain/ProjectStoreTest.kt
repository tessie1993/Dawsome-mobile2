package com.example.synth.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reducer transition tests for [ProjectStore], as mandated by
 * docs/spec/ARCHITECTURE_BLUEPRINT.md ("ProjectStore state transition
 * verification for all ProjectAction types").
 *
 * The store is given an eagerly-executing test scope so dispatch()
 * applies synchronously; undo()/redo() are synchronous by design.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectStoreTest {

    private fun store(initial: ProjectState = ProjectState()) =
        ProjectStore(initial, CoroutineScope(UnconfinedTestDispatcher()))

    @Test
    fun `setBpm clamps to valid range`() {
        val s = store()
        s.dispatch(ProjectAction.SetBpm(999f))
        assertEquals(300f, s.state.value.bpm)
        s.dispatch(ProjectAction.SetBpm(1f))
        assertEquals(20f, s.state.value.bpm)
    }

    @Test
    fun `togglePlay flips playback and stop resets playhead`() {
        val s = store()
        s.dispatch(ProjectAction.TogglePlay)
        assertTrue(s.state.value.isPlaying)
        s.dispatch(ProjectAction.SeekToBeat(8f))
        assertEquals(8f, s.state.value.playheadBeat)
        s.dispatch(ProjectAction.Stop)
        assertFalse(s.state.value.isPlaying)
        assertEquals(0f, s.state.value.playheadBeat)
    }

    @Test
    fun `selectTab is applied but not undoable`() {
        val s = store()
        assertEquals(DawTab.ARRANGER, s.state.value.activeTab)
        s.dispatch(ProjectAction.SelectTab(DawTab.MIXER))
        assertEquals(DawTab.MIXER, s.state.value.activeTab)
        s.undo()
        assertEquals(DawTab.MIXER, s.state.value.activeTab)
    }

    @Test
    fun `addTrack assigns type default color and selects first track`() {
        val s = store()
        s.dispatch(ProjectAction.AddTrack(TrackType.DRUM, "Kit"))
        val state = s.state.value
        assertEquals(1, state.tracks.size)
        assertEquals("Kit", state.tracks[0].name)
        assertEquals("#D96B27", state.tracks[0].colorHex)
        assertEquals(state.tracks[0].id, state.selectedTrackId)
    }

    @Test
    fun `deleteTrack reassigns selection to a surviving track`() {
        val s = store()
        s.dispatch(ProjectAction.AddTrack(TrackType.MIDI, "Lead"))
        s.dispatch(ProjectAction.AddTrack(TrackType.AUDIO, "Vox"))
        val (lead, vox) = s.state.value.tracks
        s.dispatch(ProjectAction.SelectTrack(lead.id))
        s.dispatch(ProjectAction.DeleteTrack(lead.id))
        assertEquals(listOf(vox.id), s.state.value.tracks.map { it.id })
        assertEquals(vox.id, s.state.value.selectedTrackId)
    }

    @Test
    fun `track mutations only touch the addressed track`() {
        val s = store()
        s.dispatch(ProjectAction.AddTrack(TrackType.MIDI, "A"))
        s.dispatch(ProjectAction.AddTrack(TrackType.MIDI, "B"))
        val (a, b) = s.state.value.tracks
        s.dispatch(ProjectAction.SetTrackVolume(a.id, -6f))
        s.dispatch(ProjectAction.ToggleTrackMute(a.id))
        val after = s.state.value
        assertEquals(-6f, after.tracks[0].volumeDb)
        assertTrue(after.tracks[0].isMuted)
        assertEquals(b.volumeDb, after.tracks[1].volumeDb)
        assertFalse(after.tracks[1].isMuted)
    }

    @Test
    fun `undo restores previous state and redo reapplies it`() {
        val s = store()
        s.dispatch(ProjectAction.AddTrack(TrackType.MIDI, "A"))
        val trackId = s.state.value.tracks[0].id
        s.dispatch(ProjectAction.SetBpm(140f))
        s.dispatch(ProjectAction.ToggleTrackMute(trackId))
        assertTrue(s.state.value.tracks[0].isMuted)

        s.undo()
        assertFalse(s.state.value.tracks[0].isMuted)
        assertEquals(140f, s.state.value.bpm)

        s.redo()
        assertTrue(s.state.value.tracks[0].isMuted)
    }

    @Test
    fun `new undoable action clears the redo stack`() {
        val s = store()
        s.dispatch(ProjectAction.SetBpm(130f))
        s.undo()
        s.dispatch(ProjectAction.SetBpm(150f))
        s.redo()
        assertEquals(150f, s.state.value.bpm)
    }

    @Test
    fun `no-op dispatch pushes no undo history`() {
        val s = store()
        val before = s.state.value
        s.dispatch(ProjectAction.SetTrackVolume("nonexistent", -5f))
        assertEquals(before, s.state.value)
        s.undo()
        assertEquals(before, s.state.value)
    }

    @Test
    fun `quantizeClipNotes snaps note starts to the grid`() {
        val trackId = "t1"
        val clip = ArrangementClip(
            id = "c1",
            name = "Clip",
            trackId = trackId,
            startBeat = 0f,
            lengthBeats = 4f,
            notes = listOf(MidiNote(id = "n1", pitch = 60, startBeat = 0.6f))
        )
        val track = TrackModel(id = trackId, name = "T", type = TrackType.MIDI, arrangementClips = listOf(clip))
        val s = store(ProjectState(tracks = listOf(track)))

        s.dispatch(ProjectAction.QuantizeClipNotes(trackId, "c1", gridBeat = 0.5f))
        assertEquals(0.5f, s.state.value.tracks[0].arrangementClips[0].notes[0].startBeat)
    }

    @Test
    fun `createDefaultProject builds the demo project`() {
        val p = ProjectStore.createDefaultProject()
        assertEquals(3, p.tracks.size)
        assertEquals(8, p.scenes.size)
        assertEquals(120f, p.bpm)
        assertEquals("track_lead", p.selectedTrackId)
        assertNotEquals(null, p.tracks.first().devices.firstOrNull())
    }
}
