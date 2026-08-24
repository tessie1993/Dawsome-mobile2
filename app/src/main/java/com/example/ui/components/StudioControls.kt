package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.FilterType
import com.example.synth.Waveform
import kotlin.math.*

// Hardware Studio Dark Color Scheme
val StudioDarkBg = Color(0xFF0C0E14)
val StudioCardBg = Color(0xFF141722)
val StudioCardHeader = Color(0xFF1B2030)
val StudioBorder = Color(0xFF282E44)
val StudioTextPrimary = Color(0xFFF1F5F9)
val StudioTextSecondary = Color(0xFF94A3B8)
val StudioKnobCap = Color(0xFF1E2235)

// Glowing Studio Accent Palette
val NeonCyan = Color(0xFF00E5FF)
val NeonGreen = Color(0xFF00FF66)
val NeonPink = Color(0xFFFF0055)
val NeonOrange = Color(0xFFFF9100)
val NeonYellow = Color(0xFFFFD600)
val NeonPurple = Color(0xFFB388FF)

// Ableton Live Distinctive Theme Palette
val AbletonBgDark = Color(0xFF1A1C1E)
val AbletonSurface = Color(0xFF24272B)
val AbletonPanel = Color(0xFF2D3139)
val AbletonHeader = Color(0xFF373C46)
val AbletonBorder = Color(0xFF454B57)
val AbletonOrange = Color(0xFFFF764D)
val AbletonYellow = Color(0xFFFED142)
val AbletonGreen = Color(0xFF57E389)
val AbletonBlue = Color(0xFF38A3FF)
val AbletonCyan = Color(0xFF00E5FF)
val AbletonRed = Color(0xFFFF3B30)
val AbletonPurple = Color(0xFFB28DFF)
val AbletonPink = Color(0xFFFF5286)
val AbletonTrackLead = Color(0xFFFF764D)
val AbletonTrackBass = Color(0xFF38A3FF)
val AbletonTrackDrums = Color(0xFFFED142)
val AbletonTrackMaster = Color(0xFFB28DFF)

@Composable
fun StudioKnob(
    value: Float,
    label: String,
    modifier: Modifier = Modifier,
    displayValue: String = "",
    accentColor: Color = AbletonCyan,
    onValueChange: (Float) -> Unit
) {
    RotaryKnob(
        value = value,
        onValueChange = onValueChange,
        range = 0f..1f,
        label = label,
        unit = displayValue,
        accentColor = accentColor,
        size = 38.dp,
        modifier = modifier
    )
}

@Composable
fun RotaryKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    modifier: Modifier = Modifier,
    unit: String = "",
    accentColor: Color = NeonCyan,
    size: Dp = 64.dp
) {
    val normalizedValue = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    var dragAccumulator by remember { mutableStateOf(0f) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(size + 16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .pointerInput(range) {
                    detectDragGestures(
                        onDragStart = { dragAccumulator = normalizedValue },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Vertical drag controls knob rotation
                            val delta = -dragAmount.y / 150f + dragAmount.x / 150f
                            dragAccumulator = (dragAccumulator + delta).coerceIn(0f, 1f)
                            val computed = range.start + dragAccumulator * (range.endInclusive - range.start)
                            onValueChange(computed)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(this.size.width / 2, this.size.height / 2)
                val radius = this.size.minDimension / 2 - 4.dp.toPx()

                // Background track arc (270 degrees: from 135 deg to 405 deg)
                val startAngle = 135f
                val sweepMax = 270f
                drawArc(
                    color = StudioBorder,
                    startAngle = startAngle,
                    sweepAngle = sweepMax,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )

                // Active glowing value arc
                val activeSweep = normalizedValue * sweepMax
                if (activeSweep > 0.5f) {
                    drawArc(
                        color = accentColor,
                        startAngle = startAngle,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Inner Knob Body
                val innerRadius = radius * 0.72f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(StudioKnobCap.copy(alpha = 0.9f), StudioCardBg),
                        center = center,
                        radius = innerRadius
                    ),
                    radius = innerRadius,
                    center = center
                )
                drawCircle(
                    color = StudioBorder,
                    radius = innerRadius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Pointer Line
                val currentAngleRad = Math.toRadians((startAngle + activeSweep).toDouble())
                val pointerStart = Offset(
                    (center.x + (innerRadius * 0.35f) * cos(currentAngleRad)).toFloat(),
                    (center.y + (innerRadius * 0.35f) * sin(currentAngleRad)).toFloat()
                )
                val pointerEnd = Offset(
                    (center.x + (innerRadius * 0.85f) * cos(currentAngleRad)).toFloat(),
                    (center.y + (innerRadius * 0.85f) * sin(currentAngleRad)).toFloat()
                )
                drawLine(
                    color = Color.White,
                    start = pointerStart,
                    end = pointerEnd,
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // Value text
        val displayStr = if (abs(value) >= 100f) "${value.toInt()}$unit"
        else if (abs(value) >= 10f) String.format("%.1f%s", value, unit)
        else String.format("%.2f%s", value, unit)

        Text(
            text = displayStr,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            maxLines = 1
        )
        Text(
            text = label.uppercase(),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = StudioTextSecondary,
            maxLines = 1
        )
    }
}

@Composable
fun StudioFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    accentColor: Color = NeonCyan
) {
    val normalized = value.coerceIn(0f, 1.5f) / 1.5f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(48.dp)
    ) {
        Box(
            modifier = Modifier
                .height(height)
                .width(28.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount.y / (height.toPx()) * 1.5f
                        onValueChange((value + delta).coerceIn(0f, 1.5f))
                    }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackX = size.width / 2
                val topY = 12.dp.toPx()
                val bottomY = size.height - 12.dp.toPx()
                val trackHeight = bottomY - topY

                // Fader Groove Slot
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(trackX - 3.dp.toPx(), topY),
                    size = Size(6.dp.toPx(), trackHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )

                // Fill level
                val fillY = bottomY - normalized * trackHeight
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.7f),
                    topLeft = Offset(trackX - 2.5.dp.toPx(), fillY),
                    size = Size(5.dp.toPx(), bottomY - fillY),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
                )

                // Fader Thumb Cap
                val thumbY = (bottomY - normalized * trackHeight).coerceIn(topY, bottomY)
                val thumbWidth = 24.dp.toPx()
                val thumbHeight = 16.dp.toPx()

                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF333B50), Color(0xFF1E2230))),
                    topLeft = Offset(trackX - thumbWidth / 2, thumbY - thumbHeight / 2),
                    size = Size(thumbWidth, thumbHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawRoundRect(
                    color = StudioBorder,
                    topLeft = Offset(trackX - thumbWidth / 2, thumbY - thumbHeight / 2),
                    size = Size(thumbWidth, thumbHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )

                // Center thumb line
                drawLine(
                    color = accentColor,
                    start = Offset(trackX - thumbWidth / 3, thumbY),
                    end = Offset(trackX + thumbWidth / 3, thumbY),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        val dbVal = if (value <= 0.01f) "-inf" else String.format("%.1fdB", 20 * log10(value.toDouble()))
        Text(
            text = dbVal,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = accentColor,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label.uppercase(),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = StudioTextSecondary
        )
    }
}

@Composable
fun LedVuMeter(
    level: Float,
    modifier: Modifier = Modifier,
    height: Dp = 100.dp,
    segments: Int = 12
) {
    val normalized = level.coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .height(height)
            .width(14.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .border(1.dp, StudioBorder, RoundedCornerShape(4.dp))
            .padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom)
    ) {
        for (i in (segments - 1) downTo 0) {
            val threshold = (i + 1).toFloat() / segments
            val isActive = normalized >= threshold

            val segColor = when {
                i >= segments - 2 -> NeonPink   // Peak / Clip
                i >= segments - 5 -> NeonYellow // Warning
                else -> NeonGreen               // Safe
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (isActive) segColor else segColor.copy(alpha = 0.12f)
                    )
            )
        }
    }
}

@Composable
fun WaveformSelector(
    selected: Waveform,
    onSelect: (Waveform) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .border(1.dp, StudioBorder, RoundedCornerShape(6.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Waveform.values().forEach { wav ->
            val isSelected = wav == selected
            val label = when (wav) {
                Waveform.SINE -> "SIN"
                Waveform.SAWTOOTH -> "SAW"
                Waveform.SQUARE -> "SQR"
                Waveform.TRIANGLE -> "TRI"
                Waveform.NOISE -> "NZ"
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) NeonCyan else Color.Transparent)
                    .clickable { onSelect(wav) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.Black else StudioTextSecondary
                )
            }
        }
    }
}

@Composable
fun EnvelopeVisualizer(
    attack: Float,
    decay: Float,
    sustain: Float,
    release: Float,
    modifier: Modifier = Modifier,
    accentColor: Color = NeonGreen
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .border(1.dp, StudioBorder, RoundedCornerShape(6.dp))
            .padding(4.dp)
    ) {
        val totalTime = attack + decay + 0.8f + release // fixed sustain display width
        val w = size.width
        val h = size.height - 4.dp.toPx()
        val bottomY = size.height - 2.dp.toPx()

        val x0 = 4.dp.toPx()
        val xA = x0 + (attack / totalTime) * (w - 8.dp.toPx())
        val xD = xA + (decay / totalTime) * (w - 8.dp.toPx())
        val xS = xD + (0.8f / totalTime) * (w - 8.dp.toPx())
        val xR = w - 4.dp.toPx()

        val yPeak = 4.dp.toPx()
        val ySustain = bottomY - (sustain.coerceIn(0f, 1f) * h)

        val path = Path().apply {
            moveTo(x0, bottomY)
            lineTo(xA, yPeak)               // Attack
            lineTo(xD, ySustain)            // Decay
            lineTo(xS, ySustain)            // Sustain
            lineTo(xR, bottomY)             // Release
        }

        // Fill under curve
        val fillPath = Path().apply {
            addPath(path)
            lineTo(xR, bottomY)
            lineTo(x0, bottomY)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(accentColor.copy(alpha = 0.35f), Color.Transparent),
                startY = yPeak,
                endY = bottomY
            )
        )

        // Stroke line
        drawPath(
            path = path,
            color = accentColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

fun getNoteName(pitch: Int): String {
    val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val octave = (pitch / 12) - 1
    val note = noteNames[(pitch % 12 + 12) % 12]
    return "$note$octave"
}
