package com.arina.assistant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color

/**
 * Entrion's palette: warm violet and rose. Light by day, deep aubergine (not black) by night, following the phone.
 * [dark] is set by [ArinaTheme] before anything is drawn, so every colour below is the right one for the current theme.
 */
object ArinaColors {
    @Volatile var dark: Boolean = true

    val Background: Color get() = if (dark) Color(0xFF15111F) else Color(0xFFFBF8FE)
    val Surface: Color get() = if (dark) Color(0xFF221A33) else Color(0xFFFFFFFF)
    val Primary: Color get() = if (dark) Color(0xFFB794F6) else Color(0xFF7C3AED)
    /** The accent used for highlights, icons, buttons. */
    val Glow: Color get() = if (dark) Color(0xFFC9A8FF) else Color(0xFF7C3AED)
    /** Borders and dividers. */
    val Deep: Color get() = if (dark) Color(0xFF3B2F59) else Color(0xFFE6DCF6)
    val Text: Color get() = if (dark) Color(0xFFF5EFFF) else Color(0xFF2A1F3D)
    val Muted: Color get() = if (dark) Color(0xFFB2A5CC) else Color(0xFF6B5F80)
    /** The warm second colour. */
    val Pink: Color get() = if (dark) Color(0xFFF58FC0) else Color(0xFFDB2777)
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
    val dark = isSystemInDarkTheme()
    ArinaColors.dark = dark
    // Everything is rebuilt when the phone switches between light and dark.
    key(dark) { MaterialTheme(colorScheme = scheme(dark), content = content) }
}
