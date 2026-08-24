package com.example.ui.theme

import androidx.compose.runtime.Composable
import com.example.ui.theme.earth.EarthTheme

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    EarthTheme(content = content)
}
