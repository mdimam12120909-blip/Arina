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

private fun moodColor(persona: Persona, mood: Mood): Color {
    val green = persona == Persona.Arina
    return when (mood) {
        Mood.Standby -> if (green) Color(0xFF2FD16F) else Color(0xFF3D8BFF)
        Mood.Listening -> if (green) Color(0xFF5BFF9A) else Color(0xFF6BB4FF)
        Mood.Thinking -> Color(0xFFA27BFF)
        Mood.Speaking -> if (green) Color(0xFF7DFFB0) else Color(0xFF8CC8FF)
        Mood.Researching -> Color(0xFFFFB84D)
        Mood.Alert -> Color(0xFFFF5C6C)
    }
}

/** The colour the orb (and the screen-edge light) shows for a mood. */
fun moodTint(persona: Persona, mood: Mood): Color = moodColor(persona, mood)

/** The persona's signature colour (green for Arina, blue for Aurion), used across the app. */
fun accentOf(persona: Persona): Color = if (persona == Persona.Arina) Color(0xFF2FD16F) else Color(0xFF3D8BFF)

private const val PARTICLES = 900

/** Points spread evenly over a sphere (Fibonacci lattice): x, y, z, and a per-point random for twinkle and size. */
private val sphere: FloatArray = FloatArray(PARTICLES * 4).also { out ->
    val golden = (PI * (3.0 - sqrt(5.0))).toFloat()
    val rnd = java.util.Random(7)
    for (i in 0 until PARTICLES) {
        val y = 1f - 2f * (i + 0.5f) / PARTICLES
        val rr = sqrt((1f - y * y).coerceAtLeast(0f))
        val th = golden * i
        out[i * 4] = cos(th) * rr
        out[i * 4 + 1] = y
        out[i * 4 + 2] = sin(th) * rr
        out[i * 4 + 3] = rnd.nextFloat()
    }
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
        val r = min(size.width, size.height) / 2f * 0.60f * breathe

        // The voice is played by the system, so the speaking amplitude is simulated from two slow waves.
        val talk = if (mood == Mood.Speaking) (0.5f + 0.5f * sin(t * 9f) * sin(t * 5.3f + 1f)).coerceIn(0f, 1f) else 0f

        // Outer glow
        val glow = 0.26f + 0.34f * energy + level * 0.35f + talk * 0.25f
        drawCircle(
            Brush.radialGradient(listOf(tint.copy(alpha = glow), Color.Transparent), center = c, radius = r * 2.0f),
            radius = r * 2.0f, center = c
        )

        // Core haze behind the particles
        drawCircle(
            Brush.radialGradient(listOf(tint.copy(alpha = 0.22f + 0.2f * energy), Color.Transparent), center = c, radius = r * 0.95f),
            radius = r * 0.95f, center = c
        )

        // Orbit rings
        for (i in 0..1) {
            val rr = r * (1.28f + 0.1f * i)
            val dir = if (i % 2 == 0) 1f else -1f
            rotate(degrees = dir * t * (12f + 40f * energy) * (1f + i * 0.4f), pivot = c) {
                drawArc(
                    color = tint.copy(alpha = 0.5f - 0.15f * i),
                    startAngle = 0f, sweepAngle = 90f + i * 60f, useCenter = false,
                    topLeft = Offset(c.x - rr, c.y - rr), size = Size(rr * 2, rr * 2),
                    style = Stroke(width = (1.4f - 0.3f * i) * dp)
                )
            }
        }
        // A tilted golden orbit, like a ring around a planet
        rotate(degrees = -28f + sin(t * 0.4f) * 6f, pivot = c) {
            drawOval(
                Color(0xFFFFD34D).copy(alpha = 0.75f),
                topLeft = Offset(c.x - r * 1.45f, c.y - r * 0.5f), size = Size(r * 2.9f, r * 1.0f),
                style = Stroke(1.3f * dp)
            )
        }

        // Particle sphere: turns slowly, faster while thinking or researching
        val spin = t * (0.22f + 0.55f * energy) * (if (mood == Mood.Thinking) 1.8f else 1f)
        val ca = cos(spin); val sa = sin(spin)
        val tilt = 0.32f
        val ct = cos(tilt); val st = sin(tilt)
        val scanPos = ((t * 0.30f) % 1f) * 2f - 1f
        val scanPower = when (mood) {
            Mood.Listening, Mood.Researching -> 1f
            Mood.Thinking -> 0.7f
            else -> 0.25f
        }
        for (i in 0 until PARTICLES) {
            val x = sphere[i * 4]; val y = sphere[i * 4 + 1]; val z = sphere[i * 4 + 2]; val seed = sphere[i * 4 + 3]
            val x1 = x * ca + z * sa
            val z1 = -x * sa + z * ca
            val y1 = y * ct - z1 * st
            val z2 = y * st + z1 * ct
            var disp = 1f
            if (mood == Mood.Speaking) disp += 0.07f * talk * sin(t * 12f + i * 0.7f)
            if (mood == Mood.Listening) disp += level * 0.10f * sin(t * 9f + i * 0.5f)
            if (mood == Mood.Alert) disp += 0.03f * sin(t * 20f + i)
            val depth = z2 * 0.5f + 0.5f
            var alpha = (0.10f + 0.80f * depth) * (0.65f + 0.35f * sin(t * (1.5f + seed * 2f) + seed * 40f))
            val d = y1 - scanPos
            alpha += exp(-d * d * 45f) * 0.55f * scanPower * depth
            val px = c.x + x1 * r * disp
            val py = c.y + y1 * r * disp
            val bright = seed > 0.93f
            drawCircle(
                (if (bright) Color.White else tint).copy(alpha = alpha.coerceIn(0f, 1f)),
                radius = (0.8f + 1.9f * depth) * dp * (0.7f + 0.5f * seed),
                center = Offset(px, py)
            )
        }

        // The face: a dot-matrix portrait floating in front of the particles (woman for Arina, man for Aurion)
        val n = rows.size
        val half = (n - 1) / 2f
        val cell = (2f * r * 0.80f) / n
        val sway = sin(t * 0.5f) * 0.05f
        val blink = if ((t % 4.2f) < 0.14f) 0f else 1f
        for (row in 0 until n) {
            val line = rows[row]
            for (col in 0 until n) {
                val ch = line[col]
                if (ch == '.' || ch == 'f') continue
                val u = (col - half) / (half + 0.5f)
                val v = (row - half) / (half + 0.5f)
                val d2 = u * u + v * v
                if (d2 > 0.95f) continue
                val z = sqrt((1f - d2).coerceAtLeast(0f))
                var alpha: Float
                var rad: Float
                when (ch) {
                    'H' -> { alpha = 0.40f; rad = 0.22f }
                    'E' -> { alpha = 0.95f * blink; rad = 0.34f }
                    else -> { alpha = 0.85f; rad = 0.28f }
                }
                val x = c.x + (u + sway * z) * r * 0.80f
                var y = c.y + v * r * 0.80f
                if (ch == 'L') {
                    y += sin(t * 14f + col * 0.35f) * talk * cell * 0.55f * (if (row > half + 3.5f) 1.4f else 0.6f)
                    alpha *= 0.75f + 0.25f * talk
                }
                alpha *= 0.85f + 0.15f * sin(t * 2.2f + (row * 31 + col * 17) * 0.37f)
                alpha *= 0.4f + 0.6f * z
                drawCircle(
                    (if (ch == 'E') Color.White else tint).copy(alpha = alpha.coerceIn(0f, 1f)),
                    radius = cell * rad * (0.6f + 0.4f * z), center = Offset(x, y)
                )
            }
        }

        // Mood effects
        when (mood) {
            Mood.Listening -> for (k in 0..1) {
                val p = (t * 0.8f + k * 0.5f) % 1f
                drawCircle(
                    tint.copy(alpha = (1f - p) * (0.25f + 0.5f * level)),
                    radius = r * (1.05f + 0.5f * p), center = c, style = Stroke(1.6f * dp)
                )
            }
            Mood.Thinking -> for (i in 0 until 10) {
                val a = t * 2.4f + i * 2f * PI.toFloat() / 10f
                drawCircle(
                    tint.copy(alpha = 0.35f + 0.55f * (0.5f + 0.5f * sin(a * 1.3f))),
                    radius = 3.2f * dp,
                    center = Offset(c.x + cos(a) * r * 1.3f, c.y + sin(a) * r * 0.42f)
                )
            }
            Mood.Speaking -> {
                val path = Path()
                val steps = 90
                for (i in 0..steps) {
                    val a = i * 2f * PI.toFloat() / steps
                    val wob = sin(a * 7f + t * 11f) * 0.028f * talk + sin(a * 3f - t * 6f) * 0.012f
                    val rr = r * (1.10f + wob)
                    val px = c.x + cos(a) * rr
                    val py = c.y + sin(a) * rr
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                drawPath(path, tint.copy(alpha = 0.9f), style = Stroke(2.0f * dp))
            }
            Mood.Researching -> rotate(degrees = t * 140f, pivot = c) {
                drawArc(
                    color = tint, startAngle = 0f, sweepAngle = 70f, useCenter = false,
                    topLeft = Offset(c.x - r * 1.12f, c.y - r * 1.12f), size = Size(r * 2.24f, r * 2.24f),
                    style = Stroke(3.2f * dp)
                )
            }
            Mood.Alert -> drawCircle(
                tint.copy(alpha = 0.4f + 0.4f * sin(t * 8f)),
                radius = r * 1.1f, center = c, style = Stroke(2f * dp)
            )
            Mood.Standby -> Unit
        }
    }
}
