package com.arina.assistant.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arina.assistant.Brand
import kotlinx.coroutines.delay

/** A glowing orb with soft rings drifting outward. [reveal] 0..1 grows it in; it keeps breathing afterwards. */
@Composable
fun OrbMark(reveal: Float, modifier: Modifier = Modifier) {
    val loop = rememberInfiniteTransition(label = "orb")
    val phase by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val breath by loop.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2
        // soft halo
        drawCircle(
            Brush.radialGradient(listOf(ArinaColors.Glow.copy(alpha = 0.40f * reveal), Color.Transparent), center = c, radius = r),
            radius = r, center = c
        )
        // three rings, each a third of a cycle apart, growing and fading
        for (i in 0 until 3) {
            val t = (phase + i / 3f) % 1f
            drawCircle(
                ArinaColors.Glow.copy(alpha = (1f - t) * 0.45f * reveal),
                radius = r * (0.30f + 0.70f * t) * reveal.coerceAtLeast(0.2f),
                center = c, style = Stroke(width = 1.5.dp.toPx())
            )
        }
        // the core
        val core = r * 0.22f * breath * reveal
        drawCircle(
            Brush.radialGradient(listOf(Color.White, ArinaColors.Glow, ArinaColors.Deep), center = c, radius = core.coerceAtLeast(1f)),
            radius = core, center = c
        )
    }
}

/** The opening animation: the orb grows in, the name appears letter by letter, then the company. Tap to skip. */
@Composable
fun IntroScreen(onFinished: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(2600, easing = LinearEasing))
        delay(500)
        onFinished()
    }
    val orb = FastOutSlowInEasing.transform((p.value / 0.45f).coerceIn(0f, 1f))
    val name = Brand.APP.uppercase()
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF020817), Color(0xFF031428), Color(0xFF020817))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFinished() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OrbMark(orb, Modifier.size(220.dp))
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                name.forEachIndexed { i, ch ->
                    val a = ((p.value - 0.35f - i * 0.05f) / 0.18f).coerceIn(0f, 1f)
                    Text(
                        ch.toString(), color = ArinaColors.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.alpha(a)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "by " + Brand.COMPANY, color = ArinaColors.Muted, fontSize = 13.sp, letterSpacing = 3.sp,
                modifier = Modifier.alpha(((p.value - 0.75f) / 0.2f).coerceIn(0f, 1f))
            )
        }
    }
}
