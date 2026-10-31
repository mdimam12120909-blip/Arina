package com.arina.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos

/**
 * The always-there layer drawn over every app (it never takes a touch):
 *  - a coloured light that runs around the screen edge while Entrion listens, thinks, speaks or researches,
 *    in the colour of its mood and growing with the voice level, and a 4 second welcome pulse when it wakes up;
 *  - a small pill at the top that shows what it is doing and the live caption.
 */
@Composable
fun Aura(engine: ArinaEngine) {
    val persona = engine.persona.value
    val mood = moodOf(engine.status.value, engine.researchActive.value || engine.agentActive.value, engine.notice.value != null)
    val level = engine.level.floatValue

    val pulse = remember { Animatable(0f) }
    LaunchedEffect(engine.glow.intValue) {
        if (engine.glow.intValue == 0) return@LaunchedEffect
        pulse.snapTo(0f)
        pulse.animateTo(1f, tween(700))
        kotlinx.coroutines.delay(2300)
        pulse.animateTo(0f, tween(1000))
    }

    val target = when (mood) {
        Mood.Speaking -> 0.9f
        Mood.Thinking -> 0.65f
        Mood.Researching -> 0.55f
        Mood.Listening -> 0.32f + level * 0.5f
        Mood.Alert -> 0.9f
        Mood.Standby -> 0f
    }
    val eased by animateFloatAsState(target, tween(450), label = "aura")
    val a = maxOf(eased, pulse.value * 0.8f)
    val tint by animateColorAsState(moodTint(persona, mood), tween(500), label = "auraTint")

    var time by remember { mutableFloatStateOf(0f) }
    val running = a > 0.01f
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1_000_000_000f }
    }

    Box(Modifier.fillMaxSize()) {
        if (running) Canvas(Modifier.fillMaxSize()) {
            val corner = CornerRadius(46.dp.toPx())
            for (i in 1..16) {
                drawRoundRect(tint.copy(alpha = 0.045f * a), cornerRadius = corner, style = Stroke(width = i * 3.2.dp.toPx()))
            }
            // A bright light running around the edge
            val inset = 2.dp.toPx()
            val path = Path().apply {
                addRoundRect(RoundRect(Rect(inset, inset, size.width - inset, size.height - inset), CornerRadius(44.dp.toPx())))
            }
            val pm = PathMeasure().also { it.setPath(path, false) }
            val len = pm.length
            val span = len * 0.16f
            val head = ((time * (0.18f + 0.2f * a)) % 1f) * len
            fun seg(from: Float, to: Float, w: Float, alpha: Float) {
                val s = Path()
                if (pm.getSegment(from, to, s, true)) drawPath(s, tint.copy(alpha = alpha), style = Stroke(w, cap = StrokeCap.Round))
            }
            for ((w, al) in listOf(10.dp.toPx() to 0.18f, 5.dp.toPx() to 0.5f, 2.2.dp.toPx() to 1f)) {
                val from = head - span
                if (from >= 0f) seg(from, head, w, al * a)
                else { seg(0f, head, w, al * a); seg(len + from, len, w, al * a) }
            }
            drawRoundRect(tint.copy(alpha = 0.55f * a), cornerRadius = corner, style = Stroke(width = 1.6.dp.toPx()))
        }

        val line = engine.notice.value ?: if (engine.agentActive.value) engine.agentLine.value else engine.caption.value
        AnimatedVisibility(
            visible = mood == Mood.Speaking || mood == Mood.Thinking || mood == Mood.Researching || mood == Mood.Alert || engine.agentActive.value,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 34.dp)
        ) {
            Row(
                Modifier.widthIn(max = 340.dp).clip(RoundedCornerShape(50))
                    .background(Color(0xE6020817))
                    .border(1.2.dp, tint.copy(alpha = 0.9f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
                Spacer(Modifier.width(8.dp))
                Text(mood.label, color = tint, fontSize = 10.sp, letterSpacing = 2.sp)
                if (line.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(line, color = ArinaColors.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
