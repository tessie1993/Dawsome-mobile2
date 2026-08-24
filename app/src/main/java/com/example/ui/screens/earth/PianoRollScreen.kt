package com.example.ui.screens.earth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.domain.MidiNote
import com.example.synth.domain.MusicalScale
import com.example.ui.state.PianoRollStateHolder
import com.example.ui.theme.earth.EarthColorTokens
import com.example.ui.theme.earth.EarthTheme
import com.example.ui.theme.earth.earthGlass

/**
 * Detail Note & Piano Roll Editor (Earth.Design).
 */
@Composable
fun PianoRollScreen(
    pianoRollStateHolder: PianoRollStateHolder,
    modifier: Modifier = Modifier
) {
    val state by pianoRollStateHolder.state.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EarthColorTokens.BgObsidianDeep)
    ) {
        // --- Top Scale & Transform Toolbar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .earthGlass(shape = RoundedCornerShape(0.dp), baseColor = EarthColorTokens.GlassEspresso)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Scale Display
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "SCALE: C ${state.scale.displayName}",
                    style = EarthTheme.typography.trackTitle,
                    fontSize = 11.sp,
                    color = EarthColorTokens.NatureEmerald
                )
            }

            // Quick Transformation Actions
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(EarthColorTokens.GlassSurface)
                        .border(0.5.dp, EarthColorTokens.GlassBorderSubtle, RoundedCornerShape(3.dp))
                        .clickable { pianoRollStateHolder.quantizeNotes(0.25f) }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("QUANTIZE 1/16", style = EarthTheme.typography.microBadge, color = EarthColorTokens.EarthAmber)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(EarthColorTokens.GlassSurface)
                        .border(0.5.dp, EarthColorTokens.GlassBorderSubtle, RoundedCornerShape(3.dp))
                        .clickable { pianoRollStateHolder.quantizeNotes(0.125f) }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("QUANTIZE 1/32", style = EarthTheme.typography.microBadge, color = EarthColorTokens.TextSecondary)
                }
            }
        }

        // --- Main Piano Roll Grid ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Left Piano Keys (C2 to B4: pitches 36 to 71)
            val pitches = (71 downTo 36).toList()

            LazyColumn(
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .border(0.5.dp, EarthColorTokens.GlassBorderSubtle)
            ) {
                items(pitches.size) { index ->
                    val pitch = pitches[index]
                    val isBlack = isBlackKey(pitch)
                    val noteName = getNoteName(pitch)
                    val isInKey = state.scale.intervals.contains((pitch - state.keyRoot + 120) % 12)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .background(
                                if (isBlack) Color(0xFF141210)
                                else if (isInKey) Color(0xFF1E281F) // Nature Green tint for in-key
                                else Color(0xFF282522)
                            )
                            .border(0.5.dp, EarthColorTokens.GlassBorderSubtle)
                            .clickable {
                                // Preview note
                                pianoRollStateHolder.addNote(
                                    MidiNote(pitch = pitch, startBeat = state.playheadBeat, lengthBeats = 1.0f)
                                )
                            }
                            .padding(end = 4.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = noteName,
                            style = EarthTheme.typography.microBadge,
                            fontSize = 7.sp,
                            color = if (isInKey) EarthColorTokens.NatureEmerald else EarthColorTokens.TextDisabled
                        )
                    }
                }
            }

            // Right Note Grid Canvas (Horizontal scrollable beats)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(scrollState)
            ) {
                val totalBeats = 16f
                val beatWidth = 32.dp

                Canvas(
                    modifier = Modifier
                        .width(beatWidth * totalBeats)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val beat = (offset.x / (beatWidth.toPx())).coerceAtLeast(0f)
                                val rowHeight = size.height / pitches.size
                                val pitchIndex = (offset.y / rowHeight).toInt().coerceIn(0, pitches.size - 1)
                                val pitch = pitches[pitchIndex]

                                pianoRollStateHolder.addNote(
                                    MidiNote(pitch = pitch, startBeat = Math.floor(beat.toDouble()).toFloat(), lengthBeats = 1.0f)
                                )
                            }
                        }
                ) {
                    val rowHeight = size.height / pitches.size

                    // Draw grid lines
                    for (i in 0..totalBeats.toInt()) {
                        val x = i * beatWidth.toPx()
                        val isBar = i % 4 == 0
                        drawLine(
                            color = if (isBar) EarthColorTokens.GlassBorderRimAmber.copy(alpha = 0.5f) else EarthColorTokens.GlassBorderSubtle.copy(alpha = 0.3f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = if (isBar) 1.dp.toPx() else 0.5.dp.toPx()
                        )
                    }

                    // Draw horizontal pitch rows
                    for (i in 0 until pitches.size) {
                        val y = i * rowHeight
                        val pitch = pitches[i]
                        val isBlack = isBlackKey(pitch)
                        if (isBlack) {
                            drawRect(
                                color = Color.Black.copy(alpha = 0.2f),
                                topLeft = Offset(0f, y),
                                size = Size(size.width, rowHeight)
                            )
                        }
                        drawLine(
                            color = EarthColorTokens.GlassBorderSubtle.copy(alpha = 0.2f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 0.5.dp.toPx()
                        )
                    }

                    // Draw Notes
                    state.notes.forEach { note ->
                        val pitchIdx = pitches.indexOf(note.pitch)
                        if (pitchIdx != -1) {
                            val noteX = note.startBeat * beatWidth.toPx()
                            val noteY = pitchIdx * rowHeight
                            val noteW = (note.lengthBeats * beatWidth.toPx()).coerceAtLeast(8.dp.toPx())

                            drawRoundRect(
                                color = EarthColorTokens.EarthAmber.copy(alpha = 0.85f),
                                topLeft = Offset(noteX, noteY + 1.dp.toPx()),
                                size = Size(noteW, rowHeight - 2.dp.toPx()),
                                cornerRadius = CornerRadius(2.dp.toPx())
                            )
                            drawRoundRect(
                                color = EarthColorTokens.AutumnMapleAmber,
                                topLeft = Offset(noteX, noteY + 1.dp.toPx()),
                                size = Size(noteW, rowHeight - 2.dp.toPx()),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                            )
                        }
                    }

                    // Draw Playhead Line
                    val playheadX = state.playheadBeat * beatWidth.toPx()
                    drawLine(
                        color = EarthColorTokens.EarthAmber,
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, size.height),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }
        }
    }
}

private fun isBlackKey(pitch: Int): Boolean {
    val note = pitch % 12
    return note == 1 || note == 3 || note == 6 || note == 8 || note == 10
}

private fun getNoteName(pitch: Int): String {
    val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val octave = (pitch / 12) - 1
    return "${names[pitch % 12]}$octave"
}
