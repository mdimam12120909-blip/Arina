package com.arina.assistant.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.arina.assistant.R

enum class ArinaState { Idle, Listening, Thinking, Speaking }

/**
 * Holographic avatar: portrait + breathing motion + blue rim glow + rotating rings.
 * Glow intensity reacts to state (speaking pulses faster/brighter).
 */
@Composable
fun ArinaAvatar(state: ArinaState, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "avatar")
    val speed = when (state) {
        ArinaState.Idle -> 3600
        ArinaState.Listening -> 2200
        ArinaState.Thinking -> 1400
        ArinaState.Speaking -> 700
    }
    val pulse by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(speed, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val spin by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "spin"
    )

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Glow rings behind the avatar
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height * 0.45f)
            val r = size.minDimension * 0.5f
            drawCircle(
                Brush.radialGradient(
                    listOf(ArinaColors.Primary.copy(alpha = 0.25f + 0.25f * pulse), Color.Transparent),
                    center = c, radius = r * 1.3f
                ),
                radius = r * 1.3f, center = c
            )
            for (i in 0..2) {
                val rr = r * (0.85f + i * 0.12f)
                drawCircle(
                    ArinaColors.Glow.copy(alpha = 0.18f + 0.1f * pulse),
                    radius = rr, center = c, style = Stroke(width = 2f + i)
                )
            }
            // rotating arc segments
            val rr = r * 1.05f
            for (k in 0..3) {
                drawArc(
                    ArinaColors.Primary.copy(alpha = 0.7f),
                    startAngle = spin + k * 90f, sweepAngle = 28f, useCenter = false,
                    topLeft = Offset(c.x - rr, c.y - rr),
                    size = androidx.compose.ui.geometry.Size(rr * 2, rr * 2),
                    style = Stroke(width = 5f)
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.arina_avatar),
            contentDescription = "Arina avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize(0.92f)
                .scale(1f + 0.012f * pulse)
                .alpha(0.96f)
        )
        // blue holographic tint + bottom fade into background
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.6f to Color.Transparent,
                    1f to ArinaColors.Background
                )
            )
            drawRect(ArinaColors.Deep.copy(alpha = 0.10f + 0.06f * pulse))
        }
    }
}
