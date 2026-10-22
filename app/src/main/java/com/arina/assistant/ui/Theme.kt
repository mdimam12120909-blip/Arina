package com.arina.assistant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object ArinaColors {
    val Background = Color(0xFF020817)
    val Surface = Color(0xFF071A33)
    val Primary = Color(0xFF28C8FF)
    val Glow = Color(0xFF6BE3FF)
    val Deep = Color(0xFF0B5FFF)
    val Text = Color(0xFFD8F4FF)
    val Muted = Color(0xFF7FA9C4)
}

private val BlueScheme = darkColorScheme(
    primary = ArinaColors.Primary,
    onPrimary = ArinaColors.Background,
    background = ArinaColors.Background,
    onBackground = ArinaColors.Text,
    surface = ArinaColors.Surface,
    onSurface = ArinaColors.Text,
    secondary = ArinaColors.Deep
)

@Composable
fun ArinaTheme(content: @Composable () -> Unit) {
    // Always the blue neon theme, regardless of system setting.
    @Suppress("UNUSED_VARIABLE") val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = BlueScheme, content = content)
}
