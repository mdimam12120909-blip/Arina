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

private fun card(strong: Boolean = false): Modifier {
    val dark = ArinaColors.dark
    val shape = RoundedCornerShape(22.dp)
    val edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.30f else 0.95f), Color.White.copy(alpha = if (dark) 0.07f else 0.35f)))
    return Modifier.clip(shape)
        .background(if (strong) ArinaColors.Glow.copy(alpha = 0.22f) else Color.White.copy(alpha = if (dark) 0.09f else 0.58f))
        .border(1.dp, if (strong) Brush.verticalGradient(listOf(ArinaColors.Glow, ArinaColors.Glow.copy(alpha = 0.3f))) else edge, shape)
}

@Composable
private fun Chip(text: String, color: Color, filled: Boolean = false, onClick: () -> Unit) {
    Text(
        text, color = if (filled) Color.White else ArinaColors.Text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .then(if (filled) Modifier.background(Brush.linearGradient(listOf(if (color == Bad) Color(0xFFE5484D) else Color(0xFF4F7DFF), if (color == Bad) Color(0xFFFF7A59) else Color(0xFF8F5BFF)))) else Modifier.border(1.dp, Color.White.copy(alpha = if (ArinaColors.dark) 0.3f else 0.9f), RoundedCornerShape(50)).background(Color.White.copy(alpha = if (ArinaColors.dark) 0.08f else 0.5f)))
            .clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 8.dp)
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
        // "Next up": the routine that fires soonest, with the time left.
        run {
            val now = System.currentTimeMillis()
            val up = vm.routines.filter { it.enabled }.mapNotNull { r -> r.nextAfter(now)?.let { r to it } }.minByOrNull { it.second }
            if (up != null) {
                val mins = ((up.second - now) / 60000L).coerceAtLeast(0)
                val left = if (mins >= 60) "in ${mins / 60} h ${mins % 60} min" else "in $mins min"
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF4F7DFF), Color(0xFF8F5BFF))))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("NEXT UP", color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, letterSpacing = 2.sp)
                        Text(up.first.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(left, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                    Text(up.first.timeText(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        var showForm by remember { mutableStateOf(vm.routines.isEmpty()) }
        Row(
            Modifier.fillMaxWidth().then(card()).clickable { showForm = !showForm }.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF4F7DFF), Color(0xFF8F5BFF)))), contentAlignment = Alignment.Center) {
                Text(if (showForm) "–" else "+", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(14.dp))
            Text("New routine", color = ArinaColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        if (showForm) Column(Modifier.fillMaxWidth().then(card()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassField(title, { title = it }, "Title (Study, Gym, Sleep…)")
            GlassField(note, { note = it }, "What to say (optional)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("%02d:%02d".format(hour, minute), color = ArinaColors.Text, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
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
                            .background(if (on) Brush.linearGradient(listOf(Color(0xFF4F7DFF), Color(0xFF8F5BFF))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                            .border(1.dp, if (on) Color.Transparent else ArinaColors.Muted.copy(alpha = 0.5f), CircleShape)
                            .clickable { days = days xor (1 shl i) },
                        contentAlignment = Alignment.Center
                    ) { Text(n, color = if (on) Color.White else ArinaColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Chip("Add routine", accent, filled = true) {
                if (title.isNotBlank() && days and 127 != 0) {
                    vm.saveRoutine(Routine(System.currentTimeMillis(), title.trim(), hour, minute, days, note.trim(), true))
                    title = ""; note = ""; showForm = false
                }
            }
        }

        if (vm.routines.isEmpty()) Text("No routines yet.", color = ArinaColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
        else Text("YOUR ROUTINES", color = ArinaColors.Muted, fontSize = 11.sp, letterSpacing = 1.6.sp, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
        vm.routines.forEach { r ->
            Row(Modifier.fillMaxWidth().then(card()).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0x594F7DFF), Color(0x598F5BFF)))), contentAlignment = Alignment.Center) {
                    Text(r.timeText().take(2), color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, color = ArinaColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(r.timeText(), color = ArinaColors.Text, fontSize = 13.sp)
                    Text(vm.daysText(r.days) + if (r.note.isNotBlank()) " · ${r.note}" else "", color = ArinaColors.Muted, fontSize = 12.sp)
                }
                Switch(
                    checked = r.enabled, onCheckedChange = { vm.toggleRoutine(r.id) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF6C7CFF), checkedThumbColor = Color.White)
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
            // Health score: how comfortable the phone is right now (memory, storage, battery).
            val ramUsed = (st.ramTotalMb - st.ramFreeMb).toFloat() / st.ramTotalMb.coerceAtLeast(1)
            val stoUsed = 1f - st.storageFreeGb / st.storageTotalGb.coerceAtLeast(0.1f)
            val bat = st.battery.coerceIn(0, 100) / 100f
            val score = (100f - ramUsed * 35f - stoUsed * 40f - (1f - bat) * 25f).toInt().coerceIn(0, 100)
            val shown by androidx.compose.animation.core.animateFloatAsState(score / 100f, androidx.compose.animation.core.tween(900), label = "health")
            Row(Modifier.fillMaxWidth().then(card()).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val w = 9.dp.toPx()
                        val inset = w / 2f
                        val arc = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                        drawArc(accent.copy(alpha = 0.15f), 0f, 360f, false, androidx.compose.ui.geometry.Offset(inset, inset), arc, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                        drawArc(
                            Brush.sweepGradient(listOf(Color(0xFF4F7DFF), Color(0xFF8F5BFF), Color(0xFF4F7DFF))),
                            -90f, 360f * shown, false, androidx.compose.ui.geometry.Offset(inset, inset), arc,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )
                    }
                    Text("$score", color = ArinaColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("PHONE HEALTH", color = ArinaColors.Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                    Text(if (score >= 75) "Feeling good" else if (score >= 50) "Could be better" else "Needs attention", color = ArinaColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Based on memory, storage and battery.", color = ArinaColors.Muted, fontSize = 11.sp)
                }
            }
        }
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
