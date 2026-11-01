package com.arina.assistant.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arina.assistant.Brand
import kotlinx.coroutines.delay

/**
 * The opening animation, kept short and calm: the logo's ring draws itself, the dot steps into the doorway,
 * then the name and the company appear. Tap to skip.
 */
@Composable
fun IntroScreen(onFinished: () -> Unit) {
    val ring = remember { Animatable(0f) }
    val dot = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        lift.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        ring.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        dot.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        name.animateTo(1f, tween(500, easing = LinearEasing))
        delay(650)
        onFinished()
    }
    Box(
        Modifier.fillMaxSize().background(ArinaColors.Background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFinished() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LogoTile(
                Modifier.size(132.dp).alpha(lift.value).scale(0.86f + 0.14f * lift.value),
                ring = ring.value, dot = dot.value
            )
            Spacer(Modifier.height(30.dp))
            Text(
                Brand.APP, color = ArinaColors.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp, modifier = Modifier.alpha(name.value)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "by " + Brand.COMPANY, color = ArinaColors.Muted, fontSize = 13.sp,
                modifier = Modifier.alpha(name.value)
            )
        }
    }
}
