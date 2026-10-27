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
import androidx.compose.foundation.layout.aspectRatio
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

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.GraphicEq),
    Chat("Chat", Icons.Outlined.ChatBubbleOutline),
    Memory("Memory", Icons.Outlined.Psychology),
    Settings("Settings", Icons.Outlined.Settings)
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
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020817), Color(0xFF041634), Color(0xFF020817))))) {
        GridBackdrop(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().imePadding()) {
            Header(vm, onPower, Modifier.statusBarsPadding())
            PersonaSwitch(vm.persona.value, vm::switchPersona)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    Tab.Home -> HomePanel(vm, onMicClick)
                    Tab.Chat -> ChatPanel(vm)
                    Tab.Memory -> MemoryPanel(vm)
                    Tab.Settings -> SettingsPanel(vm, onPermission, onGrantAll, onAppInfo)
                }
            }
            BottomNav(tab) { tab = it }
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
private fun Header(vm: ArinaEngine, onPower: () -> Unit, modifier: Modifier = Modifier) {
    val on = vm.overlayOn.value
    Row(
        modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LogoMark(Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("ARIAURI", color = ArinaColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            Text(vm.persona.value.displayName.uppercase() + " ACTIVE", color = ArinaColors.Muted, fontSize = 9.sp, letterSpacing = 3.sp)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.glass(RoundedCornerShape(50), strong = on).clickable(onClick = onPower)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PowerSettingsNew, "Floating orb", tint = if (on) Good else ArinaColors.Glow, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (on) "ORB ON" else "ORB OFF", color = ArinaColors.Text, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PersonaSwitch(selected: Persona, onSelect: (Persona) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
        Row(Modifier.glass(RoundedCornerShape(50)).padding(4.dp)) {
            Persona.entries.forEach { p ->
                val on = p == selected
                Text(
                    p.displayName.uppercase(),
                    color = if (on) ArinaColors.Background else ArinaColors.Text,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) ArinaColors.Glow else Color.Transparent)
                        .clickable { onSelect(p) }
                        .padding(horizontal = 26.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun LogoMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val outer = Path().apply {
            moveTo(w * 0.5f, h * 0.06f); lineTo(w * 0.94f, h * 0.92f); lineTo(w * 0.06f, h * 0.92f); close()
        }
        val inner = Path().apply {
            moveTo(w * 0.5f, h * 0.34f); lineTo(w * 0.72f, h * 0.78f); lineTo(w * 0.28f, h * 0.78f); close()
        }
        val brush = Brush.linearGradient(listOf(ArinaColors.Glow, ArinaColors.Deep))
        drawPath(outer, brush, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
        drawPath(inner, brush, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
    }
}

@Composable
private fun HomePanel(vm: ArinaEngine, onMicClick: () -> Unit) {
    val problem = vm.notice.value != null
    val mood = moodOf(vm.status.value, vm.researchActive.value, problem)
    val shown = vm.notice.value ?: vm.caption.value

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            OrbView(
                persona = vm.persona.value,
                mood = mood,
                level = vm.level.floatValue,
                modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp).aspectRatio(1f)
            )
        }
        MoodChip(mood)
        Spacer(Modifier.size(10.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 64.dp, max = 120.dp), contentAlignment = Alignment.Center) {
            if (shown.isNotBlank()) {
                Text(
                    shown,
                    color = if (problem) Warn else ArinaColors.Text,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().glass().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else {
                Text(
                    if (vm.micOn.value) "I'm listening." else "Turn the microphone on and speak.",
                    color = ArinaColors.Muted, fontSize = 14.sp
                )
            }
        }
        Spacer(Modifier.size(6.dp))
        MicSwitch(vm.micOn.value, vm.level.floatValue, onMicClick)
        Text(
            if (vm.micOn.value) "MICROPHONE ON  ·  TAP TO TURN OFF" else "MICROPHONE OFF  ·  TAP TO TURN ON",
            color = ArinaColors.Muted, fontSize = 10.sp, letterSpacing = 2.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
        )
    }
}

@Composable
private fun MoodChip(mood: Mood) {
    val color = when (mood) {
        Mood.Alert -> Color(0xFFFF5C6C)
        Mood.Researching -> Warn
        Mood.Thinking -> Color(0xFFA27BFF)
        Mood.Standby -> ArinaColors.Muted
        else -> ArinaColors.Glow
    }
    Row(
        Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(mood.label, color = color, fontSize = 11.sp, letterSpacing = 2.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MicSwitch(micOn: Boolean, level: Float, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "mic")
    val ring by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "ring")
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension * 0.34f
            drawCircle(
                Brush.radialGradient(listOf(ArinaColors.Glow.copy(alpha = if (micOn) 0.30f + level * 0.4f else 0.10f), Color.Transparent), center = center, radius = base * 1.9f),
                radius = base * 1.9f, center = center
            )
            if (micOn) for (i in 0..1) {
                val p = (ring + i * 0.5f) % 1f
                drawCircle(ArinaColors.Primary.copy(alpha = (1f - p) * 0.55f), radius = base + p * base * 0.7f, center = center, style = Stroke(2.dp.toPx()))
            }
        }
        Box(
            Modifier.size(70.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(if (micOn) Color(0xFF0A3A7A) else Color(0xFF0A1B38), Color(0xFF031230))))
                .border(2.dp, Brush.sweepGradient(listOf(ArinaColors.Glow, ArinaColors.Deep, ArinaColors.Glow)), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, "Microphone",
                tint = if (micOn) ArinaColors.Glow else ArinaColors.Muted, modifier = Modifier.size(32.dp)
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
            KeyField("Gemini API key", gemini, true) { gemini = it }
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
            StatusLine("Gemini", vm.geminiKeySet)
            StatusLine("Cartesia voice (Arina)", vm.cartesiaKeySet)
            StatusLine("Cartesia voice (Aurion)", vm.voiceIdAurion.value.isNotBlank())
            StatusLine("Web research", vm.tavilyKeySet)
        }

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
private fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF2020817))))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Tab.entries.forEach { t ->
            val sel = t == selected
            Column(
                Modifier.clip(RoundedCornerShape(14.dp)).clickable { onSelect(t) }.padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(t.icon, t.label, tint = if (sel) ArinaColors.Glow else ArinaColors.Muted, modifier = Modifier.size(24.dp))
                Text(t.label.uppercase(), fontSize = 9.sp, letterSpacing = 1.5.sp, color = if (sel) ArinaColors.Glow else ArinaColors.Muted)
            }
        }
    }
}
