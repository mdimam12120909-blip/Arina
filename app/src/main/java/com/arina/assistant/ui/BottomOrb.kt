package com.arina.assistant.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What shows over other apps: just the orb, rising from the bottom edge of the screen. It peeks out a little while
 * idle, rises when the mic is on and more while it listens or speaks. A short line appears above it only while
 * it is talking or working. Tap the orb to turn the mic on or off; press and hold to open the app.
 * No edge light and no big panels: it stays light on the screen and on the phone.
 */
@Composable
fun BottomOrb(engine: ArinaEngine, onOpenApp: () -> Unit) {
    val problem = engine.notice.value != null
    val mood = moodOf(engine.status.value, engine.researchActive.value || engine.agentActive.value, problem)
    val micOn = engine.micOn.value
    val shown by animateDpAsState(
        when {
            mood != Mood.Standby -> 62.dp
            micOn -> 44.dp
            else -> 26.dp
        },
        tween(450), label = "rise"
    )
    val line = engine.notice.value ?: if (engine.agentActive.value) engine.agentLine.value else engine.caption.value
    val showLine = line.isNotBlank() &&
        (mood == Mood.Speaking || mood == Mood.Thinking || mood == Mood.Researching || mood == Mood.Alert || engine.agentActive.value)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (showLine) {
            Text(
                line, color = ArinaColors.Text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp))
                    .background(ArinaColors.Surface)
                    .border(1.dp, ArinaColors.Deep, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
        Box(
            Modifier.size(width = 100.dp, height = shown).clipToBounds()
                .pointerInput(Unit) { detectTapGestures(onTap = { engine.onMicTap() }, onLongPress = { onOpenApp() }) }
        ) {
            // the orb is drawn in the middle of a 100 dp square; lift it so its top edge is at the top of this window
            OrbView(
                persona = engine.persona.value, mood = mood, level = engine.level.floatValue,
                modifier = Modifier.size(100.dp).align(Alignment.TopCenter).offset(y = (-19).dp)
            )
        }
    }
}
