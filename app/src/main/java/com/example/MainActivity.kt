package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import com.example.ui.MainDawScreen
import com.example.ui.theme.earth.EarthColorTokens
import com.example.ui.theme.earth.EarthTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    DawRuntime.ensureStarted()
    enableEdgeToEdge()
    setContent {
      EarthTheme {
        Surface(color = EarthColorTokens.BgObsidianDeep) {
          MainDawScreen(store = DawRuntime.store)
        }
      }
    }
  }
}
