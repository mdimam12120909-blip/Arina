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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.compositeOver
import com.arina.assistant.Brand
import kotlinx.coroutines.launch
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
import androidx.compose.runtime.mutableStateListOf
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
    Home("Home"), Chat("Chat"), Memory("Memory"), Routine("Routine"), Health("Phone health"), Soul("Soul"),
    Settings("Settings"), Account("Account"), Permissions("Permissions"), Keys("Gemini keys"),
    Assistant("Assistant"), Admin("Admin")
}

/** Pages that open from Settings: they show a back arrow that returns to Settings. */
private val SETTINGS_SUB = setOf(Page.Account, Page.Permissions, Page.Keys, Page.Assistant, Page.Admin)

private val Good = Color(0xFF3DDC97)
private val Warn = Color(0xFFFFB84D)

private fun Modifier.glass(shape: Shape = RoundedCornerShape(18.dp), strong: Boolean = false): Modifier {
    val fill = if (strong) ArinaColors.Glow.copy(alpha = if (ArinaColors.dark) 0.16f else 0.10f)
    else if (ArinaColors.dark) ArinaColors.Surface.copy(alpha = 0.85f) else ArinaColors.Surface
    return this
        .clip(shape)
        .background(fill)
        .border(1.dp, if (strong) ArinaColors.Glow.copy(alpha = 0.55f) else ArinaColors.Deep.copy(alpha = if (ArinaColors.dark) 0.9f else 1f), shape)
}

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
    val drawer = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = !drawer.isOpen && page != Page.Home) { page = if (page in SETTINGS_SUB) Page.Settings else Page.Home }
    fun open(p: Page) { page = p; scope.launch { drawer.close() } }

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = page == Page.Home || drawer.isOpen,
        drawerContent = { AppDrawer(vm, page, ::open) }
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ArinaColors.Background, ArinaColors.Background.mix(ArinaColors.Glow, 0.07f))))) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Header(
                    vm, page, onPower,
                    onMenu = { scope.launch { drawer.open() } },
                    onBack = { page = if (page in SETTINGS_SUB) Page.Settings else Page.Home },
                    modifier = Modifier.statusBarsPadding()
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (page) {
                        Page.Home -> HomePanel(vm, onMicClick, onPermission)
                        Page.Chat -> ChatPanel(vm)
                        Page.Memory -> MemoryPanel(vm)
                        Page.Routine -> RoutinePanel(vm)
                        Page.Health -> HealthPanel(vm)
                        Page.Soul -> SoulPanel(vm)
                        Page.Settings -> SettingsHome(vm) { page = it }
                        Page.Account -> AccountPage(vm)
                        Page.Permissions -> PermissionsPage(vm, onPermission, onGrantAll, onAppInfo)
                        Page.Keys -> KeysPage(vm)
                        Page.Assistant -> AssistantPage(vm)
                        Page.Admin -> AdminPage(vm)
                    }
                }
                Spacer(Modifier.navigationBarsPadding())
            }
            Stage(vm, Modifier.align(Alignment.Center).padding(horizontal = 14.dp))
        }
    }
}

/** Blends this colour toward [other] by [amount] (0..1). */
private fun Color.mix(other: Color, amount: Float): Color = androidx.compose.ui.graphics.lerp(this, other, amount)

/** The side menu: who is signed in, every page, and account actions. */
@Composable
private fun AppDrawer(vm: ArinaEngine, current: Page, go: (Page) -> Unit) {
    androidx.compose.material3.ModalDrawerSheet(drawerContainerColor = ArinaColors.Background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(vm, 46.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        if (vm.signedIn.value) vm.accountName.value.ifBlank { "Signed in" } else "Guest",
                        color = ArinaColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                    Text(
                        if (vm.signedIn.value) vm.accountEmail.value else "Not signed in",
                        color = ArinaColors.Muted, fontSize = 12.sp, maxLines = 1
                    )
                }
            }
            DrawerRow("Home", Icons.Filled.Home, current == Page.Home) { go(Page.Home) }
            DrawerRow("Chat", Icons.Outlined.ChatBubbleOutline, current == Page.Chat) { go(Page.Chat) }
            DrawerRow("Memory", Icons.Outlined.Psychology, current == Page.Memory) { go(Page.Memory) }
            DrawerRow("Routine", Icons.Outlined.Schedule, current == Page.Routine) { go(Page.Routine) }
            DrawerRow("Phone health", Icons.Outlined.HealthAndSafety, current == Page.Health) { go(Page.Health) }
            DrawerRow("Soul", Icons.Outlined.Face, current == Page.Soul) { go(Page.Soul) }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(ArinaColors.Deep))
            Spacer(Modifier.height(6.dp))
            DrawerRow("Account", Icons.Filled.Person, current == Page.Account) { go(Page.Account) }
            DrawerRow("Permissions", Icons.Filled.Lock, current == Page.Permissions) { go(Page.Permissions) }
            DrawerRow("Settings", Icons.Outlined.Settings, current == Page.Settings || current in SETTINGS_SUB && current != Page.Account && current != Page.Permissions) { go(Page.Settings) }
            Spacer(Modifier.height(18.dp))
            if (vm.signedIn.value) {
                DrawerRow("Switch account", Icons.Filled.Person, false) { vm.signOut() }
                DrawerRow("Sign out", Icons.Filled.ExitToApp, false) { vm.signOut() }
            } else {
                DrawerRow("Sign in", Icons.Filled.Person, false) { vm.openSignIn() }
            }
            Text(Brand.APP + " by " + Brand.COMPANY, color = ArinaColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(20.dp))
        }
    }
}

@Composable
private fun DrawerRow(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.NavigationDrawerItem(
        label = { Text(label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        icon = { Icon(icon, null) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
        colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
            selectedContainerColor = ArinaColors.Glow.copy(alpha = 0.16f),
            unselectedContainerColor = Color.Transparent,
            selectedTextColor = ArinaColors.Glow,
            unselectedTextColor = ArinaColors.Text,
            selectedIconColor = ArinaColors.Glow,
            unselectedIconColor = ArinaColors.Muted
        )
    )
}

/** A round badge with the person's first letter. */
@Composable
private fun Avatar(vm: ArinaEngine, size: androidx.compose.ui.unit.Dp) {
    val letter = (vm.accountName.value.ifBlank { vm.accountEmail.value }.firstOrNull() ?: 'G').uppercaseChar().toString()
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(ArinaColors.Glow, ArinaColors.Pink))),
        contentAlignment = Alignment.Center
    ) { Text(letter, color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Header(vm: ArinaEngine, page: Page, onPower: () -> Unit, onMenu: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val on = vm.overlayOn.value
    val accent = accentOf(vm.persona.value)
    val nested = page in SETTINGS_SUB
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (nested) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = ArinaColors.Text) }
        } else {
            IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "Menu", tint = ArinaColors.Text) }
        }
        if (page == Page.Home) {
            LogoTile(Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(Brand.APP, color = ArinaColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    vm.agentName.value.ifBlank { vm.persona.value.displayName + " agent" },
                    color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium
                )
            }
        } else {
            Text(page.title, color = ArinaColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.glass(RoundedCornerShape(50), strong = on).clickable(onClick = onPower)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PowerSettingsNew, "Floating orb", tint = if (on) Good else ArinaColors.Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (on) "Orb on" else "Orb off", color = ArinaColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(8.dp))
    }
}

// ---- Home ---------------------------------------------------------------------------------------

@Composable
private fun HomePanel(vm: ArinaEngine, onMicClick: () -> Unit, onPermission: (PermItem) -> Unit) {
    val persona = vm.persona.value
    val accent = accentOf(persona)
    val problem = vm.notice.value != null
    val mood = moodOf(vm.status.value, vm.researchActive.value, problem)
    LaunchedEffect(vm.signedIn.value) { vm.refreshHeadlines() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (vm.phoneControlLost.value) {
            Row(Modifier.fillMaxWidth().glass(strong = true).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Phone control switched itself off", color = ArinaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Some phones turn it off to save battery. Turn it on again. The keep-alive steps in Permissions stop this from happening.", color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 16.sp)
                }
                Spacer(Modifier.width(10.dp))
                PillButton("Turn on") { PermissionCenter.special.firstOrNull { it.id == "accessibility" }?.let(onPermission) }
            }
        }
        TranscriptCard(vm, accent)

        // The agent
        BoxWithConstraints(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
            OrbView(
                persona = persona, mood = mood, level = vm.level.floatValue,
                modifier = Modifier.size(minOf(maxWidth, 300.dp)), backdrop = true
            )
            Text(
                mood.label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomCenter)
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
                Text("Stop", color = if (busy) ArinaColors.Text else ArinaColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(18.dp))
            MicSwitch(vm.micOn.value, vm.level.floatValue, accent, onMicClick)
            Spacer(Modifier.width(18.dp))
            Text(
                if (vm.micOn.value) "Mic on" else "Mic off",
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
        Text("Transcript", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
private fun MicSwitch(micOn: Boolean, level: Float, accent: Color, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "mic")
    val ring by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart), label = "ring")
    Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension * 0.31f
            if (micOn) for (i in 0..1) {
                val p = (ring + i * 0.5f) % 1f
                drawCircle(ArinaColors.Glow.copy(alpha = (1f - p) * 0.35f), radius = base + p * base * (0.55f + level * 0.5f), center = center, style = Stroke(2.dp.toPx()))
            }
        }
        Box(
            Modifier.size(62.dp).clip(CircleShape)
                .background(if (micOn) Brush.linearGradient(listOf(Color(0xFF4F8DF7), Color(0xFF1D4ED8))) else Brush.linearGradient(listOf(ArinaColors.Surface, ArinaColors.Surface)))
                .border(1.5.dp, if (micOn) Color.Transparent else ArinaColors.Deep, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, "Microphone",
                tint = if (micOn) Color.White else ArinaColors.Muted, modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun HeadlinesCard(vm: ArinaEngine, accent: Color, modifier: Modifier) {
    Column(modifier.heightIn(min = 190.dp).glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Today's headlines", color = ArinaColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Icon(
                Icons.Outlined.Refresh, "Refresh", tint = if (vm.headlinesBusy.value) ArinaColors.Muted else accent,
                modifier = Modifier.size(18.dp).clickable { vm.refreshHeadlines(force = true) }
            )
        }
        when {
            !vm.signedIn.value -> Text("Sign in to see today's headlines.", color = ArinaColors.Muted, fontSize = 12.sp)
            vm.headlines.isEmpty() -> Text(if (vm.headlinesBusy.value) "Loading…" else if (vm.headlinesError.value.isNotBlank()) "Headlines are not available: ${vm.headlinesError.value}" else "No headlines yet.", color = ArinaColors.Muted, fontSize = 12.sp)
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
                Text("Today's tasks", color = ArinaColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
                    .background(if (sel) color.copy(alpha = 0.08f).compositeOver(ArinaColors.Surface) else ArinaColors.Surface)
                    .border(if (sel) 2.dp else 1.dp, color.copy(alpha = if (sel) 1f else 0.35f), shape)
                    .clickable { vm.switchPersona(p) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // the orb sits in a dark "portal" so its light shows up on the white screen
                Box(
                    Modifier.size(124.dp).clip(RoundedCornerShape(20.dp))
                        .background(Brush.radialGradient(listOf(Color(0xFF16367A), Color(0xFF071236), Color(0xFF040A1E)))),
                    contentAlignment = Alignment.Center
                ) { OrbView(p, if (sel) Mood.Listening else Mood.Standby, 0f, Modifier.size(124.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.displayName.uppercase(), color = ArinaColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    Text(if (p == Persona.Arina) "Female voice · pink" else "Male voice · blue", color = color, fontSize = 12.sp)
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
            "Everything here stays on this phone. The agent reads it before every answer.",
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
private fun LongField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 12.sp) },
        minLines = 3,
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

/** Who is signed in, the agent's name, and sign in, switch or sign out. */
@Composable
private fun AccountCard(vm: ArinaEngine) {
    val activity = androidx.compose.ui.platform.LocalContext.current as android.app.Activity
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var agentName by remember { mutableStateOf(vm.agentName.value) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().glass().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(vm, 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (vm.signedIn.value) vm.accountName.value.ifBlank { "Signed in" } else "Guest",
                    color = ArinaColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (vm.signedIn.value) vm.accountEmail.value else "Sign in to keep your data across phones.",
                    color = ArinaColors.Muted, fontSize = 13.sp
                )
                if (vm.isAdmin.value) Text("Admin", color = ArinaColors.Pink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (vm.signedIn.value) {
            Text("Your memory, chats, skills and keys belong to this account only. Another account on this phone never sees them.", color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your agent's name", color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            KeyField("Name (optional; empty: the agent has no personal name)", agentName, false) { agentName = it }
            PillButton("Save name") { vm.setAgentName(agentName) }
        }
        if (vm.signedIn.value) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Switch account") { vm.signOut() }
                PillButton("Sign out") { vm.signOut() }
            }
        } else {
            PillButton("Continue with Google") { startGoogleSignIn(activity, vm, scope) }
        }
    }
}

@Composable
private fun PageColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = {
            content()
            Spacer(Modifier.height(24.dp))
        }
    )
}

@Composable
private fun NavRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(ArinaColors.Glow.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = ArinaColors.Glow, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ArinaColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Text("\u203A", color = ArinaColors.Muted, fontSize = 24.sp)
    }
}

@Composable
private fun SettingsHome(vm: ArinaEngine, go: (Page) -> Unit) {
    PageColumn {
        NavRow("Account", if (vm.signedIn.value) vm.accountEmail.value else "Sign in to keep your data", Icons.Filled.Person) { go(Page.Account) }
        NavRow("Permissions", "Choose what the agent may do on this phone", Icons.Filled.Lock) { go(Page.Permissions) }
        NavRow("Gemini keys", "Your five keys, one for each job", Icons.Outlined.GraphicEq) { go(Page.Keys) }
        NavRow("Assistant", "Companion, skills and speed report", Icons.Filled.Build) { go(Page.Assistant) }
        if (vm.isAdmin.value) NavRow("Admin", "Settings published to every user", Icons.Filled.Info) { go(Page.Admin) }
    }
}

@Composable
private fun AccountPage(vm: ArinaEngine) {
    PageColumn {
        AccountCard(vm)
    }
}

@Composable
private fun AdminPage(vm: ArinaEngine) {
    var section by rememberSaveable { mutableStateOf("Agent") }
    PageColumn {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Agent", "Facts", "Users", "Secrets").forEach { name ->
                val on = section == name
                Text(
                    name,
                    color = if (on) ArinaColors.Background else ArinaColors.Text,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (on) ArinaColors.Glow else ArinaColors.Glow.copy(alpha = 0.12f))
                        .clickable { section = name }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        when (section) {
            "Agent" -> AdminAgent(vm)
            "Facts" -> AdminFacts(vm)
            "Users" -> AdminUsers(vm)
            else -> AdminSecrets(vm)
        }
        vm.notice.value?.let { Text(it, color = Warn, fontSize = 12.sp, lineHeight = 17.sp) }
    }
}

@Composable
private fun AdminAgent(vm: ArinaEngine) {
    var liveFemale by remember { mutableStateOf(vm.adminValue("live_voice_female")) }
    var liveMale by remember { mutableStateOf(vm.adminValue("live_voice_male")) }
    var instrFemale by remember { mutableStateOf(vm.adminValue("instructions_female")) }
    var instrMale by remember { mutableStateOf(vm.adminValue("instructions_male")) }
    var style by remember { mutableStateOf(vm.adminValue("speaking_style").removeSurrounding("\"")) }
    Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Published to every user of Entrion. It applies the next time someone starts talking.", color = ArinaColors.Muted, fontSize = 12.sp)
        KeyField("Female voice name", liveFemale, false) { liveFemale = it }
        KeyField("Male voice name", liveMale, false) { liveMale = it }
        LongField("Female agent: character and instructions", instrFemale) { instrFemale = it }
        LongField("Male agent: character and instructions", instrMale) { instrMale = it }
        LongField("How to speak (tone, length, manners, languages), for both agents", style) { style = it }
        PillButton("Publish") {
            vm.adminSave("live_voice_female", liveFemale.trim())
            vm.adminSave("live_voice_male", liveMale.trim())
            vm.adminSave("instructions_female", instrFemale.trim())
            vm.adminSave("instructions_male", instrMale.trim())
            vm.adminSave("speaking_style", style.trim())
        }
    }
}

@Composable
private fun AdminFacts(vm: ArinaEngine) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadFacts() }
    Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Facts every agent knows for every user (about Entrion, Kashikoi, products, rules). Short and true.", color = ArinaColors.Muted, fontSize = 12.sp)
        LongField("New fact", text) { text = it }
        PillButton("Add fact") { vm.addGlobalFact(text); text = "" }
    }
    if (vm.adminFacts.isEmpty()) Text(if (vm.adminBusy.value) "Loading…" else "No facts yet.", color = ArinaColors.Muted, fontSize = 13.sp)
    vm.adminFacts.forEach { f ->
        Row(Modifier.fillMaxWidth().glass().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(f.text, color = ArinaColors.Text, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text("Delete", color = Warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { vm.deleteGlobalFact(f.id) }.padding(6.dp))
        }
    }
}

@Composable
private fun AdminUsers(vm: ArinaEngine) {
    var confirm by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadUsers() }
    Row(Modifier.fillMaxWidth().glass().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(vm.adminStatsText.value.ifBlank { if (vm.adminBusy.value) "Loading…" else "No data yet." }, color = ArinaColors.Text, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.Refresh, "Refresh", tint = ArinaColors.Glow, modifier = Modifier.size(20.dp).clickable { vm.loadUsers() })
    }
    vm.adminUsers.forEach { u ->
        val me = u.email.equals(vm.accountEmail.value, ignoreCase = true)
        Column(Modifier.fillMaxWidth().glass().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(u.name.ifBlank { u.email }, color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(u.email, color = ArinaColors.Muted, fontSize = 12.sp)
            Text("Joined ${u.created}" + (if (u.lastSeen.isNotBlank()) "  |  last seen ${u.lastSeen}" else "") + (if (u.banned) "  |  BANNED" else ""), color = if (u.banned) Warn else ArinaColors.Muted, fontSize = 12.sp)
            if (!me) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(if (u.banned) "Allow again" else "Ban", color = ArinaColors.Glow, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { vm.banUser(u.id, !u.banned) }.padding(vertical = 4.dp))
                    if (confirm == u.id) {
                        Text("Tap again to remove for good", color = Warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { confirm = ""; vm.removeUser(u.id) }.padding(vertical = 4.dp))
                    } else {
                        Text("Remove", color = Warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { confirm = u.id }.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminSecrets(vm: ArinaEngine) {
    var tavily by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.checkSearch() }
    Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Stored in the database where only you can read them. The web search service uses this key on the server; it never reaches users' phones.", color = ArinaColors.Muted, fontSize = 12.sp)
        KeyField("New web search key", tavily, false) { tavily = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Save") { if (tavily.isNotBlank()) { vm.saveSearchKey(tavily); tavily = "" } }
            PillButton("Test") { vm.checkSearch() }
        }
        Text(vm.searchStatus.value, color = ArinaColors.Text, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun PermissionsPage(vm: ArinaEngine, onPermission: (PermItem) -> Unit, onGrantAll: () -> Unit, onAppInfo: () -> Unit) {
    PageColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Turn on what you want the agent to be able to do. You can change any of these later.", color = ArinaColors.Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            PillButton("Allow all") { onGrantAll() }
        }
        PermissionCenter.special.forEach { item ->
            PermRow(item, vm.perms[item.id] == true, onPermission)
            if (item.id == "accessibility" && vm.perms[item.id] != true) {
                Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Device control will not switch on?", color = Warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Android blocks this for apps installed outside the Play Store. Do this once:\n" +
                            "1. Tap Open app info below.\n" +
                            "2. Tap the three dots at the top right and choose Allow restricted settings.\n" +
                            "3. Come back and tap Device control again, then turn Entrion on.\n" +
                            "If the three dots are missing, first try to switch Entrion on in Accessibility, then repeat step 1.",
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
        Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Keep Entrion alive (Realme, Oppo, Xiaomi)", color = ArinaColors.Glow, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "These phones close background apps and switch phone control off. To stop that, open App info and:\n" +
                    "1. Turn Autostart on.\n" +
                    "2. Battery: set Background usage to Allow, and turn battery optimization off.\n" +
                    "3. In the recent apps screen, lock Entrion (pull the card down or tap the lock).\n" +
                    "If phone control still switches itself off, the Home screen shows a Turn on button.",
                color = ArinaColors.Text, fontSize = 12.sp, lineHeight = 18.sp
            )
            PillButton("Open app info") { onAppInfo() }
        }
    }
}

@Composable
private fun ChoiceCard(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(strong = selected).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (selected) Text("\u2713", color = ArinaColors.Glow, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToggleCard(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().glass().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = ArinaColors.Glow, checkedThumbColor = ArinaColors.Background)
        )
    }
}

@Composable
private fun KeysPage(vm: ArinaEngine) {
    val gemini = remember { mutableStateListOf(*vm.geminiSlots.toTypedArray()) }
    PageColumn {
        Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your keys are stored only on this phone, under your account.", color = ArinaColors.Muted, fontSize = 12.sp)
            val keyJobs = listOf(
                "Key 1: female agent (conversation, also the fallback)",
                "Key 2: male agent (conversation)",
                "Key 3: research",
                "Key 4: workflows and skills",
                "Key 5: phone agent, camera, background"
            )
            Text("Each key has one job, so nothing waits for another job. An empty key uses key 1.", color = ArinaColors.Muted, fontSize = 12.sp)
            gemini.forEachIndexed { i, v -> KeyField(keyJobs.getOrElse(i) { "Key ${i + 1}" }, v, true) { gemini[i] = it } }
            PillButton("Save") { vm.saveKeys(gemini.toList()) }
            vm.notice.value?.let { Text(it, color = Warn, fontSize = 12.sp, lineHeight = 17.sp) }
            StatusLine("Keys set: " + vm.geminiKeyCount + " of 5", vm.geminiKeySet)
        }
    }
}

@Composable
private fun AssistantPage(vm: ArinaEngine) {
    PageColumn {
        ToggleCard(
            "Speak up on its own",
            "Notices long scrolling or non-stop phone use (needs Usage access) and nudges you kindly, at most once every 30 minutes. Routines always remind you.",
            vm.proactiveOn.value
        ) { vm.setProactive(it) }

        SectionTitle("WHEN IT OPERATES YOUR PHONE")
        ChoiceCard("Smart (recommended)", "Starts at once. Asks only before a risky tap you did not ask for: send, pay, buy, delete, install.", vm.operateMode.value == "smart") { vm.setOperateMode("smart") }
        ChoiceCard("Ask me every time", "Shows a question before every phone task.", vm.operateMode.value == "ask") { vm.setOperateMode("ask") }
        ChoiceCard("Never ask", "Does everything without asking. Only use this if you fully trust your commands.", vm.operateMode.value == "auto") { vm.setOperateMode("auto") }

        SectionTitle("WHILE IT RUNS IN THE BACKGROUND")
        ChoiceCard("Quiet (recommended)", "Nothing on screen while it waits. A small status pill and a thin edge light show only while it speaks or works.", vm.bgStyle.value == "quiet") { vm.setBgStyle("quiet") }
        ChoiceCard("Orb", "The orb rises from the bottom when it wakes, then melts away, with a soft sound.", vm.bgStyle.value == "orb") { vm.setBgStyle("orb") }
        ChoiceCard("Nothing", "No picture at all. Only the notification shows it is on.", vm.bgStyle.value == "off") { vm.setBgStyle("off") }

        SectionTitle("SKILLS")
        SkillsCard(vm)

        SectionTitle("SPEED (LAST REPLY)")
        Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                vm.lastTiming.value.ifBlank { "Talk to the agent once, then look here. Each line is the time since you finished speaking." },
                color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 17.sp
            )
        }
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
            "A skill is a named method with fixed steps. Say its name, or just ask for that kind of work, and Entrion follows the steps exactly and checks the result.",
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
