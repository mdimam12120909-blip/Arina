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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.arina.assistant.R

/**
 * Arina's portrait with a slow breathing motion, a pulsing face glow (stronger while she
 * listens or speaks) and a sweeping scan line. [compact] = square face crop for the floating window.
 */
@Composable
fun AvatarView(status: Status, level: Float, modifier: Modifier = Modifier, compact: Boolean = false) {
    val infinite = rememberInfiniteTransition(label = "avatar")
    val breathe by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.25f, targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val scan by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5600, easing = LinearEasing)),
        label = "scan"
    )
    val boost by animateFloatAsState(
        targetValue = when (status) {
            Status.Speaking -> 0.5f
            Status.Listening -> 0.35f
            Status.Thinking -> 0.2f
            Status.Idle -> 0f
        },
        animationSpec = tween(500),
        label = "boost"
    )

    Box(modifier.clipToBounds()) {
        Image(
            painter = painterResource(R.drawable.arina_avatar),
            contentDescription = "Arina",
            contentScale = ContentScale.Crop,
            alignment = if (compact) BiasAlignment(0f, -0.8f) else Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = breathe
                    scaleY = breathe
                    transformOrigin = TransformOrigin(0.5f, if (compact) 0.5f else 0.3f)
                }
        )
        Canvas(Modifier.fillMaxSize()) {
            val glow = (pulse * 0.6f + boost + level * 0.4f).coerceIn(0f, 1f)
            drawRect(
                Brush.radialGradient(
                    listOf(ArinaColors.Glow.copy(alpha = glow * 0.32f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * (if (compact) 0.45f else 0.28f)),
                    radius = size.width * 0.85f
                )
            )
            val y = size.height * (if (compact) 1f else 0.75f) * scan
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, ArinaColors.Glow.copy(alpha = 0.16f), Color.Transparent),
                    startY = y - 50f, endY = y + 50f
                ),
                topLeft = Offset(0f, y - 50f),
                size = Size(size.width, 100f)
            )
            if (!compact) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, ArinaColors.Background),
                        startY = size.height * 0.55f, endY = size.height
                    )
                )
                drawRect(
                    Brush.verticalGradient(
                        listOf(ArinaColors.Background.copy(alpha = 0.55f), Color.Transparent),
                        startY = 0f, endY = size.height * 0.15f
                    )
                )
            }
        }
    }
}
