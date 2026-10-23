package com.arina.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Arina's voice: ElevenLabs only. There is deliberately no phone-voice fallback.
 * If ElevenLabs fails, [speak] throws an IOException whose message is shown on screen.
 */
class VoiceOutput(
    private val ctx: Context,
    private val credentials: () -> Pair<String, String> // API key, Voice ID
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var player: MediaPlayer? = null

    suspend fun speak(text: String) {
        val (key, voiceId) = credentials()
        if (key.isBlank() || voiceId.isBlank()) {
            throw IOException("ElevenLabs API key or Voice ID is missing. Paste them in the Setup tab and tap Save.")
        }
        val file = withContext(Dispatchers.IO) { fetch(text, key.trim(), voiceId.trim()) }
        playFile(file)
    }

    private fun fetch(text: String, key: String, voiceId: String): File {
        var firstError: String? = null
        // eleven_v3 supports Bengali. multilingual_v2 is only a backup if v3 is not available on the account.
        for (model in listOf("eleven_v3", "eleven_multilingual_v2")) {
            val body = JSONObject().put("text", text).put("model_id", model).toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=mp3_44100_64")
                .header("xi-api-key", key)
                .header("Accept", "audio/mpeg")
                .post(body)
                .build()
            val response = try {
                http.newCall(request).execute()
            } catch (e: IOException) {
                throw IOException("ElevenLabs request failed: ${e.message ?: "no internet?"}")
            }
            response.use { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val f = File(ctx.cacheDir, "arina_voice.mp3")
                        f.writeBytes(bytes)
                        return f
                    }
                    if (firstError == null) firstError = "ElevenLabs returned empty audio."
                } else {
                    val raw = resp.body?.string().orEmpty()
                    val detail = runCatching { JSONObject(raw).getJSONObject("detail").getString("message") }
                        .getOrElse { raw.take(160) }
                    var msg = "ElevenLabs error ${resp.code}: $detail"
                    if (resp.code == 402 || detail.contains("library", ignoreCase = true) ||
                        detail.contains("paid", ignoreCase = true)
                    ) {
                        msg += " (Free plans cannot use Voice Library voices through the API. Use a voice from My Voices.)"
                    }
                    if (firstError == null) firstError = msg
                    // A wrong key, no credits or a wrong voice will not be fixed by another model.
                    if (resp.code == 401 || resp.code == 402 || resp.code == 429 ||
                        detail.contains("voice", ignoreCase = true)
                    ) throw IOException(msg)
                }
                Unit
            }
        }
        throw IOException(firstError ?: "ElevenLabs returned no audio.")
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
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(file.path)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { finish() }
            mp.setOnErrorListener { _, _, _ -> finish(); true }
            mp.prepareAsync()
        } catch (e: Exception) {
            finish()
        }
        cont.invokeOnCancellation {
            runCatching { mp.release() }
            if (player === mp) player = null
        }
    }

    fun stop() {
        runCatching { player?.release() }
        player = null
    }
}
