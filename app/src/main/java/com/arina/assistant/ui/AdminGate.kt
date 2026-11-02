package com.arina.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arina.assistant.update.Updater

/** Shown to an admin until the face / screen-lock check passes. */
@Composable
fun AdminGate(vm: ArinaEngine, onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier.fillMaxSize().background(ArinaColors.Background).padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Admin Console is locked", color = ArinaColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Confirm it is you with your face or fingerprint.", color = ArinaColors.Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        vm.notice.value?.let { Spacer(Modifier.height(8.dp)); Text(it, color = ArinaColors.Pink, fontSize = 12.sp, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(20.dp))
        Text(
            "Unlock", color = ArinaColors.Background, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(ArinaColors.Glow).clickable { onUnlock() }.padding(horizontal = 28.dp, vertical = 12.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text("Sign out", color = ArinaColors.Muted, fontSize = 13.sp, modifier = Modifier.clickable { vm.signOut() }.padding(8.dp))
    }
}

/** "A new version is ready" with an Update button. A must-update release cannot be dismissed. */
@Composable
fun UpdatePrompt(vm: ArinaEngine) {
    val r = vm.update.value ?: return
    if (vm.updateDismissed.value && !r.force) return
    val ctx = LocalContext.current
    val busy = vm.updateProgress.floatValue >= 0f
    AlertDialog(
        onDismissRequest = { if (!r.force && !busy) vm.updateDismissed.value = true },
        containerColor = ArinaColors.Surface,
        title = { Text("A new version is ready", color = ArinaColors.Text, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(r.notes.ifBlank { "Update Entrion to get the latest improvements." }, color = ArinaColors.Text, fontSize = 14.sp, lineHeight = 20.sp)
                if (r.force) Text("This update is required to keep using Entrion.", color = ArinaColors.Muted, fontSize = 12.sp)
                if (busy) {
                    val p = vm.updateProgress.floatValue
                    if (p >= 2f) Text("Opening the installer… tap Install.", color = ArinaColors.Muted, fontSize = 12.sp)
                    else {
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.padding(top = 4.dp), color = ArinaColors.Glow)
                        Text("Downloading… ${(p * 100).toInt()}%", color = ArinaColors.Muted, fontSize = 12.sp)
                    }
                }
                if (vm.updateError.value.isNotBlank()) Text(vm.updateError.value, color = ArinaColors.Pink, fontSize = 12.sp)
                if (!Updater.canInstall(ctx) && !busy) Text("Android will first ask you to allow installs from Entrion. Allow it, then tap Update again.", color = ArinaColors.Muted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = vm.updateProgress.floatValue < 0f || vm.updateProgress.floatValue >= 2f, onClick = {
                if (!Updater.canInstall(ctx)) runCatching { ctx.startActivity(Updater.installPermissionIntent(ctx)) }
                else vm.startUpdate { f -> runCatching { Updater.install(ctx, f) } }
            }) { Text("Update", color = ArinaColors.Glow, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            if (!r.force && !busy) TextButton(onClick = { vm.updateDismissed.value = true }) { Text("Later", color = ArinaColors.Muted) }
        }
    )
}
