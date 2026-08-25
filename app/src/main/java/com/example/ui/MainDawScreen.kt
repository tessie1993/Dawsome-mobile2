package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.domain.DawTab
import com.example.synth.domain.ProjectAction
import com.example.synth.domain.ProjectStore
import com.example.synth.engine.EngineReadback
import com.example.ui.components.earth.EarthTransportBar
import com.example.ui.screens.earth.*
import com.example.ui.state.*
import com.example.ui.theme.earth.EarthColorTokens
import com.example.ui.theme.earth.EarthGlassTokens
import com.example.ui.theme.earth.EarthTheme
import com.example.ui.theme.earth.earthGlass

/**
 * Main Integrated DAW Workspace (Earth.Design).
 */
@Composable
fun MainDawScreen(
    store: ProjectStore = remember { ProjectStore() },
    readback: EngineReadback? = null,
    modifier: Modifier = Modifier
) {
    val projectState by store.state.collectAsState()

    // Instantiate modular state holders
    val transportStateHolder = remember(store) { TransportStateHolder(store) }
    val arrangementStateHolder = remember(store) { ArrangementStateHolder(store) }
    val sessionStateHolder = remember(store) { SessionStateHolder(store) }
    val mixerStateHolder = remember(store, readback) { MixerStateHolder(store, readback) }
    val deviceRackStateHolder = remember(store) { DeviceRackStateHolder(store) }
    val pianoRollStateHolder = remember(store) { PianoRollStateHolder(store) }
    val browserStateHolder = remember(store) { SoundBrowserStateHolder(store) }
    val masteringStateHolder = remember(store) { MasteringStateHolder(store) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EarthColorTokens.BgObsidianDeep)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // --- 1. GLOBAL TRANSPORT HEADER ---
        EarthTransportBar(
            transportStateHolder = transportStateHolder,
            onUndo = { store.undo() },
            onRedo = { store.redo() }
        )

        // --- 2. ACTIVE VIEWPORT WORKSPACE ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AnimatedContent(
                targetState = projectState.activeTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(140)) togetherWith fadeOut(animationSpec = tween(140))
                },
                label = "EarthWorkspaceTransition"
            ) { tab ->
                when (tab) {
                    DawTab.SESSION -> SessionViewScreen(sessionStateHolder = sessionStateHolder)
                    DawTab.ARRANGER -> ArrangerScreen(
                        arrangementStateHolder = arrangementStateHolder,
                        mixerStateHolder = mixerStateHolder
                    )
                    DawTab.MIXER -> MixerScreen(mixerStateHolder = mixerStateHolder)
                    DawTab.PIANO_ROLL -> PianoRollScreen(pianoRollStateHolder = pianoRollStateHolder)
                    DawTab.SYNTH -> ModularSynthScreen(deviceRackStateHolder = deviceRackStateHolder)
                    DawTab.SAMPLER, DawTab.DRUMS -> SamplerDrumLabScreen(deviceRackStateHolder = deviceRackStateHolder)
                    DawTab.BROWSER -> SoundBrowserScreen(browserStateHolder = browserStateHolder)
                    DawTab.MASTERING -> MasteringSuiteScreen(masteringStateHolder = masteringStateHolder)
                }
            }
        }

        // --- 3. FLOATING CRYSTAL DOCK NAVIGATION BAR ---
        EarthNavigationDock(
            activeTab = projectState.activeTab,
            onTabSelected = { store.dispatch(ProjectAction.SelectTab(it)) }
        )
    }
}

/**
 * Floating crystal navigation dock, per the pack's portrait reference:
 * a rounded glass card with icon-over-label items; the active item is an
 * amber-tinted glass chip (tokens' Primary/Active role), inactive items
 * sit in secondary text. Horizontal scroll carries the full tab set until
 * the EDIT/DEVICES/MORE consolidation lands with the UX pass.
 */
@Composable
private fun EarthNavigationDock(
    activeTab: DawTab,
    onTabSelected: (DawTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .earthGlass(
                elevation = EarthGlassTokens.Level1Dock,
                shape = RoundedCornerShape(14.dp),
                baseColor = EarthColorTokens.GlassEspresso
            )
            .horizontalScroll(scrollState)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DawTab.entries.forEach { tab ->
            val isSelected = tab == activeTab
            val tint = if (isSelected) EarthColorTokens.EarthAmber
                       else EarthColorTokens.TextSecondary

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) EarthColorTokens.EarthAmber.copy(alpha = 0.16f)
                        else Color.Transparent
                    )
                    .border(
                        0.5.dp,
                        if (isSelected) EarthColorTokens.GlassBorderRimAmber else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = dockIconFor(tab),
                    contentDescription = tab.title,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tab.title.uppercase(),
                    style = EarthTheme.typography.microBadge,
                    fontSize = 7.sp,
                    color = tint
                )
            }
        }
    }
}

private fun dockIconFor(tab: DawTab): ImageVector = when (tab) {
    DawTab.SESSION -> Icons.Filled.GridView
    DawTab.ARRANGER -> Icons.Filled.Reorder
    DawTab.MIXER -> Icons.Filled.Tune
    DawTab.PIANO_ROLL -> Icons.Filled.Piano
    DawTab.SYNTH -> Icons.Filled.GraphicEq
    DawTab.SAMPLER -> Icons.Filled.LibraryMusic
    DawTab.DRUMS -> Icons.Filled.Apps
    DawTab.BROWSER -> Icons.Filled.Search
    DawTab.MASTERING -> Icons.Filled.Equalizer
}
