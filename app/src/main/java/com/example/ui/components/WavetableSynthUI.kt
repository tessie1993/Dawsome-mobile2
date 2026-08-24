package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.SynthViewModel
import com.example.ui.theme.*

@Composable
fun WavetableSynthUI(viewModel: SynthViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseGridBg)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- Header ---
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).background(PulseGridActive, RoundedCornerShape(6.dp)))
                Spacer(modifier = Modifier.width(8.dp))
                Text("WAVETABLE", color = PulseGridTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = PulseGridTextSecondary, modifier = Modifier.size(20.dp))
                Text("Forest Sub", color = PulseGridTextPrimary, fontSize = 12.sp)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = PulseGridTextSecondary, modifier = Modifier.size(20.dp))
            }
            
            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = PulseGridActive, modifier = Modifier.size(20.dp))
        }

        // --- Main Synth Grid ---
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left Column (OSC 1, Filter 1)
            Column(modifier = Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WavetableOscPanel("OSC 1", "Basic Shapes", true, modifier = Modifier.weight(1f))
                WavetableFilterPanel("FILTER 1", modifier = Modifier.weight(1f))
            }
            
            // Center Column (OSC 2, Filter 2)
            Column(modifier = Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WavetableOscPanel("OSC 2", "Organic Fold", false, modifier = Modifier.weight(1f))
                WavetableFilterPanel("FILTER 2", modifier = Modifier.weight(1f))
            }
            
            // Right Column (Sub/Noise, Amp/Env)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WavetableSubNoisePanel("SUB", "Sine", modifier = Modifier.weight(1f))
                    WavetableSubNoisePanel("NOISE", "White", modifier = Modifier.weight(1f))
                }
                WavetableEnvPanel("AMP", modifier = Modifier.weight(1f))
                WavetableEnvPanel("FILTER ENV", modifier = Modifier.weight(1f))
            }
            
            // Far Right (Global/Voice)
            WavetableVoicePanel(modifier = Modifier.width(80.dp))
        }
        
        // --- Bottom Modulation Row ---
        Row(
            modifier = Modifier.height(140.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WavetableLfoPanel("LFO 1", modifier = Modifier.weight(1f))
            WavetableLfoPanel("LFO 2", modifier = Modifier.weight(1f))
            WavetableModMatrix(modifier = Modifier.weight(1.5f))
            WavetableMacros(modifier = Modifier.weight(1f))
        }
        
        // --- Keyboard / Pitch Wheels ---
        Row(
            modifier = Modifier.height(100.dp).fillMaxWidth().background(PulseGridPanel).border(1.dp, PulseGridBorder),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pitch / Mod Wheels
            Row(modifier = Modifier.width(120.dp).fillMaxHeight().padding(8.dp), horizontalArrangement = Arrangement.SpaceAround) {
                WheelControl("PITCH")
                WheelControl("MOD")
            }
            // Keyboard (simplified visual)
            WavetableKeyboard(modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
fun WavetableOscPanel(title: String, table: String, active: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PulseGridPanel)
            .border(1.dp, PulseGridBorder)
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(if(active) PulseGridActive else PulseGridTextSecondary))
                Spacer(modifier = Modifier.width(4.dp))
                Text(title, color = PulseGridTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text(table, color = PulseGridTextSecondary, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Knobs
            Column(modifier = Modifier.width(40.dp), verticalArrangement = Arrangement.SpaceAround) {
                WavetableKnob("Position", "0.0 %")
                WavetableKnob("Warp", "0.0 %")
            }
            // 3D Canvas
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    // Draw a pseudo-3D wavetable
                    for (i in 0..10) {
                        val path = Path()
                        val yOffset = (i * h / 12)
                        path.moveTo(0f, h / 2 + yOffset - 20f)
                        for (x in 0..100) {
                            val xPos = x * w / 100
                            val noise = (Math.random() * 20 - 10).toFloat()
                            val wave = (kotlin.math.sin(x * 0.2) * 30.0).toFloat()
                            path.lineTo(xPos, h / 2 + yOffset - wave + noise)
                        }
                        drawPath(path, PulseGridActive.copy(alpha = 0.3f + (0.05f * i)), style = Stroke(width = 1f))
                    }
                }
            }
            // Right Knobs
            Column(modifier = Modifier.width(40.dp), verticalArrangement = Arrangement.SpaceAround) {
                WavetableKnob("Detune", "18 %")
                WavetableKnob("Blend", "50 %")
            }
        }
    }
}

@Composable
fun WavetableFilterPanel(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            
            .background(PulseGridPanel)
            .border(1.dp, PulseGridBorder)
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).background(PulseGridActive))
            Spacer(modifier = Modifier.width(4.dp))
            Text(title, color = PulseGridTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Text("MS2 ▼", color = PulseGridTextSecondary, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.width(40.dp), verticalArrangement = Arrangement.SpaceAround) {
                WavetableKnob("Freq", "1.25 kHz")
                WavetableKnob("Res", "20 %")
            }
            // Filter Curve Canvas
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val path = Path()
                    path.moveTo(0f, h/2)
                    path.lineTo(w*0.4f, h/2)
                    path.cubicTo(w*0.5f, h/2, w*0.55f, h*0.8f, w, h)
                    drawPath(path, TrackBlue, style = Stroke(width = 3f))
                }
            }
            Column(modifier = Modifier.width(40.dp), verticalArrangement = Arrangement.SpaceAround) {
                WavetableKnob("Drive", "6.0 dB")
                WavetableKnob("Key", "60 %")
            }
        }
    }
}

@Composable
fun WavetableSubNoisePanel(title: String, type: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(PulseGridPanel)
            .border(1.dp, PulseGridBorder)
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).background(PulseGridActive))
            Spacer(modifier = Modifier.width(4.dp))
            Text(title, color = PulseGridTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(type, color = PulseGridTextSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.weight(1f))
        WavetableKnob("Level", "-12 dB")
    }
}

@Composable
fun WavetableEnvPanel(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(PulseGridPanel)
            .border(1.dp, PulseGridBorder)
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).background(PulseGridActive))
            Spacer(modifier = Modifier.width(4.dp))
            Text(title, color = PulseGridTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        // Envelope curve
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val path = Path()
                path.moveTo(0f, h)
                path.lineTo(w*0.2f, 0f) // Attack
                path.lineTo(w*0.4f, h*0.4f) // Decay to Sustain
                path.lineTo(w*0.8f, h*0.4f) // Sustain
                path.lineTo(w, h) // Release
                drawPath(path, Color(0xFF00E5FF), style = Stroke(width = 2f))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("A", color = PulseGridTextSecondary, fontSize = 9.sp)
            Text("D", color = PulseGridTextSecondary, fontSize = 9.sp)
            Text("S", color = PulseGridTextSecondary, fontSize = 9.sp)
            Text("R", color = PulseGridTextSecondary, fontSize = 9.sp)
        }
    }
}

@Composable
fun WavetableLfoPanel(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(PulseGridPanel)
            .border(1.dp, PulseGridBorder)
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).background(PulseGridActive))
            Spacer(modifier = Modifier.width(4.dp))
            Text(title, color = PulseGridTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            // Waveform icons
            Icon(Icons.Default.ShowChart, contentDescription = null, tint = PulseGridActive, modifier = Modifier.size(16.dp))
            Icon(Icons.Default.MultilineChart, contentDescription = null, tint = PulseGridTextSecondary, modifier = Modifier.size(16.dp))
            Icon(Icons.Default.StackedLineChart, contentDescription = null, tint = PulseGridTextSecondary, modifier = Modifier.size(16.dp))
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceAround) {
            WavetableKnob("Rate", "0.25 Hz")
            WavetableKnob("Phase", "0°")
            WavetableKnob("Amount", "50 %")
        }
    }
}

@Composable
fun WavetableModMatrix(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(PulseGridPanel)
            .border(1.dp, PulseGridBorder)
            .padding(8.dp)
    ) {
        Text("MOD MATRIX", color = PulseGridTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        val routes = listOf("LFO 1" to "Osc 1 Position" to "50 %", "Env 2" to "Filter 1 Freq" to "40 %", "Velocity" to "Amp" to "30 %")
        routes.forEach { (srcTgt, amt) ->
            val (src, tgt) = srcTgt
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(src, color = PulseGridTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = PulseGridTextSecondary, modifier = Modifier.size(14.dp))
                Text(tgt, color = PulseGridTextPrimary, fontSize = 11.sp, modifier = Modifier.weight(1.5f))
                Text(amt, color = PulseGridTextSecondary, fontSize = 11.sp, modifier = Modifier.width(40.dp))
            }
        }
    }
}

@Composable
fun WavetableMacros(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(PulseGridPanel)
            .border(1.dp, PulseGridBorder)
            .padding(8.dp)
    ) {
        Text("MACROS", color = PulseGridTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceAround) {
                WavetableKnob("Bright", "")
                WavetableKnob("Width", "")
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceAround) {
                WavetableKnob("Motion", "")
                WavetableKnob("Air", "")
            }
        }
    }
}

@Composable
fun WavetableVoicePanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(PulseGridPanel)
            .border(1.dp, PulseGridBorder)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("VOICE", color = PulseGridTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Poly 8", color = PulseGridTextSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.weight(1f))
        WavetableKnob("Glide", "0.0 ms")
        Spacer(modifier = Modifier.height(8.dp))
        WavetableKnob("Spread", "60 %")
    }
}

@Composable
fun WheelControl(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = PulseGridTextSecondary, fontSize = 10.sp)
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(60.dp)
                .background(PulseGridBg)
                .border(1.dp, PulseGridBorderDark)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .align(Alignment.Center)
                    .background(PulseGridTextSecondary)
            )
        }
    }
}

@Composable
fun WavetableKeyboard(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color.White)) {
        Row(modifier = Modifier.fillMaxSize()) {
            for (i in 0..20) { // white keys
                Box(modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color.Black))
            }
        }
        Row(modifier = Modifier.fillMaxSize().padding(bottom = 40.dp)) {
            for (i in 0..20) { // black keys overlay
                if (i % 7 != 2 && i % 7 != 6) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 4.dp).background(Color.Black))
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun WavetableKnob(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = PulseGridTextSecondary, fontSize = 9.sp)
        Box(modifier = Modifier.size(24.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = PulseGridTextSecondary.copy(alpha = 0.3f),
                    startAngle = 140f,
                    sweepAngle = 260f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawArc(
                    color = TrackBlue,
                    startAngle = 140f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        if (value.isNotEmpty()) {
            Text(value, color = PulseGridTextPrimary, fontSize = 9.sp)
        }
    }
}
