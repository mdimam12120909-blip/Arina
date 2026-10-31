package com.arina.assistant.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arina.assistant.health.PhoneHealth
import com.arina.assistant.health.Risk
import com.arina.assistant.routine.Routine

private val Good = Color(0xFF3DDC97)
private val Warn = Color(0xFFFFB84D)
private val Bad = Color(0xFFFF5C6C)

private fun card(strong: Boolean = false) = Modifier
    .clip(RoundedCornerShape(20.dp))
    .background(Brush.verticalGradient(if (strong) listOf(Color(0x550B5FFF), Color(0x33071A33)) else listOf(Color(0x260B5FFF), Color(0x66071A33))))
    .border(1.dp, Brush.linearGradient(listOf(ArinaColors.Glow.copy(alpha = 0.55f), ArinaColors.Deep.copy(alpha = 0.15f))), RoundedCornerShape(20.dp))

@Composable
private fun Chip(text: String, color: Color, filled: Boolean = false, onClick: () -> Unit) {
    Text(
        text, color = if (filled) ArinaColors.Background else color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .then(if (filled) Modifier.background(color) else Modifier.border(1.dp, color, RoundedCornerShape(50)))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

// ---- Routines -------------------------------------------------------------------------------------

@Composable
fun RoutinePanel(vm: ArinaEngine) {
    val accent = accentOf(vm.persona.value)
    val ctx = LocalContext.current
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var hour by remember { mutableIntStateOf(20) }
    var minute by remember { mutableIntStateOf(0) }
    var days by remember { mutableIntStateOf(127) }
    val names = listOf("M", "T", "W", "T", "F", "S", "S")
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = ArinaColors.Text, unfocusedTextColor = ArinaColors.Text,
        focusedBorderColor = accent, unfocusedBorderColor = ArinaColors.Muted
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Entrion reminds you at these times with a notification and its voice. You can also just say: “add a routine, study at 9 pm”.",
            color = ArinaColors.Muted, fontSize = 13.sp, lineHeight = 18.sp
        )
        Column(Modifier.fillMaxWidth().then(card(true)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("NEW ROUTINE", color = accent, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(title, { title = it }, singleLine = true, label = { Text("Title (Study, Gym, Sleep…)") }, colors = fieldColors, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, singleLine = true, label = { Text("What to say (optional)") }, colors = fieldColors, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("%02d:%02d".format(hour, minute), color = ArinaColors.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(14.dp))
                Chip("Set time", accent) {
                    TimePickerDialog(ctx, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                names.forEachIndexed { i, n ->
                    val on = (days shr i) and 1 == 1
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .background(if (on) accent else Color.Transparent)
                            .border(1.dp, accent, CircleShape)
                            .clickable { days = days xor (1 shl i) },
                        contentAlignment = Alignment.Center
                    ) { Text(n, color = if (on) ArinaColors.Background else ArinaColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Chip("Add routine", accent, filled = true) {
                if (title.isNotBlank() && days and 127 != 0) {
                    vm.saveRoutine(Routine(System.currentTimeMillis(), title.trim(), hour, minute, days, note.trim(), true))
                    title = ""; note = ""
                }
            }
        }

        if (vm.routines.isEmpty()) Text("No routines yet.", color = ArinaColors.Muted, fontSize = 13.sp)
        vm.routines.forEach { r ->
            Row(Modifier.fillMaxWidth().then(card(r.enabled)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.timeText() + "  " + r.title, color = ArinaColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(vm.daysText(r.days) + if (r.note.isNotBlank()) " · ${r.note}" else "", color = ArinaColors.Muted, fontSize = 12.sp)
                }
                Switch(
                    checked = r.enabled, onCheckedChange = { vm.toggleRoutine(r.id) },
                    colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = ArinaColors.Background)
                )
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Outlined.Close, "Delete", tint = ArinaColors.Muted, modifier = Modifier.size(20.dp).clickable { vm.deleteRoutine(r.id) })
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---- Phone health ---------------------------------------------------------------------------------

@Composable
fun HealthPanel(vm: ArinaEngine) {
    val accent = accentOf(vm.persona.value)
    LaunchedEffect(Unit) { vm.refreshStats() }
    val st = vm.deviceStats.value

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (st != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("MEMORY", "${(st.ramTotalMb - st.ramFreeMb) * 100 / st.ramTotalMb.coerceAtLeast(1)}%", "${st.ramFreeMb} MB free", accent, Modifier.weight(1f))
                Stat("STORAGE", "%.0f GB".format(st.storageFreeGb), "free of %.0f GB".format(st.storageTotalGb), accent, Modifier.weight(1f))
                Stat("BATTERY", "${st.battery}%", "", accent, Modifier.weight(1f))
            }
        }

        SectionLabel("CLEAN AND OPTIMISE", accent)
        Column(Modifier.fillMaxWidth().then(card()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Android does not let any app clear other apps' cache by itself. Entrion cleans its own cache and takes you straight to the right system pages for the rest.",
                color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 17.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Clean Entrion cache", accent, filled = true) { vm.cleanCache() }
                Chip("Storage", accent) { vm.openIntent(android.content.Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Battery", accent) { vm.openIntent(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                Chip("Running apps", accent) { vm.openIntent(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS)) }
            }
        }

        SectionLabel("APP CHECK", accent)
        Column(Modifier.fillMaxWidth().then(card()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Looks at every app you installed: where it came from and what powerful things it can do (read your screen, SMS, notifications, hide itself…). This is a careful check, not an antivirus engine, so a flagged app is only one that deserves a second look.",
                color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 17.sp
            )
            Chip(if (vm.healthScanning.value) "Scanning…" else "Scan apps now", accent, filled = true) { vm.scanHealth() }
        }
        if (vm.healthMessage.value.isNotBlank()) Text(vm.healthMessage.value, color = ArinaColors.Text, fontSize = 13.sp)

        vm.healthFindings.forEach { f ->
            val color = when (f.risk) { Risk.High -> Bad; Risk.Medium -> Warn; Risk.Low -> ArinaColors.Muted }
            Column(Modifier.fillMaxWidth().then(card()).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(20.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.label, color = ArinaColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(f.risk.label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                }
                Text(f.pkg, color = ArinaColors.Muted, fontSize = 11.sp)
                f.reasons.forEach { Text("• $it", color = ArinaColors.Text, fontSize = 12.sp, lineHeight = 16.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Chip("Delete app", Bad, filled = true) { vm.openIntent(PhoneHealth.uninstallIntent(f.pkg)) }
                    Chip("App info", accent) { vm.openIntent(PhoneHealth.appInfoIntent(f.pkg)) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionLabel(text: String, accent: Color) {
    Text(text, color = accent, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Stat(label: String, value: String, sub: String, accent: Color, modifier: Modifier) {
    Column(modifier.then(card()).padding(12.dp)) {
        Text(label, color = ArinaColors.Muted, fontSize = 10.sp, letterSpacing = 2.sp)
        Text(value, color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (sub.isNotBlank()) Text(sub, color = ArinaColors.Muted, fontSize = 10.sp)
    }
}
