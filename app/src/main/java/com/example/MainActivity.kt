package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import com.example.ui.MainDawScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.earth.EarthColorTokens

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(color = EarthColorTokens.BgObsidianDeep) {
          MainDawScreen()
        }
      }
    }
  }
}
