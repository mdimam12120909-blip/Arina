package com.arina.assistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** What the orb is doing right now. Each mood has its own colour and motion. */
enum class Mood(val label: String) {
    Standby("Standby"),
    Listening("Listening"),
    Thinking("Thinking"),
    Speaking("Speaking"),
    Researching("Researching"),
    Alert("Attention")
}

fun moodOf(status: Status, researching: Boolean, problem: Boolean): Mood = when {
    problem -> Mood.Alert
    status == Status.Speaking -> Mood.Speaking
    status == Status.Thinking -> Mood.Thinking
    researching -> Mood.Researching
    status == Status.Listening -> Mood.Listening
    else -> Mood.Standby
}

private fun moodColor(persona: Persona, mood: Mood): Color {
    val female = persona == Persona.Arina
    return when (mood) {
        Mood.Standby -> if (female) Color(0xFF3B82F6) else Color(0xFF2563EB)
        Mood.Listening -> if (female) Color(0xFF38BDF8) else Color(0xFF3B82F6)
        Mood.Thinking -> Color(0xFF6366F1)
        Mood.Speaking -> if (female) Color(0xFF0EA5E9) else Color(0xFF2F6BFF)
        Mood.Researching -> Color(0xFF06B6D4)
        Mood.Alert -> Color(0xFFEF4444)
    }
}

/** The colour the orb (and the screen-edge light) shows for a mood. */
fun moodTint(persona: Persona, mood: Mood): Color = moodColor(persona, mood)

/** The persona's signature colour (green for Arina, blue for Aurion), used across the app. */
fun accentOf(persona: Persona): Color = if (persona == Persona.Arina) Color(0xFF0EA5E9) else Color(0xFF2563EB)

/**
 * The agent's orb: a glass-like sphere in blue with soft light drifting inside it and a glossy edge.
 * The tint and motion follow [mood]; [level] is the microphone loudness while listening. Rings spread out
 * while listening and speaking.
 */
@Composable
fun OrbView(persona: Persona, mood: Mood, level: Float, modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1_000_000_000f }
    }
    val tint by animateColorAsState(moodColor(persona, mood), tween(600), label = "tint")
    val energy by animateFloatAsState(
        when (mood) {
            Mood.Standby -> 0.25f
            Mood.Listening -> 0.6f
            Mood.Thinking -> 0.8f
            Mood.Speaking -> 0.9f
            Mood.Researching -> 0.8f
            Mood.Alert -> 0.85f
        },
        tween(500), label = "energy"
    )
    val density = LocalDensity.current.density

    Canvas(modifier) {
        val t = time
        val c = Offset(size.width / 2f, size.height / 2f)
        val base = min(size.width, size.height) / 2f
        val talk = if (mood == Mood.Speaking) (0.5f + 0.5f * sin(t * 9f) * sin(t * 5.3f + 1f)).coerceIn(0f, 1f) else 0f
        val r = base * 0.62f * (1f + 0.012f * sin(t * 1.3f) + level * 0.10f + talk * 0.06f)
        val light = lerp(tint, Color.White, 0.6f)
        val deep = lerp(tint, Color(0xFF0A1A5C), 0.45f)

        // a quiet halo
        drawCircle(
            Brush.radialGradient(listOf(tint.copy(alpha = 0.16f + 0.14f * energy), Color.Transparent), center = c, radius = r * 1.6f),
            radius = r * 1.6f, center = c
        )
        // rings while listening or speaking
        if (mood == Mood.Listening || mood == Mood.Speaking) for (i in 0..1) {
            val p = (t * 0.5f + i * 0.5f) % 1f
            drawCircle(tint.copy(alpha = (1f - p) * 0.30f), radius = r * (1.02f + 0.45f * p), center = c, style = Stroke(1.6f * density))
        }
        // the sphere
        drawCircle(
            Brush.radialGradient(listOf(light, tint, deep), center = Offset(c.x - r * 0.3f, c.y - r * 0.38f), radius = r * 1.45f),
            radius = r, center = c
        )
        // light drifting inside
        val sphere = Path().apply { addOval(Rect(c, r)) }
        clipPath(sphere) {
            for (i in 0..2) {
                val a = t * (0.30f + 0.17f * i) * (if (i % 2 == 0) 1f else -1f) + i * 2.1f
                val p = Offset(c.x + cos(a) * r * 0.42f, c.y + sin(a * 1.17f) * r * 0.42f)
                val br = r * (0.55f + 0.12f * i)
                drawCircle(
                    Brush.radialGradient(listOf(lerp(tint, Color.White, 0.55f).copy(alpha = 0.50f * (0.45f + energy)), Color.Transparent), center = p, radius = br),
                    radius = br, center = p
                )
            }
            drawCircle(
                Brush.radialGradient(listOf(Color.Transparent, deep.copy(alpha = 0.38f)), center = c, radius = r),
                radius = r, center = c
            )
        }
        // glossy edge and rim
        drawArc(
            Color.White.copy(alpha = 0.55f), startAngle = 200f, sweepAngle = 62f, useCenter = false,
            topLeft = Offset(c.x - r * 0.82f, c.y - r * 0.82f), size = Size(r * 1.64f, r * 1.64f),
            style = Stroke(width = 2.2f * density, cap = StrokeCap.Round)
        )
        drawCircle(Color.White.copy(alpha = 0.25f), radius = r, center = c, style = Stroke(1f * density))
    }
}
