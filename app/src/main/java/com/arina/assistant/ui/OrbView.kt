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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
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

/** The colour the orb shows for a mood (used for small accents). */
fun moodTint(persona: Persona, mood: Mood): Color = when (mood) {
    Mood.Alert -> Color(0xFFEF4444)
    Mood.Thinking -> Color(0xFF6366F1)
    Mood.Researching -> Color(0xFF06B6D4)
    else -> if (persona == Persona.Arina) Color(0xFFEC4899) else Color(0xFF2F8BFF)
}

/** The persona's signature colour: pink for the female agent, blue for the male agent. */
fun accentOf(persona: Persona): Color = if (persona == Persona.Arina) Color(0xFFEC4899) else Color(0xFF2563EB)

/** Colours of one orb: the white-hot core, a light tone, a mid tone and a deep tone. */
internal class OrbPalette(val core: Color, val light: Color, val mid: Color, val deep: Color)

internal fun paletteOf(persona: Persona, mood: Mood): OrbPalette {
    val base = if (persona == Persona.Arina)
        OrbPalette(Color(0xFFFFF4FA), Color(0xFFFF9AD5), Color(0xFFF0449F), Color(0xFF9D4EDD))
    else
        OrbPalette(Color(0xFFFFFFFF), Color(0xFF9BE7FF), Color(0xFF3AA0FF), Color(0xFF1B4FE0))
    return when (mood) {
        Mood.Alert -> OrbPalette(base.core, lerp(base.light, Color(0xFFFF9A9A), 0.6f), lerp(base.mid, Color(0xFFEF4444), 0.65f), lerp(base.deep, Color(0xFF9B1C1C), 0.6f))
        Mood.Thinking -> OrbPalette(base.core, lerp(base.light, Color(0xFFC4B5FD), 0.4f), lerp(base.mid, Color(0xFF7C6CF5), 0.4f), base.deep)
        else -> base
    }
}

private const val TWO_PI = (2.0 * PI).toFloat()

/**
 * The cloud of tiny lights an orb is made of. [kind] means a different thing per orb (see the builders);
 * [tone] is 0 white-hot, 1 light, 2 mid colour. dx/dy/dz are the way a particle drifts when the orb dissolves.
 */
private class Cloud(val n: Int) {
    val a = FloatArray(n); val u = FloatArray(n); val rad = FloatArray(n); val seed = FloatArray(n)
    val kind = IntArray(n); val tone = IntArray(n); val arm = IntArray(n)
    val dx = FloatArray(n); val dy = FloatArray(n); val dz = FloatArray(n)
    fun scatter(i: Int, rnd: java.util.Random) {
        val uu = rnd.nextFloat() * 2f - 1f
        val aa = rnd.nextFloat() * TWO_PI
        val q = sqrt(1f - uu * uu)
        val s = 0.5f + rnd.nextFloat()
        dx[i] = q * cos(aa) * s; dy[i] = uu * s; dz[i] = q * sin(aa) * s
        seed[i] = rnd.nextFloat() * TWO_PI
    }
}

/** Blue "Rasengan": a swirling sphere of light. Kinds: 0 swirl body, 1 orbiting ring, 2 white-hot core, 3 outer mist. */
private fun buildRasengan(): Cloud {
    val rnd = java.util.Random(11L)
    val body = 1000; val ring = 2 * 64; val core = 170; val mist = 140
    val c = Cloud(body + ring + core + mist)
    var i = 0
    for (j in 0 until body) {
        val k = rnd.nextInt(8)
        val u = rnd.nextFloat() * 2f - 1f
        val rad = 0.52f + 0.48f * sqrt(rnd.nextFloat())
        c.u[i] = u; c.rad[i] = rad; c.kind[i] = 0; c.arm[i] = k
        c.a[i] = k * TWO_PI / 8f + u * 2.4f + (rnd.nextFloat() - 0.5f) * 0.10f   // lines up the specks into curved streams
        c.tone[i] = if (rad < 0.72f) 1 else 2
        c.scatter(i, rnd); i++
    }
    for (k in 0 until 2) for (j in 0 until 64) {
        c.a[i] = j / 64f * TWO_PI; c.rad[i] = 1.0f + (rnd.nextFloat() - 0.5f) * 0.03f
        c.kind[i] = 1; c.arm[i] = k; c.tone[i] = 0
        c.scatter(i, rnd); i++
    }
    for (j in 0 until core) {
        c.u[i] = rnd.nextFloat() * 2f - 1f; c.a[i] = rnd.nextFloat() * TWO_PI
        c.rad[i] = 0.30f * rnd.nextFloat() * rnd.nextFloat() + 0.02f
        c.kind[i] = 2; c.tone[i] = 0
        c.scatter(i, rnd); i++
    }
    for (j in 0 until mist) {
        c.u[i] = rnd.nextFloat() * 2f - 1f; c.a[i] = rnd.nextFloat() * TWO_PI
        c.rad[i] = 1.05f + 0.30f * rnd.nextFloat()
        c.kind[i] = 3; c.tone[i] = 1
        c.scatter(i, rnd); i++
    }
    return c
}

/** Pink galaxy: three spiral arms, a glowing bulge, a faint halo and twinkling sparkles. Kinds: 0 arm, 1 bulge, 2 halo, 3 sparkle. */
private fun buildGalaxy(): Cloud {
    val rnd = java.util.Random(7L)
    val per = 380; val arms = 3; val bulge = 170; val halo = 230; val spark = 110
    val c = Cloud(per * arms + bulge + halo + spark)
    var i = 0
    for (k in 0 until arms) for (j in 0 until per) {
        val s = (j + 1) / per.toFloat()
        c.rad[i] = 0.07f + 0.90f * s
        c.a[i] = k * TWO_PI / arms + s * 6.0f + (rnd.nextFloat() - 0.5f) * (0.08f + 0.40f * s)
        c.u[i] = (rnd.nextFloat() + rnd.nextFloat() - 1f) * 0.12f * (1f - 0.5f * s)
        c.kind[i] = 0; c.arm[i] = k
        c.tone[i] = if (s < 0.30f) 0 else if (s < 0.68f) 1 else 2
        c.scatter(i, rnd); i++
    }
    for (j in 0 until bulge) {
        c.u[i] = rnd.nextFloat() * 2f - 1f; c.a[i] = rnd.nextFloat() * TWO_PI
        c.rad[i] = 0.28f * rnd.nextFloat() + 0.02f
        c.kind[i] = 1; c.tone[i] = 0
        c.scatter(i, rnd); i++
    }
    for (j in 0 until halo) {
        c.u[i] = rnd.nextFloat() * 2f - 1f; c.a[i] = rnd.nextFloat() * TWO_PI
        c.rad[i] = 0.55f + 0.45f * rnd.nextFloat()
        c.kind[i] = 2; c.tone[i] = 2
        c.scatter(i, rnd); i++
    }
    for (j in 0 until spark) {
        c.u[i] = rnd.nextFloat() * 2f - 1f; c.a[i] = rnd.nextFloat() * TWO_PI
        c.rad[i] = 0.85f + 0.30f * rnd.nextFloat()
        c.kind[i] = 3; c.tone[i] = 1
        c.scatter(i, rnd); i++
    }
    return c
}

private const val BUCKETS = 15   // 4 depths x 3 tones, then 3 sparkle levels

/**
 * The agent's orb, made of about fifteen hundred tiny lights.
 * Blue (male): a Rasengan, a swirling ball of light with a white-hot heart and two orbiting rings.
 * Pink (female): a small galaxy with three spiral arms and twinkling sparkles.
 * [mood] sets speed and brightness, [level] is the microphone loudness. [dissolve] (0 to 1) lets the specks drift
 * apart and fade, so the orb melts into the screen.
 */
@Composable
internal fun ParticleOrb(persona: Persona, mood: Mood, level: Float, modifier: Modifier = Modifier, dissolve: Float = 0f, backdrop: Boolean = false) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1_000_000_000f }
    }
    val male = persona == Persona.Aurion
    val cloud = remember(persona) { if (male) buildRasengan() else buildGalaxy() }
    val pts = remember(cloud) { Array(BUCKETS) { FloatArray(cloud.n * 2) } }
    val cnt = remember(cloud) { IntArray(BUCKETS) }
    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
    }
    val pal = paletteOf(persona, mood)
    val tintMid by animateColorAsState(pal.mid, tween(600), label = "mid")
    val tintLight by animateColorAsState(pal.light, tween(600), label = "light")
    val tintDeep by animateColorAsState(pal.deep, tween(600), label = "deep")
    val energy by animateFloatAsState(
        when (mood) {
            Mood.Standby -> 0.25f
            Mood.Listening -> 0.6f
            Mood.Thinking -> 0.9f
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
        val r = base * 0.60f * (1f + 0.015f * sin(t * 1.3f) + level * 0.14f + talk * 0.06f)
        val spin = t * (0.30f + 1.35f * energy)
        val tilt = (if (male) 0.40f else 1.02f) + 0.12f * sin(t * 0.37f)
        val ct = cos(tilt); val st = sin(tilt)
        val spreadK = d * 1.5f * r

        // a deep, soft night behind the orb (only where asked), so white light shows on a white screen
        if (backdrop) drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF0A1B4D).copy(alpha = 0.96f * fade), Color(0xFF071236).copy(alpha = 0.80f * fade), Color.Transparent),
                center = c, radius = base * 1.0f
            ),
            radius = base * 1.0f, center = c
        )
        // soft glow behind the specks (so the white-hot parts show on a light screen too)
        drawCircle(
            Brush.radialGradient(listOf(tintMid.copy(alpha = (0.24f + 0.26f * energy) * fade), Color.Transparent), center = c, radius = r * 1.9f),
            radius = r * 1.9f, center = c
        )
        drawCircle(
            Brush.radialGradient(listOf(tintLight.copy(alpha = (0.30f + 0.20f * energy) * fade), Color.Transparent), center = c, radius = r * 0.85f),
            radius = r * 0.85f, center = c
        )

        java.util.Arrays.fill(cnt, 0)
        for (i in 0 until cloud.n) {
            val kd = cloud.kind[i]
            val u = cloud.u[i]; val rad = cloud.rad[i]; val a0 = cloud.a[i]; val sd = cloud.seed[i]
            var x: Float; var y: Float; var z: Float
            if (male) {
                when (kd) {
                    0 -> {
                        val q = sqrt(max(0f, 1f - u * u))
                        val ang = a0 + spin * (0.5f + 1.5f * q)          // the equator turns faster: the swirl
                        val rr = rad * (1f + 0.06f * talk * sin(rad * 9f - t * 11f) + 0.02f * sin(t * 2f + sd))
                        x = q * rr * cos(ang); z = q * rr * sin(ang); y = u * rr
                    }
                    1 -> {
                        val k = cloud.arm[i]
                        val ang = a0 + spin * 2.4f * (if (k == 0) 1f else -1f)
                        val tl = 0.55f + k * 1.2f
                        val px = cos(ang) * rad; val pz = sin(ang) * rad
                        x = px; y = -pz * sin(tl); z = pz * cos(tl)
                    }
                    2 -> {
                        val q = sqrt(max(0f, 1f - u * u))
                        val ang = a0 + spin * 1.3f
                        val rr = rad * (1f + 0.18f * sin(t * 3.1f + sd) + 0.25f * energy * talk)
                        x = q * rr * cos(ang); z = q * rr * sin(ang); y = u * rr
                    }
                    else -> {
                        val q = sqrt(max(0f, 1f - u * u))
                        val ang = a0 + spin * 0.3f
                        val rr = rad * (1f + 0.05f * sin(t * 1.7f + sd))
                        x = q * rr * cos(ang); z = q * rr * sin(ang); y = u * rr
                    }
                }
            } else {
                when (kd) {
                    0 -> {
                        val ang = a0 + spin * 0.9f / (0.30f + rad)       // the middle turns faster than the rim
                        x = rad * cos(ang); z = rad * sin(ang)
                        y = u * (1f + 0.25f * sin(t * 1.4f + sd))
                    }
                    1 -> {
                        val q = sqrt(max(0f, 1f - u * u))
                        val ang = a0 + spin * 1.1f
                        val rr = rad * (1f + 0.14f * sin(t * 2.6f + sd) + 0.20f * talk)
                        x = q * rr * cos(ang); z = q * rr * sin(ang); y = u * rr
                    }
                    else -> {
                        val q = sqrt(max(0f, 1f - u * u))
                        val ang = a0 + spin * (if (kd == 2) 0.25f else 0.12f)
                        x = q * rad * cos(ang); z = q * rad * sin(ang); y = u * rad
                    }
                }
            }
            // tilt the whole thing toward the viewer
            val y1 = y * ct - z * st
            val z1 = y * st + z * ct
            var px = c.x + x * r
            var py = c.y + y1 * r
            if (d > 0f) {   // melting: every speck drifts its own way
                val s = spreadK * (0.4f + 0.6f * ((sd / TWO_PI)))
                px += cloud.dx[i] * s; py += cloud.dy[i] * s
            }
            val depth = ((z1 + 1.3f) / 2.6f).coerceIn(0f, 0.999f)
            val dc = (depth * 4f).toInt()
            val b: Int = if (!male && kd == 3) {
                val tw = sin(t * 3.4f + sd)
                12 + (if (tw > 0.55f) 2 else if (tw > -0.2f) 1 else 0)
            } else dc * 3 + cloud.tone[i]
            val n = cnt[b]
            val arr = pts[b]
            arr[n * 2] = px; arr[n * 2 + 1] = py
            cnt[b] = n + 1
        }

        val bright = 0.80f + 0.30f * energy
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            for (b in 0 until BUCKETS) {
                if (cnt[b] == 0) continue
                val col: Color; val dAlpha: Float; val w: Float
                if (b >= 12) {
                    col = lerp(tintLight, pal.core, 0.7f)
                    dAlpha = when (b) { 12 -> 0.18f; 13 -> 0.55f; else -> 1f }
                    w = base * (0.016f + 0.026f * (b - 11) / 3f)
                } else {
                    val dc = b / 3; val tone = b % 3
                    col = when (tone) { 0 -> pal.core; 1 -> tintLight; else -> lerp(tintMid, tintDeep, 0.45f * (1f - dc / 3f)) }
                    dAlpha = 0.20f + 0.80f * (dc + 0.5f) / 4f
                    w = base * (0.014f + 0.016f * (dc + 0.5f) / 4f) * (if (tone == 0) 1.35f else if (tone == 1) 1.1f else 0.95f)
                }
                val ww = max(w, 1.0f * density)
                // a wide faint pass first (the glow around each speck), then the speck itself
                paint.color = col.copy(alpha = (dAlpha * bright * fade * 0.20f).coerceIn(0f, 1f)).toArgb()
                paint.strokeWidth = ww * 3.4f
                nc.drawPoints(pts[b], 0, cnt[b] * 2, paint)
                paint.color = col.copy(alpha = (dAlpha * bright * fade).coerceIn(0f, 1f)).toArgb()
                paint.strokeWidth = ww
                nc.drawPoints(pts[b], 0, cnt[b] * 2, paint)
            }
        }

        // the bright heart
        val coreR = r * (0.40f + 0.10f * energy + 0.07f * talk)
        drawCircle(
            Brush.radialGradient(listOf(pal.core.copy(alpha = 0.92f * fade), tintLight.copy(alpha = 0.40f * fade), Color.Transparent), center = c, radius = coreR),
            radius = coreR, center = c
        )
        // rings spreading out while listening or speaking
        if (mood == Mood.Listening || mood == Mood.Speaking) for (i in 0..1) {
            val p = (t * 0.5f + i * 0.5f) % 1f
            drawCircle(tintMid.copy(alpha = (1f - p) * 0.30f * fade), radius = r * (1.04f + 0.50f * p), center = c, style = Stroke(1.5f * density))
        }
    }
}
