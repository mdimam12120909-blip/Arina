package com.arina.assistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Entrion's mark, the same everywhere (app icon, intro, sign-in, menu): a white ring that is open on the right,
 * like a doorway, and a dot that steps into the opening, on a blue tile.
 * [ring] and [dot] (0..1) draw it in step by step for the intro animation; at 1 it is complete.
 * The app icon (res/drawable/ic_launcher_foreground.xml) uses the same geometry.
 */
@Composable
fun LogoTile(modifier: Modifier = Modifier, ring: Float = 1f, dot: Float = 1f, tile: Boolean = true) {
    Canvas(modifier) {
        val s = size.minDimension
        if (tile) {
            drawRoundRect(
                Brush.linearGradient(listOf(Color(0xFF4F8DF7), Color(0xFF1D4ED8)), Offset.Zero, Offset(s, s)),
                size = Size(s, s), cornerRadius = CornerRadius(s * 0.26f)
            )
        }
        val c = Offset(s / 2f, s / 2f)
        val r = s * 0.27f           // ring radius
        val w = s * 0.095f          // stroke
        val gap = 35f               // half of the opening, in degrees
        val sweep = (360f - 2 * gap) * ring.coerceIn(0f, 1f)
        if (sweep > 0.5f) {
            drawArc(
                Color.White, startAngle = gap, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                style = Stroke(width = w, cap = StrokeCap.Round)
            )
        }
        val d = dot.coerceIn(0f, 1f)
        if (d > 0.01f) {
            // the dot slides in from the right edge into the opening
            val x = c.x + r + (1f - d) * s * 0.30f
            drawCircle(Color.White.copy(alpha = d), radius = s * 0.082f, center = Offset(x, c.y))
        }
    }
}
