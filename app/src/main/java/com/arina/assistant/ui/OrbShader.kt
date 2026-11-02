package com.arina.assistant.ui

import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.min
import kotlin.math.sin

/**
 * The GPU version of the orbs (Android 13 and newer): a small program that paints every pixel, so the orb is
 * smooth and soft at any size and costs the phone very little. Older phones, or a phone that refuses the program,
 * get the speck version ([ParticleOrb]).
 */
internal object OrbShaders {

    class Holder(val shader: android.graphics.Shader, val brush: ShaderBrush)

    private const val HEAD = """
uniform float2 res;
uniform float t;
uniform float energy;
uniform float level;
uniform float melt;
uniform float3 cCore;
uniform float3 cLight;
uniform float3 cMid;
uniform float3 cDeep;
float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
"""

    /** Blue: a Rasengan. A swirling ball with a white-hot heart, curved streams of light and two thin orbiting rings. */
    private const val MALE = HEAD + """
half4 main(float2 fc) {
  float2 p = (fc - 0.5 * res) / (0.5 * min(res.x, res.y));
  float2 cell = floor(p * 46.0);
  p += (float2(hash(cell), hash(cell + 7.3)) - 0.5) * melt * 1.6;
  float r = length(p);
  float a = atan(p.y, p.x);
  float R = 0.62 * (1.0 + 0.10 * level + 0.015 * sin(t * 1.3));
  float spin = t * (0.5 + 1.4 * energy);
  float k = r / R;
  float3 b = mix(cCore, cLight, smoothstep(0.0, 0.45, k));
  b = mix(b, cMid, smoothstep(0.35, 0.85, k));
  b = mix(b, cDeep, smoothstep(0.75, 1.0, k));
  float m = 1.0 - smoothstep(R - 0.10, R + 0.03, r);
  float s = pow(0.5 + 0.5 * sin(7.0 * a - 16.0 * r + spin * 2.0), 3.0);
  float s2 = pow(0.5 + 0.5 * sin(5.0 * a + 11.0 * r - spin * 1.3), 3.0);
  float I = m * (0.22 + 0.78 * clamp(s * 0.8 + s2 * 0.5, 0.0, 1.0)) * (1.1 - 0.5 * k);
  float2 g = p * 70.0;
  float h = hash(floor(g));
  float2 f = fract(g) - 0.5;
  float sp = step(0.8, h) * (1.0 - smoothstep(0.0, 0.38, length(f))) * (0.5 + 0.5 * sin(t * 3.0 + h * 40.0)) * (1.0 - smoothstep(R * 0.2, R * 1.25, r));
  float ring = 0.0;
  for (int i = 0; i < 2; i++) {
    float fi = float(i);
    float rot = mix(t * 0.9, -t * 0.7 + 1.9, fi) + fi * 1.6;
    float c = cos(rot);
    float sn = sin(rot);
    float2 q = float2(p.x * c + p.y * sn, (-p.x * sn + p.y * c) / 0.30);
    float e = (length(q) - 0.74) * 34.0;
    float rr = exp(-e * e);
    float ang = atan(q.y, q.x);
    float pulse = 0.5 + 0.5 * sin(ang * 2.0 + t * 3.0 + fi * 2.0);
    rr *= 0.35 + 0.65 * pulse * pulse;
    ring = max(ring, rr * 0.75);
  }
  float halo = exp(-max(r - R, 0.0) * 4.2) * 0.55 * (0.4 + 0.6 * energy) * step(R, r);
  float core = exp(-r * r * 14.0);
  float3 rgb = b * I + cLight * halo + cLight * ring + cCore * core + cCore * sp;
  float al = clamp(max(max(max(I, halo * 0.8), max(ring, core)), sp), 0.0, 1.0);
  float fade = pow(1.0 - melt, 1.5);
  al *= fade;
  rgb = clamp(rgb, 0.0, 1.0);
  return half4(half3(rgb * al), half(al));
}
"""

    /** Pink: a small galaxy seen at an angle, three curved arms of light, a bright heart and a scatter of stars. */
    private const val FEMALE = HEAD + """
half4 main(float2 fc) {
  float2 p = (fc - 0.5 * res) / (0.5 * min(res.x, res.y));
  float2 cell = floor(p * 46.0);
  p += (float2(hash(cell), hash(cell + 7.3)) - 0.5) * melt * 1.6;
  p = p / (1.0 + 0.10 * level);
  float py2 = p.y * 1.6;
  float r2 = length(float2(p.x, py2));
  float a2 = atan(py2, p.x);
  float ph = a2 * 3.0 + log(r2 + 0.05) * 5.0 - t * 0.6 * (0.5 + energy);
  float arm = pow(0.5 + 0.5 * cos(ph), 3.0);
  float fall = exp(-r2 * 2.0);
  float disc = 1.0 - smoothstep(0.55, 1.0, r2);
  float I = disc * (0.10 + 0.9 * arm) * (0.25 + fall * 1.6);
  float2 g = p * 80.0;
  float h = hash(floor(g));
  float2 f = fract(g) - 0.5;
  float stars = step(1.0 - 0.22 * (0.3 + arm * 1.2) * disc, h) * (1.0 - smoothstep(0.0, 0.42, length(f))) * (0.5 + 0.5 * sin(t * 3.0 + h * 50.0));
  float core = exp(-r2 * r2 * 26.0);
  float halo = exp(-max(r2 - 0.2, 0.0) * 3.0) * 0.35 * (0.5 + 0.5 * energy);
  float3 body = mix(cCore, mix(cLight, cMid, smoothstep(0.4, 1.0, r2)), smoothstep(0.0, 0.7, r2));
  body = mix(body, cDeep, smoothstep(0.7, 1.0, r2));
  float3 rgb = body * I + cMid * halo + cCore * core * 1.2 + cCore * stars;
  float al = clamp(max(max(I, halo * 0.9), max(core, stars)), 0.0, 1.0);
  float fade = pow(1.0 - melt, 1.5);
  al *= fade;
  rgb = clamp(rgb, 0.0, 1.0);
  return half4(half3(rgb * al), half(al));
}
"""

    /** The program for one orb, or null when this phone cannot run it. Never throws. */
    fun create(male: Boolean): Holder? {
        if (Build.VERSION.SDK_INT < 33) return null
        return try { make(male) } catch (e: Throwable) { null }
    }

    @RequiresApi(33)
    private fun make(male: Boolean): Holder {
        val sh = android.graphics.RuntimeShader(if (male) MALE else FEMALE)
        return Holder(sh, ShaderBrush(sh))
    }

    @RequiresApi(33)
    fun set(sh: android.graphics.Shader, w: Float, h: Float, t: Float, energy: Float, level: Float, melt: Float, pal: OrbPalette) {
        val s = sh as android.graphics.RuntimeShader
        s.setFloatUniform("res", w, h)
        s.setFloatUniform("t", t)
        s.setFloatUniform("energy", energy)
        s.setFloatUniform("level", level)
        s.setFloatUniform("melt", melt)
        s.setFloatUniform("cCore", pal.core.red, pal.core.green, pal.core.blue)
        s.setFloatUniform("cLight", pal.light.red, pal.light.green, pal.light.blue)
        s.setFloatUniform("cMid", pal.mid.red, pal.mid.green, pal.mid.blue)
        s.setFloatUniform("cDeep", pal.deep.red, pal.deep.green, pal.deep.blue)
    }
}

/**
 * The agent's orb. Blue (male): a Rasengan. Pink (female): a small galaxy.
 * [mood] sets speed and brightness, [level] is the microphone loudness. [dissolve] (0 to 1) breaks the orb into
 * drifting specks that fade, so it can melt into the screen. [backdrop] puts a soft deep-blue night behind it, so
 * its light shows on a white screen.
 */
@Composable
fun OrbView(persona: Persona, mood: Mood, level: Float, modifier: Modifier = Modifier, dissolve: Float = 0f, backdrop: Boolean = false) {
    val male = persona == Persona.Aurion
    val holder = remember(male) { OrbShaders.create(male) }
    if (holder == null) {
        ParticleOrb(persona, mood, level, modifier, dissolve, backdrop)
        return
    }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1_000_000_000f }
    }
    val pal = paletteOf(persona, mood)
    val core by animateColorAsState(pal.core, tween(600), label = "core")
    val light by animateColorAsState(pal.light, tween(600), label = "light")
    val mid by animateColorAsState(pal.mid, tween(600), label = "mid")
    val deep by animateColorAsState(pal.deep, tween(600), label = "deep")
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
    val live = OrbPalette(core, light, mid, deep)

    Canvas(modifier) {
        val d = dissolve.coerceIn(0f, 1f)
        val fade = (1f - d) * (1f - d)
        val base = min(size.width, size.height) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        val talk = if (mood == Mood.Speaking) (0.5f + 0.5f * sin(time * 9f) * sin(time * 5.3f + 1f)).coerceIn(0f, 1f) else 0f
        if (backdrop) drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF0A1B4D).copy(alpha = 0.96f * fade), Color(0xFF071236).copy(alpha = 0.80f * fade), Color.Transparent),
                center = c, radius = base
            ),
            radius = base, center = c
        )
        if (Build.VERSION.SDK_INT >= 33) {
            // if the phone refuses the program for any reason, draw nothing rather than crash
            val ok = try { OrbShaders.set(holder.shader, size.width, size.height, time, energy, level + talk * 0.5f, d, live); true } catch (e: Throwable) { false }
            if (ok) drawRect(holder.brush)
        }
    }
}
