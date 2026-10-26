package com.arina.assistant.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.arina.assistant.Keys
import com.arina.assistant.ai.GeminiClient
import com.arina.assistant.ai.Memory
import com.arina.assistant.ai.Reply
import com.arina.assistant.ai.TavilySearch
import com.arina.assistant.ai.ToolRunner
import com.arina.assistant.device.ArinaAccessibilityService
import com.arina.assistant.device.DeviceActions
import com.arina.assistant.permissions.PermissionCenter
import com.arina.assistant.voice.MicListener
import com.arina.assistant.voice.SoundLoop
import com.arina.assistant.voice.SpeechInput
import com.arina.assistant.voice.SpeechProblem
import com.arina.assistant.voice.VoiceOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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

        fun get(context: Context): ArinaEngine =
            instance ?: synchronized(this) {
                instance ?: ArinaEngine(context.applicationContext as Application).also { instance = it }
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val status = mutableStateOf(Status.Idle)
    val micOn = mutableStateOf(false)
    val level = mutableFloatStateOf(0f)
    val caption = mutableStateOf("")
    val notice = mutableStateOf<String?>(null)
    val overlayOn = mutableStateOf(false)
    val messages = mutableStateListOf(
        ChatMessage(false, "Hi, I'm Arina. Turn the mic on and give me a task.")
    )

    // Keys (editable in the Setup tab)
    val geminiKey: MutableState<String> = mutableStateOf(Keys.gemini(app))
    val cartesiaKey: MutableState<String> = mutableStateOf(Keys.cartesia(app))
    val voiceId: MutableState<String> = mutableStateOf(Keys.voiceId(app))
    val tavilyKey: MutableState<String> = mutableStateOf(Keys.tavily(app))
    val voiceIdAurion: MutableState<String> = mutableStateOf(Keys.voiceIdAurion(app))
    val tavilyKeySet: Boolean get() = tavilyKey.value.isNotBlank()
    val geminiKeySet: Boolean get() = geminiKey.value.isNotBlank()
    val cartesiaKeySet: Boolean get() = cartesiaKey.value.isNotBlank() && voiceId.value.isNotBlank()

    // Permissions
    val micGranted = mutableStateOf(false)
    val phoneGranted = mutableStateOf(false)
    val overlayGranted = mutableStateOf(false)
    val accessibilityOn = mutableStateOf(false)

    // Memory (stored only on this phone)
    private val memory = Memory(app)

    // Which assistant is active (remembered between launches)
    private val uiPrefs = app.getSharedPreferences("arina_ui", Context.MODE_PRIVATE)
    val persona = mutableStateOf(
        runCatching { Persona.valueOf(uiPrefs.getString("persona", "Arina").orEmpty()) }.getOrDefault(Persona.Arina)
    )
    val profile: MutableState<String> = mutableStateOf(memory.profile())
    val facts = mutableStateListOf<Memory.Fact>().also { it.addAll(memory.facts()) }
    val researchActive = mutableStateOf(false)

    private val gemini: GeminiClient = GeminiClient(
        { geminiKey.value },
        ToolRunner(DeviceActions(app), memory, { scope.launch { reloadFacts() } }, { q -> startResearch(q) }),
        { memory.promptBlock() },
        { persona.value.intro }
    )
    private val sound = SoundLoop()
    private val researchJobs = mutableListOf<Job>()
    private val voice: VoiceOutput = VoiceOutput(app) { cartesiaKey.value to currentVoiceId() }
    private val ears: SpeechInput = SpeechInput(app)
    private var job: Job? = null
    private var failStreak = 0

    private fun currentVoiceId(): String =
        if (persona.value == Persona.Aurion) voiceIdAurion.value else voiceId.value

    /** Switches between Arina (female) and Aurion (male): voice, avatar and personality change together. */
    fun switchPersona(p: Persona) {
        if (p == persona.value) return
        job?.cancel()
        stopEars()
        voice.stop()
        level.floatValue = 0f
        status.value = Status.Idle
        gemini.reset()
        persona.value = p
        uiPrefs.edit().putString("persona", p.name).apply()
        caption.value = ""
        notice.value = if (p == Persona.Aurion && voiceIdAurion.value.isBlank())
            "Aurion has no voice yet. Paste his Cartesia Voice ID in Setup and tap Save." else null
        messages += ChatMessage(false, "Hi, I'm ${p.displayName}.")
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

    /** true: our own microphone capture and Gemini hears the audio (no beep). false: Android's speech recognizer. */
    val geminiEars = mutableStateOf(uiPrefs.getBoolean("gemini_ears", true))
    private val mic = MicListener()

    private fun stopEars() {
        ears.cancel()
        mic.stop()
    }

    fun setGeminiEars(on: Boolean) {
        geminiEars.value = on
        uiPrefs.edit().putBoolean("gemini_ears", on).apply()
        if (micOn.value && status.value == Status.Listening) {
            stopEars()
            startListening()
        }
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

    fun saveKeys(gemini: String, cartesia: String, voice: String, tavily: String, aurionVoice: String) {
        Keys.save(app, gemini, cartesia, voice, tavily, aurionVoice)
        voiceIdAurion.value = Keys.voiceIdAurion(app)
        tavilyKey.value = Keys.tavily(app)
        geminiKey.value = Keys.gemini(app)
        cartesiaKey.value = Keys.cartesia(app)
        voiceId.value = Keys.voiceId(app)
        notice.value = "Saved."
    }

    /** Speaks a short test line so the Cartesia key and voice can be checked. */
    fun testVoice() {
        if (status.value != Status.Idle) return
        notice.value = null
        status.value = Status.Speaking
        job = scope.launch {
            try {
                voice.speak("আমি অ্যারিনা। আমার কণ্ঠ ঠিকমতো কাজ করছে।")
                notice.value = "Voice test OK."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice.value = (e.message ?: "Voice test failed.").take(260)
            } finally {
                status.value = Status.Idle
                if (micOn.value) restartListening()
            }
        }
    }

    // ---- Mic switch -------------------------------------------------------------------------

    /** Avatar or mic tap: switch the mic on, or switch everything off. */
    fun onMicTap() {
        if (micOn.value) turnOff() else turnOn()
    }

    private fun turnOn() {
        micOn.value = true
        notice.value = null
        failStreak = 0
        startListening()
    }

    fun turnOff() {
        micOn.value = false
        job?.cancel()
        stopEars()
        voice.stop()
        level.floatValue = 0f
        status.value = Status.Idle
    }

    /** Called when the floating avatar is closed. */
    fun stopAll() {
        turnOff()
        researchJobs.toList().forEach { it.cancel() }
        researchJobs.clear()
        sound.stop()
        researchActive.value = false
    }

    private fun startListening() {
        if (!micOn.value) return
        status.value = Status.Listening
        if (geminiEars.value) {
            mic.start(
                onLevel = { level.floatValue = it },
                onSpeech = { wav ->
                    level.floatValue = 0f
                    failStreak = 0
                    handle(null, wav, typed = false)
                },
                onError = { msg ->
                    level.floatValue = 0f
                    notice.value = msg
                    turnOff()
                }
            )
            return
        }
        ears.listen(
            onLevel = { level.floatValue = it },
            onFinal = { text ->
                level.floatValue = 0f
                if (text.isBlank()) restartListening()
                else {
                    failStreak = 0
                    handle(text, null, typed = false)
                }
            },
            onProblem = { kind, message ->
                level.floatValue = 0f
                when (kind) {
                    SpeechProblem.Silence -> restartListening()
                    SpeechProblem.Transient ->
                        if (++failStreak <= 6) restartListening()
                        else {
                            notice.value = message
                            turnOff()
                        }
                    SpeechProblem.Fatal -> {
                        notice.value = message
                        turnOff()
                    }
                }
            }
        )
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
        stopEars()
        handle(t, null, typed = true)
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
        val key = tavilyKey.value
        if (key.isBlank()) {
            return "ERROR: the web search key is missing. Tell the user to paste a free Tavily key in the Setup tab."
        }
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
                    val material = TavilySearch.search(key, q)
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
        return "OK: research started in the background; the answer will be announced automatically. Now tell the user briefly that you are looking it up and he can do other things."
    }

    /** Speaks a finished research result once nothing else is being said. */
    private suspend fun announce(r: Reply) {
        while (status.value == Status.Thinking || status.value == Status.Speaking) delay(400)
        stopEars()
        level.floatValue = 0f
        messages += ChatMessage(false, r.en)
        caption.value = r.en
        status.value = Status.Speaking
        try {
            voice.speak(r.bn)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notice.value = (e.message ?: "Voice failed.").take(260)
        } finally {
            status.value = Status.Idle
            if (micOn.value) restartListening()
        }
    }

    private fun handle(text: String?, wav: ByteArray?, typed: Boolean) {
        notice.value = null
        caption.value = ""
        if (typed && text != null) messages += ChatMessage(true, text)
        status.value = Status.Thinking
        job = scope.launch {
            try {
                val reply = try {
                    withContext(Dispatchers.IO) { if (wav != null) gemini.chatAudio(wav) else gemini.chat(text.orEmpty()) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fail("Error: ${(e.message ?: e.javaClass.simpleName).take(220)}")
                    return@launch
                }
                if (!typed) messages += ChatMessage(true, reply.you.ifBlank { "(voice request)" })
                messages += ChatMessage(false, reply.en)
                caption.value = reply.en
                status.value = Status.Speaking
                try {
                    voice.speak(reply.bn)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    notice.value = (e.message ?: "Voice failed.").take(260)
                }
            } finally {
                status.value = Status.Idle
                if (micOn.value) restartListening()
            }
        }
    }
}
