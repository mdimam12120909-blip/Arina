package com.arina.assistant.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arina.assistant.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val IntroBg = Color(0xFFF7F8FD)
private val IntroInk = Color(0xFF0E1330)

/**
 * The opening animation: always white, over in about a second and a half. A ripple spreads, the Entrion mark
 * pops in and its voice bars pulse, "entrion" appears letter by letter, then "FROM" and the Pravat logo rise
 * in at the bottom. Tap to skip.
 */
@Composable
fun IntroScreen(onFinished: () -> Unit) {
    val pop = remember { Animatable(0f) }
    val bars = remember { Animatable(0.35f) }
    val ripple = remember { Animatable(0f) }
    val letters = remember { List(7) { Animatable(0f) } }
    val fromA = remember { Animatable(0f) }
    val out = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { ripple.animateTo(1f, tween(850, easing = FastOutSlowInEasing)) }
            launch { pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)) }
            launch { delay(250); bars.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)) }
            letters.forEachIndexed { i, a -> launch { delay(380L + i * 35L); a.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) } }
            launch { delay(800); fromA.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
        }
        delay(250)
        out.animateTo(0f, tween(200, easing = LinearEasing))
        onFinished()
    }
    Box(
        Modifier.fillMaxSize().background(IntroBg).alpha(out.value)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFinished() },
        contentAlignment = Alignment.Center
    ) {
        // soft colour glows
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(EntrionBlue.copy(alpha = 0.22f), Color.Transparent), Offset(size.width * 0.2f, size.height * 0.05f), size.width * 0.7f),
                radius = size.width * 0.7f, center = Offset(size.width * 0.2f, size.height * 0.05f)
            )
            drawCircle(
                Brush.radialGradient(listOf(EntrionViolet.copy(alpha = 0.18f), Color.Transparent), Offset(size.width * 1.0f, size.height * 0.4f), size.width * 0.6f),
                radius = size.width * 0.6f, center = Offset(size.width * 1.0f, size.height * 0.4f)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension * (0.35f + 0.65f * ripple.value)
                    drawCircle(
                        EntrionIndigo.copy(alpha = 0.35f * (1f - ripple.value)), radius = r * 1.45f, center = center,
                        style = Stroke(width = 3f)
                    )
                }
                EntrionMark(
                    Modifier.size(104.dp).alpha(pop.value.coerceIn(0f, 1f)).scale(0.6f + 0.4f * pop.value),
                    bars = bars.value
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                "entrion".forEachIndexed { i, ch ->
                    Text(
                        ch.toString(), color = IntroInk, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp,
                        modifier = Modifier.graphicsLayer { alpha = letters[i].value; translationY = (1f - letters[i].value) * 14.dp.toPx() }
                    )
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 48.dp)
                .alpha(fromA.value).graphicsLayer { translationY = (1f - fromA.value) * 18.dp.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("FROM", color = IntroInk.copy(alpha = 0.45f), fontSize = 10.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(painterResource(R.drawable.pv_mark), null, Modifier.height(40.dp))
                Image(painterResource(R.drawable.pv_word), null, Modifier.height(26.dp))
            }
        }
    }
}
