package com.letsmoe.otter.tv.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val otterColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF111111),
    onSurface = Color.White,
)

@Composable
fun OtterTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = otterColors, content = content)
}
