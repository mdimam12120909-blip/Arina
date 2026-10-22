package com.arina.assistant.voice

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.arina.assistant.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Arina's voice. Uses ElevenLabs when keys are set; otherwise (or if ElevenLabs fails)
 * falls back to Android's built-in Bengali text-to-speech.
 */
class VoiceOutput(private val ctx: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("bn", "BD"))
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    suspend fun speak(text: String) {
        val file = if (BuildConfig.ELEVENLABS_API_KEY.isNotBlank() && BuildConfig.ELEVENLABS_VOICE_ID.isNotBlank()) {
            withContext(Dispatchers.IO) { fetchElevenLabs(text) }
        } else null
        if (file != null) playFile(file) else speakFallback(text)
    }

    private fun fetchElevenLabs(text: String): File? {
        // eleven_v3 supports Bengali; multilingual_v2 is the backup model.
        for (model in listOf("eleven_v3", "eleven_multilingual_v2")) {
            try {
                val body = JSONObject().put("text", text).put("model_id", model).toString()
                    .toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("https://api.elevenlabs.io/v1/text-to-speech/${BuildConfig.ELEVENLABS_VOICE_ID}?output_format=mp3_44100_64")
                    .header("xi-api-key", BuildConfig.ELEVENLABS_API_KEY)
                    .post(body)
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            val f = File(ctx.cacheDir, "arina_tts.mp3")
                            f.writeBytes(bytes)
                            return f
                        }
                    } else {
                        Log.w("Arina", "ElevenLabs $model failed: ${resp.code} ${resp.body?.string()}")
                    }
                }
            } catch (e: Exception) {
                Log.w("Arina", "ElevenLabs $model error", e)
            }
        }
        return null
    }

    private suspend fun playFile(file: File) = suspendCancellableCoroutine<Unit> { cont ->
        val mp = MediaPlayer()
        player = mp
        fun finish() {
            runCatching { mp.release() }
            if (player === mp) player = null
            if (cont.isActive) cont.resume(Unit)
        }
        try {
            mp.setDataSource(file.path)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { finish() }
            mp.setOnErrorListener { _, _, _ -> finish(); true }
            mp.prepareAsync()
        } catch (e: Exception) {
            finish()
        }
        cont.invokeOnCancellation { runCatching { mp.release() }; if (player === mp) player = null }
    }

    private suspend fun speakFallback(text: String) = suspendCancellableCoroutine<Unit> { cont ->
        val engine = tts
        if (engine == null || !ttsReady) {
            cont.resume(Unit)
            return@suspendCancellableCoroutine
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "arina")
        cont.invokeOnCancellation { engine.stop() }
    }

    fun stop() {
        runCatching { player?.release() }
        player = null
        tts?.stop()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
