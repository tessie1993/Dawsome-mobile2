package com.example.data

import com.example.synth.*
import org.json.JSONArray
import org.json.JSONObject

object ProjectSerializer {

    fun serializeStateToJson(
        name: String,
        genre: String,
        bpm: Float,
        swing: Float,
        rootNote: Int,
        scale: MusicalScale,
        keyboardOctave: Int,
        patch: SynthPatch,
        leadNotes: List<MidiNote>,
        bassNotes: List<MidiNote>,
        drumGrid: Map<DrumType, List<Float>>,
        leadAutomation: Map<AutomationParameter, AutomationLane>,
        bassAutomation: Map<AutomationParameter, AutomationLane>,
        synthVolume: Float, synthPan: Float, synthMute: Boolean,
        bassVolume: Float, bassPan: Float, bassMute: Boolean,
        drumVolume: Float, drumPan: Float, drumMute: Boolean,
        masterVolume: Float,
        rackModules: List<AudioEffectModule>,
        scenes: List<SessionScene>,
        arrangementTracks: List<ArrangementTrack> = emptyList(),
        trackGroups: List<TrackGroup> = emptyList(),
        macroRack: MacroRack? = null,
        lfoDevice: LfoDevice? = null
    ): String {
        val root = JSONObject()
        root.put("version", 2)
        root.put("name", name)
        root.put("genre", genre)
        root.put("bpm", bpm.toDouble())
        root.put("swing", swing.toDouble())
        root.put("rootNote", rootNote)
        root.put("scale", scale.name)
        root.put("keyboardOctave", keyboardOctave)

        // Synth Patch
        val patchObj = JSONObject().apply {
            put("name", patch.name)
            put("description", patch.description)
            put("vco1Waveform", patch.vco1Waveform.name)
            put("vco1Octave", patch.vco1Octave)
            put("vco1Mix", patch.vco1Mix.toDouble())
            put("vco2Waveform", patch.vco2Waveform.name)
            put("vco2Semi", patch.vco2Semi)
            put("vco2Detune", patch.vco2Detune.toDouble())
            put("vco2Mix", patch.vco2Mix.toDouble())
            put("fmDepth", patch.fmDepth.toDouble())
            put("ringModMix", patch.ringModMix.toDouble())
            put("lfoWaveform", patch.lfoWaveform.name)
            put("lfoFrequency", patch.lfoFrequency.toDouble())
            put("lfoDepth", patch.lfoDepth.toDouble())
            put("lfoDestination", patch.lfoDestination.name)
            put("filterType", patch.filterType.name)
            put("filterCutoff", patch.filterCutoff.toDouble())
            put("filterResonance", patch.filterResonance.toDouble())
            put("egAmt", patch.egAmt.toDouble())
            put("attackTime", patch.attackTime.toDouble())
            put("decayTime", patch.decayTime.toDouble())
            put("sustainLevel", patch.sustainLevel.toDouble())
            put("releaseTime", patch.releaseTime.toDouble())
            put("glideTime", patch.glideTime.toDouble())
            put("delayTime", patch.delayTime.toDouble())
            put("delayFeedback", patch.delayFeedback.toDouble())
            put("delayMix", patch.delayMix.toDouble())
            put("masterVolume", patch.masterVolume.toDouble())
        }
        root.put("synthPatch", patchObj)

        // Lead Notes
        val leadArray = JSONArray()
        leadNotes.forEach { note ->
            leadArray.put(JSONObject().apply {
                put("id", note.id)
                put("pitch", note.pitch)
                put("startBeat", note.startBeat.toDouble())
                put("lengthBeats", note.lengthBeats.toDouble())
                put("velocity", note.velocity.toDouble())
            })
        }
        root.put("leadNotes", leadArray)

        // Bass Notes
        val bassArray = JSONArray()
        bassNotes.forEach { note ->
            bassArray.put(JSONObject().apply {
                put("id", note.id)
                put("pitch", note.pitch)
                put("startBeat", note.startBeat.toDouble())
                put("lengthBeats", note.lengthBeats.toDouble())
                put("velocity", note.velocity.toDouble())
            })
        }
        root.put("bassNotes", bassArray)

        // Drum Grid
        val drumObj = JSONObject()
        drumGrid.forEach { (type, steps) ->
            val stepArr = JSONArray()
            steps.forEach { stepArr.put(it.toDouble()) }
            drumObj.put(type.name, stepArr)
        }
        root.put("drumGrid", drumObj)

        // Mixer
        val mixerObj = JSONObject().apply {
            put("synthVolume", synthVolume.toDouble())
            put("synthPan", synthPan.toDouble())
            put("synthMute", synthMute)
            put("bassVolume", bassVolume.toDouble())
            put("bassPan", bassPan.toDouble())
            put("bassMute", bassMute)
            put("drumVolume", drumVolume.toDouble())
            put("drumPan", drumPan.toDouble())
            put("drumMute", drumMute)
            put("masterVolume", masterVolume.toDouble())
        }
        root.put("mixer", mixerObj)

        // Lead Automation
        val leadAutoObj = JSONObject()
        leadAutomation.forEach { (param, lane) ->
            val laneObj = JSONObject().apply {
                put("isEnabled", lane.isEnabled)
                val pointsArr = JSONArray()
                lane.points.forEach { pt ->
                    pointsArr.put(JSONObject().apply {
                        put("beat", pt.beat.toDouble())
                        put("normalizedValue", pt.normalizedValue.toDouble())
                    })
                }
                put("points", pointsArr)
            }
            leadAutoObj.put(param.name, laneObj)
        }
        root.put("leadAutomation", leadAutoObj)

        // Bass Automation
        val bassAutoObj = JSONObject()
        bassAutomation.forEach { (param, lane) ->
            val laneObj = JSONObject().apply {
                put("isEnabled", lane.isEnabled)
                val pointsArr = JSONArray()
                lane.points.forEach { pt ->
                    pointsArr.put(JSONObject().apply {
                        put("beat", pt.beat.toDouble())
                        put("normalizedValue", pt.normalizedValue.toDouble())
                    })
                }
                put("points", pointsArr)
            }
            bassAutoObj.put(param.name, laneObj)
        }
        root.put("bassAutomation", bassAutoObj)

        // Effects Rack
        val fxArray = JSONArray()
        rackModules.forEach { module ->
            val fxObj = JSONObject().apply {
                put("type", module.type.name)
                put("isEnabled", module.isEnabled)
                val params = JSONObject()
                when (module) {
                    is ReverbModule -> {
                        params.put("roomSize", module.roomSize.toDouble())
                        params.put("damping", module.damping.toDouble())
                        params.put("preDelayMs", module.preDelayMs.toDouble())
                        params.put("mix", module.mix.toDouble())
                    }
                    is DelayModule -> {
                        params.put("timeMs", module.timeMs.toDouble())
                        params.put("feedback", module.feedback.toDouble())
                        params.put("pingPong", module.pingPong)
                        params.put("tone", module.tone.toDouble())
                        params.put("mix", module.mix.toDouble())
                    }
                    is FilterModule -> {
                        params.put("filterType", module.filterType.name)
                        params.put("cutoffHz", module.cutoffHz.toDouble())
                        params.put("resonance", module.resonance.toDouble())
                        params.put("drive", module.drive.toDouble())
                        params.put("mix", module.mix.toDouble())
                    }
                    is DistortionModule -> {
                        params.put("drive", module.drive.toDouble())
                        params.put("tone", module.tone.toDouble())
                        params.put("mode", module.mode.name)
                        params.put("mix", module.mix.toDouble())
                    }
                    is ChorusModule -> {
                        params.put("rateHz", module.rateHz.toDouble())
                        params.put("depth", module.depth.toDouble())
                        params.put("feedback", module.feedback.toDouble())
                        params.put("mix", module.mix.toDouble())
                    }
                    is ParametricEqModule -> {
                        params.put("lowGainDb", module.lowGainDb.toDouble())
                        params.put("midGainDb", module.midGainDb.toDouble())
                        params.put("highGainDb", module.highGainDb.toDouble())
                        params.put("mix", module.mix.toDouble())
                    }
                    is CompressorModule -> {
                        params.put("thresholdDb", module.thresholdDb.toDouble())
                        params.put("ratio", module.ratio.toDouble())
                        params.put("attackMs", module.attackMs.toDouble())
                        params.put("releaseMs", module.releaseMs.toDouble())
                        params.put("makeupGainDb", module.makeupGainDb.toDouble())
                    }
                    is MultibandCompressorModule -> {
                        params.put("lowThresholdDb", module.lowThresholdDb.toDouble())
                        params.put("lowRatio", module.lowRatio.toDouble())
                        params.put("lowMidThresholdDb", module.lowMidThresholdDb.toDouble())
                        params.put("lowMidRatio", module.lowMidRatio.toDouble())
                        params.put("highMidThresholdDb", module.highMidThresholdDb.toDouble())
                        params.put("highMidRatio", module.highMidRatio.toDouble())
                        params.put("highThresholdDb", module.highThresholdDb.toDouble())
                        params.put("highRatio", module.highRatio.toDouble())
                    }
                }
                put("params", params)
            }
            fxArray.put(fxObj)
        }
        root.put("effectsRack", fxArray)

        // Session Scenes
        val sceneArray = JSONArray()
        scenes.forEach { scene ->
            val sceneObj = JSONObject().apply {
                put("id", scene.id)
                put("name", scene.name)
                put("bpm", scene.bpm.toDouble())
                val clipsObj = JSONObject()
                scene.clips.forEach { (trackType, clip) ->
                    val clipObj = JSONObject().apply {
                        put("id", clip.id)
                        put("name", clip.name)
                        put("trackType", clip.trackType.name)
                        val cLeadNotes = JSONArray()
                        clip.leadNotes.forEach { n ->
                            cLeadNotes.put(JSONObject().apply {
                                put("pitch", n.pitch)
                                put("startBeat", n.startBeat.toDouble())
                                put("lengthBeats", n.lengthBeats.toDouble())
                                put("velocity", n.velocity.toDouble())
                            })
                        }
                        put("leadNotes", cLeadNotes)

                        val cBassNotes = JSONArray()
                        clip.bassNotes.forEach { n ->
                            cBassNotes.put(JSONObject().apply {
                                put("pitch", n.pitch)
                                put("startBeat", n.startBeat.toDouble())
                                put("lengthBeats", n.lengthBeats.toDouble())
                                put("velocity", n.velocity.toDouble())
                            })
                        }
                        put("bassNotes", cBassNotes)

                        val cDrums = JSONObject()
                        clip.drumGrid.forEach { (dtype, steps) ->
                            val sArr = JSONArray()
                            steps.forEach { sArr.put(it.toDouble()) }
                            cDrums.put(dtype.name, sArr)
                        }
                        put("drumGrid", cDrums)
                    }
                    clipsObj.put(trackType.name, clipObj)
                }
                put("clips", clipsObj)
            }
            sceneArray.put(sceneObj)
        }
        root.put("scenes", sceneArray)

        // Arrangement Tracks & Clips
        val arrTracksArray = JSONArray()
        arrangementTracks.forEach { track ->
            val tObj = JSONObject().apply {
                put("id", track.id)
                put("name", track.name)
                put("trackType", track.trackType.name)
                put("groupId", track.groupId ?: "")
                put("colorHex", track.colorHex)
                put("volume", track.volume.toDouble())
                put("pan", track.pan.toDouble())
                put("sendA", track.sendA.toDouble())
                put("sendB", track.sendB.toDouble())
                put("isMuted", track.isMuted)
                put("isSolo", track.isSolo)
                put("isArmed", track.isArmed)

                val clipsArr = JSONArray()
                track.clips.forEach { clip ->
                    val cObj = JSONObject().apply {
                        put("id", clip.id)
                        put("name", clip.name)
                        put("startBar", clip.startBar.toDouble())
                        put("lengthBars", clip.lengthBars.toDouble())
                        put("colorHex", clip.colorHex)
                        put("isLooping", clip.isLooping)
                        put("isMuted", clip.isMuted)
                    }
                    clipsArr.put(cObj)
                }
                put("clips", clipsArr)
            }
            arrTracksArray.put(tObj)
        }
        root.put("arrangementTracks", arrTracksArray)

        // Track Groups
        val groupsArray = JSONArray()
        trackGroups.forEach { grp ->
            val gObj = JSONObject().apply {
                put("id", grp.id)
                put("name", grp.name)
                put("colorHex", grp.colorHex)
                put("isFolded", grp.isFolded)
                put("volume", grp.volume.toDouble())
                put("isMuted", grp.isMuted)
                put("isSolo", grp.isSolo)
                val tIdsArr = JSONArray()
                grp.trackIds.forEach { tIdsArr.put(it) }
                put("trackIds", tIdsArr)
            }
            groupsArray.put(gObj)
        }
        root.put("trackGroups", groupsArray)

        // Macro Rack
        if (macroRack != null) {
            val mrObj = JSONObject().apply {
                put("name", macroRack.name)
                put("isEnabled", macroRack.isEnabled)
                val mArr = JSONArray()
                macroRack.macros.forEach { m ->
                    mArr.put(JSONObject().apply {
                        put("index", m.index)
                        put("name", m.name)
                        put("value", m.value.toDouble())
                        put("targetParam", m.targetParam)
                    })
                }
                put("macros", mArr)
            }
            root.put("macroRack", mrObj)
        }

        // LFO Device
        if (lfoDevice != null) {
            val lfoObj = JSONObject().apply {
                put("isEnabled", lfoDevice.isEnabled)
                put("waveform", lfoDevice.waveform.name)
                put("rateHz", lfoDevice.rateHz.toDouble())
                put("depth", lfoDevice.depth.toDouble())
                put("phaseOffset", lfoDevice.phaseOffset.toDouble())
                put("target", lfoDevice.target)
            }
            root.put("lfoDevice", lfoObj)
        }

        return root.toString(2)
    }

    data class DeserializedDawState(
        val name: String,
        val genre: String,
        val bpm: Float,
        val swing: Float,
        val rootNote: Int,
        val scale: MusicalScale,
        val keyboardOctave: Int,
        val synthPatch: SynthPatch,
        val leadNotes: List<MidiNote>,
        val bassNotes: List<MidiNote>,
        val drumGrid: Map<DrumType, List<Float>>,
        val synthVolume: Float,
        val synthPan: Float,
        val synthMute: Boolean,
        val bassVolume: Float,
        val bassPan: Float,
        val bassMute: Boolean,
        val drumVolume: Float,
        val drumPan: Float,
        val drumMute: Boolean,
        val masterVolume: Float,
        val leadAutomation: Map<AutomationParameter, AutomationLane>,
        val bassAutomation: Map<AutomationParameter, AutomationLane>,
        val scenes: List<SessionScene>,
        val arrangementTracks: List<ArrangementTrack> = emptyList(),
        val trackGroups: List<TrackGroup> = emptyList(),
        val macroRack: MacroRack? = null,
        val lfoDevice: LfoDevice? = null
    )

    fun deserializeStateFromJson(jsonStr: String): DeserializedDawState? {
        return try {
            val root = JSONObject(jsonStr)
            val name = root.optString("name", "Untitled Project")
            val genre = root.optString("genre", "Electronic")
            val bpm = root.optDouble("bpm", 120.0).toFloat()
            val swing = root.optDouble("swing", 0.0).toFloat()
            val rootNote = root.optInt("rootNote", 0)
            val scaleName = root.optString("scale", MusicalScale.PENTATONIC_MINOR.name)
            val scale = try { MusicalScale.valueOf(scaleName) } catch (e: Exception) { MusicalScale.PENTATONIC_MINOR }
            val keyboardOctave = root.optInt("keyboardOctave", 4)

            // Synth Patch
            val patch = if (root.has("synthPatch")) {
                val p = root.getJSONObject("synthPatch")
                SynthPatch(
                    name = p.optString("name", "Custom"),
                    description = p.optString("description", "Custom Patch"),
                    vco1Waveform = try { Waveform.valueOf(p.optString("vco1Waveform", "SAWTOOTH")) } catch (e: Exception) { Waveform.SAWTOOTH },
                    vco1Octave = p.optInt("vco1Octave", 0),
                    vco1Mix = p.optDouble("vco1Mix", 0.8).toFloat(),
                    vco2Waveform = try { Waveform.valueOf(p.optString("vco2Waveform", "TRIANGLE")) } catch (e: Exception) { Waveform.TRIANGLE },
                    vco2Semi = p.optInt("vco2Semi", 0),
                    vco2Detune = p.optDouble("vco2Detune", 10.0).toFloat(),
                    vco2Mix = p.optDouble("vco2Mix", 0.5).toFloat(),
                    fmDepth = p.optDouble("fmDepth", 0.0).toFloat(),
                    ringModMix = p.optDouble("ringModMix", 0.0).toFloat(),
                    lfoWaveform = try { Waveform.valueOf(p.optString("lfoWaveform", "SINE")) } catch (e: Exception) { Waveform.SINE },
                    lfoFrequency = p.optDouble("lfoFrequency", 3.0).toFloat(),
                    lfoDepth = p.optDouble("lfoDepth", 0.1).toFloat(),
                    lfoDestination = try { LfoDestination.valueOf(p.optString("lfoDestination", "NONE")) } catch (e: Exception) { LfoDestination.NONE },
                    filterType = try { FilterType.valueOf(p.optString("filterType", "LOW_PASS")) } catch (e: Exception) { FilterType.LOW_PASS },
                    filterCutoff = p.optDouble("filterCutoff", 2000.0).toFloat(),
                    filterResonance = p.optDouble("filterResonance", 2.0).toFloat(),
                    egAmt = p.optDouble("egAmt", 0.3).toFloat(),
                    attackTime = p.optDouble("attackTime", 0.05).toFloat(),
                    decayTime = p.optDouble("decayTime", 0.3).toFloat(),
                    sustainLevel = p.optDouble("sustainLevel", 0.6).toFloat(),
                    releaseTime = p.optDouble("releaseTime", 0.3).toFloat(),
                    glideTime = p.optDouble("glideTime", 0.05).toFloat(),
                    delayTime = p.optDouble("delayTime", 0.3).toFloat(),
                    delayFeedback = p.optDouble("delayFeedback", 0.3).toFloat(),
                    delayMix = p.optDouble("delayMix", 0.2).toFloat(),
                    masterVolume = p.optDouble("masterVolume", 0.85).toFloat()
                )
            } else {
                SynthPatch.PRESETS[0]
            }

            // Lead Notes
            val leadNotes = mutableListOf<MidiNote>()
            if (root.has("leadNotes")) {
                val arr = root.getJSONArray("leadNotes")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    leadNotes.add(
                        MidiNote(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            pitch = obj.getInt("pitch"),
                            startBeat = obj.getDouble("startBeat").toFloat(),
                            lengthBeats = obj.optDouble("lengthBeats", 1.0).toFloat(),
                            velocity = obj.optDouble("velocity", 0.9).toFloat()
                        )
                    )
                }
            }

            // Bass Notes
            val bassNotes = mutableListOf<MidiNote>()
            if (root.has("bassNotes")) {
                val arr = root.getJSONArray("bassNotes")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    bassNotes.add(
                        MidiNote(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            pitch = obj.getInt("pitch"),
                            startBeat = obj.getDouble("startBeat").toFloat(),
                            lengthBeats = obj.optDouble("lengthBeats", 1.0).toFloat(),
                            velocity = obj.optDouble("velocity", 0.9).toFloat()
                        )
                    )
                }
            }

            // Drum Grid
            val drumGrid = mutableMapOf<DrumType, List<Float>>()
            if (root.has("drumGrid")) {
                val drumObj = root.getJSONObject("drumGrid")
                DrumType.values().forEach { type ->
                    if (drumObj.has(type.name)) {
                        val arr = drumObj.getJSONArray(type.name)
                        val steps = mutableListOf<Float>()
                        for (i in 0 until arr.length()) {
                            steps.add(arr.getDouble(i).toFloat())
                        }
                        drumGrid[type] = steps
                    } else {
                        drumGrid[type] = List(16) { 0f }
                    }
                }
            } else {
                DrumType.values().forEach { type -> drumGrid[type] = List(16) { 0f } }
            }

            // Mixer
            var sVol = 0.85f; var sPan = 0f; var sMute = false
            var bVol = 0.85f; var bPan = 0f; var bMute = false
            var dVol = 0.9f; var dPan = 0f; var dMute = false
            var mVol = 0.85f
            if (root.has("mixer")) {
                val m = root.getJSONObject("mixer")
                sVol = m.optDouble("synthVolume", 0.85).toFloat()
                sPan = m.optDouble("synthPan", 0.0).toFloat()
                sMute = m.optBoolean("synthMute", false)
                bVol = m.optDouble("bassVolume", 0.85).toFloat()
                bPan = m.optDouble("bassPan", 0.0).toFloat()
                bMute = m.optBoolean("bassMute", false)
                dVol = m.optDouble("drumVolume", 0.9).toFloat()
                dPan = m.optDouble("drumPan", 0.0).toFloat()
                dMute = m.optBoolean("drumMute", false)
                mVol = m.optDouble("masterVolume", 0.85).toFloat()
            }

            // Automation
            val leadAuto = mutableMapOf<AutomationParameter, AutomationLane>()
            if (root.has("leadAutomation")) {
                val autoObj = root.getJSONObject("leadAutomation")
                AutomationParameter.values().forEach { param ->
                    if (autoObj.has(param.name)) {
                        val laneObj = autoObj.getJSONObject(param.name)
                        val isEnabled = laneObj.optBoolean("isEnabled", true)
                        val pointsArr = laneObj.optJSONArray("points")
                        val pts = mutableListOf<AutomationPoint>()
                        if (pointsArr != null) {
                            for (p in 0 until pointsArr.length()) {
                                val ptObj = pointsArr.getJSONObject(p)
                                pts.add(AutomationPoint(ptObj.getDouble("beat").toFloat(), ptObj.getDouble("normalizedValue").toFloat()))
                            }
                        }
                        leadAuto[param] = AutomationLane(param, isEnabled, pts)
                    } else {
                        leadAuto[param] = AutomationLane.defaultLane(param)
                    }
                }
            } else {
                AutomationParameter.values().forEach { leadAuto[it] = AutomationLane.defaultLane(it) }
            }

            val bassAuto = mutableMapOf<AutomationParameter, AutomationLane>()
            if (root.has("bassAutomation")) {
                val autoObj = root.getJSONObject("bassAutomation")
                AutomationParameter.values().forEach { param ->
                    if (autoObj.has(param.name)) {
                        val laneObj = autoObj.getJSONObject(param.name)
                        val isEnabled = laneObj.optBoolean("isEnabled", true)
                        val pointsArr = laneObj.optJSONArray("points")
                        val pts = mutableListOf<AutomationPoint>()
                        if (pointsArr != null) {
                            for (p in 0 until pointsArr.length()) {
                                val ptObj = pointsArr.getJSONObject(p)
                                pts.add(AutomationPoint(ptObj.getDouble("beat").toFloat(), ptObj.getDouble("normalizedValue").toFloat()))
                            }
                        }
                        bassAuto[param] = AutomationLane(param, isEnabled, pts)
                    } else {
                        bassAuto[param] = AutomationLane.defaultLane(param)
                    }
                }
            } else {
                AutomationParameter.values().forEach { bassAuto[it] = AutomationLane.defaultLane(it) }
            }

            // Scenes
            val scenes = mutableListOf<SessionScene>()
            if (root.has("scenes")) {
                val sceneArr = root.getJSONArray("scenes")
                for (s in 0 until sceneArr.length()) {
                    val scObj = sceneArr.getJSONObject(s)
                    val scId = scObj.optString("id", java.util.UUID.randomUUID().toString())
                    val scName = scObj.optString("name", "Scene ${s + 1}")
                    val scBpm = scObj.optDouble("bpm", 120.0).toFloat()
                    val clipsMap = mutableMapOf<SessionTrackType, SessionClip>()
                    if (scObj.has("clips")) {
                        val clipsObj = scObj.getJSONObject("clips")
                        SessionTrackType.values().forEach { tType ->
                            if (clipsObj.has(tType.name)) {
                                val cObj = clipsObj.getJSONObject(tType.name)
                                val cNotes = mutableListOf<MidiNote>()
                                if (cObj.has("leadNotes")) {
                                    val lnArr = cObj.getJSONArray("leadNotes")
                                    for (ln in 0 until lnArr.length()) {
                                        val no = lnArr.getJSONObject(ln)
                                        cNotes.add(MidiNote(pitch = no.getInt("pitch"), startBeat = no.getDouble("startBeat").toFloat(), lengthBeats = no.optDouble("lengthBeats", 1.0).toFloat(), velocity = no.optDouble("velocity", 0.9).toFloat()))
                                    }
                                }
                                val cBass = mutableListOf<MidiNote>()
                                if (cObj.has("bassNotes")) {
                                    val bnArr = cObj.getJSONArray("bassNotes")
                                    for (bn in 0 until bnArr.length()) {
                                        val no = bnArr.getJSONObject(bn)
                                        cBass.add(MidiNote(pitch = no.getInt("pitch"), startBeat = no.getDouble("startBeat").toFloat(), lengthBeats = no.optDouble("lengthBeats", 1.0).toFloat(), velocity = no.optDouble("velocity", 0.9).toFloat()))
                                    }
                                }
                                val cDrumGrid = mutableMapOf<DrumType, List<Float>>()
                                if (cObj.has("drumGrid")) {
                                    val dgObj = cObj.getJSONObject("drumGrid")
                                    DrumType.values().forEach { dt ->
                                        if (dgObj.has(dt.name)) {
                                            val dArr = dgObj.getJSONArray(dt.name)
                                            val dSteps = mutableListOf<Float>()
                                            for (di in 0 until dArr.length()) dSteps.add(dArr.getDouble(di).toFloat())
                                            cDrumGrid[dt] = dSteps
                                        }
                                    }
                                }
                                clipsMap[tType] = SessionClip(
                                    id = cObj.optString("id", java.util.UUID.randomUUID().toString()),
                                    name = cObj.optString("name", "Clip"),
                                    trackType = tType,
                                    leadNotes = cNotes,
                                    bassNotes = cBass,
                                    drumGrid = cDrumGrid
                                )
                            }
                        }
                    }
                    scenes.add(SessionScene(id = scId, name = scName, bpm = scBpm, clips = clipsMap))
                }
            }

            // Arrangement Tracks
            val arrangementTracks = mutableListOf<ArrangementTrack>()
            if (root.has("arrangementTracks")) {
                val arrArr = root.getJSONArray("arrangementTracks")
                for (ti in 0 until arrArr.length()) {
                    val tObj = arrArr.getJSONObject(ti)
                    val tId = tObj.optString("id", java.util.UUID.randomUUID().toString())
                    val tName = tObj.optString("name", "Track")
                    val tTypeStr = tObj.optString("trackType", "LEAD")
                    val tType = try { SessionTrackType.valueOf(tTypeStr) } catch (e: Exception) { SessionTrackType.LEAD }
                    val gId = tObj.optString("groupId", "").takeIf { it.isNotEmpty() }
                    val colHex = tObj.optLong("colorHex", 0xFF29B6F6)
                    val vol = tObj.optDouble("volume", 0.85).toFloat()
                    val pan = tObj.optDouble("pan", 0.0).toFloat()
                    val sendA = tObj.optDouble("sendA", 0.2).toFloat()
                    val sendB = tObj.optDouble("sendB", 0.1).toFloat()
                    val isMuted = tObj.optBoolean("isMuted", false)
                    val isSolo = tObj.optBoolean("isSolo", false)
                    val isArmed = tObj.optBoolean("isArmed", false)

                    val clips = mutableListOf<ArrangementClip>()
                    if (tObj.has("clips")) {
                        val cArr = tObj.getJSONArray("clips")
                        for (ci in 0 until cArr.length()) {
                            val cObj = cArr.getJSONObject(ci)
                            clips.add(
                                ArrangementClip(
                                    id = cObj.optString("id", java.util.UUID.randomUUID().toString()),
                                    trackId = tId,
                                    name = cObj.optString("name", "Clip"),
                                    startBar = cObj.optDouble("startBar", 0.0).toFloat(),
                                    lengthBars = cObj.optDouble("lengthBars", 4.0).toFloat(),
                                    colorHex = cObj.optLong("colorHex", colHex),
                                    isLooping = cObj.optBoolean("isLooping", true),
                                    isMuted = cObj.optBoolean("isMuted", false)
                                )
                            )
                        }
                    }

                    arrangementTracks.add(
                        ArrangementTrack(
                            id = tId,
                            name = tName,
                            trackType = tType,
                            groupId = gId,
                            colorHex = colHex,
                            volume = vol,
                            pan = pan,
                            sendA = sendA,
                            sendB = sendB,
                            isMuted = isMuted,
                            isSolo = isSolo,
                            isArmed = isArmed,
                            clips = clips
                        )
                    )
                }
            }

            // Track Groups
            val trackGroups = mutableListOf<TrackGroup>()
            if (root.has("trackGroups")) {
                val gArr = root.getJSONArray("trackGroups")
                for (gi in 0 until gArr.length()) {
                    val gObj = gArr.getJSONObject(gi)
                    val gId = gObj.optString("id", java.util.UUID.randomUUID().toString())
                    val gName = gObj.optString("name", "Group")
                    val gCol = gObj.optLong("colorHex", 0xFFFF764D)
                    val gFolded = gObj.optBoolean("isFolded", false)
                    val gVol = gObj.optDouble("volume", 0.9).toFloat()
                    val gMuted = gObj.optBoolean("isMuted", false)
                    val gSolo = gObj.optBoolean("isSolo", false)
                    val tIds = mutableListOf<String>()
                    if (gObj.has("trackIds")) {
                        val tiArr = gObj.getJSONArray("trackIds")
                        for (k in 0 until tiArr.length()) tIds.add(tiArr.getString(k))
                    }
                    trackGroups.add(
                        TrackGroup(
                            id = gId,
                            name = gName,
                            trackIds = tIds,
                            colorHex = gCol,
                            isFolded = gFolded,
                            volume = gVol,
                            isMuted = gMuted,
                            isSolo = gSolo
                        )
                    )
                }
            }

            // Macro Rack
            var macroRack: MacroRack? = null
            if (root.has("macroRack")) {
                val mrObj = root.getJSONObject("macroRack")
                val mrName = mrObj.optString("name", "Master Macro Rack")
                val mrEnabled = mrObj.optBoolean("isEnabled", true)
                val macrosList = mutableListOf<MacroControl>()
                if (mrObj.has("macros")) {
                    val mArr = mrObj.getJSONArray("macros")
                    for (mi in 0 until mArr.length()) {
                        val mObj = mArr.getJSONObject(mi)
                        macrosList.add(
                            MacroControl(
                                index = mObj.optInt("index", mi),
                                name = mObj.optString("name", "Macro ${mi + 1}"),
                                value = mObj.optDouble("value", 0.5).toFloat(),
                                targetParam = mObj.optString("targetParam", "")
                            )
                        )
                    }
                }
                macroRack = MacroRack(name = mrName, isEnabled = mrEnabled, macros = macrosList)
            }

            // LFO Device
            var lfoDevice: LfoDevice? = null
            if (root.has("lfoDevice")) {
                val lObj = root.getJSONObject("lfoDevice")
                val lEnabled = lObj.optBoolean("isEnabled", false)
                val lWfStr = lObj.optString("waveform", "SINE")
                val lWf = try { Waveform.valueOf(lWfStr) } catch (e: Exception) { Waveform.SINE }
                val lRate = lObj.optDouble("rateHz", 1.0).toFloat()
                val lDepth = lObj.optDouble("depth", 0.5).toFloat()
                val lPhase = lObj.optDouble("phaseOffset", 0.0).toFloat()
                val lTarget = lObj.optString("target", "Filter Cutoff")
                lfoDevice = LfoDevice(
                    isEnabled = lEnabled,
                    waveform = lWf,
                    rateHz = lRate,
                    depth = lDepth,
                    phaseOffset = lPhase,
                    target = lTarget
                )
            }

            DeserializedDawState(
                name = name,
                genre = genre,
                bpm = bpm,
                swing = swing,
                rootNote = rootNote,
                scale = scale,
                keyboardOctave = keyboardOctave,
                synthPatch = patch,
                leadNotes = leadNotes,
                bassNotes = bassNotes,
                drumGrid = drumGrid,
                synthVolume = sVol,
                synthPan = sPan,
                synthMute = sMute,
                bassVolume = bVol,
                bassPan = bPan,
                bassMute = bMute,
                drumVolume = dVol,
                drumPan = dPan,
                drumMute = dMute,
                masterVolume = mVol,
                leadAutomation = leadAuto,
                bassAutomation = bassAuto,
                scenes = scenes,
                arrangementTracks = arrangementTracks,
                trackGroups = trackGroups,
                macroRack = macroRack,
                lfoDevice = lfoDevice
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
