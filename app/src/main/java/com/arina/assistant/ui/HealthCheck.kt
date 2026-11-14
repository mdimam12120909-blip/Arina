package com.arina.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arina.assistant.device.ArinaAccessibilityService
import com.arina.assistant.device.ArinaNotificationListener
import com.arina.assistant.permissions.PermissionCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Check(val ok: Boolean, val title: String, val hint: String)

/** One tap checks everything Entrion depends on and says exactly what to fix. */
@Composable
fun HealthCheckCard(vm: ArinaEngine) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val results = remember { mutableStateListOf<Check>() }
    var ran by remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Check everything", color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("Tests the AI service, internet, permissions and connections, and tells you what to fix.", color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 17.sp)
        PillButton(if (ran) "Check again" else "Run check") {
            scope.launch {
                val list = withContext(Dispatchers.IO) {
                    val out = ArrayList<Check>()
                    val online = runCatching { vm.deviceNow() }.getOrDefault("").let { !it.contains("offline") }
                    out += Check(online, "Internet", if (online) "Connected." else "No internet. Entrion needs it to think and talk.")
                    out += Check(vm.geminiKeyCount > 0, "AI service", if (vm.geminiKeyCount > 0) "Ready." else "Not ready. Sign in and try again.")
                    val acc = ArinaAccessibilityService.instance != null
                    val accOn = PermissionCenter.accessibilityEnabled(ctx)
                    out += Check(acc, "Device control", when { acc -> "Working."; accOn -> "Switched on but not running. Turn Entrion's Accessibility off and on once."; else -> "Off. Turn it on in Settings, Accessibility, Entrion." })
                    val mic = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    out += Check(mic, "Microphone", if (mic) "Allowed." else "Not allowed. Entrion cannot hear you.")
                    val over = android.provider.Settings.canDrawOverlays(ctx)
                    out += Check(over, "Show over other apps", if (over) "Allowed: cards and the orb can appear over any app." else "Off: cards only show inside Entrion.")
                    val notif = ArinaNotificationListener.instance != null
                    out += Check(notif, "Notification access", if (notif) "Working." else "Off. Entrion cannot read notifications for you.")
                    val yt = vm.uiPrefs.getBoolean("yt_connected", false)
                    out += Check(yt, "YouTube", if (yt) "Connected." else "Not connected (optional). Connectors, Connect.")
                    val mcp = vm.mcp.servers().size
                    if (mcp > 0) out += Check(vm.mcp.cached().isNotEmpty(), "MCP servers", "$mcp connected, ${vm.mcp.cached().size} tools found.")
                    out
                }
                results.clear(); results.addAll(list); ran = true
            }
        }
        results.forEach { c ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(if (c.ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, null, tint = if (c.ok) Color(0xFF3DDC84) else Color(0xFFFFB020), modifier = Modifier.size(18.dp).padding(top = 1.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(c.title, color = ArinaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(c.hint, color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}
