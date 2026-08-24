package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.DawTab
import com.example.synth.SynthViewModel
import com.example.ui.components.AbletonTransportBar
import com.example.ui.components.SaveProjectDialog
import com.example.ui.screens.ArrangerScreen
import com.example.ui.screens.PianoRollScreen
import com.example.ui.screens.SynthWorkspaceScreen
import com.example.ui.theme.PulseGridActive
import com.example.ui.theme.PulseGridBg
import com.example.ui.theme.PulseGridBorder
import com.example.ui.theme.PulseGridHeader
import com.example.ui.theme.PulseGridPanel
import com.example.ui.theme.PulseGridTextPrimary

@Composable
fun MainDawScreen(
    viewModel: SynthViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val isSaveDialogOpen by viewModel.isSaveDialogOpen.collectAsState()
    val statusToast by viewModel.statusToast.collectAsState()

    Box(modifier = modifier.fillMaxSize().background(PulseGridBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- TOP BAR ---
            Box(modifier = Modifier.fillMaxWidth().background(PulseGridHeader).border(1.dp, PulseGridBorder)) {
                AbletonTransportBar(viewModel = viewModel)
            }

            // --- MAIN WORKSPACE ---
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(120)) togetherWith fadeOut(animationSpec = tween(120))
                    },
                    label = "PulseGridWorkspaceTransition"
                ) { tab ->
                    when (tab) {
                        DawTab.PIANO_ROLL -> PianoRollScreen(viewModel = viewModel)
                        DawTab.SYNTH -> SynthWorkspaceScreen(viewModel = viewModel)
                        else -> ArrangerScreen(viewModel = viewModel) // ArrangerScreen acts as the primary "Main" view
                    }
                }
            }
        }

        // Save Project Dialog
        if (isSaveDialogOpen) {
            SaveProjectDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.closeSaveDialog() }
            )
        }

        // Status Toast Notification
        if (statusToast != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 64.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PulseGridPanel,
                    border = androidx.compose.foundation.BorderStroke(1.dp, PulseGridBorder),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = PulseGridActive,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusToast!!,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PulseGridTextPrimary
                        )
                    }
                }
            }
        }
    }
}
