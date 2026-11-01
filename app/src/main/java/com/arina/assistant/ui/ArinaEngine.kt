package com.arina.assistant.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import com.arina.assistant.health.DeviceStats
import com.arina.assistant.health.Finding
import com.arina.assistant.health.PhoneHealth
import com.arina.assistant.routine.Routine
import com.arina.assistant.routine.RoutineScheduler
import com.arina.assistant.routine.RoutineStore
import org.json.JSONArray
import org.json.JSONObject
import com.arina.assistant.Keys
import com.arina.assistant.ai.DeepResearch
import com.arina.assistant.ai.Workflow
import com.arina.assistant.device.FrontCamera
import com.arina.assistant.ai.GeminiClient
import com.arina.assistant.ai.PhoneAgent
import com.arina.assistant.ai.Tasks
import com.arina.assistant.ai.ToolHooks
import com.arina.assistant.device.ShowTools
import com.arina.assistant.device.Termux
import com.arina.assistant.ai.Memory
import com.arina.assistant.ai.Reply
import com.arina.assistant.ai.TavilySearch
import com.arina.assistant.ai.ToolRunner
import com.arina.assistant.device.ArinaAccessibilityService
import com.arina.assistant.device.DeviceActions
import com.arina.assistant.permissions.PermissionCenter
import com.arina.assistant.ai.GeminiLive
import com.arina.assistant.ai.Identity
import com.arina.assistant.cloud.Cloud
import com.arina.assistant.cloud.Local
import com.arina.assistant.Defaults
import com.arina.assistant.ai.Timing
import com.arina.assistant.ai.KeyRole
import com.arina.assistant.voice.SoundLoop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Status { Idle, Listening, Thinking, Speaking }

/** All text here is English: it is the only text the UI shows. Arina speaks Bengali out loud. */
data class ChatMessage(val fromUser: Boolean, val text: String)

/**
 * Arina's brain, shared by the app screen and the floating avatar (one instance per process).
 * Ears (Android Bengali recognizer) -> Gemini -> phone tools -> voice (Cartesia).
 *
 * The mic is a switch: the user turns it on and it stays on, listening again after every
 * answer, until the user turns it off.
 */
class ArinaEngine private constructor(private val app: Application) {

    companion object {
        @Volatile
        private var instance: ArinaEngine? = null

        /** The engine of whoever is signed in now. If the account changed since it was built, it is replaced, never reused. */
        fun get(context: Context): ArinaEngine {
            val who = Local.currentUid(context)
            instance?.let { if (it.ownerId == who) return it }
            val stale = synchronized(this) {
                val cur = instance
                if (cur != null && cur.ownerId == who) return cur
                instance = null
                cur
            }
            runCatching { stale?.shutdown() }
            return synchronized(this) {
                instance ?: ArinaEngine(context.applicationContext as Application).also { instance = it }
            }
        }

        /** Forgets the running engine so the next [get] builds a fresh one (used when the account changes). */
        fun discard() {
            val old = synchronized(this) { instance.also { instance = null } } ?: return
            old.shutdown()
        }
    }

    /** The account this engine was built for. Everything it stores belongs to that account only. */
    private val ownerId: String = Local.currentUid(app)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val status = mutableStateOf(Status.Idle)
    val micOn = mutableStateOf(false)
    val level = mutableFloatStateOf(0f)
    val caption = mutableStateOf("")
    val notice = mutableStateOf<String?>(null)
    val overlayOn = mutableStateOf(false)
    /** True while the floating orb is on screen (the small touch window grows to cover it). */
    val orbVisible = mutableStateOf(false)
    private val chatPrefs = com.arina.assistant.cloud.Local.prefs(app, "arina_chat")
    val messages = mutableStateListOf<ChatMessage>().also { l ->
        runCatching {
            val a = JSONArray(chatPrefs.getString("m", "[]"))
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); l += ChatMessage(o.getBoolean("u"), o.getString("t")) }
        }
        if (l.isEmpty()) l += ChatMessage(false, "Hi, I'm Entrion. Turn the mic on and give me a task.")
    }

    init {
        // The chat survives closing the app: the last 80 messages are saved on this phone.
        scope.launch {
            snapshotFlow { messages.toList() }.collect { list ->
                val a = JSONArray()
                list.takeLast(80).forEach { a.put(JSONObject().put("u", it.fromUser).put("t", it.text)) }
                chatPrefs.edit().putString("m", a.toString()).apply()
            }
        }
    }

    // Keys (editable in the Setup tab)
    val geminiKey: MutableState<String> = mutableStateOf(Keys.gemini(app))
    val geminiSlots = mutableStateListOf<String>().also { it.addAll(Keys.geminiSlots(app)) }
    val geminiKeySet: Boolean get() = geminiKey.value.isNotBlank()
    val geminiKeyCount: Int get() = geminiSlots.count { it.isNotBlank() }

    // Permissions
    val micGranted = mutableStateOf(false)
    val phoneGranted = mutableStateOf(false)
    val overlayGranted = mutableStateOf(false)
    val accessibilityOn = mutableStateOf(false)

    // Memory (stored only on this phone)
    private val memory = Memory(app)

    // Which assistant is active (remembered between launches)
    private val uiPrefs = com.arina.assistant.cloud.Local.prefs(app, "arina_ui")
    val persona = mutableStateOf(
        runCatching { Persona.valueOf(uiPrefs.getString("persona", "Arina").orEmpty()) }.getOrDefault(Persona.Arina)
    )
    val profile: MutableState<String> = mutableStateOf(memory.profile())
    val facts = mutableStateListOf<Memory.Fact>().also { it.addAll(memory.facts()) }
    val researchActive = mutableStateOf(false)

    // ---- Cards, approvals and big jobs ---------------------------------------------------------
    val card = mutableStateOf<Card?>(null)
    val approval = mutableStateOf<Approval?>(null)
    val agentActive = mutableStateOf(false)
    val agentLine = mutableStateOf("")
    /** Goes up each time the screen-edge glow should play. */
    val glow = mutableIntStateOf(0)

    // ---- Routines, companion talk, phone health -------------------------------------------------
    private val routineStore = RoutineStore(app)
    /**
     * Each Gemini key has one job: 1 Arina's conversation, 2 Aurion's conversation, 3 research, 4 workflows and
     * skills, 5 phone agent, camera and background thoughts. A job with no key of its own uses the first key that exists.
     */
    fun keyForRole(role: KeyRole): String {
        val slot = when (role) {
            KeyRole.Talk -> if (persona.value == Persona.Aurion) 1 else 0
            KeyRole.Research -> 2
            KeyRole.Workflow -> 3
            KeyRole.Agent -> 4
        }
        return geminiSlots.getOrNull(slot)?.takeIf { it.isNotBlank() } ?: geminiSlots.firstOrNull { it.isNotBlank() }.orEmpty()
    }

    /** Where the waiting time went in the last spoken exchange; shown in Settings. */
    val lastTiming = mutableStateOf("")

    private val skillStore = com.arina.assistant.ai.SkillStore(app)
    val skills = mutableStateListOf<com.arina.assistant.ai.Skill>().also { it.addAll(skillStore.all()) }
    fun addSkill(name: String, whenText: String, steps: String): Boolean {
        val ok = skillStore.save(name, whenText, steps)
        skills.clear(); skills.addAll(skillStore.all())
        return ok
    }
    fun removeSkill(name: String) {
        skillStore.remove(name)
        skills.clear(); skills.addAll(skillStore.all())
    }

    val routines = mutableStateListOf<Routine>().also { it.addAll(routineStore.all()) }
    val proactiveOn = mutableStateOf(com.arina.assistant.cloud.Local.prefs(app, "arina_ui").getBoolean("proactive", true))

    fun setProactive(on: Boolean) {
        proactiveOn.value = on
        com.arina.assistant.cloud.Local.prefs(app, "arina_ui").edit().putBoolean("proactive", on).apply()
    }

    private fun reloadRoutines() {
        routines.clear()
        routines.addAll(routineStore.all())
    }

    fun daysText(mask: Int): String {
        if (mask and 127 == 127) return "every day"
        val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return names.filterIndexed { i, _ -> (mask shr i) and 1 == 1 }.joinToString(" ").ifBlank { "no days" }
    }

    fun saveRoutine(r: Routine) {
        val list = routineStore.all().filter { it.id != r.id } + r
        routineStore.save(list)
        RoutineScheduler.schedule(app, r)
        reloadRoutines()
    }

    fun toggleRoutine(id: Long) {
        routineStore.get(id)?.let { saveRoutine(it.copy(enabled = !it.enabled)) }
    }

    fun deleteRoutine(id: Long) {
        RoutineScheduler.cancel(app, id)
        routineStore.save(routineStore.all().filter { it.id != id })
        reloadRoutines()
    }

    private fun doAddRoutine(title: String, time: String, days: String, note: String): String {
        val m = Regex("(\\d{1,2})[:.](\\d{2})").find(time) ?: return "ERROR: time must look like 21:30 (24-hour)."
        val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
        if (h !in 0..23 || min !in 0..59) return "ERROR: that time is not valid."
        val d = days.lowercase()
        val mask = when {
            d.isBlank() || "every" in d || "daily" in d -> 127
            "weekday" in d -> 31
            "weekend" in d -> 96
            else -> listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun").foldIndexed(0) { i, acc, n -> if (n in d) acc or (1 shl i) else acc }.let { if (it == 0) 127 else it }
        }
        val r = Routine(System.currentTimeMillis(), title.trim().take(60), h, min, mask, note.trim().take(160), true)
        scope.launch { saveRoutine(r) }
        return "OK: routine '${r.title}' set for ${r.timeText()} (${daysText(mask)})."
    }

    /** True when Entrion may speak up by itself: nothing else is going on. */
    fun canSpeakUnprompted(): Boolean =
        status.value == Status.Idle && !agentActive.value && approval.value == null &&
            ackJob?.isActive != true && job?.isActive != true

    private fun proactiveLine(situation: String): Reply {
        val system = currentIntro() + "\n" +
            "You are speaking up on your own now; nobody asked you a question. React to the situation below like a close, caring friend: warm, a little playful, never preachy, never scolding.\n" +
            "Reply with JSON only: {\"bn\":\"...\",\"en\":\"...\"}. bn: one or two short natural spoken Bengali sentences in Bangla script, at most 22 words. en: the same in English.\n" +
            "Do not reuse any of these recent lines or their wording: ${recentLines.joinToString(" | ")}\n" + memory.promptBlock()
        val raw = gemini.generate(system, "SITUATION: $situation", json = true, role = KeyRole.Agent)
        val o = JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        val bn = o.optString("bn")
        if (bn.isBlank()) throw java.io.IOException("empty nudge")
        recentLines.addLast(bn)
        while (recentLines.size > 8) recentLines.removeFirst()
        return Reply("", bn, o.optString("en").ifBlank { "…" })
    }

    /** Speaks up about [situation] if the moment is quiet. */
    fun nudge(situation: String) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { proactiveLine(situation) }.getOrNull() } ?: return@launch
            if (canSpeakUnprompted()) announce(r)
        }
    }

    /** A routine's time has come: say it even if the app was idle. */
    fun routineDue(r: Routine) {
        scope.launch {
            reloadRoutines()
            val r2 = withContext(Dispatchers.IO) {
                runCatching { proactiveLine("Routine time has come: '${r.title}'. ${r.note} Remind him now, briefly.") }.getOrNull()
            } ?: Reply("", "সময় হয়েছে: ${r.title}।", "Time for: ${r.title}.")
            announce(r2)
        }
    }

    val healthFindings = mutableStateListOf<Finding>()
    val healthScanning = mutableStateOf(false)
    val healthMessage = mutableStateOf("")
    val deviceStats = mutableStateOf<DeviceStats?>(null)

    fun refreshStats() { deviceStats.value = runCatching { PhoneHealth.stats(app) }.getOrNull() }

    fun scanHealth() {
        if (healthScanning.value) return
        healthScanning.value = true
        healthMessage.value = "Scanning installed apps…"
        scope.launch {
            try {
                val list = withContext(Dispatchers.IO) { PhoneHealth.scan(app) }
                healthFindings.clear(); healthFindings.addAll(list)
                healthMessage.value = if (list.isEmpty()) "No suspicious apps found." else "${list.size} app(s) deserve a look."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                healthMessage.value = "Scan failed: ${(e.message ?: "unknown").take(120)}"
            } finally {
                healthScanning.value = false
            }
        }
    }

    fun cleanCache() {
        val freed = PhoneHealth.clearOwnCache(app)
        healthMessage.value = "Cleaned %.1f MB of Entrion's cache.".format(freed / 1_048_576f)
        refreshStats()
    }

    fun openIntent(i: android.content.Intent) {
        runCatching { app.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private val device = DeviceActions(app)
    private val tasks = Tasks(app)
    val taskList = mutableStateListOf<Tasks.Task>().also { it.addAll(tasks.all()) }
    val headlines = mutableStateListOf<Pair<String, String>>()
    val headlinesBusy = mutableStateOf(false)
    val headlinesError = mutableStateOf("")

    private fun reloadTasks() {
        taskList.clear()
        taskList.addAll(tasks.all())
    }

    fun addTaskTyped(text: String) { tasks.add(text); reloadTasks() }
    fun toggleTask(id: Long) { tasks.toggle(id); reloadTasks() }
    fun removeTask(id: Long) { tasks.remove(id); reloadTasks() }

    /** Fetches today's top headlines (one Tavily search) and keeps them for three hours. */
    fun refreshHeadlines(force: Boolean = false) {
        if (!cloud.signedIn || headlinesBusy.value) return
        val last = uiPrefs.getLong("headlines_at", 0L)
        if (!force && headlines.isNotEmpty() && System.currentTimeMillis() - last < 3 * 3600_000L) return
        headlinesBusy.value = true
        scope.launch {
            try {
                val list = withContext(Dispatchers.IO) { TavilySearch.headlines() }
                headlinesError.value = ""
                if (list.isNotEmpty()) {
                    headlines.clear(); headlines.addAll(list)
                    uiPrefs.edit().putLong("headlines_at", System.currentTimeMillis()).apply()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                headlinesError.value = (e.message ?: "unknown").take(140)
            } finally {
                headlinesBusy.value = false
            }
        }
    }

    /** Opens a headline inside a card for one minute. */
    fun openLink(url: String, title: String) = openCard(Card(CardKind.Web, title.take(40), url = url))
    private val show = ShowTools(app)
    private var agentJob: Job? = null
    private var ackJob: Job? = null
    private var cardTimer: Job? = null
    private val recentLines = ArrayDeque<String>()

    private val hooks = object : ToolHooks {
        override fun research(question: String) = startResearch(question)
        override fun deepResearch(topic: String) = startDeepResearch(topic)
        override fun runWorkflow(goal: String) = startWorkflow(goal, null)
        override fun lookCamera(question: String): String = doLook(question)
        override fun useSkill(name: String, task: String): String {
            val sk = skillStore.find(name) ?: return "ERROR: no skill named '$name'. Saved skills: " + skillStore.all().joinToString { it.name }
            return startWorkflow(task.ifBlank { name }, sk)
        }
        override fun saveSkill(name: String, whenText: String, steps: String): String =
            if (addSkill(name, whenText, steps)) "OK: skill '${name.trim()}' saved." else "ERROR: the skill needs a new name and steps (built-in names cannot be replaced)."
        override fun listSkills(): String = skillStore.all().joinToString("\n") { "- ${it.name}: ${it.when_}" }
        override fun showImage(query: String) = doShowImage(query)
        override fun showGallery(album: String, count: Int) = doShowGallery(album, count)
        override fun showFile(name: String) = doShowFile(name)
        override fun showLink(url: String) = doShowLink(url)
        override fun operatePhone(goal: String, chat: Boolean) = startOperate(goal, chat)
        override fun addTask(text: String): String {
            val ok = tasks.add(text)
            scope.launch { reloadTasks() }
            return if (ok) "OK: task added." else "OK: that task is already on the list."
        }
        override fun completeTask(text: String): String {
            val ok = tasks.complete(text)
            scope.launch { reloadTasks() }
            return if (ok) "OK: marked done." else "ERROR: no open task matches '$text'."
        }
        override fun listTasks(): String = tasks.summary()
        override fun addRoutine(title: String, time: String, days: String, note: String): String = doAddRoutine(title, time, days, note)
        override fun listRoutines(): String =
            routines.joinToString("\n") { "- ${it.title} at ${it.timeText()} (${daysText(it.days)})${if (it.enabled) "" else " [off]"}" }.ifBlank { "No routines yet." }
        override fun removeRoutine(title: String): String {
            val hit = routines.firstOrNull { it.title.contains(title, true) } ?: return "ERROR: no routine matches '$title'."
            scope.launch { deleteRoutine(hit.id) }
            return "OK: removed ${hit.title}."
        }
        override fun scanPhone(): String {
            val list = PhoneHealth.scan(app)
            scope.launch { healthFindings.clear(); healthFindings.addAll(list) }
            return if (list.isEmpty()) "OK: no suspicious apps found."
            else "OK: ${list.size} apps deserve a look; the most risky: " + list.take(3).joinToString { "${it.label} (${it.risk.label}: ${it.reasons.first()})" } + ". The full list is in Settings, Phone Health."
        }
        override fun termux(command: String): String {
            if (!Termux.installed(app)) return "ERROR: Termux is not installed on this phone."
            val ok = runBlocking { ask("Run a command in Termux", command, ASK_LINES) }
            if (!ok) return "ERROR: the user did not allow this command."
            return Termux.run(app, command)
        }
    }

    private val gemini: GeminiClient = GeminiClient(
        { role -> keyForRole(role) },
        ToolRunner(device, memory, { scope.launch { reloadFacts() } }, hooks),
        { memory.promptBlock() },
        { currentIntro() },
        { skillStore.index() }
    )
    private val sound = SoundLoop()

    // ---- Entrion account and cloud settings --------------------------------------------------

    val cloud = Cloud(app)
    val signedIn = mutableStateOf(cloud.signedIn)
    init { TavilySearch.token = { cloud.accessToken() } }
    val accountName = mutableStateOf(cloud.name)
    val accountEmail = mutableStateOf(cloud.email)
    val isAdmin = mutableStateOf(false)
    val signInBusy = mutableStateOf(false)
    /** The person chose to look around before signing in. */
    val skippedSignIn = mutableStateOf(uiPrefs.getBoolean("skip_signin", false))
    fun skipSignIn() { skippedSignIn.value = true; uiPrefs.edit().putBoolean("skip_signin", true).apply() }
    /** Shows the sign-in screen again (for someone who skipped it). */
    fun openSignIn() { skippedSignIn.value = false; uiPrefs.edit().putBoolean("skip_signin", false).apply() }

    /** What the user calls the agent. Empty: the agent has no personal name. */
    val agentName = mutableStateOf(uiPrefs.getString("agent_name", "").orEmpty())
    fun setAgentName(n: String) {
        agentName.value = n.trim()
        uiPrefs.edit().putString("agent_name", n.trim()).apply()
        gemini.reset()
    }

    /** The agent's character: the admin's published text if there is one, plus the name the user gave it. */
    private fun currentIntro(): String {
        val male = persona.value == Persona.Aurion
        val base = Cloud.cfg(app, if (male) "instructions_male" else "instructions_female", persona.value.intro)
        val n = agentName.value.trim()
        val style = Cloud.cfg(app, "speaking_style", "").trim().removeSurrounding("\"")
        val facts = Cloud.cfg(app, "global_facts", "").trim()
        val extra = (if (style.isNotEmpty()) "\nHOW TO SPEAK (set by Entrion): $style" else "") +
            (if (facts.isNotEmpty()) "\nFACTS YOU KNOW (set by Entrion; treat as true):\n$facts" else "")
        return base + extra + if (n.isEmpty()) " You have no personal name; if the user wants, they can give you one." else " The user has named you \"$n\"; that is your name."
    }

    /** Called with Google's ID token after the account chooser. */
    fun signInWithGoogle(idToken: String) {
        signInBusy.value = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { cloud.signInWithGoogle(idToken) }
                // Each account's personal data lives apart from every other account's. Nothing is ever moved between them.
                switchAccount()
            } catch (e: Exception) {
                notice.value = (e.message ?: "Sign-in failed.").take(240)
            } finally {
                signInBusy.value = false
            }
        }
    }

    /**
     * Set by the screen: rebuilds the screen with a fresh engine. The app stays open; only the account's
     * data is swapped, so nothing of the previous account remains in memory.
     */
    var onAccountSwitched: (() -> Unit)? = null
    private fun switchAccount() { onAccountSwitched?.invoke() }

    fun signInProblem(message: String) { notice.value = message.take(240) }

    fun signOut() {
        cloud.signOut()
        Identity.userName = ""
        // Back to the signed-out (guest) data, which holds nothing of any account.
        switchAccount()
    }

    /** Downloads the admin's settings and checks whether this account is the admin. Quietly does nothing offline. */
    fun refreshCloud() {
        scope.launch {
            val admin = withContext(Dispatchers.IO) {
                runCatching { cloud.fetchConfig() }
                if (cloud.signedIn) runCatching { cloud.isAdmin() }.getOrDefault(false) else false
            }
            signedIn.value = cloud.signedIn
            accountName.value = cloud.name
            accountEmail.value = cloud.email
            Identity.userName = cloud.name
            isAdmin.value = admin
            gemini.reset()
        }
    }

    /** Admin only. Saves one published setting; the database refuses anyone else. */
    fun adminSave(key: String, value: String, secret: Boolean = false) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { if (secret) cloud.saveSecret(key, value) else cloud.saveConfig(key, value) }
                if (!secret) withContext(Dispatchers.IO) { runCatching { cloud.fetchConfig() } }
                gemini.reset()
                notice.value = "Saved."
            } catch (e: Exception) {
                notice.value = (e.message ?: "Could not save.").take(240)
            }
        }
    }

    val searchStatus = mutableStateOf("")

    /** Admin: shows whether the web search key is stored (last 4 characters) and whether a real search works. */
    fun checkSearch() {
        scope.launch {
            searchStatus.value = "Checking…"
            val msg = withContext(Dispatchers.IO) {
                val tail = runCatching { cloud.secretTail("tavily") }.getOrElse { return@withContext "Could not read the key: ${it.message}" }
                if (tail == null) return@withContext "No search key is stored yet."
                runCatching { TavilySearch.search("today", 1); "Key stored (ends with $tail). Search works." }
                    .getOrElse { "Key stored (ends with $tail), but search failed: ${(it.message ?: "unknown").take(200)}" }
            }
            searchStatus.value = msg
        }
    }

    fun saveSearchKey(key: String) {
        scope.launch {
            searchStatus.value = "Saving…"
            try {
                withContext(Dispatchers.IO) { cloud.saveSecret("tavily", key.trim()) }
                checkSearch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                searchStatus.value = "Not saved: ${(e.message ?: "unknown error").take(200)}"
            }
        }
    }

    fun adminValue(key: String, fallback: String = ""): String = Cloud.cfg(app, key, fallback)

    // ---- Admin tools (the database refuses all of these for anyone but the admin) -------------------
    val adminFacts = mutableStateListOf<Cloud.Fact>()
    val adminUsers = mutableStateListOf<Cloud.User>()
    val adminStatsText = mutableStateOf("")
    val adminBusy = mutableStateOf(false)

    private fun adminRun(block: suspend () -> Unit) {
        scope.launch {
            adminBusy.value = true
            try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { notice.value = (e.message ?: "Not done.").take(240) } finally { adminBusy.value = false }
        }
    }

    fun loadFacts() = adminRun {
        val l = withContext(Dispatchers.IO) { cloud.facts() }
        adminFacts.clear(); adminFacts.addAll(l)
    }

    fun addGlobalFact(text: String) {
        if (text.isBlank()) return
        adminRun {
            withContext(Dispatchers.IO) { cloud.addFact(text); runCatching { cloud.fetchConfig() } }
            val l = withContext(Dispatchers.IO) { cloud.facts() }
            adminFacts.clear(); adminFacts.addAll(l)
            notice.value = "Fact added for every user."
        }
    }

    fun deleteGlobalFact(id: Long) = adminRun {
        withContext(Dispatchers.IO) { cloud.deleteFact(id); runCatching { cloud.fetchConfig() } }
        adminFacts.removeAll { it.id == id }
    }

    fun loadUsers() = adminRun {
        val (list, st) = withContext(Dispatchers.IO) { cloud.users() to cloud.stats() }
        adminUsers.clear(); adminUsers.addAll(list)
        adminStatsText.value = "${st.optInt("total")} signed up  |  ${st.optInt("today")} today  |  ${st.optInt("week")} this week  |  ${st.optInt("active_week")} active this week  |  ${st.optInt("banned")} banned"
    }

    fun banUser(id: String, ban: Boolean) = adminRun {
        withContext(Dispatchers.IO) { cloud.setBan(id, ban) }
        notice.value = if (ban) "User banned. They are signed out." else "User allowed again."
        val list = withContext(Dispatchers.IO) { cloud.users() }
        adminUsers.clear(); adminUsers.addAll(list)
    }

    fun removeUser(id: String) = adminRun {
        withContext(Dispatchers.IO) { cloud.deleteUser(id) }
        adminUsers.removeAll { it.id == id }
        notice.value = "User and their data removed."
    }

    private val liveSession: GeminiLive by lazy {
        GeminiLive(
            { keyForRole(KeyRole.Talk) },
            { gemini.liveSystemPrompt() },
            ToolRunner(device, memory, { scope.launch { reloadFacts() } }, hooks),
            { if (persona.value == Persona.Aurion) Cloud.cfg(app, "live_voice_male", Defaults.LIVE_VOICE_AURION) else Cloud.cfg(app, "live_voice_female", Defaults.LIVE_VOICE_ARINA) },
            object : GeminiLive.Callbacks {
                override fun onLevel(level: Float) { scope.launch { if (status.value == Status.Listening) this@ArinaEngine.level.floatValue = level } }
                override fun onUserSpeaking() { scope.launch { notice.value = null; caption.value = ""; status.value = Status.Thinking } }
                override fun onModelSpeaking() { scope.launch { status.value = Status.Speaking } }
                override fun onToolRunning(name: String) { scope.launch { status.value = Status.Thinking } }
                override fun onTurn(user: String, model: String) {
                    scope.launch {
                        if (user.isNotBlank()) messages += ChatMessage(true, user)
                        if (model.isNotBlank()) { messages += ChatMessage(false, model); caption.value = model }
                        lastTiming.value = Timing.report()
                        status.value = if (micOn.value) Status.Listening else Status.Idle
                    }
                }
                override fun onError(message: String) {
                    scope.launch { fail(message); turnOff() }
                }
            }
        )
    }
    private val researchJobs = mutableListOf<Job>()
    private var job: Job? = null
    private var failStreak = 0

    /** Switches between Arina (female) and Aurion (male): voice, avatar and personality change together. */
    fun switchPersona(p: Persona) {
        if (p == persona.value) return
        job?.cancel()
        stopEars()
        level.floatValue = 0f
        status.value = Status.Idle
        gemini.reset()
        persona.value = p
        liveSession.stop()
        uiPrefs.edit().putString("persona", p.name).apply()
        caption.value = ""
        notice.value = null
        messages += ChatMessage(false, "${p.displayName} agent here, part of Entrion by Kashikoi.")
        if (micOn.value) restartListening()
    }

    /** Status of every permission row in the Settings tab, by row id. */
    val perms = mutableStateMapOf<String, Boolean>()

    fun refreshSetup() {
        for (item in PermissionCenter.runtime + PermissionCenter.special) {
            perms[item.id] = runCatching { PermissionCenter.isGranted(app, item) }.getOrDefault(false)
        }
        micGranted.value = perms["mic"] == true
        overlayGranted.value = perms["overlay"] == true
        phoneGranted.value = perms["phone"] == true
        accessibilityOn.value = perms["accessibility"] == true
    }

    // ---- Listening ---------------------------------------------------------------------------

    private fun stopEars() {
        liveSession.mute()
    }

    fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun reloadFacts() {
        facts.clear()
        facts.addAll(memory.facts())
    }

    fun saveProfile(text: String) {
        memory.setProfile(text)
        profile.value = memory.profile()
        notice.value = "Profile saved on this phone."
    }

    fun addFact(text: String) {
        memory.add(text)
        reloadFacts()
    }

    fun deleteFact(id: Long) {
        memory.remove(id)
        reloadFacts()
    }

    fun saveKeys(gemini: List<String>) {
        Keys.save(app, gemini)
        geminiKey.value = Keys.gemini(app)
        geminiSlots.clear(); geminiSlots.addAll(Keys.geminiSlots(app))
        liveSession.stop()
        if (micOn.value) restartListening()
        notice.value = "Saved."
    }

    // ---- Mic switch -------------------------------------------------------------------------

    /** Avatar or mic tap: switch the mic on, or switch everything off. */
    fun onMicTap() {
        if (micOn.value) turnOff() else turnOn()
    }

    private fun turnOn() {
        glow.intValue++
        micOn.value = true
        notice.value = null
        failStreak = 0
        startListening()
    }

    fun turnOff() {
        micOn.value = false
        job?.cancel()
        stopEars()
        liveSession.stop()
        level.floatValue = 0f
        status.value = Status.Idle
    }

    /** Ends everything this engine started; it is not used again afterwards. */
    fun shutdown() {
        stopAll()
        liveSession.stop()
        scope.cancel()
    }

    /** Called when the floating avatar is closed. */
    fun stopAll() {
        turnOff()
        researchJobs.toList().forEach { it.cancel() }
        researchJobs.clear()
        stopAgent()
        closeCard()
        approval.value?.answer?.complete(false)
        sound.stop()
        researchActive.value = false
    }

    private fun startListening() {
        if (!micOn.value) return
        status.value = Status.Listening
        liveSession.start()
    }

    private fun restartListening() {
        scope.launch {
            delay(300)
            if (micOn.value && (job?.isActive != true) &&
                status.value != Status.Speaking && status.value != Status.Thinking
            ) startListening()
        }
    }

    // ---- Talking ----------------------------------------------------------------------------

    fun sendText(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (status.value == Status.Thinking || status.value == Status.Speaking) return
        notice.value = null
        caption.value = ""
        messages += ChatMessage(true, t)
        status.value = Status.Thinking
        liveSession.start()
        if (!micOn.value) liveSession.mute()
        liveSession.sendText(t)
    }

    private fun fail(msg: String) {
        notice.value = msg
        caption.value = msg
        messages += ChatMessage(false, msg)
        status.value = Status.Idle
    }

    // ---- Background research ------------------------------------------------------------------

    /**
     * Called by Gemini's research tool. Returns at once; the work continues in the background while the
     * mic stays usable. A soft hum plays meanwhile, and the answer is announced when it is ready.
     */
    private fun startResearch(question: String): String {
        val q = question.trim()
        if (q.isEmpty()) return "ERROR: no question given."
        if (!cloud.signedIn) return "ERROR: web search needs a signed-in account. Tell him to sign in from Settings."
        scope.launch {
            val me = coroutineContext[Job]!!
            researchJobs += me
            researchActive.value = true
            val hum = launch {
                while (true) {
                    if (status.value == Status.Speaking) sound.stop() else sound.start()
                    delay(250)
                }
            }
            try {
                val reply = withContext(Dispatchers.IO) {
                    val material = TavilySearch.search(q)
                    gemini.summarize(q, material)
                }
                hum.cancel()
                sound.stop()
                withContext(Dispatchers.IO) { gemini.noteResearch(q, reply) }
                announce(reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hum.cancel()
                sound.stop()
                notice.value = "Research failed: ${(e.message ?: e.javaClass.simpleName).take(200)}"
                messages += ChatMessage(false, "I couldn't finish the research: ${(e.message ?: "unknown error").take(160)}")
            } finally {
                hum.cancel()
                researchJobs.remove(me)
                if (researchJobs.isEmpty()) {
                    sound.stop()
                    researchActive.value = false
                }
            }
        }
        ack(SEARCH_LINES)
        return TOLD
    }

    // ---- Spoken lines, approvals, cards -------------------------------------------------------

    private val TOLD = "OK: started. The user was ALREADY told, so say nothing more now."
    private val SEARCH_LINES = listOf("I am looking it up.", "Give me a moment, checking.", "Searching right now.", "Let me look that up online.", "One minute, finding out.")
    private val DEEP_LINES = listOf("Starting the deep work, it will take a few minutes.", "This is a big job, I will go through many sources.", "Started, I will tell you when it is done.", "Working through it step by step, it takes a little time.")
    private val SHOW_LINES = listOf("Here it is.", "Showing it now.", "Got it, take a look.", "On the screen now.")
    private val OPERATE_LINES = listOf("Allow it on the screen and I will start.", "As soon as you allow it, I begin.", "Give permission and I will run the phone.")
    private val PROGRESS_LINES = listOf("Making good progress, checking more sources.", "Still working, cross-checking the facts.", "Nearly there, a little more.", "Found quite a lot, verifying it now.")
    private val ASK_LINES = listOf("Look at the screen, I need your permission.", "I will go ahead once you allow it.", "I need a yes from you, see the screen.")

    /** Picks a line that was not used recently, so the same sentence is never repeated back to back. */
    private fun fresh(options: List<String>): String {
        val pick = options.filter { it !in recentLines }.ifEmpty { options }.random()
        recentLines.addLast(pick)
        while (recentLines.size > 8) recentLines.removeFirst()
        return pick
    }

    /** Has the agent say a short line right now (before the slow work starts). Only works while a voice conversation is open. */
    private fun ack(options: List<String>) {
        val line = fresh(options)
        if (liveSession.active) {
            status.value = Status.Speaking
            liveSession.sendText("Say this to the user now in one short sentence, in the language he speaks, in your own words: $line")
        }
    }

    /** Asks the user to allow something big. Waits up to 45 seconds; no answer means no. */
    private suspend fun ask(title: String, detail: String, lines: List<String>): Boolean {
        val d = CompletableDeferred<Boolean>()
        approval.value = Approval(title, detail, d)
        ack(lines)
        val ok = withTimeoutOrNull(45_000) { d.await() } ?: false
        approval.value = null
        return ok
    }

    fun answerApproval(ok: Boolean) {
        approval.value?.answer?.complete(ok)
    }

    private fun openCard(c: Card) {
        cardTimer?.cancel()
        card.value = c
        if (c.seconds > 0) cardTimer = scope.launch {
            delay(c.seconds * 1000L)
            if (card.value === c) card.value = null
        }
    }

    fun closeCard() {
        cardTimer?.cancel()
        card.value = null
    }

    private fun doShowImage(query: String): String {
        if (!cloud.signedIn) return "ERROR: picture search needs a signed-in account. Tell him to sign in from Settings."
        val files = show.downloadImages(TavilySearch.images(query), 4)
        if (files.isEmpty()) return "ERROR: no picture could be loaded for '$query'. Tell him briefly."
        ack(SHOW_LINES)
        scope.launch { openCard(Card(CardKind.Images, query.take(40), files)) }
        return TOLD
    }

    private fun doShowGallery(album: String, count: Int): String {
        if (!show.hasPhotoAccess()) return "ERROR: Photos permission is off. Tell him to allow Photos in the Settings tab."
        val list = show.gallery(album, count)
        if (list.isEmpty()) return "ERROR: no photos found${if (album.isBlank()) "" else " in '$album'"}."
        ack(SHOW_LINES)
        scope.launch { openCard(Card(CardKind.Images, if (album.isBlank()) "Gallery" else album, list)) }
        return TOLD
    }

    private fun doShowFile(name: String): String {
        val f = show.findFile(name)
            ?: return "ERROR: no file matching '$name' in Downloads, Documents, Pictures or DCIM. (Seeing documents needs 'All files access' in Settings.)"
        val ext = f.extension.lowercase()
        val c = when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> Card(CardKind.Images, f.name, listOf(f.path))
            "pdf" -> Card(CardKind.Images, f.name, show.pdfPages(f))
            "txt", "md", "csv", "json", "log", "xml", "html", "kt", "py", "js" -> Card(CardKind.Text, f.name, text = show.textPreview(f))
            else -> return "ERROR: a .$ext file cannot be previewed here. Found: ${f.path}"
        }
        ack(SHOW_LINES)
        scope.launch { openCard(c) }
        return TOLD
    }

    private fun doShowLink(url: String): String {
        val u = url.trim().let { if (it.startsWith("http")) it else "https://$it" }
        ack(SHOW_LINES)
        scope.launch { openCard(Card(CardKind.Web, u.removePrefix("https://").take(40), url = u)) }
        return TOLD
    }

    // ---- Deep research ----------------------------------------------------------------------

    /** Runs on the tool thread (IO). One front-camera photo, looked at by Gemini; the photo is not saved. */
    private fun doLook(question: String): String {
        if (app.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            return "ERROR: camera permission is not allowed. Tell the user to allow Camera in Settings, Permissions."
        scope.launch { caption.value = "Looking through the front camera…" }
        val jpeg = try {
            FrontCamera.capture(app)
        } catch (e: Exception) {
            return "ERROR: could not use the front camera: ${(e.message ?: e.javaClass.simpleName).take(120)}"
        }
        return try {
            "OK: " + gemini.vision(jpeg, question.ifBlank { "Describe what you see." })
        } catch (e: Exception) {
            "ERROR: could not understand the photo: ${(e.message ?: e.javaClass.simpleName).take(120)}"
        }
    }

    private fun startWorkflow(goal: String, skill: com.arina.assistant.ai.Skill? = null): String {
        val g = goal.trim()
        if (g.isEmpty()) return "ERROR: no goal given."
        ack(DEEP_LINES)
        scope.launch {
            val me = coroutineContext[Job]!!
            researchJobs += me
            researchActive.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    Workflow.run(
                        goal = g, gemini = gemini, skill = skill,
                        onProgress = { done, total, line -> scope.launch { caption.value = "Working: step $done of $total, $line" } },
                        cancelled = { !me.isActive }
                    )
                }
                openCard(Card(CardKind.Text, "Result: ${g.take(36)}", text = result.output, seconds = 0))
                withContext(Dispatchers.IO) { gemini.noteResearch(g, Reply("", result.bn, result.en)) }
                announce(Reply("", result.bn.ifBlank { "কাজ শেষ, ফলাফল স্ক্রিনে আছে।" }, result.en.ifBlank { "Done. The result is on screen." }))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice.value = "Workflow failed: ${(e.message ?: e.javaClass.simpleName).take(200)}"
            } finally {
                researchJobs.remove(me)
                if (researchJobs.isEmpty()) researchActive.value = false
            }
        }
        return TOLD
    }

    private fun startDeepResearch(topic: String): String {
        val t = topic.trim()
        if (t.isEmpty()) return "ERROR: no topic given."
        if (!cloud.signedIn) return "ERROR: web search needs a signed-in account. Tell him to sign in from Settings."
        ack(DEEP_LINES)
        scope.launch {
            val me = coroutineContext[Job]!!
            researchJobs += me
            researchActive.value = true
            val hum = launch {
                while (true) {
                    if (status.value == Status.Speaking) sound.stop() else sound.start()
                    delay(250)
                }
            }
            try {
                val result = withContext(Dispatchers.IO) {
                    DeepResearch.run(t, gemini, { round, total, sources ->
                        scope.launch {
                            caption.value = "Deep research: round $round of $total, $sources sources"
                            if (round == 2 || round == 3) announce(Reply("", "", fresh(PROGRESS_LINES) + " ($sources sources so far)"))
                        }
                    }, { !me.isActive })
                }
                hum.cancel(); sound.stop()
                openCard(Card(CardKind.Text, "Research: ${t.take(36)}", text = result.report, seconds = 0))
                withContext(Dispatchers.IO) { gemini.noteResearch(t, Reply("", result.bn, result.en)) }
                announce(Reply("", result.bn.ifBlank { "গবেষণা শেষ, রিপোর্ট স্ক্রিনে আছে।" }, result.en.ifBlank { "Research finished. The report is on screen." }))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hum.cancel(); sound.stop()
                notice.value = "Deep research failed: ${(e.message ?: e.javaClass.simpleName).take(200)}"
            } finally {
                hum.cancel()
                researchJobs.remove(me)
                if (researchJobs.isEmpty()) { sound.stop(); researchActive.value = false }
            }
        }
        return TOLD
    }

    // ---- Operating the phone ------------------------------------------------------------------

    private fun startOperate(goal: String, chat: Boolean): String {
        if (ArinaAccessibilityService.instance == null) {
            return "ERROR: Device control (Accessibility) is off. Tell him to turn it on in the Settings tab."
        }
        if (agentJob?.isActive == true) return "ERROR: already operating the phone. Tell him to press STOP first."
        agentJob = scope.launch {
            val ok = ask("Let Entrion operate your phone?", "It will tap, type and scroll by itself to:\n$goal\n\nPress STOP at any time to end it.", OPERATE_LINES)
            if (!ok) {
                announce(Reply("", fresh(listOf("ঠিক আছে, করছি না।", "আচ্ছা, থাক।", "বাদ দিলাম।")), "Okay, I won't do that."))
                return@launch
            }
            agentActive.value = true
            agentLine.value = "Starting…"
            try {
                val outcome = withContext(Dispatchers.IO) {
                    PhoneAgent(gemini, device).run(goal, chat, { line -> agentLine.value = line }, {
                        while (agentHold && agentJob?.isActive == true) Thread.sleep(100)
                        agentJob?.isActive != true
                    })
                }
                agentActive.value = false
                if (outcome.report.isNotBlank()) openCard(Card(CardKind.Text, "Result", text = outcome.report, seconds = 0))
                announce(Reply("", outcome.bn, outcome.en))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice.value = "Phone control stopped: ${(e.message ?: e.javaClass.simpleName).take(160)}"
            } finally {
                agentActive.value = false
            }
        }
        return TOLD
    }

    /** The STOP button: cut off whatever is being said or worked on right now, keep the mic as it is. */
    fun interrupt() {
        liveSession.interruptPlayback()
        job?.cancel()
        ackJob?.cancel()
        stopAgent()
        level.floatValue = 0f
        status.value = Status.Idle
        if (micOn.value) restartListening()
    }

    @Volatile private var agentHold = false

    fun stopAgent() {
        agentJob?.cancel()
        agentActive.value = false
        agentLine.value = ""
    }

    /** Tells the user a finished result. Spoken by the agent while a voice conversation is open; otherwise shown as text. */
    private suspend fun announce(r: Reply) {
        while (status.value == Status.Thinking || status.value == Status.Speaking) delay(400)
        level.floatValue = 0f
        messages += ChatMessage(false, r.en)
        caption.value = r.en
        if (liveSession.active) {
            status.value = Status.Speaking
            liveSession.sendText("Tell the user this now, briefly, in the language he speaks and in your own words: ${r.en}")
        }
    }
}
