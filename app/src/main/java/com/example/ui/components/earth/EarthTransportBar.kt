package com.example.ui.components.earth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.state.TransportStateHolder
import com.example.ui.theme.earth.EarthColorTokens
import com.example.ui.theme.earth.EarthGlassTokens
import com.example.ui.theme.earth.EarthTheme
import com.example.ui.theme.earth.earthGlass

/**
 * Earth.Design Global Transport Bar - built to the pack's portrait
 * reference render + COMPONENTS.md §2: a FLOATING rounded crystal card
 * holding the five-button transport cluster (36dp bodies, 6dp corners;
 * play = radiant amber fill, record = glowing #DC2626 red circle, loop =
 * infinity glyph, metronome toggle) and the dark readout panel on the
 * right (stacked BPM label/value in amber mono, clock timecode in
 * bars.beats.sixteenths, project name). Undo/redo ride as micro buttons.
 */
@Composable
fun EarthTransportBar(
    transportStateHolder: TransportStateHolder,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by transportStateHolder.state.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .earthGlass(
                elevation = EarthGlassTokens.Level1Dock,
                shape = RoundedCornerShape(12.dp),
                baseColor = EarthColorTokens.GlassEspresso
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // --- Transport cluster (COMPONENTS.md 2.1) ---------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TransportButton(
                icon = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                active = state.isPlaying,
                activeFill = EarthColorTokens.EarthAmber,
                idleTint = EarthColorTokens.EarthAmber,
                activeTint = Color.Black,
                onClick = { transportStateHolder.togglePlay() }
            )
            TransportButton(
                icon = Icons.Filled.Stop,
                contentDescription = "Stop",
                active = false,
                activeFill = EarthColorTokens.GlassSurface,
                idleTint = EarthColorTokens.TextPrimary,
                activeTint = EarthColorTokens.TextPrimary,
                onClick = { transportStateHolder.stop() }
            )
            TransportButton(
                icon = Icons.Filled.FiberManualRecord,
                contentDescription = "Record",
                active = state.isRecording,
                // COMPONENTS.md 2.1: record is the glowing #DC2626 red circle
                // (crimson maple is the ARM pill color, not transport record).
                activeFill = EarthColorTokens.MeterAutumnRust,
                idleTint = EarthColorTokens.MeterAutumnRust,
                activeTint = Color.White,
                iconSize = 14.dp,
                onClick = { transportStateHolder.toggleRecord() }
            )
            TransportButton(
                icon = Icons.Filled.AllInclusive,
                contentDescription = "Loop",
                active = state.isLooping,
                activeFill = EarthColorTokens.EarthAmber.copy(alpha = 0.22f),
                idleTint = EarthColorTokens.TextSecondary,
                activeTint = EarthColorTokens.EarthAmber,
                activeBorder = EarthColorTokens.EarthAmber,
                onClick = { transportStateHolder.toggleLoop() }
            )
            TransportButton(
                icon = Icons.Filled.AvTimer,
                contentDescription = "Metronome",
                active = state.isMetronomeOn,
                activeFill = EarthColorTokens.EarthAmber.copy(alpha = 0.22f),
                idleTint = EarthColorTokens.TextSecondary,
                activeTint = EarthColorTokens.EarthAmber,
                activeBorder = EarthColorTokens.EarthAmber,
                onClick = { transportStateHolder.toggleMetronome() }
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // --- Readout panel (reference: dark inset card, right side) ----------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(EarthColorTokens.BgObsidianDeep.copy(alpha = 0.85f))
                .padding(horizontal = 10.dp)
        ) {
            // BPM: label stacked over the amber mono value; tap steps tempo
            // (scrub gesture joins the precision-input milestone).
            Column(
                modifier = Modifier.clickable {
                    val nextBpm = if (state.bpm >= 180f) 60f else state.bpm + 5f
                    transportStateHolder.setBpm(nextBpm)
                }
            ) {
                Text(
                    text = "BPM",
                    style = EarthTheme.typography.microLabel
                )
                Text(
                    text = String.format(java.util.Locale.ROOT, "%.2f", state.bpm),
                    style = EarthTheme.typography.bpmValue
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(EarthColorTokens.GlassBorderSubtle)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = EarthColorTokens.TextSecondary,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = state.barsBeatsFormatted,
                        style = EarthTheme.typography.displayTimeCompact,
                        maxLines = 1
                    )
                }
                Text(
                    text = "Project: ${state.projectName}",
                    style = EarthTheme.typography.microLabel,
                    maxLines = 1
                )
            }

            Text(
                text = "${state.timeSigNum}/${state.timeSigDen}",
                style = EarthTheme.typography.paramValue,
                color = EarthColorTokens.TextSecondary
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // --- Undo / Redo micro actions ---------------------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MicroButton(Icons.AutoMirrored.Filled.Undo, "Undo", onUndo)
            MicroButton(Icons.AutoMirrored.Filled.Redo, "Redo", onRedo)
        }
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    activeFill: Color,
    idleTint: Color,
    activeTint: Color,
    activeBorder: Color? = null,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(36.dp)
            .earthGlass(
                elevation = EarthGlassTokens.Level2Panel,
                shape = shape,
                baseColor = if (active) activeFill else EarthColorTokens.GlassSurface,
                borderColor = if (active) (activeBorder ?: activeFill)
                              else EarthColorTokens.GlassBorderSubtle
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) activeTint else idleTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun MicroButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .earthGlass(
                elevation = EarthGlassTokens.Level2Panel,
                shape = RoundedCornerShape(6.dp),
                baseColor = EarthColorTokens.GlassSurface
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = EarthColorTokens.TextSecondary,
            modifier = Modifier.size(14.dp)
        )
    }
}
