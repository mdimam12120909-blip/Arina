package com.arina.assistant.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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

private val ORB_SIZE = 190.dp

/**
 * What shows over other apps. When the agent wakes (or speaks, listens, works) a small sound plays and the orb
 * rises from the bottom edge to nearly the size it has inside the app. Two seconds after things go quiet it
 * melts into the screen. A short line appears above it while the agent talks or works.
 * Tap the orb to turn the mic on or off; press and hold to open the app.
 */
@Composable
fun BottomOrb(engine: ArinaEngine, onOpenApp: () -> Unit) {
    val problem = engine.notice.value != null
    val mood = moodOf(engine.status.value, engine.researchActive.value || engine.agentActive.value, problem)
    val micOn = engine.micOn.value
    // passive listening does not keep the orb out; it comes when the agent hears you, talks or works
    val busy = mood == Mood.Speaking || mood == Mood.Thinking || mood == Mood.Researching || mood == Mood.Alert
    // glow.intValue changes whenever the floating orb is switched on or tapped awake
    val wake = engine.glow.intValue

    var expanded by remember { mutableStateOf(true) }
    LaunchedEffect(busy, wake) {
        if (busy) expanded = true
        else {
            expanded = true
            delay(2200)
            expanded = false
        }
    }
    val p by animateFloatAsState(
        if (expanded) 1f else 0f,
        tween(if (expanded) 800 else 1100, easing = FastOutSlowInEasing), label = "orbIn"
    )
    var lastExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded != lastExpanded) {
            if (expanded) Cue.appear() else Cue.fade()
            lastExpanded = expanded
        }
    }

    val line = engine.notice.value ?: if (engine.agentActive.value) engine.agentLine.value else engine.caption.value
    val showLine = expanded && line.isNotBlank() &&
        (mood == Mood.Speaking || mood == Mood.Thinking || mood == Mood.Researching || mood == Mood.Alert || engine.agentActive.value)
    val density = LocalDensity.current.density

    if (p <= 0.001f && !expanded) {
        // fully melted: only a small handle stays (tap it to bring the orb back); nothing else blocks the app underneath
        Box(
            Modifier.size(width = 80.dp, height = 18.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { engine.glow.intValue++ }, onLongPress = { onOpenApp() }) },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                Modifier.padding(bottom = 4.dp).size(width = 38.dp, height = 4.dp).clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Color(0x552F8BFF), Color(0xCC7FDBFF), Color(0x55EC4899))))
            )
        }
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (showLine) {
            Text(
                line, color = ArinaColors.Text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp))
                    .background(ArinaColors.Surface)
                    .border(1.dp, ArinaColors.Deep, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(4.dp))
        }
        Box(
            Modifier.size(ORB_SIZE)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { engine.onMicTap(); engine.glow.intValue++ }, onLongPress = { onOpenApp() })
                }
        ) {
            OrbView(
                persona = engine.persona.value, mood = mood, level = engine.level.floatValue,
                dissolve = 1f - p,
                modifier = Modifier.size(ORB_SIZE).graphicsLayer {
                    val s = 0.45f + 0.55f * p
                    scaleX = s; scaleY = s
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 110f * density      // rises from the bottom edge
                }
            )
        }
    }
}

/**
 * A thin living line along the bottom of the screen, shown whenever the agent is on: it tells you at a glance that
 * an AI agent is working on this phone. Calm when waiting, brighter and faster while it listens, talks or works.
 * It never takes touches.
 */
@Composable
fun AgentLine(engine: ArinaEngine) {
    val problem = engine.notice.value != null
    val mood = moodOf(engine.status.value, engine.researchActive.value || engine.agentActive.value, problem)
    val on = engine.micOn.value || mood != Mood.Standby
    if (!on) { Spacer(Modifier.size(1.dp)); return }
    val female = engine.persona.value == Persona.Arina
    val a = if (female) Color(0xFFFF9AD5) else Color(0xFF7FDBFF)
    val b = if (female) Color(0xFFEC4899) else Color(0xFF2F8BFF)
    val c = if (female) Color(0xFF9D4EDD) else Color(0xFF1D4ED8)
    val speed = when (mood) { Mood.Standby -> 3600; Mood.Listening -> 2200; else -> 1300 }
    val shift by rememberInfiniteTransition(label = "line").animateFloat(
        0f, 1f, infiniteRepeatable(tween(speed, easing = LinearEasing), RepeatMode.Restart), label = "shift"
    )
    val strength by animateFloatAsState(if (mood == Mood.Standby) 0.55f else 1f, tween(500), label = "strength")
    Canvas(Modifier.fillMaxWidth().height(3.dp)) {
        val w = size.width
        val x0 = shift * w
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, a, b, c, Color.Transparent),
                startX = x0, endX = x0 + w, tileMode = TileMode.Repeated
            ),
            alpha = strength
        )
    }
}
