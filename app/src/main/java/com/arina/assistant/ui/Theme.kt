package com.arina.assistant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color

/**
 * Entrion's palette: deep navy blue and white.
 * [dark] is set by [ArinaTheme] before anything is drawn, so every colour below is the right one for the current theme.
 */
object ArinaColors {
    @Volatile var dark: Boolean = true

    val Background: Color get() = if (dark) Color(0xFF0B1220) else Color(0xFFFFFFFF)
    val Surface: Color get() = if (dark) Color(0xFF131E33) else Color(0xFFF3F7FF)
    val Primary: Color get() = if (dark) Color(0xFF6EA8FF) else Color(0xFF2563EB)
    /** The one accent: buttons, icons, highlights. */
    val Glow: Color get() = if (dark) Color(0xFF6EA8FF) else Color(0xFF2563EB)
    /** Borders and dividers. */
    val Deep: Color get() = if (dark) Color(0xFF263754) else Color(0xFFD9E4F8)
    val Text: Color get() = if (dark) Color(0xFFEAF1FF) else Color(0xFF0F1B33)
    val Muted: Color get() = if (dark) Color(0xFF93A4C3) else Color(0xFF5B6B87)
    /** A lighter blue for the second colour of gradients. */
    val Pink: Color get() = if (dark) Color(0xFF9CC4FF) else Color(0xFF60A5FA)
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
    val dark = true   // Entrion is always deep blue
    ArinaColors.dark = dark
    // Everything is rebuilt when the phone switches between light and dark.
    key(dark) { MaterialTheme(colorScheme = scheme(dark), content = content) }
}
