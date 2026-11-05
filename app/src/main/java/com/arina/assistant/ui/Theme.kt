package com.arina.assistant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Entrion's palette: deep navy blue and white.
 * [dark] is set by [ArinaTheme] before anything is drawn, so every colour below is the right one for the current theme.
 */
object ArinaColors {
    /** Backed by Compose state, so changing the theme redraws every screen. */
    var dark: Boolean by androidx.compose.runtime.mutableStateOf(true)

    val Background: Color get() = if (dark) Color(0xFF0B0F26) else Color(0xFFF7F8FD)
    val Surface: Color get() = if (dark) Color(0xFF151A3C) else Color(0xFFFFFFFF)
    val Primary: Color get() = if (dark) Color(0xFF6C8CFF) else Color(0xFF3F63F0)
    /** The one accent: buttons, icons, highlights. */
    val Glow: Color get() = if (dark) Color(0xFF6C8CFF) else Color(0xFF3F63F0)
    /** Borders and dividers. */
    val Deep: Color get() = if (dark) Color(0xFF262D5C) else Color(0xFFE3E6F3)
    val Text: Color get() = if (dark) Color(0xFFF1F3FF) else Color(0xFF14182B)
    val Muted: Color get() = if (dark) Color(0xFF9097C4) else Color(0xFF6A6F8C)
    /** The second colour of gradients. */
    val Pink: Color get() = if (dark) Color(0xFF9B7BFF) else Color(0xFF8A5CFF)
}

/** 0 = follow the phone, 1 = light, 2 = dark. Saved by the engine. */
object ThemeChoice {
    var mode: Int by androidx.compose.runtime.mutableIntStateOf(2)
    fun load(prefs: android.content.SharedPreferences) { mode = prefs.getInt("theme_mode", 2).coerceIn(0, 2) }
    fun set(prefs: android.content.SharedPreferences, m: Int) { mode = m; prefs.edit().putInt("theme_mode", m).apply() }
}

private fun scheme(dark: Boolean) =
    if (dark) darkColorScheme(
        primary = ArinaColors.Primary, onPrimary = ArinaColors.Background,
        background = ArinaColors.Background, onBackground = ArinaColors.Text,
        surface = ArinaColors.Surface, onSurface = ArinaColors.Text, secondary = ArinaColors.Pink
    ) else lightColorScheme(
        primary = ArinaColors.Primary, onPrimary = Color.White,
        background = ArinaColors.Background, onBackground = ArinaColors.Text,
        surface = ArinaColors.Surface, onSurface = ArinaColors.Text, secondary = ArinaColors.Pink
    )

@Composable
fun ArinaTheme(content: @Composable () -> Unit) {
    val system = isSystemInDarkTheme()
    val dark = when (ThemeChoice.mode) { 1 -> false; 2 -> true; else -> system }
    ArinaColors.dark = dark
    // Everything is rebuilt when the phone switches between light and dark.
    key(dark) { MaterialTheme(colorScheme = scheme(dark), content = content) }
}
