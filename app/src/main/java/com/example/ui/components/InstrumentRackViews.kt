package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.*

@Composable
fun InstrumentSelectorStrip(
    activeInstrument: InstrumentType,
    onSelectInstrument: (InstrumentType) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = AbletonSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, AbletonBorder),
        modifier = modifier.fillMaxWidth().testTag("instrument_selector_strip")
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Text(
                text = "ABLETON SYNTH & INSTRUMENT RACK",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = AbletonPurple,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                InstrumentType.values().forEach { inst ->
                    val isSelected = (inst == activeInstrument)
                    val accent = when (inst) {
                        InstrumentType.ANALOG_SUB -> AbletonOrange
                        InstrumentType.WAVETABLE -> AbletonCyan
                        InstrumentType.FM_OPERATOR -> AbletonGreen
                        InstrumentType.SAMPLER -> AbletonYellow
                        InstrumentType.ELECTRIC_PIANO -> AbletonPink
                        InstrumentType.STRING_PAD -> AbletonPurple
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) accent else AbletonPanel)
                            .border(1.dp, if (isSelected) accent else AbletonBorder, RoundedCornerShape(4.dp))
                            .clickable { onSelectInstrument(inst) }
                            .testTag("instrument_tab_${inst.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (inst) {
                                InstrumentType.ANALOG_SUB -> "ANALOG"
                                InstrumentType.WAVETABLE -> "WAVETABLE"
                                InstrumentType.FM_OPERATOR -> "4-OP FM"
                                InstrumentType.SAMPLER -> "SIMPLER"
                                InstrumentType.ELECTRIC_PIANO -> "E-PIANO"
                                InstrumentType.STRING_PAD -> "SOLINA"
                            },
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else StudioTextPrimary
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 1. WAVETABLE MORPHING SYNTH EDITOR
// ==========================================
@Composable
fun WavetableSynthEditor(
    viewModel: SynthViewModel,
    modifier: Modifier = Modifier
) {
    val bank by viewModel.wavetableBank.collectAsState()
    val position by viewModel.wavetablePosition.collectAsState()
    val warpMode by viewModel.wavetableWarpMode.collectAsState()
    val warpAmount by viewModel.wavetableWarpAmount.collectAsState()
    val unisonVoices by viewModel.unisonVoices.collectAsState()
    val unisonDetune by viewModel.unisonDetune.collectAsState()

    var showBankMenu by remember { mutableStateOf(false) }
    var showWarpMenu by remember { mutableStateOf(false) }

    Surface(
        color = AbletonSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, AbletonBorder),
        modifier = modifier.fillMaxWidth().testTag("wavetable_editor_panel")
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header & Bank Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(AbletonCyan, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "WAVETABLE DUAL-MORPH ENGINE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AbletonCyan
                    )
                }

                // Bank Selector
                Box {
                    Button(
                        onClick = { showBankMenu = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AbletonPanel),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(bank.displayName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = StudioTextPrimary)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = StudioTextSecondary, modifier = Modifier.size(12.dp))
                    }

                    DropdownMenu(
                        expanded = showBankMenu,
                        onDismissRequest = { showBankMenu = false },
                        modifier = Modifier.background(AbletonSurface).border(1.dp, AbletonBorder)
                    ) {
                        WavetableBank.values().forEach { b ->
                            DropdownMenuItem(
                                text = { Text(b.displayName, fontSize = 10.sp, color = if (b == bank) AbletonCyan else StudioTextPrimary) },
                                onClick = {
                                    viewModel.setWavetableBank(b)
                                    showBankMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Wavetable Morph Visualizer Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF0A0D12))
                    .border(1.dp, AbletonBorder, RoundedCornerShape(4.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val midY = h / 2f

                    // Draw 3 background wireframe frames representing wavetable layers
                    repeat(3) { layerIdx ->
                        val layerPos = layerIdx / 2f
                        val layerPath = Path()
                        layerPath.moveTo(0f, midY - (layerIdx - 1) * 8f)
                        for (x in 0 until w.toInt() step 3) {
                            val phase = (x / w) * (Math.PI.toFloat() * 2f)
                            val morphVal = kotlin.math.sin(phase) * (1f - layerPos) + (if (phase % (Math.PI.toFloat()) < 1f) 0.6f else -0.6f) * layerPos
                            val y = midY - (layerIdx - 1) * 8f - (morphVal * (h * 0.25f))
                            layerPath.lineTo(x.toFloat(), y)
                        }
                        drawPath(
                            path = layerPath,
                            color = AbletonCyan.copy(alpha = if (layerIdx == 1) 0.3f else 0.15f),
                            style = Stroke(width = 1f)
                        )
                    }

                    // Draw Active Interpolated Wavetable Wave
                    val activePath = Path()
                    activePath.moveTo(0f, midY)
                    for (x in 0 until w.toInt()) {
                        val phase = (x / w) * (Math.PI.toFloat() * 2f)
                        // Interpolate sine -> saw/complex based on position
                        val v1 = kotlin.math.sin(phase)
                        val v2 = (2.0f * ((phase / (Math.PI.toFloat() * 2f)) - kotlin.math.floor((phase / (Math.PI.toFloat() * 2f)) + 0.5f))).toFloat()
                        val v3 = kotlin.math.sin(phase * 3f) * 0.5f + kotlin.math.cos(phase * 2f) * 0.3f
                        val morphed = if (position < 0.5f) {
                            v1 * (1f - (position * 2f)) + v2 * (position * 2f)
                        } else {
                            val f = (position - 0.5f) * 2f
                            v2 * (1f - f) + v3 * f
                        }
                        val y = midY - (morphed * (h * 0.38f))
                        activePath.lineTo(x.toFloat(), y)
                    }

                    drawPath(
                        path = activePath,
                        color = AbletonCyan,
                        style = Stroke(width = 2.5f)
                    )

                    // Position Playhead line
                    val px = position * w
                    drawLine(AbletonYellow, Offset(px, 0f), Offset(px, h), strokeWidth = 2f)
                }
            }

            // Controls Row (Position Slider + Warp + Unison)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Table Position Knob
                StudioKnob(
                    value = position,
                    label = "WT POS",
                    displayValue = "${(position * 100).toInt()}%",
                    accentColor = AbletonCyan,
                    onValueChange = { viewModel.setWavetablePosition(it) }
                )

                // Warp Mode & Amount
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        Button(
                            onClick = { showWarpMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AbletonPanel),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(3.dp),
                            modifier = Modifier.height(22.dp)
                        ) {
                            Text(warpMode.name, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = StudioTextPrimary)
                        }

                        DropdownMenu(
                            expanded = showWarpMenu,
                            onDismissRequest = { showWarpMenu = false },
                            modifier = Modifier.background(AbletonSurface).border(1.dp, AbletonBorder)
                        ) {
                            WavetableWarpMode.values().forEach { wm ->
                                DropdownMenuItem(
                                    text = { Text(wm.name, fontSize = 9.sp, color = if (wm == warpMode) AbletonYellow else StudioTextPrimary) },
                                    onClick = {
                                        viewModel.setWavetableWarpMode(wm)
                                        showWarpMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    StudioKnob(
                        value = warpAmount,
                        label = "WARP",
                        displayValue = "${(warpAmount * 100).toInt()}%",
                        accentColor = AbletonYellow,
                        onValueChange = { viewModel.setWavetableWarpAmount(it) }
                    )
                }

                // Unison Voices (1 to 7)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("UNISON", fontSize = 8.sp, color = StudioTextSecondary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(AbletonPanel)
                                .clickable { viewModel.setUnisonVoices(unisonVoices - 1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("-", fontSize = 11.sp, color = StudioTextPrimary, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "$unisonVoices",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AbletonGreen,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(AbletonPanel)
                                .clickable { viewModel.setUnisonVoices(unisonVoices + 1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", fontSize = 11.sp, color = StudioTextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Unison Detune Knob
                StudioKnob(
                    value = unisonDetune,
                    label = "DETUNE",
                    displayValue = "${(unisonDetune * 100).toInt()}%",
                    accentColor = AbletonGreen,
                    onValueChange = { viewModel.setUnisonDetune(it) }
                )
            }
        }
    }
}

// ==========================================
// 2. 4-OPERATOR FM SYNTHESIS MATRIX
// ==========================================
@Composable
fun FmOperatorSynthEditor(
    viewModel: SynthViewModel,
    modifier: Modifier = Modifier
) {
    val engine = viewModel.engine
    val fmSynth = engine.fmSynth
    val currentAlgo by viewModel.fmAlgorithm.collectAsState()

    Surface(
        color = AbletonSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, AbletonBorder),
        modifier = modifier.fillMaxWidth().testTag("fm_synth_editor_panel")
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header & Algorithm Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(AbletonGreen, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "4-OPERATOR FM ALGORITHM MATRIX",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AbletonGreen
                    )
                }

                Text(
                    text = currentAlgo.description,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AbletonYellow
                )
            }

            // Algorithm Quick Select Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FmAlgorithm.values().forEach { algo ->
                    val isSelected = (algo == currentAlgo)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isSelected) AbletonGreen else AbletonPanel)
                            .clickable { viewModel.setFmAlgorithm(algo) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = algo.displayName,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else StudioTextPrimary
                        )
                    }
                }
            }

            // 4 Operator Cards Matrix
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val opNames = listOf("OP A (CARRIER)", "OP B (MOD 1)", "OP C (MOD 2)", "OP D (MOD 3)")
                repeat(4) { idx ->
                    val op = fmSynth.operators[idx]
                    var ratio by remember { mutableStateOf(op.ratio) }
                    var level by remember { mutableStateOf(op.level) }
                    var feedback by remember { mutableStateOf(op.feedback) }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(AbletonPanel, RoundedCornerShape(4.dp))
                            .border(1.dp, if (idx == 0) AbletonGreen else AbletonBorder, RoundedCornerShape(4.dp))
                            .padding(4.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = opNames[idx],
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (idx == 0) AbletonGreen else StudioTextSecondary
                            )

                            // Frequency Ratio
                            StudioKnob(
                                value = (ratio / 8f).coerceIn(0f, 1f),
                                label = "RATIO",
                                displayValue = "${String.format("%.1f", ratio)}x",
                                accentColor = if (idx == 0) AbletonGreen else AbletonCyan,
                                onValueChange = {
                                    val r = (it * 8f).coerceAtLeast(0.5f)
                                    ratio = r
                                    viewModel.setFmOperatorRatio(idx, r)
                                }
                            )

                            // Level
                            StudioKnob(
                                value = level,
                                label = "LEVEL",
                                displayValue = "${(level * 100).toInt()}%",
                                accentColor = AbletonYellow,
                                onValueChange = {
                                    level = it
                                    viewModel.setFmOperatorLevel(idx, it)
                                }
                            )

                            // Feedback (for Op D / Carrier)
                            if (idx == 3 || idx == 0) {
                                StudioKnob(
                                    value = feedback,
                                    label = "FBACK",
                                    displayValue = "${(feedback * 100).toInt()}%",
                                    accentColor = AbletonOrange,
                                    onValueChange = {
                                        feedback = it
                                        viewModel.setFmOperatorFeedback(idx, it)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. PHYSICAL MODELING & VINTAGE INSTRUMENTS
// ==========================================
@Composable
fun PhysicalModelingSynthEditor(
    viewModel: SynthViewModel,
    instrumentType: InstrumentType,
    modifier: Modifier = Modifier
) {
    val tineDecay by viewModel.rhodesTineDecay.collectAsState()
    val tremoloRate by viewModel.rhodesTremoloRate.collectAsState()
    val tremoloDepth by viewModel.rhodesTremoloDepth.collectAsState()
    val rhodesDrive by viewModel.rhodesDrive.collectAsState()

    val stringChorus by viewModel.stringPadChorus.collectAsState()
    val stringSpeed by viewModel.stringPadSpeed.collectAsState()
    val stringOctave by viewModel.stringPadOctave.collectAsState()

    Surface(
        color = AbletonSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, AbletonBorder),
        modifier = modifier.fillMaxWidth().testTag("phys_modeling_editor_panel")
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(8.dp).background(
                        if (instrumentType == InstrumentType.ELECTRIC_PIANO) AbletonPink else AbletonPurple,
                        CircleShape
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (instrumentType == InstrumentType.ELECTRIC_PIANO) "RHODES SUITCASE 73 PHYSICAL MODEL" else "SOLINA VINTAGE STRING ENSEMBLE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (instrumentType == InstrumentType.ELECTRIC_PIANO) AbletonPink else AbletonPurple
                )
            }

            if (instrumentType == InstrumentType.ELECTRIC_PIANO) {
                // Rhodes Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioKnob(
                        value = (tineDecay / 5f).coerceIn(0f, 1f),
                        label = "TINE DECAY",
                        displayValue = "${String.format("%.1f", tineDecay)}s",
                        accentColor = AbletonPink,
                        onValueChange = { viewModel.setRhodesTineDecay(it * 5f) }
                    )
                    StudioKnob(
                        value = (tremoloRate / 12f).coerceIn(0f, 1f),
                        label = "TREM SPEED",
                        displayValue = "${String.format("%.1f", tremoloRate)}Hz",
                        accentColor = AbletonYellow,
                        onValueChange = { viewModel.setRhodesTremolo(it * 12f, tremoloDepth) }
                    )
                    StudioKnob(
                        value = tremoloDepth,
                        label = "TREM DEPTH",
                        displayValue = "${(tremoloDepth * 100).toInt()}%",
                        accentColor = AbletonCyan,
                        onValueChange = { viewModel.setRhodesTremolo(tremoloRate, it) }
                    )
                    StudioKnob(
                        value = rhodesDrive,
                        label = "TUBE DRIVE",
                        displayValue = "${(rhodesDrive * 100).toInt()}%",
                        accentColor = AbletonOrange,
                        onValueChange = { viewModel.setRhodesDrive(it) }
                    )
                }
            } else {
                // Solina String Pad Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioKnob(
                        value = stringChorus,
                        label = "ENSEMBLE",
                        displayValue = "${(stringChorus * 100).toInt()}%",
                        accentColor = AbletonPurple,
                        onValueChange = { viewModel.setStringPadChorus(it) }
                    )
                    StudioKnob(
                        value = (stringSpeed / 3f).coerceIn(0f, 1f),
                        label = "LFO RATE",
                        displayValue = "${String.format("%.1f", stringSpeed)}Hz",
                        accentColor = AbletonCyan,
                        onValueChange = { viewModel.setStringPadSpeed(it * 3f) }
                    )
                    // Octave Layer Toggle
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (stringOctave) AbletonPurple else AbletonPanel)
                            .clickable { viewModel.toggleStringPadOctave() }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (stringOctave) "OCTAVE: 8' + 4'" else "OCTAVE: 8' ONLY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (stringOctave) Color.White else StudioTextPrimary
                        )
                    }
                }
            }
        }
    }
}
