package com.arina.assistant.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.arina.assistant.BuildConfig
import com.arina.assistant.ai.GeminiClient
import com.arina.assistant.ai.ToolRunner
import com.arina.assistant.device.ArinaAccessibilityService
import com.arina.assistant.device.DeviceActions
import com.arina.assistant.voice.SpeechInput
import com.arina.assistant.voice.VoiceOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Status { Idle, Listening, Thinking, Speaking }

/** All text here is English: it is the only text the UI shows. Arina speaks Bengali out loud. */
data class ChatMessage(val fromUser: Boolean, val text: String)

/**
 * Arina's brain, shared by the app screen and the floating avatar (one instance per process).
 * Hearing -> Gemini -> phone tools -> speaking.
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
    val level = mutableFloatStateOf(0f)
    val caption = mutableStateOf("")
    val notice = mutableStateOf<String?>(null)
    val overlayOn = mutableStateOf(false)
    val messages = mutableStateListOf(
        ChatMessage(false, "Hi, I'm Arina. Tap the mic and give me a task.")
    )

    // Setup state
    val micGranted = mutableStateOf(false)
    val phoneGranted = mutableStateOf(false)
    val overlayGranted = mutableStateOf(false)
    val accessibilityOn = mutableStateOf(false)
    val geminiKeySet = BuildConfig.GEMINI_API_KEY.isNotBlank()
    val elevenKeySet = BuildConfig.ELEVENLABS_API_KEY.isNotBlank() && BuildConfig.ELEVENLABS_VOICE_ID.isNotBlank()

    private val gemini = GeminiClient(BuildConfig.GEMINI_API_KEY, ToolRunner(DeviceActions(app)))
    private val voice = VoiceOutput(app)
    private val ears = SpeechInput(app)
    private var job: Job? = null

    fun refreshSetup() {
        fun granted(p: String) = app.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        micGranted.value = granted(Manifest.permission.RECORD_AUDIO)
        phoneGranted.value = granted(Manifest.permission.CALL_PHONE) &&
            granted(Manifest.permission.READ_CONTACTS) && granted(Manifest.permission.SEND_SMS)
        overlayGranted.value = Settings.canDrawOverlays(app)
        accessibilityOn.value = ArinaAccessibilityService.instance != null
    }

    fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 || app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Avatar or mic tap: start listening, or stop whatever Arina is doing. */
    fun onMicTap() {
        when (status.value) {
            Status.Idle -> startListening()
            Status.Listening -> ears.stop()
            Status.Thinking, Status.Speaking -> {
                job?.cancel()
                voice.stop()
                status.value = Status.Idle
            }
        }
    }

    private fun startListening() {
        notice.value = null
        status.value = Status.Listening
        ears.listen(
            onLevel = { level.floatValue = it },
            onFinal = { text ->
                level.floatValue = 0f
                if (text.isBlank()) fail("I didn't catch that. Tap and try again.", addToChat = false)
                else handle(text, typed = false)
            },
            onError = { msg ->
                level.floatValue = 0f
                fail(msg, addToChat = false)
            }
        )
    }

    fun sendText(text: String) {
        val t = text.trim()
        if (t.isNotEmpty() && status.value == Status.Idle) handle(t, typed = true)
    }

    private fun fail(msg: String, addToChat: Boolean = true) {
        notice.value = msg
        caption.value = msg
        if (addToChat) messages += ChatMessage(false, msg)
        status.value = Status.Idle
    }

    private fun handle(text: String, typed: Boolean) {
        notice.value = null
        caption.value = ""
        if (typed) messages += ChatMessage(true, text)
        status.value = Status.Thinking
        job = scope.launch {
            val reply = try {
                withContext(Dispatchers.IO) { gemini.chat(text) }
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
            } finally {
                status.value = Status.Idle
            }
        }
    }

    /** Called when the floating avatar is closed. */
    fun stopAll() {
        job?.cancel()
        ears.cancel()
        voice.stop()
        level.floatValue = 0f
        status.value = Status.Idle
    }
}
