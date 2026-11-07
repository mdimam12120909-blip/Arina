package com.arina.assistant.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arina.assistant.permissions.PermItem
import com.arina.assistant.permissions.PermissionCenter

private val GradA = Color(0xFF4F7DFF)
private val GradB = Color(0xFF8F5BFF)

private fun Modifier.obGlass(shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(22.dp), on: Boolean = false): Modifier {
    val dark = ArinaColors.dark
    return clip(shape)
        .background(if (on) GradA.copy(alpha = if (dark) 0.26f else 0.16f) else Color.White.copy(alpha = if (dark) 0.09f else 0.58f))
        .border(1.dp, if (on) Brush.verticalGradient(listOf(GradA, GradB)) else Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.30f else 0.95f), Color.White.copy(alpha = if (dark) 0.07f else 0.35f))), shape)
}

private val FOUND = listOf("A friend told me" to Icons.Outlined.Group, "YouTube or social media" to Icons.Outlined.PlayArrow, "Search" to Icons.Outlined.Search, "Something else" to Icons.Outlined.AutoAwesome)
private val USES = listOf("Calls & messages", "Reminders", "Research", "Studying", "Translate", "Control my phone", "Just talk", "Memory", "Daily planning", "Writing")
private val PERS_ICONS = listOf(Icons.Outlined.EmojiEmotions, Icons.Outlined.Work, Icons.Outlined.Celebration, Icons.Outlined.Spa)
private const val STEPS = 8

/** Shown once, right after a new account signs in: a calm run of questions that sets Entrion up the way the person wants. */
@Composable
fun OnboardingScreen(vm: ArinaEngine, onPermission: (PermItem) -> Unit, onAssistant: () -> Unit, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var found by rememberSaveable { mutableIntStateOf(-1) }
    var uses by rememberSaveable { mutableStateOf("") }
    var first by rememberSaveable { mutableStateOf(vm.firstName.value.ifBlank { vm.accountName.value.substringBefore(' ') }) }
    var last by rememberSaveable { mutableStateOf(vm.lastName.value.ifBlank { vm.accountName.value.substringAfter(' ', "") }) }
    var call by rememberSaveable { mutableStateOf(vm.callName.value) }
    var about by rememberSaveable { mutableStateOf("") }
    var pers by rememberSaveable { mutableIntStateOf(0) }
    val keys = remember { mutableStateListOf(*vm.geminiSlots.toTypedArray()) }
    var back by remember { mutableStateOf(false) }

    fun finish() {
        if (first.isNotBlank() || call.isNotBlank()) vm.saveProfile(first.trim(), last.trim(), call.trim(), pers, vm.avatarTone.intValue)
        else vm.saveProfile(vm.firstName.value, vm.lastName.value, vm.callName.value, pers, vm.avatarTone.intValue)
        if (about.isNotBlank()) vm.saveProfile(about.trim())
        if (keys.any { it.isNotBlank() }) vm.saveKeys(keys.toList())
        vm.uiPrefs.edit().putBoolean("onboarded", true).putInt("ob_found", found).putString("ob_uses", uses).putBoolean("ob_synced", false).apply()
        vm.syncOnboarding()
        onDone()
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val isAssistant = (android.provider.Settings.Secure.getString(ctx.contentResolver, "assistant") ?: "").startsWith(ctx.packageName)
    // Continue only lights up once the question has an answer.
    val canNext = when (step) {
        0 -> found >= 0
        1 -> uses.isNotBlank()
        2 -> first.isNotBlank()
        3 -> about.isNotBlank()
        6 -> keys.any { it.isNotBlank() }
        7 -> isAssistant || PermissionCenter.runtime.firstOrNull { it.id == "mic" }?.let { vm.perms[it.id] == true } == true
        else -> true
    }
    fun next() { back = false; if (step >= STEPS - 1) finish() else step++ }
    fun prev() { back = true; if (step > 0) step-- }

    val ambient = Color(OrbChoice.hues[OrbChoice.hueOf(vm.persona.value)].mid)
    val dark = ArinaColors.dark
    val glowA = ArinaColors.Glow
    val glowB = ArinaColors.Pink
    Box(
        Modifier.fillMaxSize().background(ArinaColors.Background).drawBehind {
            drawRect(Brush.radialGradient(listOf(glowA.copy(alpha = if (dark) 0.78f else 0.55f), Color.Transparent), center = Offset(0f, 0f), radius = size.width * 1.15f))
            drawRect(Brush.radialGradient(listOf(glowB.copy(alpha = if (dark) 0.62f else 0.46f), Color.Transparent), center = Offset(size.width, size.height * 0.28f), radius = size.width * 1.0f))
            drawRect(Brush.radialGradient(listOf(ambient.copy(alpha = if (dark) 0.60f else 0.50f), Color.Transparent), center = Offset(size.width * 0.4f, size.height), radius = size.width * 1.25f))
        }
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until STEPS) {
                    val f by animateFloatAsState(if (i <= step) 1f else 0f, tween(420), label = "pg$i")
                    Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.18f))) {
                        Box(Modifier.fillMaxWidth(f).height(5.dp).background(Brush.horizontalGradient(listOf(GradA, GradB))))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val d = if (back) -1 else 1
                        (fadeIn(tween(320, delayMillis = 60)) + slideInHorizontally(tween(420)) { d * it / 5 }) togetherWith
                            (fadeOut(tween(160)) + slideOutHorizontally(tween(320)) { -d * it / 6 })
                    },
                    label = "ob"
                ) { s ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 28.dp, bottom = 12.dp)) {
                        when (s) {
                            0 -> {
                                Title(1, "How did you find Entrion?", "This helps Pravat know where people hear about us.")
                                FOUND.forEachIndexed { i, (t, ic) -> Option(ic, t, found == i) { found = i } }
                            }
                            1 -> {
                                Title(2, "What will you use Entrion for?", "Pick as many as you like. You can change this later.")
                                USES.chunked(2).forEach { row ->
                                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        row.forEach { u ->
                                            val on = uses.split("|").contains(u)
                                            Box(
                                                Modifier.weight(1f).obGlass(RoundedCornerShape(22.dp), on)
                                                    .clickable { uses = if (on) uses.split("|").filter { it != u && it.isNotBlank() }.joinToString("|") else (uses.split("|").filter { it.isNotBlank() } + u).joinToString("|") }
                                                    .padding(horizontal = 14.dp, vertical = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) { Text(u, color = ArinaColors.Text, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center) }
                                        }
                                        if (row.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                            2 -> {
                                Title(3, "What should Entrion call you?", "Your name is used in greetings and when it speaks to you.")
                                Fields {
                                    GlassField(first, { first = it }, "First name", label = "First name")
                                    GlassField(last, { last = it }, "Last name (optional)", label = "Last name")
                                    GlassField(call, { call = it }, "Same as first name", label = "Call me")
                                }
                            }
                            3 -> {
                                Title(4, "Write about yourself", "Who you are, your work, your goals and how Entrion should treat you. It stays on this phone.")
                                GlassField(about, { about = it.take(6000) }, "For example: I am a student and an app builder. I like short, clear answers.", singleLine = false, minHeight = 200.dp)
                                Text("${about.length} / 6000", color = ArinaColors.Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.End)
                            }
                            4 -> CompanionStep(vm)
                            5 -> {
                                Title(6, "How should it talk to you?", "Choose a personality. You can change it any time.")
                                ArinaEngine.PERSONALITIES.take(4).forEachIndexed { i, (name, _) -> Option(PERS_ICONS[i], name, pers == i) { pers = i } }
                            }
                            6 -> {
                                Title(7, "Add your Gemini keys", "Five keys, one for each job. Free keys from Google AI Studio work. Stored only on this phone, under your account.")
                                val jobs = listOf("Key 1 · female agent", "Key 2 · male agent", "Key 3 · research", "Key 4 · workflows and skills", "Key 5 · phone agent and camera")
                                Fields { keys.forEachIndexed { i, v -> GlassField(v, { keys[i] = it }, "Paste key", label = jobs.getOrElse(i) { "Key ${i + 1}" }, secret = true) } }
                                Text("An empty key uses key 1.", color = ArinaColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, start = 6.dp))
                            }
                            else -> {
                                Title(8, "Let Entrion help on your phone", "Allow only what you want. Each one can be changed later in Permissions.")
                                val items = listOf(
                                    Triple(Icons.Outlined.Mic, "Microphone", PermissionCenter.runtime.firstOrNull { it.id == "mic" }),
                                    Triple(Icons.Outlined.Accessibility, "Phone control", PermissionCenter.special.firstOrNull { it.id == "accessibility" })
                                )
                                items.forEach { (ic, t, item) ->
                                    val ok = item != null && vm.perms[item.id] == true
                                    Option(ic, t, ok, trailing = if (ok) "Allowed" else "Allow") { if (item != null) onPermission(item) }
                                }
                                Option(Icons.Outlined.Layers, "Hold Home to open Entrion", isAssistant, trailing = if (isAssistant) "On" else "Set") { onAssistant() }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) Text("‹ Back", color = ArinaColors.Muted, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { prev() }.padding(horizontal = 6.dp, vertical = 10.dp))
                Spacer(Modifier.weight(1f))
                if (step in listOf(3, 6, 7)) Text("Skip", color = ArinaColors.Muted, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { next() }.padding(horizontal = 6.dp, vertical = 10.dp))
            }
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
                    .background(Brush.horizontalGradient(listOf(GradA.copy(alpha = if (canNext) 1f else 0.28f), GradB.copy(alpha = if (canNext) 1f else 0.28f))))
                    .clickable(enabled = canNext, interactionSource = remember { MutableInteractionSource() }, indication = null) { next() },
                contentAlignment = Alignment.Center
            ) { Text(if (step == STEPS - 1) "Finish" else "Continue", color = Color.White.copy(alpha = if (canNext) 1f else 0.6f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun Title(n: Int, h: String, p: String) {
    Text("STEP $n OF $STEPS", color = ArinaColors.Muted, fontSize = 11.sp, letterSpacing = 1.6.sp)
    Text(h, color = ArinaColors.Text, fontSize = 29.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    Text(p, color = ArinaColors.Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))
}

@Composable
private fun Fields(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
}

@Composable
private fun Option(icon: ImageVector, text: String, on: Boolean, trailing: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).obGlass(RoundedCornerShape(22.dp), on).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(GradA.copy(alpha = 0.35f), GradB.copy(alpha = 0.35f)))), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = ArinaColors.Text, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, color = ArinaColors.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = if (on) ArinaColors.Glow else ArinaColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        else Box(
            Modifier.size(24.dp).clip(CircleShape).then(if (on) Modifier.background(Brush.linearGradient(listOf(GradA, GradB))) else Modifier.border(2.dp, ArinaColors.Muted.copy(alpha = 0.6f), CircleShape)),
            contentAlignment = Alignment.Center
        ) { if (on) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
    }
}

@Composable
private fun CompanionStep(vm: ArinaEngine) {
    Title(5, "Choose your companion", "Pick a voice, an orb and a colour. Tap to preview.")
    val cur = vm.persona.value
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Persona.Arina to "Female voice", Persona.Aurion to "Male voice").forEach { (p, n) ->
            Box(Modifier.weight(1f).obGlass(RoundedCornerShape(50), cur == p).clickable { vm.switchPersona(p) }.padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                Text(n, color = ArinaColors.Text, fontSize = 14.sp, fontWeight = if (cur == p) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
        OrbView(cur, Mood.Listening, 0f, Modifier.size(210.dp))
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OrbChoice.hues.forEachIndexed { i, h ->
            val on = OrbChoice.hueOf(cur) == i
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(h.light), Color(h.mid), Color(h.deep))))
                    .border(if (on) 3.dp else 1.dp, if (on) ArinaColors.Text else ArinaColors.Deep, CircleShape)
                    .clickable { OrbChoice.setHue(vm.uiPrefs, cur, i) }
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OrbChoice.allNames.forEachIndexed { i, n ->
            val on = OrbChoice.of(cur) == i
            Box(Modifier.obGlass(RoundedCornerShape(50), on).clickable { OrbChoice.set(vm.uiPrefs, cur, i) }.padding(horizontal = 18.dp, vertical = 11.dp)) {
                Text(n, color = ArinaColors.Text, fontSize = 13.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}
