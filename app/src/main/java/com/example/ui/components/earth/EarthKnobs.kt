package com.example.ui.components.earth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.example.ui.theme.earth.EarthColorTokens
import com.example.ui.theme.earth.EarthTheme

/**
 * Macro Cutoff Rotary Knob (48dp x 48dp) with Amber LED arc and Modulation Ring.
 */
@Composable
fun MacroCutoffKnob(
    value: Float, // 0.0 .. 1.0
    label: String,
    modifier: Modifier = Modifier,
    displayValue: String = "${(value * 100).toInt()}%",
    modDepth: Float = 0.0f,
    accentColor: Color = EarthColorTokens.EarthAmber,
    onValueChange: (Float) -> Unit
) {
    var dragAccumulator by remember { mutableStateOf(value) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(56.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragAccumulator = value },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val delta = -dragAmount.y / 150f + dragAmount.x / 150f
                            dragAccumulator = (dragAccumulator + delta).coerceIn(0f, 1f)
                            onValueChange(dragAccumulator)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 - 4.dp.toPx()

                // Background track arc (270 degrees: 135 deg to 405 deg)
                val startAngle = 135f
                val sweepMax = 270f

                drawArc(
                    color = EarthColorTokens.GlassBorderSubtle,
                    startAngle = startAngle,
                    sweepAngle = sweepMax,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Modulation Range Arc (if active)
                if (modDepth > 0.01f) {
                    val modSweep = (value + modDepth).coerceIn(0f, 1f) * sweepMax
                    drawArc(
                        color = EarthColorTokens.AutumnTerracotta.copy(alpha = 0.45f),
                        startAngle = startAngle + (value * sweepMax),
                        sweepAngle = (modSweep - (value * sweepMax)).coerceAtLeast(0f),
                        useCenter = false,
                        topLeft = Offset(center.x - (radius + 2.dp.toPx()), center.y - (radius + 2.dp.toPx())),
                        size = Size((radius + 2.dp.toPx()) * 2, (radius + 2.dp.toPx()) * 2),
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Active LED Arc
                val activeSweep = value * sweepMax
                if (activeSweep > 0.5f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(accentColor, EarthColorTokens.AutumnMapleAmber)
                        ),
                        startAngle = startAngle,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Center Glass Dial Body
                val innerRadius = radius - 4.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF24201B), EarthColorTokens.GlassEspresso),
                        center = center,
                        radius = innerRadius
                    ),
                    radius = innerRadius,
                    center = center
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = innerRadius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Rotational indicator dot
                val currentAngleRad = Math.toRadians((startAngle + activeSweep).toDouble())
                val dotOffset = Offset(
                    (center.x + (innerRadius - 4.dp.toPx()) * kotlin.math.cos(currentAngleRad)).toFloat(),
                    (center.y + (innerRadius - 4.dp.toPx()) * kotlin.math.sin(currentAngleRad)).toFloat()
                )
                drawCircle(
                    color = EarthColorTokens.AutumnMapleAmber,
                    radius = 2.dp.toPx(),
                    center = dotOffset
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = EarthTheme.typography.paramLabel,
            maxLines = 1
        )
        Text(
            text = displayValue,
            style = EarthTheme.typography.paramValue,
            maxLines = 1
        )
    }
}

/**
 * Bi-Directional Pan Knob (36dp x 36dp) with Center Detent and Dual Emerald/Amber Halos.
 */
@Composable
fun BiDirectionalPanKnob(
    pan: Float, // -1.0 .. +1.0
    label: String = "Pan",
    modifier: Modifier = Modifier,
    onPanChange: (Float) -> Unit
) {
    var dragAccumulator by remember { mutableStateOf(pan) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(44.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragAccumulator = pan },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val delta = -dragAmount.y / 120f + dragAmount.x / 120f
                            dragAccumulator = (dragAccumulator + delta).coerceIn(-1f, 1f)
                            onPanChange(dragAccumulator)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 - 3.dp.toPx()

                // Background ring
                drawArc(
                    color = EarthColorTokens.GlassBorderSubtle,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Center Detent 12 o'clock = 270 deg
                val centerAngle = 270f
                val sweepAngle = (pan * 135f)

                if (kotlin.math.abs(pan) > 0.02f) {
                    val arcColor = if (pan < 0f) EarthColorTokens.NatureEmerald else EarthColorTokens.EarthAmber
                    drawArc(
                        color = arcColor,
                        startAngle = centerAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Center Dial Body
                val innerRadius = radius - 3.5.dp.toPx()
                drawCircle(
                    color = EarthColorTokens.GlassSurfaceRaised,
                    radius = innerRadius,
                    center = center
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = when {
                pan < -0.05f -> "L${(-pan * 100).toInt()}%"
                pan > 0.05f -> "R${(pan * 100).toInt()}%"
                else -> "C"
            },
            style = EarthTheme.typography.paramValue,
            fontSize = 8.sp
        )
    }
}

/**
 * Micro Encoder (24dp x 24dp) for compact channel strips.
 */
@Composable
fun MicroEncoder(
    value: Float, // 0.0 .. 1.0
    label: String,
    accentColor: Color = EarthColorTokens.EarthAmber,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit
) {
    var dragAccumulator by remember { mutableStateOf(value) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(28.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragAccumulator = value },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val delta = -dragAmount.y / 100f
                            dragAccumulator = (dragAccumulator + delta).coerceIn(0f, 1f)
                            onValueChange(dragAccumulator)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 - 2.dp.toPx()

                drawArc(
                    color = EarthColorTokens.GlassBorderSubtle,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )

                val activeSweep = value * 270f
                if (activeSweep > 0.5f) {
                    drawArc(
                        color = accentColor,
                        startAngle = 135f,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
        Text(
            text = label,
            style = EarthTheme.typography.microBadge,
            fontSize = 7.sp,
            color = EarthColorTokens.TextSecondary
        )
    }
}
