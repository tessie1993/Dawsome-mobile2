package com.example.data.repository

import com.example.data.ProjectSerializer
import com.example.data.local.ProjectDao
import com.example.data.local.ProjectEntity
import com.example.synth.domain.ProjectState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Clean Repository for persisting and loading DAW projects into Room DB.
 */
class DawProjectRepository(private val projectDao: ProjectDao) {

    val allProjects: Flow<List<ProjectHeader>> = projectDao.getAllProjects().map { list ->
        list.map { entity ->
            ProjectHeader(
                id = entity.id,
                name = entity.name,
                genre = entity.genre,
                bpm = entity.bpm,
                lastModified = entity.lastModified
            )
        }
    }

    suspend fun saveProject(state: ProjectState): Long = withContext(Dispatchers.IO) {
        val json = ProjectSerializer.serializeStateToJson(
            name = state.name,
            genre = "Electronic",
            bpm = state.bpm,
            swing = 0f,
            rootNote = state.keyRoot,
            scale = com.example.synth.MusicalScale.NATURAL_MINOR,
            keyboardOctave = 3,
            patch = com.example.synth.SynthPatch.DEFAULT,
            leadNotes = state.tracks.firstOrNull { it.type == com.example.synth.domain.TrackType.MIDI }?.arrangementClips?.flatMap { it.notes }?.map {
                com.example.synth.MidiNote(it.id, it.pitch, it.startBeat, it.lengthBeats, it.velocity)
            } ?: emptyList(),
            bassNotes = emptyList(),
            drumGrid = emptyMap(),
            leadAutomation = emptyMap(),
            bassAutomation = emptyMap(),
            synthVolume = 0.85f, synthPan = 0f, synthMute = false,
            bassVolume = 0.85f, bassPan = 0f, bassMute = false,
            drumVolume = 0.85f, drumPan = 0f, drumMute = false,
            masterVolume = 0.85f,
            rackModules = emptyList(),
            scenes = emptyList()
        )

        val entity = ProjectEntity(
            name = state.name,
            genre = "Electronic",
            bpm = state.bpm,
            keyRoot = state.keyRoot,
            scaleName = state.scale.displayName,
            lastModified = System.currentTimeMillis(),
            projectDataJson = json
        )

        projectDao.insertProject(entity)
    }

    suspend fun deleteProject(id: Long) = withContext(Dispatchers.IO) {
        projectDao.deleteProjectById(id)
    }
}

data class ProjectHeader(
    val id: Long,
    val name: String,
    val genre: String,
    val bpm: Float,
    val lastModified: Long
)
