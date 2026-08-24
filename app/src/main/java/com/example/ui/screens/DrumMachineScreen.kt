package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.DrumType
import com.example.synth.SynthViewModel
import com.example.ui.components.*

@Composable
fun DrumMachineScreen(
    viewModel: SynthViewModel,
    modifier: Modifier = Modifier
) {
    val drumGrid by viewModel.drumGrid.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val selectedDrumType by viewModel.selectedDrumType.collectAsState()

    val selectedVoice = viewModel.engine.drumEngine.voices[selectedDrumType]

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AbletonBgDark)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ==========================================
        // 1. ABLETON DRUM RACK HEADER & PRESETS
        // ==========================================
        Surface(
            color = AbletonSurface,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, AbletonBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drum Rack Title with Indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AbletonYellow)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ABLETON DRUM RACK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AbletonYellow,
                        letterSpacing = 0.5.sp
                    )
                }

                // Quick Helpers
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 4-on-the-floor kick generator
                    Button(
                        onClick = {
                            viewModel.toggleDrumStep(DrumType.KICK, 0)
                            viewModel.toggleDrumStep(DrumType.KICK, 4)
                            viewModel.toggleDrumStep(DrumType.KICK, 8)
                            viewModel.toggleDrumStep(DrumType.KICK, 12)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AbletonPanel),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp).testTag("drum_4onfloor_btn")
                    ) {
                        Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = AbletonYellow, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("4-ON-FLOOR", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = StudioTextPrimary)
                    }

                    // Clear
                    IconButton(
                        onClick = { viewModel.clearDrumPattern(null) },
                        modifier = Modifier.size(24.dp).background(AbletonPanel, RoundedCornerShape(4.dp))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear All", tint = Color.Red, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // ==========================================
        // 2. ABLETON DRUM PADS MATRIX (MPC / Push Style)
        // ==========================================
        Surface(
            color = AbletonSurface,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, AbletonBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "VELOCITY-SENSITIVE PADS (TAP TO AUDITION)",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = StudioTextSecondary,
                    letterSpacing = 0.5.sp
                )

                // 2 Rows x 3 Columns Pad Grid
                val drumTypes = DrumType.values()
                val chunkedDrums = drumTypes.toList().chunked(3)

                chunkedDrums.forEach { rowDrums ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowDrums.forEach { drumType ->
                            val isSelected = selectedDrumType == drumType
                            val drumColor = when (drumType) {
                                DrumType.KICK -> AbletonOrange
                                DrumType.SNARE -> AbletonYellow
                                DrumType.HIHAT_CLOSED -> AbletonGreen
                                DrumType.HIHAT_OPEN -> AbletonGreen.copy(alpha = 0.8f)
                                DrumType.CLAP -> AbletonBlue
                                DrumType.TOM -> AbletonPurple
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) drumColor.copy(alpha = 0.25f) else AbletonPanel)
                                    .border(
                                        1.5.dp,
                                        if (isSelected) drumColor else AbletonBorder,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .pointerInput(drumType) {
                                        detectTapGestures(
                                            onPress = {
                                                viewModel.selectDrumType(drumType)
                                                viewModel.engine.triggerDrum(drumType, 0.9f)
                                                tryAwaitRelease()
                                            }
                                        )
                                    }
                                    .testTag("drum_pad_${drumType.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = drumType.displayName.take(10).uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else StudioTextPrimary
                                    )
                                    Text(
                                        text = "PAD ${(drumTypes.indexOf(drumType) + 1)}",
                                        fontSize = 7.sp,
                                        color = drumColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. 16-STEP GROOVE SEQUENCER MATRIX
        // ==========================================
        Surface(
            color = AbletonSurface,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, AbletonBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "16-STEP PATTERN MATRIX",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AbletonOrange,
                        letterSpacing = 0.5.sp
                    )

                    // Step Indicator Bar
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (i in 0 until 16) {
                            val isCur = isPlaying && currentStep == i
                            val isBeat = (i % 4 == 0)
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCur) AbletonYellow
                                        else if (isBeat) AbletonOrange.copy(alpha = 0.6f)
                                        else AbletonBorder
                                    )
                            )
                        }
                    }
                }

                // Step Column Numbers (1, 2, 3... 16)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(54.dp))
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        for (i in 0 until 16) {
                            val isBeatStart = (i % 4 == 0)
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${i + 1}",
                                    fontSize = 7.sp,
                                    fontWeight = if (isBeatStart) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isBeatStart) AbletonYellow else StudioTextSecondary
                                )
                            }
                        }
                    }
                }

                // Drum Step Rows
                DrumType.values().forEach { drumType ->
                    val steps = drumGrid[drumType] ?: List(16) { 0.0f }
                    val isSelected = selectedDrumType == drumType
                    val drumColor = when (drumType) {
                        DrumType.KICK -> AbletonOrange
                        DrumType.SNARE -> AbletonYellow
                        DrumType.HIHAT_CLOSED -> AbletonGreen
                        DrumType.HIHAT_OPEN -> AbletonGreen.copy(alpha = 0.8f)
                        DrumType.CLAP -> AbletonBlue
                        DrumType.TOM -> AbletonPurple
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Drum Type Label Button
                        Box(
                            modifier = Modifier
                                .width(52.dp)
                                .height(26.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isSelected) drumColor.copy(alpha = 0.3f) else AbletonPanel)
                                .border(1.dp, if (isSelected) drumColor else AbletonBorder, RoundedCornerShape(3.dp))
                                .clickable {
                                    viewModel.selectDrumType(drumType)
                                    viewModel.engine.triggerDrum(drumType, 0.85f)
                                }
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = drumType.shortName,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else StudioTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // 16 Step Buttons (with 4-beat color grouping)
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            for (stepIndex in 0 until 16) {
                                val vel = steps.getOrElse(stepIndex) { 0.0f }
                                val isActive = vel > 0.05f
                                val isCurrentStep = isPlaying && currentStep == stepIndex
                                val isBeatGroupDark = (stepIndex / 4) % 2 == 1

                                val stepBg = when {
                                    isActive && isCurrentStep -> Color.White
                                    isActive -> drumColor
                                    isCurrentStep -> AbletonYellow.copy(alpha = 0.5f)
                                    isBeatGroupDark -> Color(0xFF1B1D22)
                                    else -> Color(0xFF262930)
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(26.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(stepBg)
                                        .border(
                                            0.5.dp,
                                            if (isActive) Color.White.copy(alpha = 0.5f) else AbletonBorder,
                                            RoundedCornerShape(2.dp)
                                        )
                                        .clickable { viewModel.toggleDrumStep(drumType, stepIndex) }
                                        .testTag("step_${drumType.name.lowercase()}_$stepIndex")
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 4. ABLETON DRUM VOICE DSP CONTROLS
        // ==========================================
        if (selectedVoice != null) {
            Surface(
                color = AbletonSurface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, AbletonBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedDrumType.displayName.uppercase()} VOICE PARAMETERS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AbletonYellow
                        )
                        Text(
                            text = "REAL-TIME DSP",
                            fontSize = 8.sp,
                            color = StudioTextSecondary
                        )
                    }

                    // Knobs Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        RotaryKnob(
                            value = selectedVoice.tune,
                            onValueChange = { selectedVoice.tune = it },
                            range = 0.5f..2.0f,
                            label = "Tune",
                            accentColor = AbletonYellow,
                            size = 46.dp
                        )

                        RotaryKnob(
                            value = selectedVoice.decay,
                            onValueChange = { selectedVoice.decay = it },
                            range = 0.2f..3.0f,
                            label = "Decay",
                            accentColor = AbletonOrange,
                            size = 46.dp
                        )

                        RotaryKnob(
                            value = selectedVoice.volume,
                            onValueChange = { selectedVoice.volume = it },
                            range = 0.0f..1.0f,
                            label = "Volume",
                            accentColor = AbletonGreen,
                            size = 46.dp
                        )

                        RotaryKnob(
                            value = selectedVoice.pan,
                            onValueChange = { selectedVoice.pan = it },
                            range = -1.0f..1.0f,
                            label = "Pan",
                            accentColor = AbletonBlue,
                            size = 46.dp
                        )
                    }
                }
            }
        }
    }
}
