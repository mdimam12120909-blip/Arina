package com.arina.assistant.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arina.assistant.permissions.PermItem
import com.arina.assistant.permissions.PermKind
import com.arina.assistant.permissions.PermissionCenter

private enum class Page(val title: String) {
    Home("Home"), Chat("Chat"), Memory("Memory"), Routine("Routine"), Health("Phone Health"), Soul("Soul"), Settings("Settings")
}

private val Good = Color(0xFF3DDC97)
private val Warn = Color(0xFFFFB84D)

private fun Modifier.glass(shape: Shape = RoundedCornerShape(20.dp), strong: Boolean = false): Modifier =
    this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                if (strong) listOf(Color(0x550B5FFF), Color(0x33071A33))
                else listOf(Color(0x260B5FFF), Color(0x66071A33))
            )
        )
        .border(
            1.dp,
            Brush.linearGradient(listOf(ArinaColors.Glow.copy(alpha = 0.55f), ArinaColors.Deep.copy(alpha = 0.15f))),
            shape
        )

@Composable
fun MainScreen(
    vm: ArinaEngine,
    onMicClick: () -> Unit,
    onPower: () -> Unit,
    onPermission: (PermItem) -> Unit,
    onGrantAll: () -> Unit,
    onAppInfo: () -> Unit
) {
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    BackHandler(enabled = page != Page.Home) { page = Page.Home }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020817), Color(0xFF031428), Color(0xFF020817))))) {
        GridBackdrop(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().imePadding()) {
            Header(vm, page, onPower, { page = Page.Home }, Modifier.statusBarsPadding())
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (page) {
                    Page.Home -> HomePanel(vm, onMicClick) { page = it }
                    Page.Chat -> ChatPanel(vm)
                    Page.Memory -> MemoryPanel(vm)
                    Page.Routine -> RoutinePanel(vm)
                    Page.Health -> HealthPanel(vm)
                    Page.Soul -> SoulPanel(vm)
                    Page.Settings -> SettingsPanel(vm, onPermission, onGrantAll, onAppInfo)
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
        Stage(vm, Modifier.align(Alignment.Center).padding(horizontal = 14.dp))
    }
}

/** A faint technical grid behind everything. */
@Composable
private fun GridBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 38.dp.toPx()
        val line = Color(0xFF28C8FF).copy(alpha = 0.045f)
        var x = 0f
        while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), 1f); y += step }
    }
}

@Composable
private fun Header(vm: ArinaEngine, page: Page, onPower: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val on = vm.overlayOn.value
    val accent = accentOf(vm.persona.value)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (page == Page.Home) {
            LogoMark(Modifier.size(34.dp), accent)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("ARIAURI", color = ArinaColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
                Text(vm.persona.value.displayName.uppercase() + " ACTIVE", color = accent, fontSize = 9.sp, letterSpacing = 3.sp)
            }
        } else {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = ArinaColors.Text) }
            Text(page.title.uppercase(), color = ArinaColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.glass(RoundedCornerShape(50), strong = on).clickable(onClick = onPower)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PowerSettingsNew, "Floating orb", tint = if (on) Good else accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (on) "ORB ON" else "ORB OFF", color = ArinaColors.Text, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun LogoMark(modifier: Modifier = Modifier, accent: Color = ArinaColors.Glow) {
    Canvas(modifier) {
        val r = size.minDimension * 0.30f
        val brush = Brush.linearGradient(listOf(Color(0xFF2FD16F), Color(0xFF3D8BFF)))
        drawCircle(brush, radius = r, center = Offset(size.width * 0.36f, size.height / 2f), style = Stroke(2.6.dp.toPx()))
        drawCircle(brush, radius = r, center = Offset(size.width * 0.64f, size.height / 2f), style = Stroke(2.6.dp.toPx()))
        drawCircle(accent, radius = 2.6.dp.toPx(), center = center)
    }
}

// ---- Home ---------------------------------------------------------------------------------------

private class Node(val label: String, val icon: ImageVector, val color: Color, val page: Page)

private val NODES = listOf(
    Node("MEMORY", Icons.Outlined.Psychology, Color(0xFF28C8FF), Page.Memory),
    Node("CHAT", Icons.Outlined.ChatBubbleOutline, Color(0xFFFF9A3D), Page.Chat),
    Node("ROUTINE", Icons.Outlined.Schedule, Color(0xFFB07BFF), Page.Routine),
    Node("HEALTH", Icons.Outlined.HealthAndSafety, Color(0xFFFF5C8A), Page.Health),
    Node("SOUL", Icons.Outlined.Face, Color(0xFFE6F2FF), Page.Soul),
    Node("SETTINGS", Icons.Outlined.Settings, Color(0xFF2FD16F), Page.Settings)
)

@Composable
private fun HomePanel(vm: ArinaEngine, onMicClick: () -> Unit, go: (Page) -> Unit) {
    val persona = vm.persona.value
    val accent = accentOf(persona)
    val problem = vm.notice.value != null
    val mood = moodOf(vm.status.value, vm.researchActive.value, problem)
    LaunchedEffect(vm.tavilyKey.value) { vm.refreshHeadlines() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TranscriptCard(vm, accent)

        // Orb with the four nodes wired to it
        BoxWithConstraints(Modifier.fillMaxWidth().height(372.dp)) {
            val orbSize = minOf(maxWidth - 104.dp, 280.dp)
            val inf = rememberInfiniteTransition(label = "wire")
            val flow by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "flow")
            Canvas(Modifier.fillMaxSize()) {
                val orbLeft = size.width - orbSize.toPx()
                val endX = orbLeft + orbSize.toPx() * 0.20f
                NODES.forEachIndexed { i, n ->
                    val sy = (28 + 62 * i).dp.toPx()
                    val sx = 112.dp.toPx()
                    val ey = size.height / 2f + (i - 2.5f) * 10.dp.toPx()
                    val c1 = Offset(sx + 60.dp.toPx(), sy)
                    val c2 = Offset(endX - 70.dp.toPx(), ey)
                    val path = Path().apply { moveTo(sx, sy); cubicTo(c1.x, c1.y, c2.x, c2.y, endX, ey) }
                    drawPath(path, n.color.copy(alpha = 0.18f), style = Stroke(5.dp.toPx()))
                    drawPath(path, n.color.copy(alpha = 0.85f), style = Stroke(1.8.dp.toPx()))
                    val u = (flow + i * 0.25f) % 1f
                    val mt = 1f - u
                    val px = mt * mt * mt * sx + 3 * mt * mt * u * c1.x + 3 * mt * u * u * c2.x + u * u * u * endX
                    val py = mt * mt * mt * sy + 3 * mt * mt * u * c1.y + 3 * mt * u * u * c2.y + u * u * u * ey
                    drawCircle(Color.White.copy(alpha = 0.9f), 2.6.dp.toPx(), Offset(px, py))
                }
            }
            NODES.forEachIndexed { i, n ->
                NodeButton(n, Modifier.offset(y = (4 + 62 * i).dp)) { go(n.page) }
            }
            OrbView(
                persona = persona, mood = mood, level = vm.level.floatValue,
                modifier = Modifier.align(Alignment.CenterEnd).size(orbSize)
            )
            Text(
                mood.label + " •", color = accent, fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = orbSize / 2 - 36.dp)
            )
        }

        // Stop and mic
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            val busy = vm.status.value == Status.Speaking || vm.status.value == Status.Thinking || vm.agentActive.value
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .border(1.5.dp, if (busy) Color(0xFFFF5C6C) else ArinaColors.Muted.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .clickable(enabled = busy) { vm.interrupt() }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Stop, "Stop", tint = if (busy) Color(0xFFFF5C6C) else ArinaColors.Muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("STOP", color = if (busy) ArinaColors.Text else ArinaColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
            Spacer(Modifier.width(18.dp))
            MicSwitch(vm.micOn.value, vm.level.floatValue, accent, onMicClick)
            Spacer(Modifier.width(18.dp))
            Text(
                if (vm.micOn.value) "MIC ON" else "MIC OFF",
                color = if (vm.micOn.value) Good else ArinaColors.Muted, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeadlinesCard(vm, accent, Modifier.weight(1.2f))
            TasksCard(vm, accent, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TranscriptCard(vm: ArinaEngine, accent: Color) {
    val lastUser = vm.messages.lastOrNull { it.fromUser }?.text
    val lastAi = vm.notice.value ?: vm.caption.value.ifBlank { vm.messages.lastOrNull { !it.fromUser }?.text.orEmpty() }
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).border(1.5.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("TRANSCRIPT", color = accent, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(vm.persona.value.displayName.uppercase(), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
            Text(lastAi.ifBlank { "—" }, color = if (vm.notice.value != null) Warn else ArinaColors.Text, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 4)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text("YOU", color = ArinaColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
            Text(lastUser ?: "—", color = ArinaColors.Muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2)
        }
    }
}

@Composable
private fun NodeButton(n: Node, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.width(112.dp).height(48.dp).clip(shape)
            .background(Color(0xCC041020))
            .border(1.6.dp, n.color, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).border(1.6.dp, n.color, CircleShape), contentAlignment = Alignment.Center) {
            Icon(n.icon, n.label, tint = n.color, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(n.label, color = ArinaColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
    }
}

@Composable
private fun MicSwitch(micOn: Boolean, level: Float, accent: Color, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "mic")
    val ring by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "ring")
    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension * 0.34f
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = if (micOn) 0.30f + level * 0.4f else 0.10f), Color.Transparent), center = center, radius = base * 1.9f),
                radius = base * 1.9f, center = center
            )
            if (micOn) for (i in 0..1) {
                val p = (ring + i * 0.5f) % 1f
                drawCircle(accent.copy(alpha = (1f - p) * 0.55f), radius = base + p * base * 0.7f, center = center, style = Stroke(2.dp.toPx()))
            }
        }
        Box(
            Modifier.size(54.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(if (micOn) Color(0xFF0A3A7A) else Color(0xFF0A1B38), Color(0xFF031230))))
                .border(2.dp, accent, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, "Microphone", tint = if (micOn) accent else ArinaColors.Muted, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun HeadlinesCard(vm: ArinaEngine, accent: Color, modifier: Modifier) {
    Column(modifier.heightIn(min = 190.dp).glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TODAY HEADLINES", color = ArinaColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Icon(
                Icons.Outlined.Refresh, "Refresh", tint = if (vm.headlinesBusy.value) ArinaColors.Muted else accent,
                modifier = Modifier.size(18.dp).clickable { vm.refreshHeadlines(force = true) }
            )
        }
        when {
            !vm.tavilyKeySet -> Text("Add a free Tavily key in Settings to see headlines.", color = ArinaColors.Muted, fontSize = 12.sp)
            vm.headlines.isEmpty() -> Text(if (vm.headlinesBusy.value) "Loading…" else "No headlines yet.", color = ArinaColors.Muted, fontSize = 12.sp)
            else -> vm.headlines.take(5).forEach { (title, url) ->
                Row(Modifier.fillMaxWidth().clickable { vm.openLink(url, title) }) {
                    Box(Modifier.width(3.dp).height(30.dp).background(accent))
                    Spacer(Modifier.width(8.dp))
                    Text(title, color = ArinaColors.Text, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun TasksCard(vm: ArinaEngine, accent: Color, modifier: Modifier) {
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val tasks = vm.taskList
    val left = tasks.count { !it.done }
    val frac = if (tasks.isEmpty()) 0f else (tasks.size - left).toFloat() / tasks.size
    Column(modifier.heightIn(min = 190.dp).glass(strong = true).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TODAY TASKS", color = ArinaColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("$left left", color = ArinaColors.Muted, fontSize = 10.sp)
            }
            Icon(Icons.Outlined.Add, "Add task", tint = accent, modifier = Modifier.size(20.dp).clickable { adding = !adding })
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(frac).height(4.dp).background(accent))
        }
        if (adding) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it }, singleLine = true,
                placeholder = { Text("New task", fontSize = 12.sp, color = ArinaColors.Muted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.addTaskTyped(draft); draft = ""; adding = false }),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = ArinaColors.Text, unfocusedTextColor = ArinaColors.Text, focusedBorderColor = accent, unfocusedBorderColor = ArinaColors.Muted),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (tasks.isEmpty() && !adding) Text("Nothing yet. Say “add a task…” or tap +.", color = ArinaColors.Muted, fontSize = 12.sp)
        tasks.take(6).forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { vm.toggleTask(t.id) }) {
                Box(Modifier.size(10.dp).clip(CircleShape).border(1.5.dp, accent, CircleShape).background(if (t.done) accent else Color.Transparent))
                Spacer(Modifier.width(8.dp))
                Text(
                    t.text, color = if (t.done) ArinaColors.Muted else ArinaColors.Text, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2,
                    textDecoration = if (t.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Outlined.Close, "Delete", tint = ArinaColors.Muted, modifier = Modifier.size(14.dp).clickable { vm.removeTask(t.id) })
            }
        }
    }
}

@Composable
private fun SoulPanel(vm: ArinaEngine) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Choose who you talk to. Each has its own colour, voice and personality.", color = ArinaColors.Muted, fontSize = 13.sp)
        Persona.entries.forEach { p ->
            val sel = p == vm.persona.value
            val color = accentOf(p)
            val shape = RoundedCornerShape(24.dp)
            Row(
                Modifier.fillMaxWidth().clip(shape)
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = if (sel) 0.28f else 0.08f), Color(0xAA041020))))
                    .border(if (sel) 2.dp else 1.dp, color.copy(alpha = if (sel) 1f else 0.4f), shape)
                    .clickable { vm.switchPersona(p) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OrbView(p, Mood.Standby, 0f, Modifier.size(120.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.displayName.uppercase(), color = ArinaColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    Text(if (p == Persona.Arina) "Female voice · green" else "Male voice · blue", color = color, fontSize = 12.sp)
                    Text(if (sel) "ACTIVE" else "Tap to switch", color = if (sel) Good else ArinaColors.Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                }
            }
        }
    }
}

@Composable
private fun ChatPanel(vm: ArinaEngine) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().background(ArinaColors.Background.copy(alpha = 0.88f))) {
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(vm.messages) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
                    Text(
                        m.text,
                        color = ArinaColors.Text,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .glass(RoundedCornerShape(16.dp), strong = m.fromUser)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message (Bengali or English)", color = ArinaColors.Muted, fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ArinaColors.Text,
                    unfocusedTextColor = ArinaColors.Text,
                    focusedBorderColor = ArinaColors.Glow,
                    unfocusedBorderColor = ArinaColors.Deep,
                    cursorColor = ArinaColors.Glow
                )
            )
            IconButton(onClick = {
                vm.sendText(input)
                input = ""
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = ArinaColors.Glow)
            }
        }
    }
}

@Composable
private fun MemoryPanel(vm: ArinaEngine) {
    var profile by remember { mutableStateOf(vm.profile.value) }
    var newFact by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize()
            .background(ArinaColors.Background.copy(alpha = 0.9f))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Memory", color = ArinaColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Everything here stays on this phone. Arina reads it before every answer.",
            color = ArinaColors.Muted, fontSize = 12.sp
        )

        Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Profile", color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = profile,
                onValueChange = { profile = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                placeholder = { Text("Paste who you are, your projects, goals and how Arina should treat you", color = ArinaColors.Muted, fontSize = 13.sp) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ArinaColors.Text,
                    unfocusedTextColor = ArinaColors.Text,
                    focusedBorderColor = ArinaColors.Glow,
                    unfocusedBorderColor = ArinaColors.Deep,
                    cursorColor = ArinaColors.Glow
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton("Save profile") { vm.saveProfile(profile) }
                Text("${profile.length} / 6000", color = ArinaColors.Muted, fontSize = 12.sp)
            }
            vm.notice.value?.let { Text(it, color = Color(0xFFFFB84D), fontSize = 12.sp) }
        }

        Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Things Arina remembers (${vm.facts.size})", color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (vm.facts.isEmpty()) {
                Text("Nothing yet. Tell Arina to remember something, or add it below.", color = ArinaColors.Muted, fontSize = 12.sp)
            }
            vm.facts.reversed().forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.text, color = ArinaColors.Text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.deleteFact(f.id) }) {
                        Icon(Icons.Outlined.Close, "Delete", tint = ArinaColors.Muted)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newFact,
                    onValueChange = { newFact = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add a fact", color = ArinaColors.Muted, fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ArinaColors.Text,
                        unfocusedTextColor = ArinaColors.Text,
                        focusedBorderColor = ArinaColors.Glow,
                        unfocusedBorderColor = ArinaColors.Deep,
                        cursorColor = ArinaColors.Glow
                    )
                )
                Spacer(Modifier.width(8.dp))
                PillButton("Add") {
                    vm.addFact(newFact)
                    newFact = ""
                }
            }
        }
    }
}

@Composable
private fun KeyField(label: String, value: String, secret: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ArinaColors.Text,
            unfocusedTextColor = ArinaColors.Text,
            focusedBorderColor = ArinaColors.Glow,
            unfocusedBorderColor = ArinaColors.Deep,
            focusedLabelColor = ArinaColors.Glow,
            unfocusedLabelColor = ArinaColors.Muted,
            cursorColor = ArinaColors.Glow
        )
    )
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = ArinaColors.Background,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ArinaColors.Glow)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    )
}


@Composable
private fun SettingsPanel(vm: ArinaEngine, onPermission: (PermItem) -> Unit, onGrantAll: () -> Unit, onAppInfo: () -> Unit) {
    var gemini by remember { mutableStateOf(vm.geminiKey.value) }
    var cartesia by remember { mutableStateOf(vm.cartesiaKey.value) }
    var voice by remember { mutableStateOf(vm.voiceId.value) }
    var tavily by remember { mutableStateOf(vm.tavilyKey.value) }
    var aurionVoice by remember { mutableStateOf(vm.voiceIdAurion.value) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("CONNECTIONS")
        Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Keys are stored only on this phone.", color = ArinaColors.Muted, fontSize = 12.sp)
            KeyField("Gemini API keys (1 to 5, separated by comma)", gemini, true) { gemini = it }
            KeyField("Cartesia API key", cartesia, true) { cartesia = it }
            KeyField("Arina voice ID (Cartesia)", voice, false) { voice = it }
            KeyField("Aurion voice ID (Cartesia)", aurionVoice, false) { aurionVoice = it }
            KeyField("Tavily search key (free, for web research)", tavily, true) { tavily = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Save") { vm.saveKeys(gemini, cartesia, voice, tavily, aurionVoice) }
                PillButton("Test voice") {
                    vm.saveKeys(gemini, cartesia, voice, tavily, aurionVoice)
                    vm.testVoice()
                }
            }
            vm.notice.value?.let { Text(it, color = Warn, fontSize = 12.sp, lineHeight = 17.sp) }
            StatusLine("Gemini (" + vm.geminiKey.value.split(Regex("[,;\\s]+")).count { it.length > 10 } + " keys)", vm.geminiKeySet)
            StatusLine("Cartesia voice (Arina)", vm.cartesiaKeySet)
            StatusLine("Cartesia voice (Aurion)", vm.voiceIdAurion.value.isNotBlank())
            StatusLine("Web research", vm.tavilyKeySet)
        }

        SectionTitle("COMPANION")
        Row(Modifier.fillMaxWidth().glass().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Speak up on its own", color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    "AriAuri notices long scrolling or non-stop phone use (needs Usage access below) and nudges you kindly, at most once every 30 minutes. Routines always remind you.",
                    color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 16.sp
                )
            }
            Switch(
                checked = vm.proactiveOn.value,
                onCheckedChange = { vm.setProactive(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = ArinaColors.Glow, checkedThumbColor = ArinaColors.Background)
            )
        }

        SectionTitle("SKILLS")
        SkillsCard(vm)

        SectionTitle("LISTENING")
        Row(Modifier.fillMaxWidth().glass().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Gemini hears me", color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (vm.geminiEars.value) "Silent listening, no beep. Gemini understands the audio."
                    else "Uses Android's speech recognizer (it makes a start sound).",
                    color = ArinaColors.Muted, fontSize = 12.sp
                )
            }
            Switch(
                checked = vm.geminiEars.value,
                onCheckedChange = { vm.setGeminiEars(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = ArinaColors.Glow, checkedThumbColor = ArinaColors.Background)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("PERMISSIONS", Modifier.weight(1f))
            PillButton("Allow all") { onGrantAll() }
        }
        Text("Turn on what you want Arina to be able to do. You can change any of these later.", color = ArinaColors.Muted, fontSize = 12.sp)

        PermissionCenter.special.forEach { item ->
            PermRow(item, vm.perms[item.id] == true, onPermission)
            if (item.id == "accessibility" && vm.perms[item.id] != true) {
                Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Device control will not switch on?", color = Warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Android blocks this for apps installed outside the Play Store. Do this once:\n" +
                            "1. Tap Open app info below.\n" +
                            "2. Tap the three dots at the top right and choose Allow restricted settings.\n" +
                            "3. Come back and tap Device control again, then turn Arina on.\n" +
                            "If the three dots are missing, first try to switch Arina on in Accessibility, then repeat step 1.",
                        color = ArinaColors.Text, fontSize = 12.sp, lineHeight = 18.sp
                    )
                    PillButton("Open app info") { onAppInfo() }
                }
            }
        }
        PermissionCenter.runtime.forEach { item ->
            PermRow(item, vm.perms[item.id] == true, onPermission)
            if (item.id == "termux") {
                Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("One-time Termux setup", color = ArinaColors.Glow, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Open Termux and run this, then restart Termux:\n" +
                            "mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties\n" +
                            "Then tap Allow on the Termux row above (or Allow all).",
                        color = ArinaColors.Text, fontSize = 12.sp, lineHeight = 18.sp
                    )
                }
            }
        }
        Spacer(Modifier.size(12.dp))
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = ArinaColors.Glow, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(top = 6.dp))
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) Good else Warn))
        Spacer(Modifier.width(10.dp))
        Text(label, color = ArinaColors.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(if (ok) "Set" else "Missing", color = if (ok) Good else Warn, fontSize = 12.sp)
    }
}

@Composable
private fun PermRow(item: PermItem, ok: Boolean, onPermission: (PermItem) -> Unit) {
    Row(Modifier.fillMaxWidth().glass(strong = ok).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) Good else Warn))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(item.subtitle, color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(8.dp))
        if (ok) {
            Text("ON", color = Good, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        } else {
            Text(
                if (item.kind == PermKind.Runtime) "Allow" else "Open",
                color = ArinaColors.Background, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(ArinaColors.Glow)
                    .clickable { onPermission(item) }.padding(horizontal = 16.dp, vertical = 7.dp)
            )
        }
    }
}


@Composable
private fun SkillsCard(vm: ArinaEngine) {
    var name by remember { mutableStateOf("") }
    var whenText by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "A skill is a named method with fixed steps. Say its name, or just ask for that kind of work, and AriAuri follows the steps exactly and checks the result.",
            color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 16.sp
        )
        vm.skills.forEach { sk ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sk.name + if (sk.builtIn) "  (built in)" else "", color = ArinaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(sk.steps.lines().take(2).joinToString("  "), color = ArinaColors.Muted, fontSize = 11.sp, maxLines = 2)
                }
                if (!sk.builtIn) Text(
                    "Delete", color = Warn, fontSize = 12.sp,
                    modifier = Modifier.clickable { vm.removeSkill(sk.name) }.padding(8.dp)
                )
            }
        }
        KeyField("New skill name", name, false) { name = it }
        KeyField("When it applies (one line)", whenText, false) { whenText = it }
        OutlinedTextField(
            value = steps, onValueChange = { steps = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
            label = { Text("Steps, one per line (1. ... 2. ...)", fontSize = 12.sp) },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ArinaColors.Text, unfocusedTextColor = ArinaColors.Text,
                focusedBorderColor = ArinaColors.Glow, unfocusedBorderColor = ArinaColors.Muted
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Save skill") {
                msg = if (vm.addSkill(name, whenText, steps)) { name = ""; whenText = ""; steps = ""; "Saved." } else "Needs a new name and steps."
            }
            if (msg.isNotBlank()) Text(msg, color = ArinaColors.Muted, fontSize = 12.sp)
        }
    }
}
