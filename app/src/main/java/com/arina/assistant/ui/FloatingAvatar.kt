package com.arina.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The floating capsule: a round orb with the current mood, a short status line and two small buttons.
 * Tap the orb to turn the mic on or off. Drag the capsule anywhere. English text only.
 */
@Composable
fun FloatingAvatar(
    engine: ArinaEngine,
    onTap: () -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit
) {
    val shape = RoundedCornerShape(40.dp)
    val problem = engine.notice.value != null
    val mood = moodOf(engine.status.value, engine.researchActive.value, problem)
    val line = engine.notice.value ?: engine.caption.value
    val micOn = engine.micOn.value
    val accent = if (micOn) Color(0xFF3DDC97) else ArinaColors.Muted

    Row(
        Modifier
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xF00B2250), Color(0xF2041020))))
            .border(1.5.dp, Brush.linearGradient(listOf(ArinaColors.Glow, ArinaColors.Deep.copy(alpha = 0.4f), ArinaColors.Glow)), shape)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(ArinaColors.Background)
                .border(1.5.dp, accent, CircleShape)
        ) {
            OrbView(
                persona = engine.persona.value,
                mood = mood,
                level = engine.level.floatValue,
                modifier = Modifier.fillMaxSize().clickable(onClick = onTap)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.widthIn(min = 96.dp, max = 150.dp)) {
            Text(
                engine.persona.value.displayName.uppercase() + "  ·  " + mood.label,
                color = ArinaColors.Glow, fontSize = 9.sp, letterSpacing = 1.2.sp, maxLines = 1
            )
            Text(
                line.ifBlank { if (micOn) "Listening…" else "Tap the orb to talk" },
                color = ArinaColors.Text, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            MiniButton(Icons.Outlined.OpenInNew, "Open AriAuri app", onOpenApp)
            MiniButton(Icons.Outlined.Close, "Turn off AriAuri", onClose)
        }
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun MiniButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(26.dp).clip(CircleShape)
            .background(ArinaColors.Background.copy(alpha = 0.6f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = ArinaColors.Glow, modifier = Modifier.size(16.dp))
    }
}
