package com.arina.assistant.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arina.assistant.BuildConfig
import com.arina.assistant.ai.GeminiClient
import com.arina.assistant.ai.ToolRunner
import com.arina.assistant.device.ArinaAccessibilityService
import com.arina.assistant.device.DeviceActions
import com.arina.assistant.voice.SpeechInput
import com.arina.assistant.voice.VoiceOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Status { Idle, Listening, Thinking, Speaking }

data class ChatMessage(val fromUser: Boolean, val text: String)

class ArinaViewModel(app: Application) : AndroidViewModel(app) {

    val status = mutableStateOf(Status.Idle)
    val level = mutableFloatStateOf(0f)
    val partial = mutableStateOf("")
    val notice = mutableStateOf<String?>(null)
    val messages = mutableStateListOf(
        ChatMessage(false, "আমি অ্যারিনা, আপনার ব্যক্তিগত AI সহকারী! আপনি কি আমাকে কোনো কাজ দিতে চান?")
    )

    // Setup screen state
    val micGranted = mutableStateOf(false)
    val phoneGranted = mutableStateOf(false)
    val accessibilityOn = mutableStateOf(false)
    val geminiKeySet = BuildConfig.GEMINI_API_KEY.isNotBlank()
    val elevenKeySet = BuildConfig.ELEVENLABS_API_KEY.isNotBlank() && BuildConfig.ELEVENLABS_VOICE_ID.isNotBlank()

    private val gemini = GeminiClient(BuildConfig.GEMINI_API_KEY, ToolRunner(DeviceActions(app)))
    private val voice = VoiceOutput(app)
    private val ears = SpeechInput(app)
    private var job: Job? = null

    fun refreshSetup() {
        val ctx = getApplication<Application>()
        fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        micGranted.value = granted(Manifest.permission.RECORD_AUDIO)
        phoneGranted.value = granted(Manifest.permission.CALL_PHONE) &&
            granted(Manifest.permission.READ_CONTACTS) && granted(Manifest.permission.SEND_SMS)
        accessibilityOn.value = ArinaAccessibilityService.instance != null
    }

    /** Mic button: start listening, or stop whatever Arina is doing. */
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
        partial.value = ""
        status.value = Status.Listening
        ears.listen(
            onPartial = { partial.value = it },
            onLevel = { level.floatValue = it },
            onFinal = { text ->
                partial.value = ""
                level.floatValue = 0f
                if (text.isBlank()) {
                    status.value = Status.Idle
                    notice.value = "I didn't catch that. Tap the mic and try again."
                } else handle(text)
            },
            onError = { msg ->
                partial.value = ""
                level.floatValue = 0f
                status.value = Status.Idle
                notice.value = msg
            }
        )
    }

    fun sendText(text: String) {
        val t = text.trim()
        if (t.isNotEmpty() && status.value == Status.Idle) handle(t)
    }

    private fun handle(text: String) {
        notice.value = null
        messages += ChatMessage(true, text)
        status.value = Status.Thinking
        job = viewModelScope.launch {
            val reply = try {
                withContext(Dispatchers.IO) { gemini.chat(text) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "দুঃখিত, একটি সমস্যা হয়েছে: ${e.message}"
            }
            messages += ChatMessage(false, reply)
            status.value = Status.Speaking
            try {
                voice.speak(reply)
            } finally {
                status.value = Status.Idle
            }
        }
    }

    override fun onCleared() {
        ears.cancel()
        voice.shutdown()
    }
}
