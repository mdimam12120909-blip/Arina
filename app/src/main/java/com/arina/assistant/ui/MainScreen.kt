package com.arina.assistant.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.TransformOrigin
import kotlinx.coroutines.launch
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.SwapHoriz
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Link
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
    Assistant("Assistant"), Admin("Admin"), Today("Today"), Profile("Edit profile"), Connectors("Connectors"), Jobs("Tasks")
}

/** The five bottom tabs, left to right. */
private fun tabIdx(p: Page): Int = when (p) { Page.Memory -> 0; Page.Chat -> 1; Page.Home -> 2; Page.Today -> 3; Page.Routine -> 4; else -> -1 }

/** Pages that open from Settings: they show a back arrow that returns to Settings. */
private val SETTINGS_SUB = setOf(Page.Account, Page.Permissions, Page.Keys, Page.Assistant, Page.Admin, Page.Connectors, Page.Jobs)

private val Good = Color(0xFF3DDC97)
private val Warn = Color(0xFFFFB84D)

internal fun Modifier.glass(shape: Shape = RoundedCornerShape(22.dp), strong: Boolean = false): Modifier {
    val dark = ArinaColors.dark
    val fill = if (strong) ArinaColors.Glow.copy(alpha = 0.24f) else Color.White.copy(alpha = if (dark) 0.09f else 0.58f)
    val edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.32f else 0.95f), Color.White.copy(alpha = if (dark) 0.07f else 0.35f)))
    return this.clip(shape).background(fill).border(1.dp, if (strong) Brush.verticalGradient(listOf(ArinaColors.Glow, ArinaColors.Glow.copy(alpha = 0.3f))) else edge, shape)
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
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = panelOpen) { panelOpen = false }
    BackHandler(enabled = !panelOpen && page != Page.Home) { page = if (page in SETTINGS_SUB) Page.Settings else Page.Home }
    fun open(p: Page) { page = p; panelOpen = false }
    val imeUp = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0

    val ambient = Color(OrbChoice.hues[OrbChoice.hueOf(vm.persona.value)].mid)
    val dark = ArinaColors.dark
    val glowA = ArinaColors.Glow
    val glowB = ArinaColors.Pink
    Box(
        Modifier.fillMaxSize().background(ArinaColors.Background).drawBehind {
            // Soft light that spreads from the corners, tinted by the agent's orb colour near the bottom.
            drawRect(Brush.radialGradient(listOf(glowA.copy(alpha = if (dark) 0.78f else 0.55f), Color.Transparent), center = Offset(0f, 0f), radius = size.width * 1.15f))
            drawRect(Brush.radialGradient(listOf(glowB.copy(alpha = if (dark) 0.62f else 0.46f), Color.Transparent), center = Offset(size.width, size.height * 0.28f), radius = size.width * 1.0f))
            drawRect(Brush.radialGradient(listOf(ambient.copy(alpha = if (dark) 0.60f else 0.50f), Color.Transparent), center = Offset(size.width * 0.4f, size.height), radius = size.width * 1.25f))
            drawRect(Brush.radialGradient(listOf(Color(0xFFFF6EC8).copy(alpha = if (dark) 0.20f else 0.18f), Color.Transparent), center = Offset(size.width * 0.15f, size.height * 0.52f), radius = size.width * 0.6f))
        }
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Header(vm, page, onAvatar = { panelOpen = true }, onBack = { page = if (page in SETTINGS_SUB) Page.Settings else Page.Home }, modifier = Modifier.statusBarsPadding())
            ResearchPanel(vm)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                androidx.compose.animation.AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val a = tabIdx(initialState); val b = tabIdx(targetState)
                        if (a >= 0 && b >= 0) {
                            val d = if (b > a) 1 else -1
                            val ease = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                            (androidx.compose.animation.fadeIn(tween(360, delayMillis = 70, easing = ease)) +
                                androidx.compose.animation.slideInHorizontally(tween(520, easing = ease)) { d * it / 3 } +
                                androidx.compose.animation.scaleIn(tween(520, easing = ease), initialScale = 0.94f)) togetherWith
                                (androidx.compose.animation.fadeOut(tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)) +
                                    androidx.compose.animation.slideOutHorizontally(tween(420, easing = ease)) { -d * it / 5 } +
                                    androidx.compose.animation.scaleOut(tween(420, easing = ease), targetScale = 0.96f))
                        } else {
                            // Depth: tabs 0, Settings 1, its sub-pages 2. Going deeper slides in from the right with the old page
                            // easing away; going back reverses it; anything else is a soft cross-fade with a tiny scale.
                            fun depth(p: Page) = if (tabIdx(p) >= 0) 0 else if (p in SETTINGS_SUB) 2 else 1
                            val da = depth(initialState); val db = depth(targetState)
                            val ease = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                            if (db > da) {
                                (androidx.compose.animation.fadeIn(tween(380, delayMillis = 40, easing = ease)) + androidx.compose.animation.slideInHorizontally(tween(460, easing = ease)) { it / 3 }) togetherWith
                                    (androidx.compose.animation.fadeOut(tween(260)) + androidx.compose.animation.slideOutHorizontally(tween(460, easing = ease)) { -it / 6 })
                            } else if (db < da) {
                                (androidx.compose.animation.fadeIn(tween(380, delayMillis = 40, easing = ease)) + androidx.compose.animation.slideInHorizontally(tween(460, easing = ease)) { -it / 5 }) togetherWith
                                    (androidx.compose.animation.fadeOut(tween(240)) + androidx.compose.animation.slideOutHorizontally(tween(460, easing = ease)) { it / 3 })
                            } else {
                                (androidx.compose.animation.fadeIn(tween(340, delayMillis = 60, easing = ease)) + androidx.compose.animation.scaleIn(tween(420, easing = ease), initialScale = 0.96f)) togetherWith
                                    (androidx.compose.animation.fadeOut(tween(200)) + androidx.compose.animation.scaleOut(tween(300), targetScale = 0.98f))
                            }
                        }
                    },
                    label = "page"
                ) { p ->
                    when (p) {
                        Page.Home -> HomePanel(vm, onMicClick, onPermission) { page = it }
                        Page.Chat -> ChatPanel(vm)
                        Page.Memory -> MemoryPanel(vm)
                        Page.Routine -> RoutinePanel(vm)
                        Page.Health -> HealthPanel(vm)
                        Page.Soul -> SoulPanel(vm)
                        Page.Today -> TodayPage(vm)
                        Page.Profile -> EditProfilePage(vm) { page = Page.Home }
                        Page.Settings -> SettingsHome(vm) { page = it }
                        Page.Account -> AccountPage(vm)
                        Page.Permissions -> PermissionsPage(vm, onPermission, onGrantAll, onAppInfo)
                        Page.Keys -> KeysPage(vm)
                        Page.Connectors -> ConnectorsPage(vm)
                        Page.Jobs -> JobsPage(vm)
                        Page.Assistant -> AssistantPage(vm, onPower)
                        Page.Admin -> AdminPage(vm)
                    }
                }
            }
            if (tabIdx(page) >= 0 && !imeUp) {
                Box(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)) {
                    Dock(page, vm.micOn.value, vm.level.floatValue, onMicClick) { page = it }
                }
                Spacer(Modifier.height(14.dp))
            }
            Spacer(Modifier.navigationBarsPadding())
        }
        Stage(vm, Modifier.align(Alignment.Center).padding(horizontal = 14.dp))

        androidx.compose.animation.AnimatedVisibility(
            visible = panelOpen,
            enter = androidx.compose.animation.fadeIn(tween(220)),
            exit = androidx.compose.animation.fadeOut(tween(180))
        ) {
            Box(
                Modifier.fillMaxSize().background(Color(0x99060818)).clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null
                ) { panelOpen = false }
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = panelOpen,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 380f)) { it },
            exit = androidx.compose.animation.slideOutHorizontally(tween(200)) { it }
        ) {
            ProfilePanel(vm, onOpen = ::open, onPower = onPower, onClose = { panelOpen = false })
        }
    }
}

/** Blends this colour toward [other] by [amount] (0..1). */
private fun Color.mix(other: Color, amount: Float): Color = androidx.compose.ui.graphics.lerp(this, other, amount)

private val AvatarTones = listOf(
    listOf(Color(0xFF6D8BFF), Color(0xFFA574FF)), listOf(Color(0xFFFF8AC0), Color(0xFFFF6B8A)),
    listOf(Color(0xFF46D6C0), Color(0xFF3B8BFF)), listOf(Color(0xFFFFC15E), Color(0xFFFF7A59))
)

@Composable
private fun Avatar(vm: ArinaEngine, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val letter = (vm.displayName().firstOrNull() ?: 'G').uppercaseChar().toString()
    Box(
        modifier.size(size).clip(CircleShape).background(Brush.linearGradient(AvatarTones[vm.avatarTone.intValue.coerceIn(0, 3)])),
        contentAlignment = Alignment.Center
    ) { Text(letter, color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun Header(vm: ArinaEngine, page: Page, onAvatar: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 10.dp, end = 18.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tabIdx(page) >= 0 && page != Page.Home) {
            Text(page.title, color = ArinaColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
        } else if (page != Page.Home) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = ArinaColors.Text) }
            Text(page.title, color = ArinaColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Text(Brand.APP, color = ArinaColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
        }
        Spacer(Modifier.weight(1f))
        Avatar(vm, 40.dp, Modifier.clickable(onClick = onAvatar))
    }
}

// ---- Home ---------------------------------------------------------------------------------------

@Composable
private fun HomePanel(vm: ArinaEngine, onMicClick: () -> Unit, onPermission: (PermItem) -> Unit, onNav: (Page) -> Unit) {
    val persona = vm.persona.value
    val problem = vm.notice.value != null
    val mood = moodOf(vm.status.value, vm.researchActive.value, problem)
    LaunchedEffect(vm.signedIn.value) { vm.refreshHeadlines() }
    val busy = vm.status.value == Status.Speaking || vm.status.value == Status.Thinking || vm.agentActive.value
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val hello = when { hour < 5 -> "Good night"; hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
    val who = vm.callName.value.ifBlank { vm.firstName.value.ifBlank { vm.accountName.value.substringBefore(' ') } }
    val say = vm.notice.value ?: vm.caption.value
    val lastUser = vm.messages.lastOrNull { it.fromUser }?.text.orEmpty()
    val lastAi = vm.messages.lastOrNull { !it.fromUser }?.text.orEmpty()
    val orbSize by animateDpAsState(if (busy) 176.dp else 262.dp, spring(dampingRatio = 0.82f, stiffness = 260f), label = "orb")

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (vm.phoneControlLost.value) {
            Row(Modifier.fillMaxWidth().glass(strong = true).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Phone control switched itself off", color = ArinaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Some phones turn it off to save battery. Turn it on again. The keep-alive steps in Permissions stop this from happening.", color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 16.sp)
                }
                Spacer(Modifier.width(10.dp))
                PillButton("Turn on") { PermissionCenter.special.firstOrNull { it.id == "accessibility" }?.let(onPermission) }
            }
            Spacer(Modifier.height(8.dp))
        }
        // What the agent can do right now
        Pop(0) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CapTag("Web search", vm.signedIn.value)
            CapTag("Phone control", !vm.phoneControlLost.value)
            CapTag("Memory", true)
        } }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            OrbView(persona = persona, mood = mood, level = vm.level.floatValue, modifier = Modifier.size(orbSize))
        }
        Column(Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = 0.9f, stiffness = 300f)), horizontalAlignment = Alignment.CenterHorizontally) {
val titleKey = if (vm.micOn.value && say.isNotBlank()) 0 else if (busy) 1 else 2
            AnimatedContent(targetState = titleKey, transitionSpec = { (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(tween(300)) { it / 6 }) togetherWith fadeOut(tween(120)) }, label = "title") { k ->
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (k == 0) {
                Text(
                    say, color = if (vm.notice.value != null) Warn else ArinaColors.Text,
                    fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            } else if (k == 1) {
                Text(
                    if (vm.status.value == Status.Speaking) "Answering you…" else if (vm.researchActive.value) "Searching the web…" else "Thinking it through…",
                    color = ArinaColors.Text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                )
            } else {
                Text(if (who.isBlank()) hello else "$hello, $who", color = ArinaColors.Text, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text("Ask me anything, or tell me what to do", color = ArinaColors.Muted, fontSize = 14.sp)
            }
              }
            }
            Spacer(Modifier.height(14.dp))
            AnimatedContent(targetState = busy, transitionSpec = { (fadeIn(tween(280)) + slideInVertically(tween(320)) { it / 8 }) togetherWith fadeOut(tween(120)) }, label = "card") { isBusy ->
            if (isBusy) {
                ActivityCard(vm)
            } else if (lastUser.isNotBlank() || lastAi.isNotBlank()) {
                Column(Modifier.fillMaxWidth().glass().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("LAST CONVERSATION", color = ArinaColors.Muted, fontSize = 11.sp, letterSpacing = 1.4.sp)
                    if (lastUser.isNotBlank()) Row { Text("YOU", color = ArinaColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(58.dp).padding(top = 3.dp)); Text(lastUser, color = ArinaColors.Text, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    if (lastAi.isNotBlank()) Row { Text("ENTRION", color = ArinaColors.Glow, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(58.dp).padding(top = 3.dp)); Text(lastAi, color = ArinaColors.Text, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                }
            }
            }
            if (!busy) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Suggest(Icons.Outlined.Call, "Make a call") { vm.sendText("I want to make a phone call. Ask me who to call.") }
                    Suggest(Icons.Outlined.CalendarToday, "What's on today?") { vm.sendText("What is on my tasks and routine today?") }
                    Suggest(Icons.Outlined.Language, "Search the web") { vm.sendText("I want to search the web. Ask me what to look up.") }
                    Suggest(Icons.Outlined.PhotoCamera, "Open camera") { vm.sendText("Open the camera.") }
                    Suggest(Icons.Outlined.Add, "Add a task") { vm.sendText("I want to add a task. Ask me what it is.") }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

/** Staggered entrance: fade + rise, delayed by [index]. */
@Composable
private fun Pop(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(80L + index * 90L); shown = true }
    androidx.compose.animation.AnimatedVisibility(shown, enter = fadeIn(tween(420)) + slideInVertically(tween(460)) { it / 4 }) { content() }
}

@Composable
private fun CapTag(label: String, ok: Boolean) {
    Row(Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (ok) Good else Warn))
        Spacer(Modifier.width(7.dp))
        Text(label, color = ArinaColors.Text, fontSize = 12.sp)
    }
}

@Composable
private fun Suggest(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.glass(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = ArinaColors.Text, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = ArinaColors.Text, fontSize = 13.5.sp)
    }
}

/** What the agent is doing right now, step by step. */
@Composable
private fun ActivityCard(vm: ArinaEngine) {
    // 2 = done, 1 = happening now
    val steps = buildList {
        add("Heard you" to 2)
        when {
            vm.status.value == Status.Speaking -> { add("Thought it through" to 2); add("Answering you" to 1) }
            vm.status.value == Status.Thinking -> add((if (vm.researchActive.value) "Searching the web" else "Thinking it through") to 1)
            else -> add("Working on your phone" to 1)
        }
    }
    Column(Modifier.fillMaxWidth().glass().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text("ACTIVITY", color = ArinaColors.Muted, fontSize = 11.sp, letterSpacing = 1.4.sp)
        steps.forEach { (name, st) ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (st == 2) Box(Modifier.size(22.dp).clip(CircleShape).background(Good), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Check, null, tint = Color(0xFF04281B), modifier = Modifier.size(14.dp))
                } else Box(Modifier.size(22.dp).border(2.dp, ArinaColors.Glow, CircleShape).padding(5.dp).clip(CircleShape).background(ArinaColors.Glow))
                Spacer(Modifier.width(12.dp))
                Text(name, color = ArinaColors.Text, fontSize = 14.5.sp)
            }
        }
    }
}

@Composable
private fun DockItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier = Modifier) {
    val tint by androidx.compose.animation.animateColorAsState(if (selected) ArinaColors.Glow else ArinaColors.Text, tween(220), label = "tint")
    val lift by androidx.compose.animation.core.animateFloatAsState(if (selected) -2f else 0f, spring(dampingRatio = 0.5f, stiffness = 420f), label = "lift")
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp).graphicsLayer { translationY = lift * density; scaleX = 1f + (-lift) * 0.06f; scaleY = 1f + (-lift) * 0.06f })
        Text(label, color = if (selected) ArinaColors.Glow else ArinaColors.Muted, fontSize = 10.5.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * The bottom bar. Put a finger anywhere on it and slide: the liquid blob follows the finger, the whole bar leans and
 * squeezes with the speed, the page changes as the blob crosses each tab, and every change plays a soft sound.
 */
@Composable
private fun Dock(page: Page, micOn: Boolean, level: Float, onMic: () -> Unit, onNav: (Page) -> Unit) {
    val inf = rememberInfiniteTransition(label = "dock")
    val pulse by inf.animateFloat(1f, 1.07f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val ring by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart), label = "ring")
    val scale = if (micOn) pulse + level.coerceIn(0f, 1f) * 0.08f else 1f
    val tabs = listOf(Page.Memory, Page.Chat, Page.Home, Page.Today, Page.Routine)
    val weights = floatArrayOf(1f, 1f, 1.4f, 1f, 1f)
    val total = weights.sum()
    val idx = tabIdx(page).coerceAtLeast(0)
    var widthPx by remember { mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val padPx = with(density) { 10.dp.toPx() }
    val blobW = with(density) { 58.dp.toPx() }
    fun centerOf(i: Int): Float {
        var acc = 0f
        for (k in 0 until i) acc += weights[k]
        return padPx + (widthPx - 2 * padPx) * (acc + weights[i] / 2f) / total
    }
    fun nearest(px: Float): Int {
        var best = 0
        for (i in 1..4) if (kotlin.math.abs(centerOf(i) - px) < kotlin.math.abs(centerOf(best) - px)) best = i
        return best
    }
    val x = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(idx, widthPx) {
        if (widthPx > 0f && !dragging) {
            if (x.value == 0f) x.snapTo(centerOf(idx)) else x.animateTo(centerOf(idx), spring(dampingRatio = 0.62f, stiffness = 430f))
        }
    }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val curPage by rememberUpdatedState(page)
    val showBlob by androidx.compose.animation.core.animateFloatAsState(if (page == Page.Home) 0f else 1f, tween(240), label = "blobAlpha")
    val dark = ArinaColors.dark
    Box(
        Modifier.fillMaxWidth().height(80.dp)
            .graphicsLayer {
                val vn = (x.velocity / 3200f).coerceIn(-1f, 1f)
                val sq = kotlin.math.abs(vn)
                transformOrigin = TransformOrigin(0.5f, 1f)
                cameraDistance = 14f * this.density
                rotationZ = vn * 1.8f
                rotationX = sq * 8f
                scaleX = 1f - sq * 0.035f
                scaleY = 1f - sq * 0.07f
            }
            .glass(RoundedCornerShape(40.dp))
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(widthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var moved = false
                    var lastIdx = nearest(down.position.x)
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        if (!moved && kotlin.math.abs(ch.position.x - down.position.x) > slop) { moved = true; dragging = true }
                        if (moved) {
                            ch.consume()
                            val px = ch.position.x.coerceIn(centerOf(0), centerOf(4))
                            scope.launch { x.animateTo(px, spring(dampingRatio = 0.9f, stiffness = 1100f)) }
                            val n = nearest(px)
                            if (n != lastIdx) {
                                lastIdx = n
                                NavSound.tick(n); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNav(tabs[n])
                            }
                        }
                    }
                    dragging = false
                    if (moved) {
                        scope.launch { x.animateTo(centerOf(lastIdx), spring(dampingRatio = 0.6f, stiffness = 380f)) }
                    } else {
                        val n = nearest(down.position.x)
                        NavSound.tick(n); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (n == 2) { if (curPage != Page.Home) onNav(Page.Home) else onMic() } else onNav(tabs[n])
                    }
                }
            }
    ) {
        Box(
            Modifier.padding(top = 10.dp).size(58.dp, 60.dp)
                .graphicsLayer {
                    val sq = (kotlin.math.abs(x.velocity) / 3200f).coerceIn(0f, 1f)
                    translationX = x.value - blobW / 2f
                    scaleX = 1f + sq * 0.6f
                    scaleY = 1f - sq * 0.2f
                    alpha = showBlob
                }
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4F7DFF).copy(alpha = if (dark) 0.36f else 0.24f), Color(0xFF8F5BFF).copy(alpha = if (dark) 0.36f else 0.24f))))
                .border(1.dp, Color.White.copy(alpha = if (dark) 0.32f else 0.85f), RoundedCornerShape(30.dp))
        )
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            DockItem(Icons.Outlined.Psychology, "Memory", page == Page.Memory, Modifier.weight(1f))
            DockItem(Icons.Outlined.ChatBubbleOutline, "Chat", page == Page.Chat, Modifier.weight(1f))
            Box(Modifier.weight(1.4f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(92.dp)) {
                    if (micOn) for (i in 0..1) {
                        val p = (ring + i * 0.5f) % 1f
                        drawCircle(ArinaColors.Glow.copy(alpha = (1f - p) * 0.45f), radius = size.minDimension * (0.36f + p * 0.16f), center = center, style = Stroke(2.dp.toPx()))
                    }
                }
                Box(
                    Modifier.size(62.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF4F7DFF), Color(0xFF8F5BFF))))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(if (page != Page.Home) Icons.Filled.Home else if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, "Microphone", tint = Color.White, modifier = Modifier.size(28.dp)) }
            }
            DockItem(Icons.Outlined.CalendarToday, "Today", page == Page.Today, Modifier.weight(1f))
            DockItem(Icons.Outlined.Schedule, "Routine", page == Page.Routine, Modifier.weight(1f))
        }
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
    Column(modifier.heightIn(min = 190.dp).glass(RoundedCornerShape(26.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TASKS", color = ArinaColors.Muted, fontSize = 11.sp, letterSpacing = 1.6.sp)
                Text("$left left", color = ArinaColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF4F7DFF), Color(0xFF8F5BFF)))).clickable { adding = !adding }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, "Add task", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(frac).height(6.dp).background(Brush.horizontalGradient(listOf(Color(0xFF4F7DFF), Color(0xFF8F5BFF)))))
        }
        if (adding) {
            GlassField(draft, { draft = it }, "New task", keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { vm.addTaskTyped(draft); draft = ""; adding = false }))
        }
        if (tasks.isEmpty() && !adding) Text("Nothing yet. Say “add a task…” or tap +.", color = ArinaColors.Muted, fontSize = 14.sp)
        tasks.take(6).forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { vm.toggleTask(t.id) }.padding(vertical = 4.dp)) {
                Box(Modifier.size(22.dp).clip(CircleShape).then(if (t.done) Modifier.background(Brush.linearGradient(listOf(Color(0xFF4F7DFF), Color(0xFF8F5BFF)))) else Modifier.border(2.dp, ArinaColors.Muted, CircleShape)), contentAlignment = Alignment.Center) { if (t.done) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                Spacer(Modifier.width(12.dp))
                Text(
                    t.text, color = if (t.done) ArinaColors.Muted else ArinaColors.Text, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 2,
                    textDecoration = if (t.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Outlined.Close, "Delete", tint = ArinaColors.Muted, modifier = Modifier.size(18.dp).clickable { vm.removeTask(t.id) })
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
                Box(
                    Modifier.size(124.dp).clip(RoundedCornerShape(20.dp))
                        .background(Brush.radialGradient(listOf(color.copy(alpha = 0.16f).compositeOver(ArinaColors.Background), ArinaColors.Background))),
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
        val cur = vm.persona.value
        val curColor = accentOf(cur)
        SectionTitle(if (cur == Persona.Arina) "ORB DESIGN - FEMALE AGENT" else "ORB DESIGN - MALE AGENT")
        Text("Pick the look of the orb. It moves with the voice and changes colour with the mood.", color = ArinaColors.Muted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrbChoice.names(cur).forEachIndexed { i, name ->
                val on = OrbChoice.of(cur) == i
                val tile = RoundedCornerShape(20.dp)
                Column(
                    Modifier.width(112.dp).clip(tile)
                        .background(if (on) curColor.copy(alpha = 0.08f).compositeOver(ArinaColors.Surface) else ArinaColors.Surface)
                        .border(if (on) 2.dp else 1.dp, curColor.copy(alpha = if (on) 1f else 0.30f), tile)
                        .clickable { OrbChoice.set(vm.uiPrefs, cur, i) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OrbView(cur, Mood.Listening, 0f, Modifier.size(104.dp), style = i)
                    Text(name, color = if (on) curColor else ArinaColors.Text, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        SectionTitle("ORB COLOUR")
        Text("Any colour for either agent.", color = ArinaColors.Muted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OrbChoice.hues.forEachIndexed { i, h ->
                val on = OrbChoice.hueOf(cur) == i
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { OrbChoice.setHue(vm.uiPrefs, cur, i) }) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(50))
                            .background(Brush.linearGradient(listOf(Color(h.light), Color(h.mid), Color(h.deep))))
                            .border(if (on) 3.dp else 1.dp, if (on) ArinaColors.Text else ArinaColors.Deep, RoundedCornerShape(50))
                    )
                    Text(h.name, color = if (on) ArinaColors.Text else ArinaColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
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
    Column(Modifier.fillMaxSize()) {
        if (vm.messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF4F7DFF), Color(0xFF8F5BFF)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("No messages yet", color = ArinaColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Type below, or tap the mic and just talk.", color = ArinaColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
        } else LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(vm.messages) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
                    val shape = if (m.fromUser) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
                    Text(
                        m.text,
                        color = if (m.fromUser) Color.White else ArinaColors.Text,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .then(
                                if (m.fromUser) Modifier.clip(shape).background(Brush.linearGradient(listOf(Color(0xE64F7DFF), Color(0xE68F5BFF)))).border(1.dp, Color.White.copy(alpha = 0.3f), shape)
                                else Modifier.glass(shape)
                            )
                            .padding(horizontal = 15.dp, vertical = 11.dp)
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f).heightIn(min = 50.dp).glass(RoundedCornerShape(26.dp)).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                androidx.compose.foundation.text.BasicTextField(
                    value = input, onValueChange = { input = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = ArinaColors.Text, fontSize = 15.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(ArinaColors.Glow),
                    maxLines = 4, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
                if (input.isEmpty()) Text("Message Entrion\u2026", color = ArinaColors.Muted, fontSize = 15.sp)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(50.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF4F7DFF), Color(0xFF8F5BFF))))
                    .clickable { if (input.isNotBlank()) { vm.sendText(input); input = "" } },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
    }
}

@Composable
private fun MemoryPanel(vm: ArinaEngine) {
    var profile by remember { mutableStateOf(vm.profile.value) }
    var newFact by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Everything here stays on this phone. Entrion reads it before every answer.",
            color = ArinaColors.Muted, fontSize = 12.sp
        )

        Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PROFILE", color = ArinaColors.Muted, fontSize = 11.sp, letterSpacing = 1.6.sp)
            GlassField(profile, { profile = it }, "Paste who you are, your projects, goals and how Entrion should treat you", singleLine = false, minHeight = 160.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton("Save profile") { vm.saveProfile(profile) }
                Text("${profile.length} / 6000", color = ArinaColors.Muted, fontSize = 12.sp)
            }
            vm.notice.value?.let { Text(it, color = Color(0xFFFFB84D), fontSize = 12.sp) }
        }

        Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("THINGS ENTRION REMEMBERS  ·  ${vm.facts.size}", color = ArinaColors.Muted, fontSize = 11.sp, letterSpacing = 1.6.sp)
            if (vm.facts.isEmpty()) {
                Text("Nothing yet. Tell Entrion to remember something, or add it below.", color = ArinaColors.Muted, fontSize = 12.sp)
            }
            vm.facts.reversed().forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(ArinaColors.Glow))
                    Spacer(Modifier.width(12.dp))
                    Text(f.text, color = ArinaColors.Text, fontSize = 14.5.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.deleteFact(f.id) }) {
                        Icon(Icons.Outlined.Close, "Delete", tint = ArinaColors.Muted)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassField(newFact, { newFact = it }, "Add a fact", modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                PillButton("Add") {
                    vm.addFact(newFact)
                    newFact = ""
                }
            }
        }
    }
}

/** A rounded glass text field used everywhere instead of the plain outlined box. */
@Composable
internal fun GlassField(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    singleLine: Boolean = true, minHeight: androidx.compose.ui.unit.Dp = 50.dp, secret: Boolean = false, label: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    Column(modifier.fillMaxWidth()) {
        if (label != null) Text(label, color = ArinaColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp, bottom = 5.dp))
        Box(
            Modifier.fillMaxWidth().heightIn(min = minHeight).glass(RoundedCornerShape(if (singleLine) 26.dp else 20.dp)).padding(horizontal = 18.dp),
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = value, onValueChange = onChange, singleLine = singleLine,
                textStyle = androidx.compose.ui.text.TextStyle(color = ArinaColors.Text, fontSize = 15.sp, lineHeight = 22.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(ArinaColors.Glow),
                visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
            )
            if (value.isEmpty()) Text(placeholder, color = ArinaColors.Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = if (singleLine) 0.dp else 14.dp))
        }
    }
}

@Composable
private fun KeyField(label: String, value: String, secret: Boolean, onChange: (String) -> Unit) {
    GlassField(value, onChange, "", label = label, secret = secret)
}

@Composable
internal fun PillButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.linearGradient(listOf(Color(0xFF4F7DFF), Color(0xFF8F5BFF))))
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
private fun NavRow(title: String, subtitle: String, icon: ImageVector, flat: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().let { if (flat) it else it.glass() }.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0x594F7DFF), Color(0x598F5BFF)))).border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
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
        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).clickable { go(Page.Profile) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(vm, 54.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(vm.displayName().ifBlank { "Your profile" }, color = ArinaColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(if (vm.signedIn.value) vm.accountEmail.value else "Not signed in", color = ArinaColors.Muted, fontSize = 12.sp)
            }
            Text("\u203A", color = ArinaColors.Muted, fontSize = 24.sp)
        }
        SectionTitle("ASSISTANT")
        Group(
            { NavRow("Assistant", "Companion, skills and speed report", Icons.Filled.Build, flat = true) { go(Page.Assistant) } },
            { NavRow("Gemini keys", "Your five keys, one for each job", Icons.Outlined.GraphicEq, flat = true) { go(Page.Keys) } },
            { NavRow("Connectors", "Apps, services and websites Entrion can use", Icons.Filled.Link, flat = true) { go(Page.Connectors) } },
            { NavRow("Tasks", "Big jobs Entrion did: steps, results, run again", Icons.Filled.Build, flat = true) { go(Page.Jobs) } }
        )
        SectionTitle("APP")
        Group(
            { NavRow("Account", if (vm.signedIn.value) vm.accountEmail.value else "Sign in to keep your data", Icons.Filled.Person, flat = true) { go(Page.Account) } },
            { NavRow("Permissions", "Choose what the agent may do on this phone", Icons.Filled.Lock, flat = true) { go(Page.Permissions) } },
            {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                NavRow("Report a problem", "Send what just happened so it can be fixed", Icons.Filled.BugReport, flat = true) {
                    com.arina.assistant.ai.Diag.share(ctx, "build " + runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode }.getOrDefault(0L), vm.messages.map { (if (it.fromUser) "You: " else "Entrion: ") + it.text })
                }
            }
        )
        if (vm.isAdmin.value) {
            SectionTitle("ADMIN")
            Group({ NavRow("Admin", "Settings published to every user", Icons.Filled.Info, flat = true) { go(Page.Admin) } })
        }
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
            listOf("Agent", "Facts", "Users", "Updates", "Secrets", "Log").forEach { name ->
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
            "Updates" -> AdminUpdates(vm)
            "Log" -> AdminLog(vm)
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
        Text("Facts every agent knows for every user (about Entrion, Pravat, products, rules). Short and true.", color = ArinaColors.Muted, fontSize = 12.sp)
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

/** What an admin sees instead of the app: the console only. No assistant, no mic, no orb. */
@Composable
fun AdminConsole(vm: ArinaEngine, onLock: () -> Unit) {
    Column(Modifier.fillMaxSize().background(ArinaColors.Background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Entrion Admin Console", color = ArinaColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(vm.accountEmail.value, color = ArinaColors.Muted, fontSize = 12.sp)
            }
            PillButton("Lock") { onLock() }
            Spacer(Modifier.width(8.dp))
            PillButton("Sign out") { vm.signOut() }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { AdminPage(vm) }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun AdminUpdates(vm: ArinaEngine) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var notes by remember { mutableStateOf("") }
    var force by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadReleases() }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pickApk(uri)
    }
    Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Update App", color = ArinaColors.Glow, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("Installed here: build ${com.arina.assistant.BuildConfig.VERSION_CODE}. Download Entrion.apk from GitHub in Chrome, then choose it here from your Downloads. Only a genuine, correctly signed Entrion.apk is accepted.", color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 17.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Choose Entrion.apk") { picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*")) }
            PillButton("Open GitHub in Chrome") {
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/mdimam12120909-blip/Arina/releases")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }
    vm.adminPicked.value?.let { p ->
        Column(Modifier.fillMaxWidth().glass(strong = true).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ready: build ${p.build}", color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("This is the real Entrion app with the right signature.", color = ArinaColors.Muted, fontSize = 12.sp)
            LongField("What is new (shown to users)", notes) { notes = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Must update", color = ArinaColors.Text, fontSize = 14.sp)
                    Text("Users cannot skip it. Use only for urgent fixes.", color = ArinaColors.Muted, fontSize = 12.sp)
                }
                Switch(checked = force, onCheckedChange = { force = it }, colors = SwitchDefaults.colors())
            }
            PillButton(if (vm.adminBusy.value) "Publishing…" else "Publish update") { if (!vm.adminBusy.value) { vm.publishPicked(notes, force); notes = ""; force = false } }
        }
    }
    SectionTitle("HISTORY")
    if (vm.adminReleases.isEmpty()) Text("No update published yet.", color = ArinaColors.Muted, fontSize = 13.sp)
    vm.adminReleases.forEach { r ->
        Row(Modifier.fillMaxWidth().glass().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Build ${r.build}" + (if (r.force) "  |  must update" else "") + (if (!r.active) "  |  withdrawn" else ""), color = if (r.active) ArinaColors.Text else Warn, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(r.date, color = ArinaColors.Muted, fontSize = 12.sp)
                if (r.notes.isNotBlank()) Text(r.notes, color = ArinaColors.Text, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Text(if (r.active) "Withdraw" else "Restore", color = Warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { vm.setReleaseActive(r, !r.active) }.padding(6.dp))
        }
    }
}

@Composable
private fun AdminLog(vm: ArinaEngine) {
    LaunchedEffect(Unit) { vm.loadReleases() }
    Text("Every admin action, newest first.", color = ArinaColors.Muted, fontSize = 12.sp)
    if (vm.adminAudit.isEmpty()) Text(if (vm.adminBusy.value) "Loading…" else "Nothing logged yet.", color = ArinaColors.Muted, fontSize = 13.sp)
    vm.adminAudit.forEach { a ->
        Column(Modifier.fillMaxWidth().glass().padding(12.dp)) {
            Text(a.action.replace('_', ' '), color = ArinaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(a.detail, color = ArinaColors.Text, fontSize = 12.sp)
            Text("${a.at}  |  ${a.actor}", color = ArinaColors.Muted, fontSize = 11.sp)
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
        SectionTitle("SPECIAL ACCESS")
        PermGroup(PermissionCenter.special, vm, onPermission)
        PermissionCenter.special.forEach { item ->
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
        SectionTitle("PERMISSIONS")
        PermGroup(PermissionCenter.runtime, vm, onPermission)
        PermissionCenter.runtime.forEach { item ->
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
private fun ChoiceCard(title: String, detail: String, selected: Boolean, flat: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().let { if (flat) it else it.glass(strong = selected) }.clickable(onClick = onClick).padding(14.dp),
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
private fun AssistantPage(vm: ArinaEngine, onPower: () -> Unit) {
    val actx = androidx.compose.ui.platform.LocalContext.current
    PageColumn {
        SectionTitle("OPEN ENTRION")
        Group(
            {
                NavRow("Hold Home to open Entrion", "Set Entrion as the default assistant, then hold the Home button", Icons.Outlined.Mic, flat = true) {
                    runCatching { actx.startActivity(android.content.Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            },
            {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Floating orb", color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("Keep a small orb over other apps (off by default)", color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                    Switch(
                        checked = vm.overlayOn.value, onCheckedChange = { onPower() },
                        colors = SwitchDefaults.colors(checkedTrackColor = ArinaColors.Glow, checkedThumbColor = Color.White)
                    )
                }
            }
        )
        ToggleCard(
            "Speak up on its own",
            "Notices long scrolling or non-stop phone use (needs Usage access) and nudges you kindly, at most once every 30 minutes. Routines always remind you.",
            vm.proactiveOn.value
        ) { vm.setProactive(it) }

        SectionTitle("WHEN IT OPERATES YOUR PHONE")
        Group(
            { ChoiceCard("Smart (recommended)", "Starts at once. Asks only before a risky tap you did not ask for: send, pay, buy, delete, install.", vm.operateMode.value == "smart", flat = true) { vm.setOperateMode("smart") } },
            { ChoiceCard("Ask me every time", "Shows a question before every phone task.", vm.operateMode.value == "ask", flat = true) { vm.setOperateMode("ask") } },
            { ChoiceCard("Never ask", "Does everything without asking. Only use this if you fully trust your commands.", vm.operateMode.value == "auto", flat = true) { vm.setOperateMode("auto") } }
        )

        SectionTitle("WHILE IT RUNS IN THE BACKGROUND")
        Group(
            { ChoiceCard("Quiet (recommended)", "Nothing on screen while it waits. A small status pill and a thin edge light show only while it speaks or works.", vm.bgStyle.value == "quiet", flat = true) { vm.setBgStyle("quiet") } },
            { ChoiceCard("Orb", "The orb rises from the bottom when it wakes, then melts away, with a soft sound.", vm.bgStyle.value == "orb", flat = true) { vm.setBgStyle("orb") } },
            { ChoiceCard("Nothing", "No picture at all. Only the notification shows it is on.", vm.bgStyle.value == "off", flat = true) { vm.setBgStyle("off") } }
        )

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
    Text(text, color = ArinaColors.Muted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(start = 4.dp, top = 8.dp))
}

/** One glass card holding several rows with thin dividers between them (the grouped look used on every page). */
@Composable
private fun Group(vararg rows: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp))) {
        rows.forEachIndexed { i, row ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 70.dp).height(1.dp).background(ArinaColors.Muted.copy(alpha = 0.14f)))
            row()
        }
    }
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
private fun PermGroup(items: List<PermItem>, vm: ArinaEngine, onPermission: (PermItem) -> Unit) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp))) {
        items.forEachIndexed { i, item ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 36.dp).height(1.dp).background(ArinaColors.Muted.copy(alpha = 0.14f)))
            PermRow(item, vm.perms[item.id] == true, flat = true, onPermission = onPermission)
        }
    }
}

@Composable
private fun PermRow(item: PermItem, ok: Boolean, flat: Boolean = false, onPermission: (PermItem) -> Unit) {
    Row(Modifier.fillMaxWidth().let { if (flat) it else it.glass(strong = ok) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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


// ---- Profile panel ------------------------------------------------------------------------------

@Composable
private fun PanelRow(icon: ImageVector, label: String, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0x474F7DFF), Color(0x478F5BFF)))).border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = ArinaColors.Text, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Text(label, color = ArinaColors.Text, fontSize = 15.5.sp, modifier = Modifier.weight(1f))
        if (trailing != null) trailing() else Text("›", color = ArinaColors.Muted, fontSize = 20.sp)
    }
}

@Composable
private fun PanelHead(text: String) {
    Text(text, color = ArinaColors.Muted, fontSize = 11.sp, letterSpacing = 1.6.sp, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 6.dp))
}

@Composable
private fun ProfilePanel(vm: ArinaEngine, onOpen: (Page) -> Unit, onPower: () -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp)
    Column(
        Modifier.width(330.dp).fillMaxHeight().clip(shape)
            .background(Brush.verticalGradient(listOf(ArinaColors.Surface.mix(ArinaColors.Glow, 0.16f), ArinaColors.Surface, ArinaColors.Surface.mix(ArinaColors.Pink, 0.12f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))), shape)
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
            .verticalScroll(rememberScrollState()).statusBarsPadding().padding(start = 24.dp, end = 22.dp, top = 24.dp, bottom = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).clickable { onOpen(Page.Profile) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(vm, 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(vm.displayName(), color = ArinaColors.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("Edit profile ›", color = ArinaColors.Muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ArinaColors.Deep.copy(alpha = 0.45f)).padding(3.dp)) {
            listOf("Light", "Dark", "Auto").forEachIndexed { i, name ->
                val mode = intArrayOf(1, 2, 0)[i]
                val on = ThemeChoice.mode == mode
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                        .background(if (on) ArinaColors.Glow else Color.Transparent)
                        .clickable { ThemeChoice.set(vm.uiPrefs, mode) }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) { Text(name, color = if (on) Color.White else ArinaColors.Muted, fontSize = 13.5.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal) }
            }
        }
        PanelHead("AGENT")
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp).clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(ArinaColors.Glow, ArinaColors.Pink)))
                .clickable { onOpen(Page.Soul) }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                OrbView(vm.persona.value, Mood.Listening, 0f, Modifier.size(46.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Soul", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(OrbChoice.names(vm.persona.value)[OrbChoice.of(vm.persona.value)] + " · " + ArinaEngine.PERSONALITIES[vm.personality.intValue].first, color = Color.White.copy(alpha = 0.85f), fontSize = 12.5.sp)
            }
            Text("›", color = Color.White, fontSize = 18.sp)
        }
        PanelHead("PHONE")
        PanelRow(Icons.Outlined.HealthAndSafety, "Phone health", { onOpen(Page.Health) })
        PanelHead("GENERAL")
        PanelRow(Icons.Outlined.Settings, "Settings", { onOpen(Page.Settings) })
        if (vm.signedIn.value) {
            Row(
                Modifier.fillMaxWidth().clickable { vm.signOut() }.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.ExitToApp, null, tint = Color(0xFFFF6B7A), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(16.dp))
                Text("Sign out", color = Color(0xFFFF6B7A), fontSize = 16.sp)
            }
        } else PanelRow(Icons.Filled.Person, "Sign in", { vm.openSignIn() })
        Text(Brand.APP + " by " + Brand.COMPANY, color = ArinaColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun EditProfilePage(vm: ArinaEngine, onDone: () -> Unit) {
    var first by rememberSaveable { mutableStateOf(vm.firstName.value.ifBlank { vm.accountName.value.substringBefore(' ') }) }
    var last by rememberSaveable { mutableStateOf(vm.lastName.value.ifBlank { vm.accountName.value.substringAfter(' ', "") }) }
    var call by rememberSaveable { mutableStateOf(vm.callName.value) }
    var pers by rememberSaveable { mutableStateOf(vm.personality.intValue) }
    var tone by rememberSaveable { mutableStateOf(vm.avatarTone.intValue) }
    PageColumn {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(92.dp).clip(CircleShape).background(Brush.linearGradient(AvatarTones[tone])), contentAlignment = Alignment.Center) {
                Text((first.firstOrNull() ?: 'G').uppercaseChar().toString(), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            AvatarTones.forEachIndexed { i, t ->
                Box(
                    Modifier.padding(horizontal = 6.dp).size(40.dp).clip(CircleShape).background(Brush.linearGradient(t))
                        .border(if (tone == i) 3.dp else 0.dp, if (tone == i) ArinaColors.Text else Color.Transparent, CircleShape)
                        .clickable { tone = i }
                )
            }
        }
        KeyField("First name", first, false) { first = it }
        KeyField("Last name", last, false) { last = it }
        KeyField("What the agent calls you", call, false) { call = it }
        SectionTitle("PERSONALITY")
        Text("How the agent talks to you.", color = ArinaColors.Muted, fontSize = 13.sp)
        for (row in 0..1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in row * 3 until row * 3 + 3) {
                    val on = pers == i
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (on) ArinaColors.Glow else ArinaColors.Surface)
                            .border(1.dp, if (on) Color.Transparent else ArinaColors.Deep, RoundedCornerShape(14.dp))
                            .clickable { pers = i }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(ArinaEngine.PERSONALITIES[i].first, color = if (on) Color.White else ArinaColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                }
            }
        }
        PillButton("Save") { vm.saveProfile(first, last, call, pers, tone); onDone() }
    }
}

@Composable
private fun TodayPage(vm: ArinaEngine) {
    val accent = ArinaColors.Glow
    LaunchedEffect(vm.signedIn.value) { vm.refreshHeadlines() }
    PageColumn {
        val left = vm.taskList.count { !it.done }
        val total = vm.taskList.size
        val frac = if (total == 0) 0f else (total - left).toFloat() / total
        val dateText = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(java.util.Date()).uppercase()
        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(dateText, color = ArinaColors.Muted, fontSize = 11.sp, letterSpacing = 1.4.sp)
                Text(if (total == 0) "A clear day" else if (left == 0) "All done" else "$left task${if (left == 1) "" else "s"} left", color = ArinaColors.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text(if (total == 0) "Say \u201Cadd a task\u201D to begin" else "${total - left} of $total finished", color = ArinaColors.Muted, fontSize = 13.sp)
            }
            Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, style = Stroke(7.dp.toPx()))
                    drawArc(Brush.sweepGradient(listOf(Color(0xFF4F7DFF), Color(0xFF8F5BFF), Color(0xFF4F7DFF))), -90f, 360f * frac, false, style = Stroke(7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                Text("${(frac * 100).toInt()}%", color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        TasksCard(vm, accent, Modifier.fillMaxWidth())
        HeadlinesCard(vm, accent, Modifier.fillMaxWidth())
    }
}


/** Live view of a running research: the topic, the steps so far, and a Stop button. */
@Composable
private fun ResearchPanel(vm: ArinaEngine) {
    val offer = vm.resumeOffer.value
    if (offer != null) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("UNFINISHED TASK", color = ArinaColors.Muted, fontSize = 10.sp, letterSpacing = 2.sp)
            Text(offer.goal.take(140), color = ArinaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Continue") { vm.resumeJob() }
                PillButton("Dismiss") { vm.dismissResume() }
            }
        }
    }
    val steps = vm.researchSteps
    androidx.compose.animation.AnimatedVisibility(
        visible = steps.isNotEmpty(),
        enter = fadeIn(tween(250)) + androidx.compose.animation.expandVertically(tween(300)),
        exit = fadeOut(tween(200)) + androidx.compose.animation.shrinkVertically(tween(250))
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4F7DFF).copy(alpha = 0.14f), Color(0xFF8F5BFF).copy(alpha = 0.14f))))
                .border(1.dp, Color(0xFF6D5BFF).copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("RESEARCH", color = ArinaColors.Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                    Text(vm.researchTitle.value, color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                if (vm.researchActive.value) Text(
                    "Stop", color = Color(0xFF6D5BFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.stopResearch() }.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            steps.takeLast(4).forEachIndexed { i, line ->
                val last = i == steps.takeLast(4).lastIndex
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (last) Color(0xFF6D5BFF) else ArinaColors.Muted.copy(alpha = 0.4f)))
                    Spacer(Modifier.width(10.dp))
                    Text(line, color = if (last) ArinaColors.Text else ArinaColors.Muted, fontSize = 13.sp)
                }
            }
        }
    }
}
