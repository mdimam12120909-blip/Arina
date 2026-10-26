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
import androidx.compose.ui.geometry.Size
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
    Standby("STANDBY"),
    Listening("LISTENING"),
    Thinking("THINKING"),
    Speaking("SPEAKING"),
    Researching("RESEARCHING"),
    Alert("ATTENTION")
}

fun moodOf(status: Status, researching: Boolean, problem: Boolean): Mood = when {
    problem -> Mood.Alert
    status == Status.Speaking -> Mood.Speaking
    status == Status.Thinking -> Mood.Thinking
    researching -> Mood.Researching
    status == Status.Listening -> Mood.Listening
    else -> Mood.Standby
}

/**
 * Dot-matrix face patterns, 25 x 25. The left half is written out and mirrored.
 * H hair, f skin, B brow, E eye, N nose, L lips, . empty.
 */
private object FacePatterns {
    private val female = listOf(
        ".........HHHH", ".......HHHHHH", "......HHHHHHH", ".....HHHHHHHH", "....HHHHfffff",
        "...HHHHffffff", "...HHHfffffff", "..HHHHfffffff", "..HHHfBBBffff", "..HHHfEEEffff",
        ".HHHHffffffff", ".HHHHffffffff", ".HHHffffffffN", ".HHHfffffffNN", ".HHHfffffffff",
        ".HHHfffffffff", ".HHHffffLLLLL", ".HHHfffffLLLL", ".HHHH.fffffff", ".HHHH..ffffff",
        ".HHHH...fffff", ".HHHH....ffff", ".HHHH...fffff", ".HHHHH.ffffff", ".HHHHHfffffff"
    )
    private val male = listOf(
        "..........HHH", "........HHHHH", ".......HHHHHH", "......HHHHHHH", "......HHHHHHH",
        ".....HHHfffff", ".....HHffffff", ".....Hfffffff", ".....fBBBBfff", ".....fEEEffff",
        ".....ffffffff", ".....ffffffff", ".....fffffffN", ".....ffffffNN", ".....ffffffff",
        ".....ffffffff", ".....fffLLLLL", ".....ffffffff", ".....ffffffff", "......fffffff",
        ".......ffffff", "......fffffff", "....fffffffff", "..fffffffffff", ".ffffffffffff"
    )

    private fun mirror(half: List<String>) = half.map { it + it.reversed().drop(1) }

    val forArina: List<String> = mirror(female)
    val forAurion: List<String> = mirror(male)
}

private fun moodColor(persona: Persona, mood: Mood): Color = when (mood) {
    Mood.Standby -> if (persona == Persona.Arina) Color(0xFF3DB8FF) else Color(0xFF5B7CFF)
    Mood.Listening -> if (persona == Persona.Arina) Color(0xFF6BE3FF) else Color(0xFF8FA6FF)
    Mood.Thinking -> Color(0xFFA27BFF)
    Mood.Speaking -> if (persona == Persona.Arina) Color(0xFF7FF0FF) else Color(0xFF9CC2FF)
    Mood.Researching -> Color(0xFFFFB84D)
    Mood.Alert -> Color(0xFFFF5C6C)
}

/**
 * Arina's and Aurion's face: a glowing orb with a dot-matrix portrait inside (a woman for Arina, a man for
 * Aurion). The colour and motion follow [mood]; [level] is the microphone loudness while listening.
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
            Mood.Thinking -> 0.75f
            Mood.Speaking -> 0.9f
            Mood.Researching -> 0.8f
            Mood.Alert -> 0.85f
        },
        tween(500), label = "energy"
    )
    val rows = remember(persona) { if (persona == Persona.Arina) FacePatterns.forArina else FacePatterns.forAurion }
    val density = LocalDensity.current.density

    Canvas(modifier) {
        val dp = density
        val t = time
        val breathe = 1f + 0.015f * sin(t * 1.2f)
        val shake = if (mood == Mood.Alert) sin(t * 38f) * 2f * dp else 0f
        val c = Offset(size.width / 2f + shake, size.height / 2f)
        val r = min(size.width, size.height) / 2f * 0.66f * breathe

        // Speaking amplitude is simulated from two slow waves (the voice is played by the system, so we have no level).
        val talk = if (mood == Mood.Speaking) (0.5f + 0.5f * sin(t * 9f) * sin(t * 5.3f + 1f)).coerceIn(0f, 1f) else 0f

        // Outer glow
        val glow = 0.30f + 0.35f * energy + level * 0.35f + talk * 0.25f
        drawCircle(
            Brush.radialGradient(listOf(tint.copy(alpha = glow), Color.Transparent), center = c, radius = r * 1.9f),
            radius = r * 1.9f, center = c
        )

        // Orbit rings
        for (i in 0..2) {
            val rr = r * (1.13f + 0.09f * i)
            val dir = if (i % 2 == 0) 1f else -1f
            rotate(degrees = dir * t * (14f + 46f * energy) * (1f + i * 0.35f), pivot = c) {
                drawArc(
                    color = tint.copy(alpha = 0.55f - 0.12f * i),
                    startAngle = 0f, sweepAngle = 90f + i * 55f, useCenter = false,
                    topLeft = Offset(c.x - rr, c.y - rr), size = Size(rr * 2, rr * 2),
                    style = Stroke(width = (1.4f - 0.2f * i) * dp)
                )
            }
        }

        // Sphere body
        drawCircle(
            Brush.radialGradient(
                listOf(tint.copy(alpha = 0.26f), Color(0xFF041634), Color(0xFF020817)),
                center = Offset(c.x - r * 0.25f, c.y - r * 0.3f), radius = r * 1.4f
            ),
            radius = r, center = c
        )
        drawCircle(tint.copy(alpha = 0.7f), radius = r, center = c, style = Stroke(1.5f * dp))

        // Dot-matrix face
        val n = rows.size
        val half = (n - 1) / 2f
        val cell = (2f * r * 0.92f) / n
        val sway = sin(t * 0.5f) * 0.05f
        val blink = if ((t % 4.2f) < 0.14f) 0f else 1f
        val scanPos = ((t * 0.32f) % 1f) * 2f - 1f
        val scanPower = when (mood) {
            Mood.Listening, Mood.Researching -> 1f
            Mood.Thinking -> 0.7f
            else -> 0.25f
        }
        for (row in 0 until n) {
            val line = rows[row]
            for (col in 0 until n) {
                val u = (col - half) / (half + 0.5f)
                val v = (row - half) / (half + 0.5f)
                val d2 = u * u + v * v
                if (d2 > 0.97f) continue
                val z = sqrt((1f - d2).coerceAtLeast(0f))
                val ch = line[col]
                var alpha: Float
                var rad: Float
                when (ch) {
                    '.' -> { alpha = 0.10f; rad = 0.12f }
                    'f' -> { alpha = 0.30f; rad = 0.20f }
                    'H' -> { alpha = 0.55f; rad = 0.26f }
                    'E' -> { alpha = 0.95f * blink; rad = 0.34f }
                    else -> { alpha = 0.90f; rad = 0.30f } // brows, nose, lips
                }
                var x = c.x + (u + sway * z) * r * 0.92f
                var y = c.y + v * r * 0.92f
                if (ch == 'L') {
                    // Lips move while speaking
                    y += sin(t * 14f + col * 0.35f) * talk * cell * 0.55f * (if (row > half + 3.5f) 1.4f else 0.6f)
                    alpha *= 0.75f + 0.25f * talk
                }
                alpha *= 0.8f + 0.2f * sin(t * 2.2f + (row * 31 + col * 17) * 0.37f)
                alpha *= 0.35f + 0.65f * z
                val d = v - scanPos
                alpha += exp(-d * d * 55f) * 0.6f * scanPower
                alpha += level * 0.2f * (if (ch == '.' || ch == 'f') 0f else 1f)
                val col0 = if (ch == 'E') Color.White else tint
                drawCircle(col0.copy(alpha = alpha.coerceIn(0f, 1f)), radius = cell * rad * (0.6f + 0.4f * z), center = Offset(x, y))
            }
        }

        // Mood effects
        when (mood) {
            Mood.Listening -> for (k in 0..1) {
                val p = (t * 0.8f + k * 0.5f) % 1f
                drawCircle(
                    tint.copy(alpha = (1f - p) * (0.25f + 0.5f * level)),
                    radius = r * (1.04f + 0.5f * p), center = c, style = Stroke(1.6f * dp)
                )
            }
            Mood.Thinking -> rotate(degrees = 25f, pivot = c) {
                for (i in 0 until 10) {
                    val a = t * 2.4f + i * 2f * PI.toFloat() / 10f
                    drawCircle(
                        tint.copy(alpha = 0.35f + 0.55f * (0.5f + 0.5f * sin(a * 1.3f))),
                        radius = 3.2f * dp,
                        center = Offset(c.x + cos(a) * r * 1.3f, c.y + sin(a) * r * 0.42f)
                    )
                }
            }
            Mood.Speaking -> {
                val path = Path()
                val steps = 90
                for (i in 0..steps) {
                    val a = i * 2f * PI.toFloat() / steps
                    val wob = sin(a * 7f + t * 11f) * 0.028f * talk + sin(a * 3f - t * 6f) * 0.012f
                    val rr = r * (1.09f + wob)
                    val px = c.x + cos(a) * rr
                    val py = c.y + sin(a) * rr
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                drawPath(path, tint.copy(alpha = 0.9f), style = Stroke(2.2f * dp))
            }
            Mood.Researching -> rotate(degrees = t * 140f, pivot = c) {
                drawArc(
                    color = tint, startAngle = 0f, sweepAngle = 70f, useCenter = false,
                    topLeft = Offset(c.x - r * 1.07f, c.y - r * 1.07f), size = Size(r * 2.14f, r * 2.14f),
                    style = Stroke(3.2f * dp)
                )
            }
            Mood.Alert -> drawCircle(
                tint.copy(alpha = 0.4f + 0.4f * sin(t * 8f)),
                radius = r * 1.07f, center = c, style = Stroke(2f * dp)
            )
            Mood.Standby -> Unit
        }
    }
}
