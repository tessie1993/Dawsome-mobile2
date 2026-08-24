package com.example.synth.export

import com.example.synth.WavWriter
import com.example.synth.domain.ProjectState
import com.example.synth.domain.TrackModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Offline Stem and Master Audio Exporter according to SPEC01.md Section 14.2.
 */
object StemExporter {

    /**
     * Render individual audio stems for all active tracks in the project.
     */
    suspend fun exportProjectStems(
        projectState: ProjectState,
        outputDir: File,
        sampleRate: Int = 44100,
        renderBars: Int = 16
    ): List<File> = withContext(Dispatchers.IO) {
        if (!outputDir.exists()) outputDir.mkdirs()

        val exportedFiles = mutableListOf<File>()
        val totalSeconds = (renderBars * 4f) / (projectState.bpm / 60.0f)
        val totalSamples = (totalSeconds * sampleRate).toInt()

        projectState.tracks.forEach { track ->
            val trackPcm = generateTrackPcm(track, totalSamples, sampleRate, projectState.bpm)
            val sanitizedTrackName = track.name.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val wavFile = File(outputDir, "${sanitizedTrackName}_stem.wav")

            WavWriter.createWavFile(
                file = wavFile,
                pcmData = trackPcm,
                sampleRate = sampleRate,
                channels = 1
            )
            exportedFiles.add(wavFile)
        }

        exportedFiles
    }

    /**
     * Package all rendered stems into a downloadable/shareable ZIP archive.
     */
    suspend fun packageStemsToZip(
        stemFiles: List<File>,
        zipOutputFile: File
    ): File = withContext(Dispatchers.IO) {
        ZipOutputStream(FileOutputStream(zipOutputFile)).use { zipOut ->
            stemFiles.forEach { file ->
                FileInputStream(file).use { fi ->
                    val entry = ZipEntry(file.name)
                    zipOut.putNextEntry(entry)
                    val buffer = ByteArray(4096)
                    var count: Int
                    while (fi.read(buffer).also { count = it } > 0) {
                        zipOut.write(buffer, 0, count)
                    }
                    zipOut.closeEntry()
                }
            }
        }
        zipOutputFile
    }

    private fun generateTrackPcm(
        track: TrackModel,
        totalSamples: Int,
        sampleRate: Int,
        bpm: Float
    ): ShortArray {
        val pcm = ShortArray(totalSamples)
        val beatsPerSec = bpm / 60.0f
        val samplesPerBeat = (sampleRate / beatsPerSec).toInt()

        // Render all arrangement notes onto the PCM timeline
        track.arrangementClips.forEach { clip ->
            clip.notes.forEach { note ->
                val startSample = (note.startBeat * samplesPerBeat).toInt()
                val noteSamples = (note.lengthBeats * samplesPerBeat).toInt()
                val freq = 440.0 * Math.pow(2.0, (note.pitch - 69) / 12.0)

                for (i in 0 until noteSamples) {
                    val sampleIdx = startSample + i
                    if (sampleIdx < totalSamples) {
                        val phase = (i * freq * 2.0 * Math.PI) / sampleRate
                        val envelope = Math.exp(-i.toDouble() / (sampleRate * 0.8))
                        val sampleVal = (Math.sin(phase) * envelope * note.velocity * 16000.0).toInt().toShort()
                        pcm[sampleIdx] = (pcm[sampleIdx] + sampleVal).coerceIn(-32767, 32767).toShort()
                    }
                }
            }
        }

        return pcm
    }
}
