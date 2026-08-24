package com.example.ui.screens.earth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.domain.TrackModel
import com.example.ui.components.earth.BiDirectionalPanKnob
import com.example.ui.components.earth.MicroEncoder
import com.example.ui.components.earth.PrecisionCrystalFader
import com.example.ui.components.earth.SoloMuteArmToggles
import com.example.ui.components.earth.StereoLedLevelMeter
import com.example.ui.state.MixerStateHolder
import com.example.ui.theme.earth.EarthColorTokens
import com.example.ui.theme.earth.EarthTheme
import com.example.ui.theme.earth.earthGlass

/**
 * Mixer Console & Channel Strip View (Earth.Design).
 */
@Composable
fun MixerScreen(
    mixerStateHolder: MixerStateHolder,
    modifier: Modifier = Modifier
) {
    val state by mixerStateHolder.state.collectAsState()
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(EarthColorTokens.BgObsidianDeep)
            .horizontalScroll(scrollState)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Channel Strips
        state.tracks.forEach { track ->
            MixerChannelStrip(
                track = track,
                isSelected = track.id == state.selectedTrackId,
                onSelect = { mixerStateHolder.selectTrack(track.id) },
                onVolumeChange = { mixerStateHolder.setTrackVolume(track.id, it) },
                onPanChange = { mixerStateHolder.setTrackPan(track.id, it) },
                onToggleSolo = { mixerStateHolder.toggleSolo(track.id) },
                onToggleMute = { mixerStateHolder.toggleMute(track.id) },
                onToggleArm = { mixerStateHolder.toggleArm(track.id) },
                onSendAChange = { mixerStateHolder.setSend(track.id, 0, it) },
                onSendBChange = { mixerStateHolder.setSend(track.id, 1, it) }
            )
        }

        // Master Bus Strip (Right)
        MasterBusStrip(
            masterVolumeDb = state.masterVolumeDb,
            onVolumeChange = { mixerStateHolder.setMasterVolume(it) }
        )
    }
}

@Composable
private fun MixerChannelStrip(
    track: TrackModel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onToggleSolo: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleArm: () -> Unit,
    onSendAChange: (Float) -> Unit,
    onSendBChange: (Float) -> Unit
) {
    val trackColor = try {
        Color(android.graphics.Color.parseColor(track.colorHex))
    } catch (_: Exception) {
        EarthColorTokens.EarthAmber
    }

    Column(
        modifier = Modifier
            .width(84.dp)
            .fillMaxHeight()
            .earthGlass(
                shape = RoundedCornerShape(6.dp),
                baseColor = if (isSelected) EarthColorTokens.GlassSurfaceRaised else EarthColorTokens.GlassSurface,
                borderColor = if (isSelected) EarthColorTokens.EarthAmber else EarthColorTokens.GlassBorderSubtle
            )
            .clickable { onSelect() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Track Name & Color Tab
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(trackColor, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = track.name,
            style = EarthTheme.typography.trackTitle,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Dual Sends (A & B)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MicroEncoder(
                value = track.sendLevelA,
                label = "SND A",
                accentColor = EarthColorTokens.AutumnMapleAmber,
                onValueChange = onSendAChange
            )
            MicroEncoder(
                value = track.sendLevelB,
                label = "SND B",
                accentColor = EarthColorTokens.NatureMossSage,
                onValueChange = onSendBChange
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Pan Knob
        BiDirectionalPanKnob(
            pan = track.pan,
            onPanChange = onPanChange
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Solo / Mute / Arm Toggles
        SoloMuteArmToggles(
            isSoloed = track.isSoloed,
            isMuted = track.isMuted,
            isArmed = track.isArmed,
            onToggleSolo = onToggleSolo,
            onToggleMute = onToggleMute,
            onToggleArm = onToggleArm
        )

        Spacer(modifier = Modifier.weight(1f))

        // Fader + Stereo Meter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrecisionCrystalFader(
                volumeDb = track.volumeDb,
                height = 140.dp,
                onVolumeChange = onVolumeChange
            )
            Spacer(modifier = Modifier.width(4.dp))
            StereoLedLevelMeter(
                levelL = track.peakMeterL.coerceIn(0.1f, 0.95f),
                levelR = track.peakMeterR.coerceIn(0.1f, 0.95f),
                height = 140.dp
            )
        }
    }
}

@Composable
private fun MasterBusStrip(
    masterVolumeDb: Float,
    onVolumeChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .width(90.dp)
            .fillMaxHeight()
            .earthGlass(
                shape = RoundedCornerShape(6.dp),
                baseColor = EarthColorTokens.GlassEspresso,
                borderColor = EarthColorTokens.AutumnHarvestGold
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(EarthColorTokens.AutumnHarvestGold, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "MASTER",
            style = EarthTheme.typography.trackTitle,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = EarthColorTokens.AutumnHarvestGold
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrecisionCrystalFader(
                volumeDb = masterVolumeDb,
                height = 160.dp,
                onVolumeChange = onVolumeChange
            )
            Spacer(modifier = Modifier.width(4.dp))
            StereoLedLevelMeter(
                levelL = 0.82f,
                levelR = 0.80f,
                height = 160.dp
            )
        }
    }
}
