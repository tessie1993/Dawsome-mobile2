package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.*

enum class DeviceChainCategory {
    ALL, MIDI_FX, INSTRUMENT, AUDIO_FX
}

@Composable
fun AbletonDeviceChain(
    viewModel: SynthViewModel,
    modifier: Modifier = Modifier
) {
    val modules by viewModel.rackModules.collectAsState()
    val engine = viewModel.engine

    // MIDI FX Parameters
    var isArpEnabled by remember { mutableStateOf(false) }
    var arpRate by remember { mutableStateOf("1/16") }
    var arpOctaves by remember { mutableStateOf(2) }
    var isChordEnabled by remember { mutableStateOf(false) }

    // Instrument Quick Controls
    var osc1Wave by remember { mutableStateOf(engine.vco1Waveform) }
    var filterCutoff by remember { mutableStateOf(engine.filterCutoff) }
    var filterRes by remember { mutableStateOf(engine.filterResonance) }
    var ampAttack by remember { mutableStateOf(engine.attackTime) }
    var ampRelease by remember { mutableStateOf(engine.releaseTime) }

    var selectedCategory by remember { mutableStateOf(DeviceChainCategory.ALL) }
    var showAddEffectDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AbletonSurface, RoundedCornerShape(8.dp))
            .border(1.dp, AbletonBorder, RoundedCornerShape(8.dp))
            .padding(6.dp)
            .testTag("ableton_device_chain_bar")
    ) {
        // Device Chain Top Bar (Navigation: MIDI FX -> Instrument -> Audio FX)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AbletonHeader, RoundedCornerShape(4.dp))
                .border(1.dp, AbletonBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Live Device Chain Label
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(AbletonYellow, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ABLETON DEVICE CHAIN",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AbletonYellow,
                        letterSpacing = 0.5.sp
                    )
                }

                // Flow Breadcrumbs: MIDI FX ➔ INSTRUMENT ➔ AUDIO FX
                Row(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DeviceCategoryChip("MIDI FX", AbletonBlue, selectedCategory == DeviceChainCategory.MIDI_FX) {
                        selectedCategory = if (selectedCategory == DeviceChainCategory.MIDI_FX) DeviceChainCategory.ALL else DeviceChainCategory.MIDI_FX
                    }
                    Text("➔", fontSize = 10.sp, color = StudioTextSecondary)
                    DeviceCategoryChip("INSTRUMENT", AbletonPurple, selectedCategory == DeviceChainCategory.INSTRUMENT) {
                        selectedCategory = if (selectedCategory == DeviceChainCategory.INSTRUMENT) DeviceChainCategory.ALL else DeviceChainCategory.INSTRUMENT
                    }
                    Text("➔", fontSize = 10.sp, color = StudioTextSecondary)
                    DeviceCategoryChip("AUDIO FX", AbletonOrange, selectedCategory == DeviceChainCategory.AUDIO_FX) {
                        selectedCategory = if (selectedCategory == DeviceChainCategory.AUDIO_FX) DeviceChainCategory.ALL else DeviceChainCategory.AUDIO_FX
                    }
                }
            }

            // Quick Add Audio Effect Device
            Button(
                onClick = { showAddEffectDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = AbletonPanel),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(24.dp).testTag("add_device_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Device", tint = AbletonOrange, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("ADD DEVICE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = StudioTextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Horizontal Device Rack (Ableton Style Modules aligned horizontally)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ==========================================
            // 1. MIDI FX DEVICES
            // ==========================================
            if (selectedCategory == DeviceChainCategory.ALL || selectedCategory == DeviceChainCategory.MIDI_FX) {
                // MIDI FX 1: Ableton Arpeggiator
                AbletonDeviceCard(
                    deviceName = "ARPEGGIATOR",
                    deviceTypeTag = "MIDI FX",
                    headerColor = AbletonBlue,
                    isEnabled = isArpEnabled,
                    onToggleEnabled = {
                        isArpEnabled = !isArpEnabled
                    },
                    modifier = Modifier.width(200.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Rate", fontSize = 8.sp, color = StudioTextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                listOf("1/4", "1/8", "1/16", "1/32").forEach { rateStr ->
                                    val isCur = arpRate == rateStr
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (isCur) AbletonBlue else AbletonHeader)
                                            .clickable { arpRate = rateStr }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(rateStr, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = if (isCur) Color.White else StudioTextSecondary)
                                    }
                                }
                            }
                        }

                        // Octaves Range
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Octave Range: $arpOctaves", fontSize = 8.sp, color = StudioTextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                listOf(1, 2, 3, 4).forEach { oct ->
                                    val isCur = arpOctaves == oct
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (isCur) AbletonBlue else AbletonHeader)
                                            .clickable { arpOctaves = oct },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("$oct", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (isCur) Color.White else StudioTextSecondary)
                                    }
                                }
                            }
                        }

                        Text("Sync: Tempo Locked (16th)", fontSize = 8.sp, color = AbletonGreen)
                    }
                }

                // MIDI FX 2: Chord Generator & Scale
                AbletonDeviceCard(
                    deviceName = "CHORD & SCALE",
                    deviceTypeTag = "MIDI FX",
                    headerColor = AbletonBlue.copy(alpha = 0.8f),
                    isEnabled = isChordEnabled,
                    onToggleEnabled = { isChordEnabled = !isChordEnabled },
                    modifier = Modifier.width(170.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Chord Memory", fontSize = 8.sp, color = StudioTextSecondary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf("Off", "Maj", "Min", "7th", "9th").forEach { ch ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(20.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (ch == "Off") AbletonBlue else AbletonHeader),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(ch, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Scale Snap: Active Pentatonic", fontSize = 8.sp, color = AbletonGreen)
                        Text("Velocity Humanize: ±12%", fontSize = 8.sp, color = StudioTextSecondary)
                    }
                }
            }

            // ==========================================
            // 2. INSTRUMENT DEVICES (Analog Synth / Drum Rack)
            // ==========================================
            if (selectedCategory == DeviceChainCategory.ALL || selectedCategory == DeviceChainCategory.INSTRUMENT) {
                // Instrument 1: Ableton Analog Lead Synth
                AbletonDeviceCard(
                    deviceName = "ANALOG LEAD",
                    deviceTypeTag = "INSTRUMENT",
                    headerColor = AbletonPurple,
                    isEnabled = true,
                    onToggleEnabled = { },
                    modifier = Modifier.width(260.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Oscillator Waveform Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Osc 1", fontSize = 8.sp, color = StudioTextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Waveform.values().take(4).forEach { wave ->
                                    val isCur = osc1Wave == wave
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (isCur) AbletonPurple else AbletonHeader)
                                            .clickable {
                                                osc1Wave = wave
                                                engine.vco1Waveform = wave
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(wave.name.take(3), fontSize = 7.sp, fontWeight = FontWeight.Bold, color = if (isCur) Color.White else StudioTextSecondary)
                                    }
                                }
                            }
                        }

                        // Filter Cutoff & Res
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Cutoff: ${filterCutoff.toInt()}Hz", fontSize = 8.sp, color = AbletonPurple)
                                Slider(
                                    value = filterCutoff,
                                    onValueChange = {
                                        filterCutoff = it
                                        engine.filterCutoff = it
                                    },
                                    valueRange = 40f..12000f,
                                    modifier = Modifier.height(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Resonance: ${(filterRes * 100).toInt()}%", fontSize = 8.sp, color = StudioTextPrimary)
                                Slider(
                                    value = filterRes,
                                    onValueChange = {
                                        filterRes = it
                                        engine.filterResonance = it
                                    },
                                    valueRange = 0f..0.95f,
                                    modifier = Modifier.height(18.dp)
                                )
                            }
                        }

                        // Amp Envelope (Attack & Release)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Attack: ${(ampAttack * 1000).toInt()}ms", fontSize = 8.sp, color = StudioTextSecondary)
                                Slider(
                                    value = ampAttack,
                                    onValueChange = {
                                        ampAttack = it
                                        engine.attackTime = it
                                    },
                                    valueRange = 0.001f..1.5f,
                                    modifier = Modifier.height(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Release: ${(ampRelease * 1000).toInt()}ms", fontSize = 8.sp, color = StudioTextSecondary)
                                Slider(
                                    value = ampRelease,
                                    onValueChange = {
                                        ampRelease = it
                                        engine.releaseTime = it
                                    },
                                    valueRange = 0.01f..3f,
                                    modifier = Modifier.height(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 3. AUDIO FX DEVICES
            // ==========================================
            if (selectedCategory == DeviceChainCategory.ALL || selectedCategory == DeviceChainCategory.AUDIO_FX) {
                modules.forEach { module ->
                    val isModuleEnabled = module.isEnabled

                    AbletonDeviceCard(
                        deviceName = module.type.displayName.uppercase(),
                        deviceTypeTag = "AUDIO FX",
                        headerColor = AbletonOrange,
                        isEnabled = isModuleEnabled,
                        onToggleEnabled = {
                            viewModel.toggleEffectBypass(module.id)
                        },
                        onDelete = { viewModel.removeEffectFromRack(module.id) },
                        modifier = Modifier.width(200.dp)
                    ) {
                        when (module) {
                            is ReverbModule -> {
                                var roomSize by remember { mutableStateOf(module.roomSize) }
                                var damping by remember { mutableStateOf(module.damping) }
                                var mix by remember { mutableStateOf(module.mix) }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Size: ${(roomSize * 100).toInt()}%", fontSize = 8.sp, color = StudioTextPrimary)
                                    Slider(value = roomSize, onValueChange = { roomSize = it; module.roomSize = it }, valueRange = 0f..1f, modifier = Modifier.height(18.dp))
                                    Text("Damping: ${(damping * 100).toInt()}%", fontSize = 8.sp, color = StudioTextSecondary)
                                    Slider(value = damping, onValueChange = { damping = it; module.damping = it }, valueRange = 0f..1f, modifier = Modifier.height(18.dp))
                                    Text("Dry/Wet: ${(mix * 100).toInt()}%", fontSize = 8.sp, color = AbletonOrange)
                                    Slider(value = mix, onValueChange = { mix = it; module.mix = it }, valueRange = 0f..1f, modifier = Modifier.height(18.dp))
                                }
                            }
                            is DelayModule -> {
                                var timeMs by remember { mutableStateOf(module.timeMs) }
                                var feedback by remember { mutableStateOf(module.feedback) }
                                var mix by remember { mutableStateOf(module.mix) }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Time: ${timeMs.toInt()} ms", fontSize = 8.sp, color = StudioTextPrimary)
                                    Slider(value = timeMs, onValueChange = { timeMs = it; module.timeMs = it }, valueRange = 20f..1000f, modifier = Modifier.height(18.dp))
                                    Text("Feedback: ${(feedback * 100).toInt()}%", fontSize = 8.sp, color = StudioTextSecondary)
                                    Slider(value = feedback, onValueChange = { feedback = it; module.feedback = it }, valueRange = 0f..0.95f, modifier = Modifier.height(18.dp))
                                    Text("Dry/Wet: ${(mix * 100).toInt()}%", fontSize = 8.sp, color = AbletonOrange)
                                    Slider(value = mix, onValueChange = { mix = it; module.mix = it }, valueRange = 0f..1f, modifier = Modifier.height(18.dp))
                                }
                            }
                            is FilterModule -> {
                                var cutoff by remember { mutableStateOf(module.cutoffHz) }
                                var res by remember { mutableStateOf(module.resonance) }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Cutoff: ${cutoff.toInt()} Hz", fontSize = 8.sp, color = AbletonOrange)
                                    Slider(value = cutoff, onValueChange = { cutoff = it; module.cutoffHz = it }, valueRange = 20f..15000f, modifier = Modifier.height(18.dp))
                                    Text("Resonance: ${(res * 100).toInt()}%", fontSize = 8.sp, color = StudioTextPrimary)
                                    Slider(value = res, onValueChange = { res = it; module.resonance = it }, valueRange = 0f..0.95f, modifier = Modifier.height(18.dp))
                                }
                            }
                            is DistortionModule -> {
                                var drive by remember { mutableStateOf(module.drive) }
                                var tone by remember { mutableStateOf(module.tone) }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Drive: ${drive.toInt()}x", fontSize = 8.sp, color = Color.Red)
                                    Slider(value = drive, onValueChange = { drive = it; module.drive = it }, valueRange = 1f..25f, modifier = Modifier.height(18.dp))
                                    Text("Tone: ${(tone * 100).toInt()}%", fontSize = 8.sp, color = StudioTextPrimary)
                                    Slider(value = tone, onValueChange = { tone = it; module.tone = it }, valueRange = 0f..1f, modifier = Modifier.height(18.dp))
                                }
                            }
                            is ChorusModule -> {
                                var rate by remember { mutableStateOf(module.rateHz) }
                                var depth by remember { mutableStateOf(module.depth) }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Rate: ${String.format("%.1f", rate)} Hz", fontSize = 8.sp, color = AbletonBlue)
                                    Slider(value = rate, onValueChange = { rate = it; module.rateHz = it }, valueRange = 0.1f..8f, modifier = Modifier.height(18.dp))
                                    Text("Depth: ${(depth * 100).toInt()}%", fontSize = 8.sp, color = StudioTextPrimary)
                                    Slider(value = depth, onValueChange = { depth = it; module.depth = it }, valueRange = 0f..1f, modifier = Modifier.height(18.dp))
                                }
                            }
                            is ParametricEqModule -> {
                                var low by remember { mutableStateOf(module.lowGainDb) }
                                var mid by remember { mutableStateOf(module.midGainDb) }
                                var high by remember { mutableStateOf(module.highGainDb) }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Low: ${low.toInt()} dB | Mid: ${mid.toInt()} dB | High: ${high.toInt()} dB", fontSize = 8.sp, color = StudioTextPrimary)
                                    Slider(value = mid, onValueChange = { mid = it; module.midGainDb = it }, valueRange = -12f..12f, modifier = Modifier.height(18.dp))
                                }
                            }
                            is CompressorModule -> {
                                var thresh by remember { mutableStateOf(module.thresholdDb) }
                                var ratio by remember { mutableStateOf(module.ratio) }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Threshold: ${thresh.toInt()} dB | Ratio: ${ratio.toInt()}:1", fontSize = 8.sp, color = AbletonYellow)
                                    Slider(value = thresh, onValueChange = { thresh = it; module.thresholdDb = it }, valueRange = -40f..0f, modifier = Modifier.height(18.dp))
                                }
                            }
                            else -> {
                                Text("Module Active: ${module.type.displayName}", fontSize = 9.sp, color = StudioTextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Effect Dialog
    if (showAddEffectDialog) {
        AlertDialog(
            onDismissRequest = { showAddEffectDialog = false },
            title = { Text("ADD AUDIO FX DEVICE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AbletonOrange) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        "Reverb" to ("Space & Room Acoustics" to EffectType.REVERB),
                        "Delay" to ("Echo & Stereo Ping-Pong" to EffectType.DELAY),
                        "Filter" to ("Lowpass / Highpass Resonant Filter" to EffectType.FILTER),
                        "Distortion" to ("Analog Overdrive & Saturation" to EffectType.DISTORTION),
                        "Chorus" to ("Multi-voice Stereo Chorus" to EffectType.CHORUS),
                        "Parametric EQ" to ("3-Band Tone Shaper EQ" to EffectType.PARAMETRIC_EQ),
                        "Compressor" to ("Dynamic Glue Compressor" to EffectType.COMPRESSOR)
                    ).forEach { (name, pair) ->
                        val (desc, effectType) = pair
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(AbletonPanel)
                                .clickable {
                                    viewModel.addEffectToRack(effectType)
                                    showAddEffectDialog = false
                                }
                                .padding(8.dp)
                        ) {
                            Column {
                                Text(name, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                                Text(desc, fontSize = 9.sp, color = StudioTextSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddEffectDialog = false }) {
                    Text("CLOSE", color = AbletonOrange)
                }
            },
            containerColor = AbletonSurface
        )
    }
}

@Composable
fun DeviceCategoryChip(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (isSelected) color.copy(alpha = 0.3f) else AbletonPanel)
            .border(1.dp, if (isSelected) color else AbletonBorder, RoundedCornerShape(2.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (isSelected) color else StudioTextSecondary)
    }
}

@Composable
fun AbletonDeviceCard(
    deviceName: String,
    deviceTypeTag: String,
    headerColor: Color,
    isEnabled: Boolean,
    onToggleEnabled: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .height(175.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isEnabled) AbletonPanel else AbletonPanel.copy(alpha = 0.5f))
            .border(
                1.dp,
                if (isEnabled) headerColor.copy(alpha = 0.6f) else AbletonBorder,
                RoundedCornerShape(4.dp)
            )
    ) {
        // Ableton Device Header (Yellow Toggle Button + Title + Drag Handle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerColor.copy(alpha = 0.25f))
                .border(1.dp, headerColor.copy(alpha = 0.4f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Ableton Yellow Device Power Switch
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) AbletonYellow else Color(0xFF333842))
                        .clickable { onToggleEnabled() }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = deviceName,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = deviceTypeTag,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = headerColor
                )
                if (onDelete != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = StudioTextSecondary,
                        modifier = Modifier.size(12.dp).clickable { onDelete() }
                    )
                }
            }
        }

        // Device Control Surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            content()
        }
    }
}
