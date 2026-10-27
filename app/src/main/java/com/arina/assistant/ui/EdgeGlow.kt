package com.arina.assistant.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** A soft light-blue glow around the edge of the whole display. Fades in, holds, fades out: 4 seconds in total. */
@Composable
fun EdgeGlow(modifier: Modifier = Modifier) {
    val level = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        level.animateTo(1f, tween(700, easing = LinearEasing))
        delay(2300)
        level.animateTo(0f, tween(1000, easing = LinearEasing))
    }
    Canvas(modifier.fillMaxSize()) {
        val a = level.value
        val corner = CornerRadius(46.dp.toPx())
        val tint = Color(0xFF4DB8FF)
        for (i in 1..18) {
            drawRoundRect(
                tint.copy(alpha = (0.050f * a)),
                cornerRadius = corner,
                style = Stroke(width = i * 3.2.dp.toPx())
            )
        }
        drawRoundRect(Color(0xFFBFE6FF).copy(alpha = 0.9f * a), cornerRadius = corner, style = Stroke(width = 3.dp.toPx()))
    }
}
