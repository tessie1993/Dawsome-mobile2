package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.*

private val DevBg = Color(0xFF1E2024)
private val DevChassis = Color(0xFF2B2E33)
private val DevHeader = Color(0xFF383C44)
private val DevBorder = Color(0xFF454B54)
private val DevOrange = Color(0xFFFF764D)
private val DevBlue = Color(0xFF29B6F6)
private val DevGreen = Color(0xFF00E676)
private val DevYellow = Color(0xFFFFD54F)
private val DevMuted = Color(0xFF8A909E)

@Composable
fun AbletonMacroRackDevice(
    viewModel: SynthViewModel,
    modifier: Modifier = Modifier
) {
    val macroRack by viewModel.macroRack.collectAsState()

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = DevChassis,
        border = androidx.compose.foundation.BorderStroke(1.dp, DevBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("macro_rack_device")
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Device Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DevHeader, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (macroRack.isEnabled) DevOrange else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AUDIO EFFECT RACK",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = DevOrange
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MACRO CONTROLS",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = DevMuted
                    )
                }

                // Preset Quick Dropdown / Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val presets = listOf("Club Banger", "Acid 303", "Ambient Space", "Lo-Fi Tape")
                    presets.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(DevBg)
                                .clickable {
                                    val fullName = when (preset) {
                                        "Club Banger" -> "Club Banger Master"
                                        "Acid 303" -> "Acid Tweaker 303"
                                        "Ambient Space" -> "Ambient Lush Space"
                                        else -> "Lo-Fi Tape Machine"
                                    }
                                    viewModel.loadMacroRackPreset(fullName)
                                }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(preset, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = DevYellow)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 8 Macro Knobs in 2 Rows of 4
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    macroRack.macros.take(4).forEach { macro ->
                        MacroKnobItem(
                            macro = macro,
                            onValueChange = { viewModel.setMacroValue(macro.index, it) },
                            accentColor = DevOrange,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    macroRack.macros.drop(4).take(4).forEach { macro ->
                        MacroKnobItem(
                            macro = macro,
                            onValueChange = { viewModel.setMacroValue(macro.index, it) },
                            accentColor = DevBlue,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MacroKnobItem(
    macro: MacroControl,
    onValueChange: (Float) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(DevBg, RoundedCornerShape(4.dp))
            .border(0.5.dp, DevBorder, RoundedCornerShape(4.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "M${macro.index + 1}: ${macro.name}",
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = macro.targetParam,
            fontSize = 7.sp,
            color = DevMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        RotaryKnob(
            value = macro.value,
            onValueChange = onValueChange,
            range = 0f..1f,
            label = "",
            accentColor = accentColor,
            size = 38.dp
        )

        Text(
            text = "${(macro.value * 100).toInt()}%",
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            color = accentColor
        )
    }
}

@Composable
fun AbletonLfoDevice(
    viewModel: SynthViewModel,
    modifier: Modifier = Modifier
) {
    val lfoDevice by viewModel.lfoDevice.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    // Real-time animation phase for visualizer
    val infiniteTransition = rememberInfiniteTransition(label = "lfo_phase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (1000f / lfoDevice.rateHz.coerceAtLeast(0.1f)).toInt().coerceIn(100, 10000),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "lfo_anim"
    )

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = DevChassis,
        border = androidx.compose.foundation.BorderStroke(1.dp, DevBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("lfo_device")
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DevHeader, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (lfoDevice.isEnabled) DevGreen else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LFO MODULATOR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = DevGreen
                    )
                }

                // Enable/Bypass switch
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (lfoDevice.isEnabled) DevGreen else DevBg)
                        .clickable {
                            viewModel.updateLfo(
                                isEnabled = !lfoDevice.isEnabled,
                                waveform = lfoDevice.waveform,
                                rateHz = lfoDevice.rateHz,
                                depth = lfoDevice.depth,
                                target = lfoDevice.target
                            )
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (lfoDevice.isEnabled) "ACTIVE" else "BYPASS",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (lfoDevice.isEnabled) Color.Black else DevMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body: Waveform Canvas & Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Waveform Display Canvas
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .height(70.dp)
                        .background(DevBg, RoundedCornerShape(4.dp))
                        .border(0.5.dp, DevBorder, RoundedCornerShape(4.dp))
                        .padding(4.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val midY = h / 2f

                        // Grid center line
                        drawLine(
                            color = DevBorder.copy(alpha = 0.5f),
                            start = Offset(0f, midY),
                            end = Offset(w, midY),
                            strokeWidth = 1f
                        )

                        val path = Path()
                        val steps = 60
                        for (i in 0..steps) {
                            val x = (i / steps.toFloat()) * w
                            val t = (i / steps.toFloat()) + (if (isPlaying) phase else 0f)
                            val yVal = when (lfoDevice.waveform) {
                                Waveform.SINE -> kotlin.math.sin(t * 2 * Math.PI).toFloat()
                                Waveform.TRIANGLE -> {
                                    val cycle = (t % 1f)
                                    if (cycle < 0.5f) (cycle * 4f) - 1f else 3f - (cycle * 4f)
                                }
                                Waveform.SAWTOOTH -> ((t % 1f) * 2f) - 1f
                                Waveform.SQUARE -> if ((t % 1f) < 0.5f) 1f else -1f
                                Waveform.NOISE -> kotlin.math.sin(t * 12.0).toFloat() * 0.7f
                            }
                            val y = midY - (yVal * (h * 0.4f) * lfoDevice.depth)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }

                        drawPath(
                            path = path,
                            color = if (lfoDevice.isEnabled) DevGreen else DevMuted,
                            style = Stroke(width = 2f)
                        )

                        // Draw moving phase indicator dot
                        val curX = (phase * w) % w
                        val curYVal = kotlin.math.sin(phase * 2 * Math.PI).toFloat()
                        val curY = midY - (curYVal * (h * 0.4f) * lfoDevice.depth)
                        drawCircle(
                            color = if (lfoDevice.isEnabled) Color.White else Color.Gray,
                            radius = 3.5f,
                            center = Offset(curX, curY)
                        )
                    }

                    // Target label
                    Text(
                        text = "DEST: ${lfoDevice.target}",
                        fontSize = 7.sp,
                        fontFamily = FontFamily.Monospace,
                        color = DevYellow,
                        modifier = Modifier.align(Alignment.BottomStart)
                    )
                }

                // Waveform Selector
                Column(
                    modifier = Modifier.weight(0.9f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Waveform.values().forEach { wf ->
                        val isSelected = lfoDevice.waveform == wf
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isSelected) DevGreen else DevBg)
                                .clickable {
                                    viewModel.updateLfo(
                                        isEnabled = lfoDevice.isEnabled,
                                        waveform = wf,
                                        rateHz = lfoDevice.rateHz,
                                        depth = lfoDevice.depth,
                                        target = lfoDevice.target
                                    )
                                }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = wf.name,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else DevMuted
                            )
                        }
                    }
                }

                // Rate & Depth Rotary Knobs
                Row(
                    modifier = Modifier.weight(1.3f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RotaryKnob(
                        value = lfoDevice.rateHz,
                        onValueChange = {
                            viewModel.updateLfo(
                                isEnabled = lfoDevice.isEnabled,
                                waveform = lfoDevice.waveform,
                                rateHz = it,
                                depth = lfoDevice.depth,
                                target = lfoDevice.target
                            )
                        },
                        range = 0.1f..20.0f,
                        label = "Rate",
                        unit = "Hz",
                        accentColor = DevGreen,
                        size = 38.dp
                    )

                    RotaryKnob(
                        value = lfoDevice.depth,
                        onValueChange = {
                            viewModel.updateLfo(
                                isEnabled = lfoDevice.isEnabled,
                                waveform = lfoDevice.waveform,
                                rateHz = lfoDevice.rateHz,
                                depth = it,
                                target = lfoDevice.target
                            )
                        },
                        range = 0f..1f,
                        label = "Depth",
                        accentColor = DevGreen,
                        size = 38.dp
                    )
                }
            }
        }
    }
}
