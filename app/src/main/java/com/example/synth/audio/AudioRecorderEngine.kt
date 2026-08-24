package com.example.synth.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.synth.WavWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Low-Latency Audio Recording and Input Metering Engine according to SPEC01.md Section 6.2.
 */
class AudioRecorderEngine(
    val sampleRate: Int = 44100,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    @Volatile var isRecording = false
        private set

    @Volatile var inputRmsLevel: Float = 0.0f
        private set

    @Volatile var inputPeakLevel: Float = 0.0f
        private set

    private var recordJob: Job? = null
    private val recordedBuffer = mutableListOf<Short>()

    private val minBufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(2048)

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) return
        recordedBuffer.clear()
        isRecording = true

        recordJob = scope.launch {
            var audioRecord: AudioRecord? = null
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    isRecording = false
                    return@launch
                }

                audioRecord.startRecording()
                val readBuffer = ShortArray(minBufferSize / 2)

                while (isActive && isRecording) {
                    val readCount = audioRecord.read(readBuffer, 0, readBuffer.size)
                    if (readCount > 0) {
                        var sumSquares = 0.0
                        var maxPeak = 0

                        for (i in 0 until readCount) {
                            val sample = readBuffer[i]
                            recordedBuffer.add(sample)
                            sumSquares += sample * sample
                            val absSample = abs(sample.toInt())
                            if (absSample > maxPeak) maxPeak = absSample
                        }

                        // Update real-time input meter levels (0.0 to 1.0)
                        inputRmsLevel = (sqrt(sumSquares / readCount) / 32767.0).toFloat().coerceIn(0f, 1f)
                        inputPeakLevel = (maxPeak / 32767.0f).coerceIn(0f, 1f)
                    }
                }
            } catch (e: Exception) {
                // Handle permission or hardware exception gracefully
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Exception) {
                    // Ignore release errors
                }
            }
        }
    }

    fun stopRecording(destinationWavFile: File? = null): ShortArray {
        isRecording = false
        recordJob?.cancel()
        inputRmsLevel = 0.0f
        inputPeakLevel = 0.0f

        val finalPcm = recordedBuffer.toShortArray()
        if (destinationWavFile != null && finalPcm.isNotEmpty()) {
            WavWriter.createWavFile(destinationWavFile, finalPcm, sampleRate, 1)
        }
        return finalPcm
    }
}
