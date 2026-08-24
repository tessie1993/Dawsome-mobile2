package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.SynthViewModel
import com.example.ui.components.AudioEffectsRackUI
import com.example.ui.components.WavetableSynthUI
import com.example.ui.theme.*

@Composable
fun SynthWorkspaceScreen(viewModel: SynthViewModel) {
    var activeView by remember { mutableStateOf("WAVETABLE") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Local Toolbar to switch views
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(PulseGridHeader),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(16.dp))
            listOf("WAVETABLE", "AUDIO EFFECT RACK").forEach { viewName ->
                val isSelected = activeView == viewName
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable { activeView = viewName }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = viewName,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) PulseGridTextPrimary else PulseGridTextSecondary
                    )
                }
            }
        }

        // Active View
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (activeView == "WAVETABLE") {
                WavetableSynthUI(viewModel = viewModel)
            } else {
                AudioEffectsRackUI(viewModel = viewModel)
            }
        }
    }
}
