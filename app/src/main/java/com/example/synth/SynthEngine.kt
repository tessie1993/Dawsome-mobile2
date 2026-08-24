package com.example.synth

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

enum class Waveform {
    SINE, SQUARE, TRIANGLE, SAWTOOTH, NOISE
}

enum class LfoDestination {
    NONE, VCO_PITCH, VCF_CUTOFF, VCA_VOLUME, PAN
}

enum class FilterType {
    LOW_PASS, HIGH_PASS, BAND_PASS
}

class SynthEngine {
    companion object {
        private const val TAG = "SynthEngine"
        const val SAMPLE_RATE = 44100
        private const val OSCILLOSCOPE_SIZE = 512
    }

    // Engine run state
    @Volatile private var isRunning = false
    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null

    // Real-time oscilloscope buffer (circular)
    private val scopeBuffer = FloatArray(OSCILLOSCOPE_SIZE)
    private var scopeWriteIndex = 0
    private val scopeLock = Any()

    // Sub-systems
    val drumEngine = DrumEngine()
    val effectsRack = MasterEffectsRack(SAMPLE_RATE)

    // Instrument Sub-Systems
    val wavetableSynth = WavetableSynth(SAMPLE_RATE)
    val fmSynth = FmOperatorSynth(SAMPLE_RATE)
    val sampler = SamplerInstrument(SAMPLE_RATE)
    val samplerInstrument: SamplerInstrument get() = sampler
    val electricPiano = ElectricPianoSynth(SAMPLE_RATE)
    val stringPad = StringPadSynth(SAMPLE_RATE)

    fun loadWavToSampler(file: java.io.File): FloatArray? {
        return try {
            if (!file.exists() || file.length() <= 44) return null
            val bytes = file.readBytes()
            val shortCount = (bytes.size - 44) / 2
            val shorts = ShortArray(shortCount)
            val bb = java.nio.ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until shortCount) {
                shorts[i] = bb.short
            }
            sampler.loadCustomBuffer(shorts)
            sampler.sampleBuffer
        } catch (e: Exception) {
            null
        }
    }

    @Volatile var activeInstrument = InstrumentType.ANALOG_SUB

    // Legacy helpers for quick access
    val stereoDelay: DelayModule get() = effectsRack.getModules().filterIsInstance<DelayModule>().firstOrNull() ?: effectsRack.addModule(EffectType.DELAY) as DelayModule
    val distortion: DistortionModule get() = effectsRack.getModules().filterIsInstance<DistortionModule>().firstOrNull() ?: effectsRack.addModule(EffectType.DISTORTION) as DistortionModule
    val reverb: ReverbModule get() = effectsRack.getModules().filterIsInstance<ReverbModule>().firstOrNull() ?: effectsRack.addModule(EffectType.REVERB) as ReverbModule
    val chorus: ChorusModule get() = effectsRack.getModules().filterIsInstance<ChorusModule>().firstOrNull() ?: effectsRack.addModule(EffectType.CHORUS) as ChorusModule
    val compressor: CompressorModule get() = effectsRack.getModules().filterIsInstance<CompressorModule>().firstOrNull() ?: effectsRack.addModule(EffectType.COMPRESSOR) as CompressorModule

    // --- Synthesizer Parameters (Lead Voice) ---
    @Volatile var masterVolume = 0.85f

    // VCO 1
    @Volatile var vco1Waveform = Waveform.SAWTOOTH
    @Volatile var vco1Octave = 0 // -2, -1, 0, 1, 2
    @Volatile var vco1Mix = 0.75f

    // VCO 2
    @Volatile var vco2Waveform = Waveform.SINE
    @Volatile var vco2Semi = 7 // Semitone offset (-12 to +12)
    @Volatile var vco2Detune = 10f // Fine detune (-50 to +50 cents)
    @Volatile var vco2Mix = 0.45f

    // Modular Routing: VCO FM Depth & Ring Mod
    @Volatile var fmDepth = 0.0f
    @Volatile var ringModMix = 0.0f

    // LFO
    @Volatile var lfoWaveform = Waveform.SINE
    @Volatile var lfoFrequency = 5.0f // 0.1Hz to 25.0Hz
    @Volatile var lfoDepth = 0.15f // 0 to 1
    @Volatile var lfoDestination = LfoDestination.VCO_PITCH

    // ADSR (Envelope Generator)
    @Volatile var attackTime = 0.05f
    @Volatile var decayTime = 0.3f
    @Volatile var sustainLevel = 0.7f
    @Volatile var releaseTime = 0.4f

    // VCF (Filter)
    @Volatile var filterType = FilterType.LOW_PASS
    @Volatile var filterCutoff = 2200.0f
    @Volatile var filterResonance = 1.2f
    @Volatile var egAmt = 0.45f

    // Performance controls
    @Volatile var pitchBend = 0.0f // -2.0 to +2.0 semitones
    @Volatile var modWheel = 0.0f  // 0.0 to 1.0 (scales LFO depth or filter cutoff)
    @Volatile var glideTime = 0.1f // 0.0 to 2.0s

    // Channel Strips (Volume & Mute/Solo)
    @Volatile var synthVolume = 0.85f
    @Volatile var synthPan = 0.0f
    @Volatile var isSynthMuted = false

    @Volatile var bassVolume = 0.85f
    @Volatile var bassPan = 0.0f
    @Volatile var bassCutoff = 1800.0f
    @Volatile var bassResonance = 1.8f
    @Volatile var isBassMuted = false

    @Volatile var drumVolume = 0.90f
    @Volatile var drumPan = 0.0f
    @Volatile var isDrumMuted = false

    // Lead Voice State
    @Volatile private var activeLeadNote: Int? = null
    @Volatile private var targetLeadFreq = 0.0f
    @Volatile private var currentLeadFreq = 0.0f
    @Volatile private var isLeadGateActive = false
    private var leadEnvAmp = 0.0f
    private var leadEnvStage = EnvStage.IDLE
    private var leadEnvReleaseStart = 0.0f
    private var leadEnvSamples = 0L

    // Bass Voice State
    @Volatile private var activeBassNote: Int? = null
    @Volatile private var targetBassFreq = 0.0f
    @Volatile private var currentBassFreq = 0.0f
    @Volatile private var isBassGateActive = false
    private var bassEnvAmp = 0.0f
    private var bassEnvStage = EnvStage.IDLE
    private var bassEnvReleaseStart = 0.0f
    private var bassEnvSamples = 0L
    private var bassFilterLow = 0.0f
    private var bassFilterBand = 0.0f

    enum class EnvStage {
        IDLE, ATTACK, DECAY, SUSTAIN, RELEASE
    }

    // Phase counters
    private var vco1Phase = 0.0f
    private var vco2Phase = 0.0f
    private var lfoPhase = 0.0f
    private var bassPhase = 0.0f
    private var bassSubPhase = 0.0f

    // VCF SVF state
    private var filterLow = 0.0f
    private var filterBand = 0.0f

    // VU Peak Levels
    @Volatile var vuSynthLevel = 0.0f
    @Volatile var vuBassLevel = 0.0f
    @Volatile var vuDrumLevel = 0.0f
    @Volatile var vuMasterLevel = 0.0f

    // Notification State for Visualizers
    private val _isSounding = MutableStateFlow(false)
    val isSounding: StateFlow<Boolean> = _isSounding.asStateFlow()

    private val _currentActiveNotes = MutableStateFlow<Set<Int>>(emptySet())
    val currentActiveNotes: StateFlow<Set<Int>> = _currentActiveNotes.asStateFlow()

    // Metronome state
    @Volatile var isMetronomeEnabled = false
    private var metronomeClickSamples = 0
    private var metronomeAccent = false

    // Real-time Audio Recorder
    @Volatile var isRecording = false
    private val recordedPcmBuffer = ArrayList<Short>(SAMPLE_RATE * 60) // up to ~60s in RAM
    private val recordLock = Any()

    init {
        start()
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = minBufferSize.coerceAtLeast(1024 * 2)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioThread = Thread {
            setThreadPriority(Thread.MAX_PRIORITY)
            synthLoop()
        }.apply {
            name = "DSP_DAW_Audio_Thread"
            start()
        }

        try {
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioTrack: ${e.message}")
        }
    }

    fun stop() {
        isRunning = false
        audioThread?.join(1000)
        audioThread = null
        try {
            audioTrack?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release AudioTrack: ${e.message}")
        }
        audioTrack = null
    }

    // --- Lead Voice Controls ---
    fun noteOn(noteNumber: Int) {
        val freq = midiNoteToFreq(noteNumber)
        targetLeadFreq = freq
        activeLeadNote = noteNumber
        _currentActiveNotes.value = setOf(noteNumber)
        sampler.triggerNoteOn(noteNumber)

        if (!isLeadGateActive) {
            if (currentLeadFreq == 0.0f) currentLeadFreq = freq
            leadEnvStage = EnvStage.ATTACK
            leadEnvSamples = 0
            isLeadGateActive = true
            _isSounding.value = true
        } else {
            // Re-trigger envelope punch
            leadEnvStage = EnvStage.ATTACK
            leadEnvSamples = 0
        }
    }

    fun noteOff(noteNumber: Int) {
        sampler.triggerNoteOff(noteNumber)
        if (activeLeadNote == noteNumber) {
            isLeadGateActive = false
            leadEnvStage = EnvStage.RELEASE
            leadEnvReleaseStart = leadEnvAmp
            leadEnvSamples = 0
            _currentActiveNotes.value = emptySet()
        }
    }

    // --- Bass Voice Controls ---
    fun bassNoteOn(noteNumber: Int) {
        val freq = midiNoteToFreq(noteNumber)
        targetBassFreq = freq
        activeBassNote = noteNumber

        if (!isBassGateActive) {
            if (currentBassFreq == 0.0f) currentBassFreq = freq
            bassEnvStage = EnvStage.ATTACK
            bassEnvSamples = 0
            isBassGateActive = true
        } else {
            bassEnvStage = EnvStage.ATTACK
            bassEnvSamples = 0
        }
    }

    fun bassNoteOff(noteNumber: Int) {
        if (activeBassNote == noteNumber) {
            isBassGateActive = false
            bassEnvStage = EnvStage.RELEASE
            bassEnvReleaseStart = bassEnvAmp
            bassEnvSamples = 0
        }
    }

    // --- Drum Trigger ---
    fun triggerDrum(type: DrumType, velocity: Float = 1.0f) {
        drumEngine.trigger(type, velocity)
    }

    fun triggerMetronome(isDownbeat: Boolean) {
        if (!isMetronomeEnabled) return
        metronomeAccent = isDownbeat
        metronomeClickSamples = (0.02f * SAMPLE_RATE).toInt() // 20ms click
    }

    fun panic() {
        isLeadGateActive = false
        activeLeadNote = null
        _currentActiveNotes.value = emptySet()
        leadEnvStage = EnvStage.IDLE
        leadEnvAmp = 0.0f
        currentLeadFreq = 0.0f

        isBassGateActive = false
        activeBassNote = null
        bassEnvStage = EnvStage.IDLE
        bassEnvAmp = 0.0f
        currentBassFreq = 0.0f

        drumEngine.stopAll()
        effectsRack.clearAll()
        _isSounding.value = false
    }

    // --- Recording API ---
    fun startRecording() {
        synchronized(recordLock) {
            recordedPcmBuffer.clear()
            isRecording = true
        }
    }

    fun stopRecording(): ShortArray {
        synchronized(recordLock) {
            isRecording = false
            return recordedPcmBuffer.toShortArray()
        }
    }

    fun saveRecordingToWav(destFile: File): Boolean {
        synchronized(recordLock) {
            if (recordedPcmBuffer.isEmpty()) return false
            WavWriter.createWavFile(destFile, recordedPcmBuffer.toShortArray(), SAMPLE_RATE, 1)
            return true
        }
    }

    private fun midiNoteToFreq(note: Int): Float {
        return 440.0f * Math.pow(2.0, (note - 69).toDouble() / 12.0).toFloat()
    }

    private fun setThreadPriority(priority: Int) {
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set audio thread priority: ${e.message}")
        }
    }

    fun getOscilloscopeData(outArray: FloatArray) {
        synchronized(scopeLock) {
            var triggerPoint = 0
            val searchWindow = OSCILLOSCOPE_SIZE / 2

            for (i in 0 until searchWindow) {
                val idx1 = (scopeWriteIndex + i) % OSCILLOSCOPE_SIZE
                val idx2 = (scopeWriteIndex + i + 1) % OSCILLOSCOPE_SIZE
                if (scopeBuffer[idx1] <= 0.0f && scopeBuffer[idx2] > 0.0f) {
                    triggerPoint = idx1
                    break
                }
            }

            for (i in outArray.indices) {
                val readIdx = (triggerPoint + i) % OSCILLOSCOPE_SIZE
                outArray[i] = scopeBuffer[readIdx]
            }
        }
    }

    // --- Core Audio Synthesis Processing Loop ---
    private fun synthLoop() {
        val writeBufferSize = 512
        val shortBuffer = ShortArray(writeBufferSize)

        var peakSynth = 0.0f
        var peakBass = 0.0f
        var peakDrum = 0.0f
        var peakMaster = 0.0f

        while (isRunning) {
            peakSynth = 0.0f
            peakBass = 0.0f
            peakDrum = 0.0f
            peakMaster = 0.0f

            for (i in 0 until writeBufferSize) {
                // 1. Process Lead Synthesizer Voice
                val leadSample = renderLeadVoice()
                if (abs(leadSample) > peakSynth) peakSynth = abs(leadSample)

                // 2. Process Bass Voice
                val bassSample = renderBassVoice()
                if (abs(bassSample) > peakBass) peakBass = abs(bassSample)

                // 3. Process Drum Engine
                val drumRawSample = if (!isDrumMuted) drumEngine.render(SAMPLE_RATE) * drumVolume else 0.0f
                if (abs(drumRawSample) > peakDrum) peakDrum = abs(drumRawSample)

                // 4. Metronome Click
                var clickSample = 0.0f
                if (metronomeClickSamples > 0) {
                    val clickFreq = if (metronomeAccent) 1200f else 800f
                    clickSample = sin(2.0 * PI * clickFreq * (metronomeClickSamples.toFloat() / SAMPLE_RATE)).toFloat() * 0.4f
                    metronomeClickSamples--
                }

                // 5. Mixer Summation with Stereo Panning
                val synthPanL = (1.0f - synthPan).coerceIn(0f, 2f) * 0.5f
                val synthPanR = (1.0f + synthPan).coerceIn(0f, 2f) * 0.5f
                val bassPanL = (1.0f - bassPan).coerceIn(0f, 2f) * 0.5f
                val bassPanR = (1.0f + bassPan).coerceIn(0f, 2f) * 0.5f
                val drumPanL = (1.0f - drumPan).coerceIn(0f, 2f) * 0.5f
                val drumPanR = (1.0f + drumPan).coerceIn(0f, 2f) * 0.5f

                val preFxL = leadSample * synthPanL + bassSample * bassPanL + drumRawSample * drumPanL + clickSample
                val preFxR = leadSample * synthPanR + bassSample * bassPanR + drumRawSample * drumPanR + clickSample

                // 6. Chainable Master Effects Rack (Stereo DSP Processing)
                val (fxOutL, fxOutR) = effectsRack.process(preFxL, preFxR)

                // Master bus gain & soft limit
                val masterMono = (fxOutL + fxOutR) * 0.5f * masterVolume
                val masterOut = masterMono.coerceIn(-1.0f, 1.0f)
                if (abs(masterOut) > peakMaster) peakMaster = abs(masterOut)

                // Update Visualizer scope
                updateScopeBuffer(masterOut)

                // Convert to 16-bit PCM
                val pcmShort = (masterOut * 32767.0f).coerceIn(-32767.0f, 32767.0f).toInt().toShort()
                shortBuffer[i] = pcmShort

                // Recording buffer
                if (isRecording) {
                    synchronized(recordLock) {
                        if (recordedPcmBuffer.size < SAMPLE_RATE * 120) { // max 2 min
                            recordedPcmBuffer.add(pcmShort)
                        }
                    }
                }
            }

            // Update VU Levels smoothly
            vuSynthLevel = vuSynthLevel * 0.7f + peakSynth * 0.3f
            vuBassLevel = vuBassLevel * 0.7f + peakBass * 0.3f
            vuDrumLevel = vuDrumLevel * 0.7f + peakDrum * 0.3f
            vuMasterLevel = vuMasterLevel * 0.7f + peakMaster * 0.3f

            // Push PCM samples to AudioTrack
            audioTrack?.write(shortBuffer, 0, writeBufferSize)
        }
    }

    private fun renderLeadVoice(): Float {
        if (isSynthMuted) return 0.0f

        // Glide
        if (currentLeadFreq != targetLeadFreq) {
            if (glideTime <= 0.005f) {
                currentLeadFreq = targetLeadFreq
            } else {
                val glideFactor = 1.0f - Math.exp(-3.0 / (SAMPLE_RATE * glideTime)).toFloat()
                currentLeadFreq += (targetLeadFreq - currentLeadFreq) * glideFactor
            }
        }

        if (currentLeadFreq <= 0.01f) return 0.0f

        // LFO
        lfoPhase += lfoFrequency / SAMPLE_RATE
        if (lfoPhase >= 1.0f) lfoPhase -= 1.0f
        val lfoEffectiveDepth = (lfoDepth + modWheel * 0.5f).coerceIn(0.0f, 1.0f)
        val lfoVal = generateLfoSample(lfoPhase, lfoWaveform) * lfoEffectiveDepth

        // ADSR Envelope
        val adsrMultiplier = processLeadEnvelope()
        if (adsrMultiplier <= 0.0f && leadEnvStage == EnvStage.IDLE) {
            if (_isSounding.value) _isSounding.value = false
            currentLeadFreq = 0.0f
            return 0.0f
        }

        // Modulations
        val pitchBendRatio = Math.pow(2.0, (pitchBend / 12.0).toDouble()).toFloat()
        var modulatedFreq = currentLeadFreq * pitchBendRatio
        var vcfCutoffMod = filterCutoff * (1.0f + modWheel * 1.5f)

        when (lfoDestination) {
            LfoDestination.VCO_PITCH -> {
                val pitchMultiplier = Math.pow(2.0, (lfoVal * 1.5).toDouble()).toFloat()
                modulatedFreq *= pitchMultiplier
            }
            LfoDestination.VCF_CUTOFF -> {
                val scaleFactor = Math.pow(2.0, (lfoVal * 6.0).toDouble()).toFloat()
                vcfCutoffMod = (vcfCutoffMod * scaleFactor).coerceIn(40.0f, 16000.0f)
            }
            LfoDestination.VCA_VOLUME, LfoDestination.PAN, LfoDestination.NONE -> {}
        }

        // Generate Raw Generator Sample based on active instrument
        val rawGeneratorSample = when (activeInstrument) {
            InstrumentType.ANALOG_SUB -> {
                // VCO 1
                val octaveMult = Math.pow(2.0, vco1Octave.toDouble()).toFloat()
                val finalVco1Freq = modulatedFreq * octaveMult

                // VCO 2
                val vco2SemiRatio = Math.pow(2.0, (vco2Semi + (vco2Detune / 100.0)).toDouble() / 12.0).toFloat()
                val finalVco2Freq = modulatedFreq * vco2SemiRatio

                vco2Phase += finalVco2Freq / SAMPLE_RATE
                if (vco2Phase >= 1.0f) vco2Phase -= 1.0f
                val vco2Sample = generateOscSample(vco2Phase, vco2Waveform)

                // FM Cross-Modulation
                val fmOffset = vco2Sample * fmDepth * 1000.0f
                vco1Phase += (finalVco1Freq + fmOffset) / SAMPLE_RATE
                if (vco1Phase >= 1.0f) vco1Phase -= 1.0f
                val vco1Sample = generateOscSample(vco1Phase, vco1Waveform)

                // Ring Mod
                val ringSample = vco1Sample * vco2Sample
                val mixedVco1 = (1.0f - ringModMix) * vco1Sample + ringModMix * ringSample

                ((mixedVco1 * vco1Mix) + (vco2Sample * vco2Mix)).coerceIn(-1.0f, 1.0f)
            }
            InstrumentType.WAVETABLE -> {
                wavetableSynth.renderSample(modulatedFreq)
            }
            InstrumentType.FM_OPERATOR -> {
                fmSynth.renderSample(modulatedFreq, adsrMultiplier)
            }
            InstrumentType.SAMPLER -> {
                return sampler.renderSample() * synthVolume
            }
            InstrumentType.ELECTRIC_PIANO -> {
                electricPiano.renderSample(modulatedFreq, adsrMultiplier)
            }
            InstrumentType.STRING_PAD -> {
                stringPad.renderSample(modulatedFreq, adsrMultiplier)
            }
        }

        // VCF SVF Filter with Envelope
        val filterEnvShift = adsrMultiplier * egAmt * 6000.0f
        val finalCutoff = (vcfCutoffMod + filterEnvShift).coerceIn(40.0f, 16000.0f)

        val f = 2.0f * sin(PI * finalCutoff / SAMPLE_RATE).toFloat()
        val qDamping = 1.0f / filterResonance.coerceAtLeast(0.5f)

        val highpass = rawGeneratorSample - filterLow - qDamping * filterBand
        filterBand += f * highpass
        filterLow += f * filterBand

        val filteredSample = when (filterType) {
            FilterType.LOW_PASS -> filterLow
            FilterType.HIGH_PASS -> highpass
            FilterType.BAND_PASS -> filterBand
        }

        return filteredSample * adsrMultiplier * synthVolume
    }

    private fun renderBassVoice(): Float {
        if (isBassMuted || currentBassFreq <= 0.01f) return 0.0f

        // Glide
        if (currentBassFreq != targetBassFreq) {
            val glideFactor = 1.0f - Math.exp(-3.0 / (SAMPLE_RATE * 0.05f)).toFloat()
            currentBassFreq += (targetBassFreq - currentBassFreq) * glideFactor
        }

        val bassEnv = processBassEnvelope()
        if (bassEnv <= 0.0f && bassEnvStage == EnvStage.IDLE) {
            currentBassFreq = 0.0f
            return 0.0f
        }

        // Analog Dual Bass Osc (Sawtooth + Sub-Sine 1 Octave below)
        bassPhase += currentBassFreq / SAMPLE_RATE
        if (bassPhase >= 1.0f) bassPhase -= 1.0f
        val saw = 2.0f * bassPhase - 1.0f

        bassSubPhase += (currentBassFreq * 0.5f) / SAMPLE_RATE
        if (bassSubPhase >= 1.0f) bassSubPhase -= 1.0f
        val sub = sin(2.0 * PI * bassSubPhase).toFloat()

        val rawBass = saw * 0.6f + sub * 0.6f

        // Resonant Acid Lowpass Filter
        val cutoff = (bassCutoff + bassEnv * 2400.0f).coerceIn(40f, 16000f)
        val f = 2.0f * sin(PI * cutoff / SAMPLE_RATE).toFloat()
        val qDamping = 1.0f / bassResonance.coerceAtLeast(0.5f)

        val highpass = rawBass - bassFilterLow - qDamping * bassFilterBand
        bassFilterBand += f * highpass
        bassFilterLow += f * bassFilterBand

        return bassFilterLow * bassEnv * bassVolume
    }

    private fun generateOscSample(phase: Float, wav: Waveform): Float {
        return when (wav) {
            Waveform.SINE -> sin(2.0f * PI * phase).toFloat()
            Waveform.SQUARE -> if (phase < 0.5f) 1.0f else -1.0f
            Waveform.TRIANGLE -> if (phase < 0.5f) 4.0f * phase - 1.0f else 3.0f - 4.0f * phase
            Waveform.SAWTOOTH -> 2.0f * phase - 1.0f
            Waveform.NOISE -> Random.nextFloat() * 2.0f - 1.0f
        }
    }

    private fun generateLfoSample(phase: Float, wav: Waveform): Float {
        return when (wav) {
            Waveform.SINE -> sin(2.0f * PI * phase).toFloat()
            Waveform.SQUARE -> if (phase < 0.5f) 1.0f else -1.0f
            Waveform.TRIANGLE, Waveform.SAWTOOTH -> if (phase < 0.5f) 4.0f * phase - 1.0f else 3.0f - 4.0f * phase
            Waveform.NOISE -> Random.nextFloat() * 2.0f - 1.0f
        }
    }

    private fun processLeadEnvelope(): Float {
        leadEnvSamples++
        when (leadEnvStage) {
            EnvStage.IDLE -> leadEnvAmp = 0.0f
            EnvStage.ATTACK -> {
                val samples = (attackTime * SAMPLE_RATE).toLong().coerceAtLeast(1)
                leadEnvAmp = leadEnvSamples.toFloat() / samples.toFloat()
                if (leadEnvAmp >= 1.0f) {
                    leadEnvAmp = 1.0f
                    leadEnvStage = EnvStage.DECAY
                    leadEnvSamples = 0
                }
            }
            EnvStage.DECAY -> {
                val samples = (decayTime * SAMPLE_RATE).toLong().coerceAtLeast(1)
                val frac = leadEnvSamples.toFloat() / samples.toFloat()
                leadEnvAmp = 1.0f - (1.0f - sustainLevel) * frac
                if (leadEnvSamples >= samples) {
                    leadEnvAmp = sustainLevel
                    leadEnvStage = EnvStage.SUSTAIN
                    leadEnvSamples = 0
                }
            }
            EnvStage.SUSTAIN -> {
                leadEnvAmp = sustainLevel
                if (!isLeadGateActive) {
                    leadEnvStage = EnvStage.RELEASE
                    leadEnvReleaseStart = leadEnvAmp
                    leadEnvSamples = 0
                }
            }
            EnvStage.RELEASE -> {
                val samples = (releaseTime * SAMPLE_RATE).toLong().coerceAtLeast(1)
                val frac = leadEnvSamples.toFloat() / samples.toFloat()
                leadEnvAmp = leadEnvReleaseStart * (1.0f - frac)
                if (leadEnvSamples >= samples || leadEnvAmp <= 0.0f) {
                    leadEnvAmp = 0.0f
                    leadEnvStage = EnvStage.IDLE
                    leadEnvSamples = 0
                }
            }
        }
        return leadEnvAmp
    }

    private fun processBassEnvelope(): Float {
        bassEnvSamples++
        when (bassEnvStage) {
            EnvStage.IDLE -> bassEnvAmp = 0.0f
            EnvStage.ATTACK -> {
                val samples = (0.01f * SAMPLE_RATE).toLong().coerceAtLeast(1)
                bassEnvAmp = bassEnvSamples.toFloat() / samples.toFloat()
                if (bassEnvAmp >= 1.0f) {
                    bassEnvAmp = 1.0f
                    bassEnvStage = EnvStage.DECAY
                    bassEnvSamples = 0
                }
            }
            EnvStage.DECAY -> {
                val samples = (0.25f * SAMPLE_RATE).toLong().coerceAtLeast(1)
                val frac = bassEnvSamples.toFloat() / samples.toFloat()
                bassEnvAmp = 1.0f - 0.7f * frac
                if (bassEnvSamples >= samples) {
                    bassEnvAmp = 0.3f
                    bassEnvStage = EnvStage.SUSTAIN
                    bassEnvSamples = 0
                }
            }
            EnvStage.SUSTAIN -> {
                bassEnvAmp = 0.3f
                if (!isBassGateActive) {
                    bassEnvStage = EnvStage.RELEASE
                    bassEnvReleaseStart = bassEnvAmp
                    bassEnvSamples = 0
                }
            }
            EnvStage.RELEASE -> {
                val samples = (0.15f * SAMPLE_RATE).toLong().coerceAtLeast(1)
                val frac = bassEnvSamples.toFloat() / samples.toFloat()
                bassEnvAmp = bassEnvReleaseStart * (1.0f - frac)
                if (bassEnvSamples >= samples || bassEnvAmp <= 0.0f) {
                    bassEnvAmp = 0.0f
                    bassEnvStage = EnvStage.IDLE
                    bassEnvSamples = 0
                }
            }
        }
        return bassEnvAmp
    }

    private fun updateScopeBuffer(sample: Float) {
        synchronized(scopeLock) {
            scopeBuffer[scopeWriteIndex] = sample
            scopeWriteIndex = (scopeWriteIndex + 1) % OSCILLOSCOPE_SIZE
        }
    }
}
