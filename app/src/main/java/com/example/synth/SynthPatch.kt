package com.example.synth

data class SynthPatch(
    val name: String,
    val description: String,
    
    // VCO 1
    val vco1Waveform: Waveform,
    val vco1Octave: Int,
    val vco1Mix: Float,

    // VCO 2
    val vco2Waveform: Waveform,
    val vco2Semi: Int,
    val vco2Detune: Float,
    val vco2Mix: Float,

    // Cross-Mod & Ring
    val fmDepth: Float,
    val ringModMix: Float,

    // LFO
    val lfoWaveform: Waveform,
    val lfoFrequency: Float,
    val lfoDepth: Float,
    val lfoDestination: LfoDestination,

    // Filter
    val filterType: FilterType,
    val filterCutoff: Float,
    val filterResonance: Float,
    val egAmt: Float,

    // ADSR
    val attackTime: Float,
    val decayTime: Float,
    val sustainLevel: Float,
    val releaseTime: Float,

    // Glide
    val glideTime: Float,

    // Delay Effects
    val delayTime: Float,
    val delayFeedback: Float,
    val delayMix: Float,
    
    val masterVolume: Float = 0.8f
) {
    companion object {
        val PRESETS = listOf(
            SynthPatch(
                name = "Fat Sub-Bass",
                description = "Deep analog dual-oscillator sub-bass with fine detuning and a tight lowpass envelope sweep.",
                vco1Waveform = Waveform.SAWTOOTH,
                vco1Octave = -1,
                vco1Mix = 0.8f,
                vco2Waveform = Waveform.SQUARE,
                vco2Semi = 0,
                vco2Detune = 12.0f,
                vco2Mix = 0.5f,
                fmDepth = 0.0f,
                ringModMix = 0.0f,
                lfoWaveform = Waveform.SINE,
                lfoFrequency = 3.5f,
                lfoDepth = 0.12f,
                lfoDestination = LfoDestination.NONE,
                filterType = FilterType.LOW_PASS,
                filterCutoff = 450.0f,
                filterResonance = 4.2f,
                egAmt = 0.55f,
                attackTime = 0.005f,
                decayTime = 0.28f,
                sustainLevel = 0.35f,
                releaseTime = 0.2f,
                glideTime = 0.08f,
                delayTime = 0.2f,
                delayFeedback = 0.1f,
                delayMix = 0.0f
            ),
            SynthPatch(
                name = "Cosmic Lead",
                description = "Gliding fifth lead with rich VCO cross modulation, deep delay echo, and expressive pitch vibrato.",
                vco1Waveform = Waveform.SAWTOOTH,
                vco1Octave = 0,
                vco1Mix = 0.7f,
                vco2Waveform = Waveform.TRIANGLE,
                vco2Semi = 7, // Perfect Fifth
                vco2Detune = 15.0f,
                vco2Mix = 0.5f,
                fmDepth = 0.25f, // FM metallic bite
                ringModMix = 0.0f,
                lfoWaveform = Waveform.SINE,
                lfoFrequency = 6.2f,
                lfoDepth = 0.18f,
                lfoDestination = LfoDestination.VCO_PITCH, // Vibrato
                filterType = FilterType.LOW_PASS,
                filterCutoff = 1800.0f,
                filterResonance = 2.0f,
                egAmt = 0.25f,
                attackTime = 0.12f,
                decayTime = 0.5f,
                sustainLevel = 0.75f,
                releaseTime = 0.4f,
                glideTime = 0.22f, // Portamento glide
                delayTime = 0.35f,
                delayFeedback = 0.55f,
                delayMix = 0.35f // Deep echo space
            ),
            SynthPatch(
                name = "Sci-Fi Acid Sweep",
                description = "Self-oscillating bandpass filter sweep modulated by a slow LFO with modular cross-talk.",
                vco1Waveform = Waveform.SQUARE,
                vco1Octave = 0,
                vco1Mix = 0.6f,
                vco2Waveform = Waveform.SQUARE,
                vco2Semi = -12, // Sub-octave
                vco2Detune = 5.0f,
                vco2Mix = 0.4f,
                fmDepth = 0.1f,
                ringModMix = 0.3f,
                lfoWaveform = Waveform.TRIANGLE,
                lfoFrequency = 1.0f, // Slow lowpass sweep
                lfoDepth = 0.4f,
                lfoDestination = LfoDestination.VCF_CUTOFF,
                filterType = FilterType.LOW_PASS,
                filterCutoff = 800.0f,
                filterResonance = 7.5f, // High resonance
                egAmt = 0.6f,
                attackTime = 0.02f,
                decayTime = 0.4f,
                sustainLevel = 0.2f,
                releaseTime = 0.3f,
                glideTime = 0.15f,
                delayTime = 0.25f,
                delayFeedback = 0.4f,
                delayMix = 0.25f
            ),
            SynthPatch(
                name = "Ethereal Ambient Pad",
                description = "A warm, swelling background ambient texturizer utilizing slow attack times and a high-pass space filter.",
                vco1Waveform = Waveform.SINE,
                vco1Octave = 0,
                vco1Mix = 0.7f,
                vco2Waveform = Waveform.TRIANGLE,
                vco2Semi = 12, // One Octave Up
                vco2Detune = 8.0f,
                vco2Mix = 0.5f,
                fmDepth = 0.0f,
                ringModMix = 0.0f,
                lfoWaveform = Waveform.SINE,
                lfoFrequency = 0.3f, // Super slow
                lfoDepth = 0.25f,
                lfoDestination = LfoDestination.VCF_CUTOFF,
                filterType = FilterType.LOW_PASS,
                filterCutoff = 1000.0f,
                filterResonance = 1.5f,
                egAmt = 0.35f,
                attackTime = 1.2f, // Very slow attack swell
                decayTime = 1.5f,
                sustainLevel = 0.9f,
                releaseTime = 1.8f, // Ultra-long release tail
                glideTime = 0.35f,
                delayTime = 0.45f,
                delayFeedback = 0.65f,
                delayMix = 0.45f
            ),
            SynthPatch(
                name = "Metallic Bell Ring",
                description = "Inharmonic bell sounds created using aggressive Ring Modulation and instant decay timings.",
                vco1Waveform = Waveform.SINE,
                vco1Octave = 1,
                vco1Mix = 0.4f,
                vco2Waveform = Waveform.SAWTOOTH,
                vco2Semi = 11, // Detuned interval
                vco2Detune = 30.0f,
                vco2Mix = 0.3f,
                fmDepth = 0.15f,
                ringModMix = 0.9f, // Max ringmod for clangy tones
                lfoWaveform = Waveform.SINE,
                lfoFrequency = 8.0f,
                lfoDepth = 0.15f,
                lfoDestination = LfoDestination.NONE,
                filterType = FilterType.HIGH_PASS,
                filterCutoff = 1200.0f,
                filterResonance = 3.0f,
                egAmt = 0.4f,
                attackTime = 0.001f,
                decayTime = 0.35f,
                sustainLevel = 0.0f, // No sustain (percussive strike)
                releaseTime = 0.4f,
                glideTime = 0.0f,
                delayTime = 0.28f,
                delayFeedback = 0.35f,
                delayMix = 0.3f
            )
        )
    }

    fun applyToEngine(engine: SynthEngine) {
        engine.vco1Waveform = this.vco1Waveform
        engine.vco1Octave = this.vco1Octave
        engine.vco1Mix = this.vco1Mix

        engine.vco2Waveform = this.vco2Waveform
        engine.vco2Semi = this.vco2Semi
        engine.vco2Detune = this.vco2Detune
        engine.vco2Mix = this.vco2Mix

        engine.fmDepth = this.fmDepth
        engine.ringModMix = this.ringModMix

        engine.lfoWaveform = this.lfoWaveform
        engine.lfoFrequency = this.lfoFrequency
        engine.lfoDepth = this.lfoDepth
        engine.lfoDestination = this.lfoDestination

        engine.filterType = this.filterType
        engine.filterCutoff = this.filterCutoff
        engine.filterResonance = this.filterResonance
        engine.egAmt = this.egAmt

        engine.attackTime = this.attackTime
        engine.decayTime = this.decayTime
        engine.sustainLevel = this.sustainLevel
        engine.releaseTime = this.releaseTime

        engine.glideTime = this.glideTime

        engine.stereoDelay.timeMs = this.delayTime * 1000f
        engine.stereoDelay.feedback = this.delayFeedback
        engine.stereoDelay.mix = this.delayMix
        
        engine.masterVolume = this.masterVolume
    }
}
