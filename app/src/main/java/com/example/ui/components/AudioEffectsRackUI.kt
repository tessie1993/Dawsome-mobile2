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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.SynthViewModel
import com.example.ui.theme.*

@Composable
fun AudioEffectsRackUI(viewModel: SynthViewModel) {
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
                Text("LEAD — AUDIO EFFECT RACK", color = PulseGridTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = PulseGridTextSecondary, modifier = Modifier.size(20.dp))
                Text("Psychedelic Space", color = PulseGridTextPrimary, fontSize = 12.sp)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = PulseGridTextSecondary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.border(1.dp, PulseGridBorder).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("Save", color = PulseGridTextPrimary, fontSize = 10.sp)
                }
            }
            
            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = PulseGridActive, modifier = Modifier.size(20.dp))
        }

        // --- Main Rack Grid ---
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Devices Chain
            Column(modifier = Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                
                // Top Row: Mini Device Chain
                Row(modifier = Modifier.height(80.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MiniDeviceCard("EQ Eight", TrackYellow, true)
                    MiniDeviceCard("Compressor", TrackBlue, false)
                    MiniDeviceCard("Saturator", TrackOrange, false)
                    MiniDeviceCard("Echo", PulseGridActive, true, isSelected = true)
                    MiniDeviceCard("Hybrid Reverb", TrackPurple, true)
                    MiniDeviceCard("Limiter", PulseGridTextSecondary, false)
                }

                // Selected Device Detailed View: Echo
                EchoDetailedView(modifier = Modifier.weight(1f).fillMaxWidth())
                
                // Bottom Row: Secondary Mini Views or other effects
                Row(modifier = Modifier.height(140.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    EqEightMiniView(modifier = Modifier.weight(1.5f))
                    CompressorMiniView(modifier = Modifier.weight(1f))
                    SaturatorMiniView(modifier = Modifier.weight(1f))
                    HybridReverbMiniView(modifier = Modifier.weight(1.2f))
                    LimiterMiniView(modifier = Modifier.weight(0.8f))
                }
            }
            
            // I/O & Macros Sidebar
            Column(modifier = Modifier.width(200.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RackIOPanel(modifier = Modifier.weight(1f))
                RackMacrosPanel(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MiniDeviceCard(title: String, color: Color, isActive: Boolean, isSelected: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(100.dp)
            .background(if (isSelected) PulseGridPanel.copy(alpha = 0.8f) else PulseGridPanel)
            .border(1.dp, if (isSelected) color else PulseGridBorder, RoundedCornerShape(4.dp))
            .padding(4.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).background(if(isActive) color else PulseGridTextSecondary))
                Spacer(modifier = Modifier.width(4.dp))
                Text(title, color = PulseGridTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(modifier = Modifier.weight(1f))
            // Pseudo graph
            Canvas(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                val path = Path()
                path.moveTo(0f, size.height/2)
                for(i in 1..10) {
                    path.lineTo(size.width * (i/10f), size.height/2 + (Math.random()*10 - 5).toFloat())
                }
                drawPath(path, color.copy(alpha = 0.5f), style = Stroke(width = 1f))
            }
        }
    }
}

@Composable
fun EchoDetailedView(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(PulseGridPanel)
            .border(1.dp, PulseGridBorder)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Left section (Delay lines visualization)
        Column(modifier = Modifier.weight(2f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(PulseGridActive))
                Spacer(modifier = Modifier.width(4.dp))
                Text("ECHO", color = PulseGridTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Left", color = PulseGridTextSecondary, fontSize = 10.sp)
                    Text("1/4", color = PulseGridTextPrimary, fontSize = 12.sp)
                    Box(modifier = Modifier.border(1.dp, PulseGridActive).padding(4.dp)) { Text("Sync", color = PulseGridActive, fontSize = 8.sp) }
                }
                Box(modifier = Modifier.weight(1f).height(60.dp).padding(horizontal = 16.dp)) {
                    // Delay visualization
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val h = size.height
                        val w = size.width
                        drawLine(PulseGridTextSecondary, Offset(0f, h/2), Offset(w, h/2))
                        for (i in 0..8) {
                            val x = w * (i/8f)
                            val yRadius = h/2 * (1f - (i*0.1f))
                            drawLine(PulseGridActive, Offset(x, h/2 - yRadius), Offset(x, h/2 + yRadius), strokeWidth = 2.dp.toPx())
                            drawCircle(PulseGridActive, radius = 3.dp.toPx(), center = Offset(x, h/2))
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Right", color = PulseGridTextSecondary, fontSize = 10.sp)
                    Text("3/16", color = PulseGridTextPrimary, fontSize = 12.sp)
                    Box(modifier = Modifier.border(1.dp, PulseGridActive).padding(4.dp)) { Text("Sync", color = PulseGridActive, fontSize = 8.sp) }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                WavetableKnob("Feedback", "42 %")
                WavetableKnob("Crossfeed", "35 %")
                WavetableKnob("Stereo", "100 %")
            }
        }
        
        Divider(modifier = Modifier.width(1.dp).fillMaxHeight(), color = PulseGridBorder)
        
        // Mod & Filter section
        Column(modifier = Modifier.weight(1f)) {
            Text("Modulation", color = PulseGridTextSecondary, fontSize = 10.sp)
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                WavetableKnob("Rate", "0.32 Hz")
                WavetableKnob("Depth", "22 %")
                WavetableKnob("Phase", "180 °")
            }
            Spacer(modifier = Modifier.weight(1f))
            Text("Feedback Filter", color = PulseGridTextSecondary, fontSize = 10.sp)
            Box(modifier = Modifier.fillMaxWidth().height(40.dp).background(PulseGridBg)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val path = Path()
                    path.moveTo(0f, size.height)
                    path.lineTo(size.width*0.2f, size.height*0.2f)
                    path.lineTo(size.width*0.8f, size.height*0.2f)
                    path.lineTo(size.width, size.height)
                    drawPath(path, Color(0xFF00E5FF), style = Stroke(width = 2f))
                }
            }
        }
    }
}

@Composable
fun EqEightMiniView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(PulseGridPanel)
            .border(1.dp, PulseGridBorder)
            .padding(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).background(TrackYellow))
            Spacer(modifier = Modifier.width(4.dp))
            Text("EQ EIGHT", color = PulseGridTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp).background(PulseGridBg)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path()
                path.moveTo(0f, size.height/2)
                path.cubicTo(size.width*0.3f, size.height*0.8f, size.width*0.6f, size.height*0.2f, size.width, size.height/2)
                drawPath(path, TrackYellow, style = Stroke(width = 2f))
            }
        }
    }
}

@Composable
fun CompressorMiniView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(PulseGridPanel)
            .border(1.dp, PulseGridBorder)
            .padding(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).background(TrackBlue))
            Spacer(modifier = Modifier.width(4.dp))
            Text("COMPRESSOR", color = PulseGridTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp).background(PulseGridBg)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path()
                path.moveTo(0f, size.height)
                path.lineTo(size.width*0.5f, size.height*0.5f)
                path.lineTo(size.width, size.height*0.3f) // Knee and reduced ratio
                drawPath(path, TrackBlue, style = Stroke(width = 2f))
            }
        }
    }
}

@Composable
fun SaturatorMiniView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(PulseGridPanel)
            .border(1.dp, PulseGridBorder)
            .padding(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).background(TrackOrange))
            Spacer(modifier = Modifier.width(4.dp))
            Text("SATURATOR", color = PulseGridTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp).background(PulseGridBg)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path()
                path.moveTo(0f, size.height)
                path.cubicTo(size.width*0.3f, size.height, size.width*0.7f, 0f, size.width, 0f)
                drawPath(path, TrackOrange, style = Stroke(width = 2f))
            }
        }
    }
}

@Composable
fun HybridReverbMiniView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(PulseGridPanel)
            .border(1.dp, PulseGridBorder)
            .padding(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).background(TrackPurple))
            Spacer(modifier = Modifier.width(4.dp))
            Text("HYBRID REVERB", color = PulseGridTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp).background(PulseGridBg)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (i in 0..100 step 2) {
                    val x = size.width * (i/100f)
                    val env = 1f - (i/100f)
                    val yLine = (Math.random() * size.height * env).toFloat()
                    drawLine(TrackPurple, Offset(x, size.height/2 - yLine/2), Offset(x, size.height/2 + yLine/2))
                }
            }
        }
    }
}

@Composable
fun LimiterMiniView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(PulseGridPanel)
            .border(1.dp, PulseGridBorder)
            .padding(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).background(PulseGridTextSecondary))
            Spacer(modifier = Modifier.width(4.dp))
            Text("LIMITER", color = PulseGridTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp).background(PulseGridBg)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path()
                path.moveTo(0f, size.height)
                path.lineTo(size.width*0.4f, size.height*0.2f)
                path.lineTo(size.width, size.height*0.2f) // Brickwall
                drawPath(path, PulseGridTextSecondary, style = Stroke(width = 2f))
            }
        }
    }
}

@Composable
fun RackIOPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PulseGridPanel)
            .border(1.dp, PulseGridBorder)
            .padding(8.dp)
    ) {
        Text("I/O", color = PulseGridTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceAround) {
            // Volume meters
            VolumeMeter(label = "INPUT")
            VolumeMeter(label = "OUTPUT")
        }
    }
}

@Composable
fun VolumeMeter(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
        Text(label, color = PulseGridTextSecondary, fontSize = 8.sp)
        Box(modifier = Modifier.width(20.dp).weight(1f).background(Color.Black).padding(2.dp)) {
            // Green/Yellow/Red gradient simulation
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f).background(TrackGreen).align(Alignment.BottomCenter))
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.1f).background(TrackYellow).align(Alignment.BottomCenter).offset(y = (-60).dp))
        }
        Text("0.0 dB", color = PulseGridTextPrimary, fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun RackMacrosPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PulseGridPanel)
            .border(1.dp, PulseGridBorder)
            .padding(8.dp)
    ) {
        Text("RACK MACROS", color = PulseGridTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            WavetableKnob("Bright", "")
            WavetableKnob("Punch", "")
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            WavetableKnob("Warmth", "")
            WavetableKnob("Motion", "")
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}
