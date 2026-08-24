package com.example.ui.screens.earth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.domain.ArrangementClip
import com.example.synth.domain.TrackModel
import com.example.ui.components.earth.InteractiveWaveformCanvas
import com.example.ui.components.earth.SoloMuteArmToggles
import com.example.ui.state.ArrangementStateHolder
import com.example.ui.state.MixerStateHolder
import com.example.ui.theme.earth.EarthColorTokens
import com.example.ui.theme.earth.EarthTheme
import com.example.ui.theme.earth.earthGlass

/**
 * Full Arranger Timeline View (Earth.Design).
 */
@Composable
fun ArrangerScreen(
    arrangementStateHolder: ArrangementStateHolder,
    mixerStateHolder: MixerStateHolder,
    modifier: Modifier = Modifier
) {
    val state by arrangementStateHolder.state.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EarthColorTokens.BgObsidianDeep)
    ) {
        // --- Timeline Header Ruler & Section Markers ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .earthGlass(shape = RoundedCornerShape(0.dp), baseColor = EarthColorTokens.GlassEspresso)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track Header Spacer
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .fillMaxHeight()
                    .border(0.5.dp, EarthColorTokens.GlassBorderSubtle)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("TRACKS", style = EarthTheme.typography.sectionLabel, fontSize = 9.sp)
            }

            // Section markers (Intro, Verse, Drop, Outro)
            val markers = listOf("Intro (1-4)" to 4, "Verse 1 (5-8)" to 4, "Main Drop (9-16)" to 8, "Outro (17-20)" to 4)
            markers.forEach { (label, bars) ->
                Box(
                    modifier = Modifier
                        .width((bars * 36).dp)
                        .fillMaxHeight()
                        .border(0.5.dp, EarthColorTokens.GlassBorderSubtle)
                        .background(EarthColorTokens.GlassSurface.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = label,
                        style = EarthTheme.typography.paramLabel,
                        fontSize = 9.sp,
                        color = EarthColorTokens.AutumnMapleAmber
                    )
                }
            }
        }

        // --- Multi-Track Arrangement Lanes ---
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(state.tracks) { track ->
                ArrangerTrackLane(
                    track = track,
                    isSelected = track.id == state.selectedTrackId,
                    onSelect = { arrangementStateHolder.selectTrack(track.id) },
                    onToggleSolo = { mixerStateHolder.toggleSolo(track.id) },
                    onToggleMute = { mixerStateHolder.toggleMute(track.id) },
                    onToggleArm = { mixerStateHolder.toggleArm(track.id) }
                )
            }
        }

        // --- Bottom Device / Overview Preview ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .earthGlass(shape = RoundedCornerShape(0.dp), baseColor = EarthColorTokens.GlassEspresso)
                .padding(8.dp)
        ) {
            InteractiveWaveformCanvas(
                modifier = Modifier.fillMaxSize(),
                accentColor = EarthColorTokens.EarthAmber
            )
        }
    }
}

@Composable
private fun ArrangerTrackLane(
    track: TrackModel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleSolo: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleArm: () -> Unit
) {
    val trackColor = try {
        Color(android.graphics.Color.parseColor(track.colorHex))
    } catch (_: Exception) {
        EarthColorTokens.EarthAmber
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(0.5.dp, if (isSelected) EarthColorTokens.EarthAmber.copy(alpha = 0.5f) else EarthColorTokens.GlassBorderSubtle)
            .background(if (isSelected) EarthColorTokens.GlassSurfaceRaised else EarthColorTokens.GlassSurface)
            .clickable { onSelect() }
    ) {
        // Track Header (Left)
        Row(
            modifier = Modifier
                .width(130.dp)
                .fillMaxHeight()
                .border(0.5.dp, EarthColorTokens.GlassBorderSubtle)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color Accent Bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(trackColor, RoundedCornerShape(1.5.dp))
            )
            Spacer(modifier = Modifier.width(6.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.name,
                    style = EarthTheme.typography.trackTitle,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                SoloMuteArmToggles(
                    isSoloed = track.isSoloed,
                    isMuted = track.isMuted,
                    isArmed = track.isArmed,
                    onToggleSolo = onToggleSolo,
                    onToggleMute = onToggleMute,
                    onToggleArm = onToggleArm
                )
            }
        }

        // Timeline Clip Canvas (Right)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp, horizontal = 6.dp)
        ) {
            track.arrangementClips.forEach { clip ->
                ArrangerClipBlock(clip = clip, trackColor = trackColor)
            }
        }
    }
}

@Composable
private fun ArrangerClipBlock(
    clip: ArrangementClip,
    trackColor: Color
) {
    val blockWidth = (clip.lengthBeats * 18).dp
    val blockOffset = (clip.startBeat * 18).dp

    Box(
        modifier = Modifier
            .offset(x = blockOffset)
            .width(blockWidth)
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(trackColor.copy(alpha = 0.35f))
            .border(1.dp, trackColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = clip.name,
            style = EarthTheme.typography.trackTitle,
            fontSize = 9.sp,
            color = EarthColorTokens.TextPrimary,
            maxLines = 1
        )
    }
}
