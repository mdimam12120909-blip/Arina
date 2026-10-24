package com.arina.assistant.ui

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlin.math.abs
import kotlin.math.sin

private enum class Tab(val label: String, val icon: ImageVector) {
    Voice("Voice", Icons.Outlined.GraphicEq),
    Chat("Chat", Icons.Outlined.ChatBubbleOutline),
    Memory("Memory", Icons.Outlined.Psychology),
    Setup("Setup", Icons.Outlined.Settings)
}

private fun Modifier.glass(shape: Shape = RoundedCornerShape(18.dp), strong: Boolean = false): Modifier =
    this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                if (strong) listOf(Color(0x660B5FFF), Color(0x33071A33))
                else listOf(Color(0x330B5FFF), Color(0x66071A33))
            )
        )
        .border(
            1.dp,
            Brush.linearGradient(listOf(ArinaColors.Glow.copy(alpha = 0.8f), ArinaColors.Deep.copy(alpha = 0.25f))),
            shape
        )

internal fun statusLabel(s: Status) = when (s) {
    Status.Idle -> "Ready"
    Status.Listening -> "Listening…"
    Status.Thinking -> "Thinking…"
    Status.Speaking -> "Speaking…"
}

@Composable
fun MainScreen(
    vm: ArinaEngine,
    onMicClick: () -> Unit,
    onPower: () -> Unit,
    onGrantPhone: () -> Unit,
    onGrantOverlay: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Voice) }

    Box(Modifier.fillMaxSize().background(ArinaColors.Background)) {
        AvatarView(
            status = vm.status.value,
            level = vm.level.floatValue,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f).align(Alignment.TopCenter)
        )
        Column(Modifier.fillMaxSize().imePadding()) {
            TopBar(vm.status.value, Modifier.statusBarsPadding())
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    Tab.Voice -> VoicePanel(vm, onMicClick, onPower, Modifier.fillMaxSize())
                    Tab.Chat -> ChatPanel(vm)
                    Tab.Memory -> MemoryPanel(vm)
                    Tab.Setup -> SetupPanel(vm, onGrantPhone, onGrantOverlay, onOpenAccessibility)
                }
            }
            BottomNav(tab) { tab = it }
        }
    }
}

@Composable
private fun TopBar(status: Status, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LogoMark(Modifier.size(38.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("ARINA", color = ArinaColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
            Text("Your AI Companion", color = ArinaColors.Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (status == Status.Idle) ArinaColors.Muted else ArinaColors.Glow)
            )
            Spacer(Modifier.width(8.dp))
            Text(statusLabel(status), color = ArinaColors.Text, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LogoMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val outer = Path().apply {
            moveTo(w * 0.5f, h * 0.06f)
            lineTo(w * 0.94f, h * 0.92f)
            lineTo(w * 0.06f, h * 0.92f)
            close()
        }
        val inner = Path().apply {
            moveTo(w * 0.5f, h * 0.34f)
            lineTo(w * 0.72f, h * 0.78f)
            lineTo(w * 0.28f, h * 0.78f)
            close()
        }
        val brush = Brush.linearGradient(listOf(ArinaColors.Glow, ArinaColors.Deep))
        drawPath(outer, brush, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
        drawPath(inner, brush, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
    }
}

@Composable
private fun VoicePanel(vm: ArinaEngine, onMicClick: () -> Unit, onPower: () -> Unit, modifier: Modifier = Modifier) {
    val isError = vm.notice.value != null
    val shown = vm.notice.value ?: vm.caption.value

    Box(modifier) {
        PowerButton(vm.overlayOn.value, onPower, Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (shown.isNotBlank()) {
                Text(
                    shown,
                    color = if (isError) Color(0xFFFFB84D) else ArinaColors.Text,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .glass()
                        .heightIn(max = 130.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            if (vm.researchActive.value) {
                Text("Researching in the background…", color = ArinaColors.Glow, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Waveform(vm.status.value, vm.level.floatValue, Modifier.padding(top = 10.dp).size(width = 220.dp, height = 34.dp))
            MicButton(vm.status.value, vm.micOn.value, vm.level.floatValue, onMicClick)
            Text(
                if (!vm.micOn.value) "Mic is off. Tap to turn it on"
                else when (vm.status.value) {
                    Status.Idle -> "Mic is on. Tap to turn it off"
                    Status.Listening -> "Listening… tap to turn the mic off"
                    Status.Thinking -> "Thinking… tap to turn the mic off"
                    Status.Speaking -> "Speaking… tap to turn the mic off"
                },
                color = ArinaColors.Muted, fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PowerButton(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.glass(RoundedCornerShape(50), strong = on).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.PowerSettingsNew, "Power",
            tint = if (on) Color(0xFF3DDC97) else ArinaColors.Glow,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (on) "Turn off floating Arina" else "Turn on Arina",
            color = ArinaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun Waveform(status: Status, level: Float, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "wave")
    val phase by inf.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val amp = when (status) {
        Status.Listening -> 0.25f + level * 0.75f
        Status.Speaking -> 0.65f
        Status.Thinking -> 0.28f
        Status.Idle -> 0.08f
    }
    Canvas(modifier) {
        val bars = 30
        val gap = size.width / bars
        val barW = gap * 0.45f
        for (i in 0 until bars) {
            val envelope = 1f - abs(i - bars / 2f) / (bars / 2f) * 0.6f
            val h = (0.15f + 0.85f * abs(sin(phase + i * 0.55f))) * amp * envelope * size.height
            val hh = h.coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                ArinaColors.Glow.copy(alpha = 0.55f + 0.45f * envelope),
                topLeft = Offset(i * gap + (gap - barW) / 2f, (size.height - hh) / 2f),
                size = Size(barW, hh),
                cornerRadius = CornerRadius(barW / 2f)
            )
        }
    }
}

@Composable
private fun MicButton(status: Status, micOn: Boolean, level: Float, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "mic")
    val ring by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "ring"
    )
    val active = micOn
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension * 0.27f
            drawCircle(
                Brush.radialGradient(
                    listOf(ArinaColors.Glow.copy(alpha = 0.30f + level * 0.4f), Color.Transparent),
                    center = center, radius = base * 2.4f
                ),
                radius = base * 2.4f, center = center
            )
            if (active) {
                for (i in 0..1) {
                    val p = (ring + i * 0.5f) % 1f
                    drawCircle(
                        ArinaColors.Primary.copy(alpha = (1f - p) * 0.55f),
                        radius = base + p * base * 1.2f,
                        center = center,
                        style = Stroke(2.dp.toPx())
                    )
                }
            }
        }
        Box(
            Modifier.size(80.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF0A2A5C), Color(0xFF031230))))
                .border(2.dp, Brush.sweepGradient(listOf(ArinaColors.Glow, ArinaColors.Deep, ArinaColors.Glow)), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff,
                contentDescription = "Microphone",
                tint = if (micOn) ArinaColors.Glow else ArinaColors.Muted,
                modifier = Modifier.size(36.dp)
            )
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
private fun SetupPanel(vm: ArinaEngine, onGrantPhone: () -> Unit, onGrantOverlay: () -> Unit, onOpenAccessibility: () -> Unit) {
    var gemini by remember { mutableStateOf(vm.geminiKey.value) }
    var cartesia by remember { mutableStateOf(vm.cartesiaKey.value) }
    var voice by remember { mutableStateOf(vm.voiceId.value) }
    var tavily by remember { mutableStateOf(vm.tavilyKey.value) }

    Column(
        Modifier.fillMaxSize()
            .background(ArinaColors.Background.copy(alpha = 0.9f))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Setup", color = ArinaColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

        Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("API keys (stored only on this phone)", color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            KeyField("Gemini API key", gemini, true) { gemini = it }
            KeyField("Cartesia API key", cartesia, true) { cartesia = it }
            KeyField("Cartesia Voice ID", voice, false) { voice = it }
            KeyField("Tavily search key (free, for web research)", tavily, true) { tavily = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Save") { vm.saveKeys(gemini, cartesia, voice, tavily) }
                PillButton("Test voice") {
                    vm.saveKeys(gemini, cartesia, voice, tavily)
                    vm.testVoice()
                }
            }
            vm.notice.value?.let {
                Text(it, color = Color(0xFFFFB84D), fontSize = 12.sp, lineHeight = 17.sp)
            }
        }

        SetupRow("Gemini API key", if (vm.geminiKeySet) "Set" else "Paste it above and tap Save", vm.geminiKeySet)
        SetupRow("Cartesia voice", if (vm.cartesiaKeySet) "Set" else "Paste the API key and Voice ID above and tap Save", vm.cartesiaKeySet)
        SetupRow("Web research", if (vm.tavilyKeySet) "Set" else "Paste a free Tavily key above and tap Save", vm.tavilyKeySet)
        SetupRow("Microphone", if (vm.micGranted.value) "Allowed" else "Asked when you first turn the mic on", vm.micGranted.value)
        SetupRow("Floating avatar", if (vm.overlayGranted.value) "Allowed" else "Needed to show Arina over other apps", vm.overlayGranted.value, "Allow", onGrantOverlay)
        SetupRow("Calls, contacts & SMS", if (vm.phoneGranted.value) "Allowed" else "Needed for calling and messaging", vm.phoneGranted.value, "Allow", onGrantPhone)
        SetupRow("Device Control", if (vm.accessibilityOn.value) "Enabled" else "Needed for Back, Home, screenshots and tapping on screen", vm.accessibilityOn.value, "Enable", onOpenAccessibility)
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
private fun SetupRow(
    title: String,
    subtitle: String,
    ok: Boolean,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().glass().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) Color(0xFF3DDC97) else Color(0xFFFFB84D)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = ArinaColors.Muted, fontSize = 12.sp)
        }
        if (!ok && actionLabel != null) {
            Text(
                actionLabel,
                color = ArinaColors.Background,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ArinaColors.Glow)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, ArinaColors.Background.copy(alpha = 0.95f))))
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Tab.entries.forEach { t ->
            val sel = t == selected
            Column(
                Modifier.clip(RoundedCornerShape(14.dp)).clickable { onSelect(t) }
                    .padding(horizontal = 22.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(t.icon, t.label, tint = if (sel) ArinaColors.Glow else ArinaColors.Muted, modifier = Modifier.size(24.dp))
                Text(t.label, fontSize = 11.sp, color = if (sel) ArinaColors.Glow else ArinaColors.Muted)
            }
        }
    }
}
