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
 * Arina's voice: Cartesia only (model sonic-3.6, which supports Bengali). There is deliberately
 * no phone-voice fallback. If Cartesia fails, [speak] throws an IOException whose message is shown on screen.
 */
class VoiceOutput(
    private val ctx: Context,
    private val credentials: () -> Pair<String, String> // API key, Voice ID
) {
    private companion object {
        const val URL = "https://api.cartesia.ai/tts/bytes"
        const val API_VERSION = "2026-08-14"
        const val MODEL = "sonic-3.6"
        const val LANGUAGE = "bn"
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var player: MediaPlayer? = null

    suspend fun speak(text: String) {
        val (key, voiceId) = credentials()
        if (key.isBlank() || voiceId.isBlank()) {
            throw IOException("Cartesia API key or Voice ID is missing. Paste them in the Setup tab and tap Save.")
        }
        val file = withContext(Dispatchers.IO) { fetch(text, key.trim(), voiceId.trim()) }
        playFile(file)
    }

    private fun fetch(text: String, key: String, voiceId: String): File {
        val body = JSONObject()
            .put("model_id", MODEL)
            .put("transcript", text)
            .put("voice", JSONObject().put("id", voiceId))
            .put("language", LANGUAGE)
            .put(
                "output_format",
                JSONObject().put("container", "mp3").put("sample_rate", 44100).put("bit_rate", 128000)
            )
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(URL)
            .header("Authorization", "Bearer $key")
            .header("Cartesia-Version", API_VERSION)
            .post(body)
            .build()

        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw IOException("Cartesia request failed: ${e.message ?: "no internet?"}")
        }
        response.use { resp ->
            if (resp.isSuccessful) {
                val bytes = resp.body?.bytes()
                if (bytes == null || bytes.isEmpty()) throw IOException("Cartesia returned empty audio.")
                val f = File(ctx.cacheDir, "arina_voice.mp3")
                f.writeBytes(bytes)
                return f
            }
            // The exact error JSON shape is not documented, so show what the server actually said.
            val raw = resp.body?.string().orEmpty()
            val detail = runCatching {
                val j = JSONObject(raw)
                j.optString("message").ifBlank { j.optString("error") }.ifBlank { raw }
            }.getOrElse { raw }.take(220)
            throw IOException("Cartesia error ${resp.code}: $detail")
        }
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
