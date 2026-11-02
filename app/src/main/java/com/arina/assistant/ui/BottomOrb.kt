package com.arina.assistant.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arina.assistant.voice.Cue
import kotlinx.coroutines.delay

private val ORB_SIZE = 260.dp

/**
 * What shows over other apps (never takes touches).
 * 1. When the agent wakes, speaks or works, a small sound plays and the orb rises from the bottom edge to nearly
 *    the size it has inside the app.
 * 2. Two seconds after things go quiet, the orb melts away speck by speck.
 * 3. While the agent is on, a living light runs along the screen's edges, so you can tell at a glance that an AI
 *    agent is working on this phone. It is calm while waiting and brighter while it listens, talks or works.
 */
@Composable
fun AgentOverlay(engine: ArinaEngine) {
    val problem = engine.notice.value != null
    val mood = moodOf(engine.status.value, engine.researchActive.value || engine.agentActive.value, problem)
    val busy = mood == Mood.Speaking || mood == Mood.Thinking || mood == Mood.Researching || mood == Mood.Alert
    val wake = engine.glow.intValue   // changes whenever the floating orb is switched on or tapped awake
    val style = engine.bgStyle.value
    if (style != "orb") {
        LaunchedEffect(Unit) { engine.orbVisible.value = false }
        if (style == "off") return
        val working = busy || engine.agentActive.value
        Box(Modifier.fillMaxSize()) {
            AgentAura(engine, mood, onlyWhenWorking = true)
            if (working) {
                val line = engine.notice.value ?: if (engine.agentActive.value) engine.agentLine.value else ""
                val label = line.ifBlank {
                    when (mood) {
                        Mood.Speaking -> "Speaking"
                        Mood.Researching -> "Looking it up"
                        Mood.Alert -> "Needs you"
                        else -> "Thinking"
                    }
                }
                val pulse by rememberInfiniteTransition(label = "dot").animateFloat(
                    0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "p"
                )
                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = 38.dp)
                        .clip(RoundedCornerShape(50)).background(ArinaColors.Surface)
                        .border(1.dp, ArinaColors.Deep, RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).graphicsLayer { alpha = pulse }.clip(RoundedCornerShape(50)).background(accentOf(engine.persona.value)))
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = ArinaColors.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp))
                }
            }
        }
        return
    }

    // 0 hidden, 1 showing, 2 melting away
    var phase by remember { mutableIntStateOf(1) }
    LaunchedEffect(busy, wake) {
        phase = 1
        if (!busy) {
            delay(2300)
            phase = 2
            delay(1800)
            phase = 0
        }
    }
    LaunchedEffect(phase) {
        engine.orbVisible.value = phase != 0
        when (phase) { 1 -> Cue.appear(); 2 -> Cue.fade() }
    }
    val appear by animateFloatAsState(
        if (phase == 0) 0f else 1f,
        if (phase == 0) snap() else tween(900, easing = FastOutSlowInEasing), label = "appear"
    )
    val melt by animateFloatAsState(
        if (phase == 2) 1f else 0f,
        if (phase == 2) tween(1700, easing = LinearEasing) else snap(), label = "melt"
    )
    val density = LocalDensity.current.density

    Box(Modifier.fillMaxSize()) {
        AgentAura(engine, mood)
        if (phase != 0 && appear > 0.001f) {
            val line = engine.notice.value ?: if (engine.agentActive.value) engine.agentLine.value else engine.caption.value
            val showLine = phase == 1 && line.isNotBlank() && (busy || engine.agentActive.value)
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (showLine) {
                    Text(
                        line, color = ArinaColors.Text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp))
                            .background(ArinaColors.Surface)
                            .border(1.dp, ArinaColors.Deep, RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                }
                OrbView(
                    persona = engine.persona.value, mood = mood, level = engine.level.floatValue,
                    dissolve = melt,
                    modifier = Modifier.size(ORB_SIZE).graphicsLayer {
                        val s = 0.40f + 0.60f * appear
                        scaleX = s; scaleY = s
                        alpha = appear.coerceIn(0f, 1f)
                        translationY = (1f - appear) * 150f * density      // rises from the bottom edge
                    }
                )
            }
        }
    }
}

/** The small window that takes touches: tap the orb (or the handle at the bottom) for the mic, press and hold to open the app. */
@Composable
fun OrbTouch(engine: ArinaEngine, onOpenApp: () -> Unit) {
    val up = engine.orbVisible.value
    val hidden = engine.bgStyle.value == "off"
    Box(
        Modifier.size(width = if (up) 220.dp else if (hidden) 1.dp else 90.dp, height = if (up) 220.dp else if (hidden) 1.dp else 22.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { engine.onMicTap(); engine.glow.intValue++ }, onLongPress = { onOpenApp() })
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        if (!up && !hidden) {
            Box(
                Modifier.padding(bottom = 5.dp).size(width = 38.dp, height = 4.dp).clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Color(0x552F8BFF), Color(0xCC7FDBFF), Color(0x55EC4899))))
            )
        }
    }
}

/**
 * Light that runs around the screen's edges while the agent is on. Calm when waiting, brighter and faster while
 * it listens, talks or works on the phone. It never takes touches.
 */
@Composable
private fun AgentAura(engine: ArinaEngine, mood: Mood, onlyWhenWorking: Boolean = false) {
    val working = mood == Mood.Speaking || mood == Mood.Thinking || mood == Mood.Researching || mood == Mood.Alert || engine.agentActive.value
    val on = if (onlyWhenWorking) working else engine.micOn.value || mood != Mood.Standby
    val female = engine.persona.value == Persona.Arina
    val a = if (female) Color(0xFFFF9AD5) else Color(0xFF9BE7FF)
    val b = if (female) Color(0xFFF0449F) else Color(0xFF3AA0FF)
    val c = if (female) Color(0xFF9D4EDD) else Color(0xFF1B4FE0)
    val worker = mood == Mood.Speaking || mood == Mood.Thinking || mood == Mood.Researching || mood == Mood.Alert || engine.agentActive.value
    val speed = if (worker) 1500 else if (mood == Mood.Listening) 2600 else 4200
    val shift by rememberInfiniteTransition(label = "aura").animateFloat(
        0f, 1f, infiniteRepeatable(tween(speed, easing = LinearEasing), RepeatMode.Restart), label = "shift"
    )
    val strength by animateFloatAsState(
        if (!on) 0f else if (worker) 1f else if (mood == Mood.Listening) 0.62f else 0.40f,
        tween(700), label = "strength"
    )
    if (strength <= 0.01f) return
    val colors = listOf(Color.Transparent, c, b, a, Color.White.copy(alpha = 0.9f), a, b, c, Color.Transparent)
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val line = 2.5.dp.toPx(); val halo = 16.dp.toPx()
        val x0 = shift * w; val y0 = shift * h
        val bx = Brush.horizontalGradient(colors, startX = x0, endX = x0 + w * 0.8f, tileMode = TileMode.Repeated)
        val by = Brush.verticalGradient(colors, startY = y0, endY = y0 + h * 0.8f, tileMode = TileMode.Repeated)
        // soft halo first, then the sharp line on top
        drawRect(bx, Offset(0f, 0f), Size(w, halo), alpha = 0.20f * strength)
        drawRect(bx, Offset(0f, h - halo), Size(w, halo), alpha = 0.20f * strength)
        drawRect(by, Offset(0f, 0f), Size(halo, h), alpha = 0.20f * strength)
        drawRect(by, Offset(w - halo, 0f), Size(halo, h), alpha = 0.20f * strength)
        drawRect(bx, Offset(0f, 0f), Size(w, line), alpha = strength)
        drawRect(bx, Offset(0f, h - line), Size(w, line), alpha = strength)
        drawRect(by, Offset(0f, 0f), Size(line, h), alpha = strength)
        drawRect(by, Offset(w - line, 0f), Size(line, h), alpha = strength)
    }
}
