package com.arina.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
 * The only thing shown while Arina runs in the background: a 1:1 square portrait.
 * Tap the portrait to talk. Drag it anywhere. English text only.
 */
@Composable
fun FloatingAvatar(
    engine: ArinaEngine,
    onTap: () -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    val status = engine.status.value
    val line = engine.notice.value ?: engine.caption.value

    Box(
        Modifier
            .size(170.dp)
            .clip(shape)
            .background(ArinaColors.Background)
            .border(2.dp, Brush.sweepGradient(listOf(ArinaColors.Glow, ArinaColors.Deep, ArinaColors.Glow)), shape)
    ) {
        AvatarView(
            status = status,
            level = engine.level.floatValue,
            compact = true,
            modifier = Modifier.fillMaxSize().clickable(onClick = onTap)
        )

        Row(Modifier.align(Alignment.TopStart).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (engine.micOn.value) Color(0xFF3DDC97) else ArinaColors.Muted)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (engine.micOn.value) "MIC ON" else "MIC OFF",
                color = if (engine.micOn.value) Color(0xFF3DDC97) else ArinaColors.Muted,
                fontSize = 9.sp
            )
        }

        Row(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
            MiniButton(Icons.Outlined.OpenInNew, "Open Arina app", onOpenApp)
            Box(Modifier.size(6.dp))
            MiniButton(Icons.Outlined.Close, "Turn off Arina", onClose)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, ArinaColors.Background.copy(alpha = 0.92f))))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(statusLabel(status), color = ArinaColors.Glow, fontSize = 10.sp)
            if (line.isNotBlank()) {
                Text(line, color = ArinaColors.Text, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
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
