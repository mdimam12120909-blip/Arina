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

/** The colour the orb shows for a mood. */
fun moodTint(persona: Persona, mood: Mood): Color = moodColor(persona, mood)

/** The persona's signature colour: pink for the female agent, blue for the male agent. */
fun accentOf(persona: Persona): Color = if (persona == Persona.Arina) Color(0xFFEC4899) else Color(0xFF2563EB)

/** Colours of one orb: glow, mid, deep, and the near-white of the core. */
private class OrbPalette(val core: Color, val light: Color, val mid: Color, val deep: Color)

private fun paletteOf(persona: Persona, mood: Mood): OrbPalette {
    val base = if (persona == Persona.Arina)
        OrbPalette(Color(0xFFFFF1F9), Color(0xFFFF9AD5), Color(0xFFEC4899), Color(0xFF7A1D8C))
    else
        OrbPalette(Color(0xFFF2FBFF), Color(0xFF7FDBFF), Color(0xFF2F8BFF), Color(0xFF0B2A9E))
    return when (mood) {
        Mood.Alert -> OrbPalette(base.core, lerp(base.light, Color(0xFFFF8A8A), 0.55f), lerp(base.mid, Color(0xFFEF4444), 0.6f), lerp(base.deep, Color(0xFF7F1D1D), 0.5f))
        Mood.Thinking -> OrbPalette(base.core, lerp(base.light, Color(0xFFC4B5FD), 0.35f), lerp(base.mid, Color(0xFF7C6CF5), 0.35f), base.deep)
        else -> base
    }
}

/** One speck of the orb. [kind]: 0 body, 1 ring/arm, 2 sparkle. */
private class Specks(val n: Int) {
    val x = FloatArray(n); val y = FloatArray(n); val z = FloatArray(n)
    val r = FloatArray(n); val seed = FloatArray(n); val kind = IntArray(n); val ring = IntArray(n)
}

private fun buildSpecks(persona: Persona): Specks {
    val rnd = java.util.Random(if (persona == Persona.Arina) 7L else 11L)
    if (persona == Persona.Aurion) {
        // Swirling energy sphere: a body of specks plus three tilted rings of bright streaks.
        val body = 230; val per = 46
        val sp = Specks(body + per * 3)
        for (i in 0 until body) {
            val u = rnd.nextFloat() * 2f - 1f
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val q = sqrt(1f - u * u)
            val rad = 0.5f + 0.5f * sqrt(rnd.nextFloat())
            sp.x[i] = q * cos(a) * rad; sp.y[i] = u * rad; sp.z[i] = q * sin(a) * rad
            sp.r[i] = rad; sp.seed[i] = rnd.nextFloat() * 6.28f; sp.kind[i] = 0
        }
        for (k in 0 until 3) for (j in 0 until per) {
            val i = body + k * per + j
            val a = j.toFloat() / per * 2f * PI.toFloat()
            sp.x[i] = cos(a) * 0.92f; sp.y[i] = 0f; sp.z[i] = sin(a) * 0.92f
            sp.r[i] = 1f; sp.seed[i] = rnd.nextFloat() * 6.28f; sp.kind[i] = 1; sp.ring[i] = k
        }
        return sp
    }
    // Pink: a galaxy of two spiral arms inside a twinkling shell of sparkles.
    val arm = 120; val shell = 150
    val sp = Specks(arm * 2 + shell)
    for (k in 0 until 2) for (j in 0 until arm) {
        val i = k * arm + j
        val s = (j + 1).toFloat() / arm
        val a = s * 5.6f + k * PI.toFloat() + (rnd.nextFloat() - 0.5f) * 0.28f
        val rad = 0.08f + 0.86f * s
        sp.x[i] = cos(a) * rad; sp.y[i] = (rnd.nextFloat() - 0.5f) * 0.10f * (1f - s * 0.4f); sp.z[i] = sin(a) * rad
        sp.r[i] = s; sp.seed[i] = rnd.nextFloat() * 6.28f; sp.kind[i] = 1; sp.ring[i] = k
    }
    for (j in 0 until shell) {
        val i = arm * 2 + j
        val u = rnd.nextFloat() * 2f - 1f
        val a = rnd.nextFloat() * 2f * PI.toFloat()
        val q = sqrt(1f - u * u)
        val rad = 0.7f + 0.3f * rnd.nextFloat()
        sp.x[i] = q * cos(a) * rad; sp.y[i] = u * rad; sp.z[i] = q * sin(a) * rad
        sp.r[i] = rad; sp.seed[i] = rnd.nextFloat() * 6.28f; sp.kind[i] = 2
    }
    return sp
}

/**
 * The agent's orb, made of thousands of tiny lights. Blue (male): a swirling energy sphere, a bright core with
 * streaks spinning around it. Pink (female): a small galaxy with two spiral arms inside a twinkling shell.
 * [mood] sets speed and brightness, [level] is the microphone loudness. [dissolve] (0 to 1) scatters the specks
 * and fades the orb away, so it can melt into the screen.
 */
@Composable
fun OrbView(persona: Persona, mood: Mood, level: Float, modifier: Modifier = Modifier, dissolve: Float = 0f) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1_000_000_000f }
    }
    val specks = remember(persona) { buildSpecks(persona) }
    val pal = paletteOf(persona, mood)
    val tintMid by animateColorAsState(pal.mid, tween(600), label = "mid")
    val tintLight by animateColorAsState(pal.light, tween(600), label = "light")
    val tintDeep by animateColorAsState(pal.deep, tween(600), label = "deep")
    val energy by animateFloatAsState(
        when (mood) {
            Mood.Standby -> 0.25f
            Mood.Listening -> 0.6f
            Mood.Thinking -> 0.85f
            Mood.Speaking -> 0.95f
            Mood.Researching -> 0.85f
            Mood.Alert -> 0.9f
        },
        tween(500), label = "energy"
    )
    val density = LocalDensity.current.density

    Canvas(modifier) {
        val t = time
        val c = Offset(size.width / 2f, size.height / 2f)
        val base = min(size.width, size.height) / 2f
        val talk = if (mood == Mood.Speaking) (0.5f + 0.5f * sin(t * 9f) * sin(t * 5.3f + 1f)).coerceIn(0f, 1f) else 0f
        val d = dissolve.coerceIn(0f, 1f)
        val fade = (1f - d) * (1f - d)
        val r = base * 0.60f * (1f + 0.012f * sin(t * 1.3f) + level * 0.12f + talk * 0.07f) * (1f - 0.25f * d)
        val male = persona == Persona.Aurion
        val spin = t * (0.35f + 1.5f * energy)

        // halo
        drawCircle(
            Brush.radialGradient(listOf(tintMid.copy(alpha = (0.20f + 0.22f * energy) * fade), Color.Transparent), center = c, radius = r * 1.75f),
            radius = r * 1.75f, center = c
        )
        // body of the sphere
        drawCircle(
            Brush.radialGradient(
                listOf(tintLight.copy(alpha = 0.95f * fade), tintMid.copy(alpha = 0.92f * fade), tintDeep.copy(alpha = 0.96f * fade)),
                center = Offset(c.x - r * 0.15f, c.y - r * 0.2f), radius = r * 1.25f
            ),
            radius = r, center = c
        )

        // specks, back half first and front half after, so near ones sit on top
        val tilt = 0.45f + 0.18f * sin(t * 0.35f)
        val ct = cos(tilt); val st = sin(tilt)
        val spread = 1f + d * 1.5f
        for (pass in 0..1) for (i in 0 until specks.n) {
            val k = specks.kind[i]
            var x = specks.x[i]; val y0 = specks.y[i]; var z = specks.z[i]
            val a: Float = if (male) {
                when (k) {
                    1 -> spin * (1.6f + specks.ring[i] * 0.55f) * (if (specks.ring[i] % 2 == 0) 1f else -1f)
                    else -> spin + y0 * 2.3f          // the twist that makes the body swirl
                }
            } else {
                if (k == 1) spin * 0.8f else spin * 0.25f
            }
            val ca = cos(a); val sa = sin(a)
            val x1 = x * ca + z * sa
            val z1 = -x * sa + z * ca
            // rings of the blue orb are tilted differently; the pink galaxy is seen from an angle
            val tl = if (male && k == 1) tilt + specks.ring[i] * 1.05f else if (!male && k == 1) 0.95f else tilt
            val cl = cos(tl); val sl = sin(tl)
            val y1 = y0 * cl - z1 * sl
            val z2 = y0 * sl + z1 * cl
            val depth = (z2 + 1f) * 0.5f
            if ((pass == 0) != (depth < 0.5f)) continue
            val px = c.x + x1 * r * spread
            val py = c.y + y1 * r * spread
            var alpha = (0.30f + 0.70f * depth) * fade
            var sz = base * 0.028f * (0.55f + depth) * (1f + 0.5f * energy)
            val col: Color
            if (k == 2) {
                val tw = 0.5f + 0.5f * sin(t * 3.2f + specks.seed[i])
                alpha *= 0.25f + 0.75f * tw
                sz *= 0.7f + 0.8f * tw
                col = lerp(tintLight, pal.core, 0.7f)
            } else if (k == 1) {
                col = lerp(tintLight, pal.core, if (male) 0.65f else 0.35f + 0.5f * (1f - specks.r[i]))
                sz *= if (male) 1.35f else 1.1f
            } else {
                col = lerp(pal.core, tintLight, (specks.r[i] * 1.2f).coerceIn(0f, 1f))
                alpha *= 0.85f
            }
            drawCircle(col.copy(alpha = alpha.coerceIn(0f, 1f)), radius = sz.coerceAtLeast(0.6f * density), center = Offset(px, py))
        }

        // bright core
        val coreR = r * (0.42f + 0.10f * energy + 0.08f * talk)
        drawCircle(
            Brush.radialGradient(listOf(pal.core.copy(alpha = 0.95f * fade), pal.light.copy(alpha = 0.45f * fade), Color.Transparent), center = c, radius = coreR),
            radius = coreR, center = c
        )
        // glossy edge
        drawArc(
            Color.White.copy(alpha = 0.50f * fade), startAngle = 205f, sweepAngle = 58f, useCenter = false,
            topLeft = Offset(c.x - r * 0.84f, c.y - r * 0.84f), size = Size(r * 1.68f, r * 1.68f),
            style = Stroke(width = 2f * density, cap = StrokeCap.Round)
        )
        drawCircle(Color.White.copy(alpha = 0.22f * fade), radius = r, center = c, style = Stroke(1f * density))
        // rings spreading out while listening or speaking
        if (mood == Mood.Listening || mood == Mood.Speaking) for (i in 0..1) {
            val p = (t * 0.5f + i * 0.5f) % 1f
            drawCircle(tintMid.copy(alpha = (1f - p) * 0.28f * fade), radius = r * (1.04f + 0.45f * p), center = c, style = Stroke(1.5f * density))
        }
    }
}
