package com.arina.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * The assistant's voice: Gemini text-to-speech through the Interactions API
 * (POST /v1beta/interactions, response_format audio, speech_config voice). Bengali is detected from the text.
 * Uses the same Gemini keys as the brain, one after the other. If every key or model fails, [speak] throws an
 * IOException whose message is shown on screen; there is deliberately no phone-voice fallback.
 */
class VoiceOutput(
    private val ctx: Context,
    private val keys: () -> String,   // all Gemini keys, separated by new lines
    private val voiceName: () -> String
) {
    private companion object {
        const val URL = "https://generativelanguage.googleapis.com/v1beta/interactions"
        // Tried in this order; the first that works stays first. The lite model is meant for speed.
        val MODELS = listOf("gemini-3.8-flash-lite-tts", "gemini-3.8-flash-tts")
        const val RATE = 24000
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private var player: MediaPlayer? = null
    @Volatile private var goodModel: String? = null
    private val cursor = AtomicInteger()

    suspend fun speak(text: String) {
        val list = keys().split(Regex("[,;\\s]+")).filter { it.length > 10 }
        if (list.isEmpty()) throw IOException("Gemini API key is missing. Add a key in Settings and tap Save.")
        val file = withContext(Dispatchers.IO) { fetch(text, list) }
        playFile(file)
    }

    private class Retry(message: String) : IOException(message)

    private fun fetch(text: String, keyList: List<String>): File {
        val voice = voiceName().trim().ifBlank { "Kore" }
        var last: IOException? = null
        val models = listOfNotNull(goodModel) + MODELS.filter { it != goodModel }
        for (model in models) {
            val start = Math.floorMod(cursor.getAndIncrement(), keyList.size)
            for (i in keyList.indices) {
                val key = keyList[(start + i) % keyList.size]
                try {
                    val f = once(model, key, voice, text)
                    goodModel = model
                    return f
                } catch (e: Retry) {
                    last = e
                    // A key out of quota: try the next key. Anything else (model missing...): try the next model.
                    if (e.message?.contains("error 429") != true) break
                }
            }
        }
        throw last ?: IOException("Gemini voice did not answer.")
    }

    private fun once(model: String, key: String, voice: String, text: String): File {
        val body = JSONObject()
            .put("model", model)
            .put("input", JSONArray().put(JSONObject()
                .put("type", "user_input")
                .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))))
            .put("response_format", JSONObject().put("type", "audio"))
            .put("generation_config", JSONObject().put("speech_config", JSONArray().put(JSONObject().put("voice", voice))))
            .toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(URL).header("x-goog-api-key", key).post(body).build()

        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw IOException("Voice request failed: ${e.message ?: "no internet?"}")
        }
        response.use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val detail = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }.getOrDefault(raw).take(200)
                val msg = "Gemini voice error ${resp.code} ($model): $detail"
                if (resp.code == 429 || resp.code == 404 || resp.code >= 500 ||
                    (resp.code == 400 && detail.contains("model", ignoreCase = true))
                ) throw Retry(msg)
                throw IOException(msg)
            }
            val b64 = findAudio(JSONObject(raw)) ?: throw Retry("Gemini voice error 200 ($model): no audio in the answer")
            var bytes = Base64.decode(b64, Base64.DEFAULT)
            // Unary answers are WAV; if raw 16-bit PCM comes back instead, give it a WAV header.
            if (bytes.size < 4 || String(bytes, 0, 4) != "RIFF") bytes = wav(bytes)
            val f = File(ctx.cacheDir, "arina_voice.wav")
            f.writeBytes(bytes)
            return f
        }
    }

    /** First object of type "audio" that carries base64 "data", wherever it sits in the answer. */
    private fun findAudio(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                if (node.optString("type") == "audio" && node.optString("data").length > 100) return node.getString("data")
                node.keys().forEach { k -> findAudio(node.opt(k))?.let { return it } }
            }
            is JSONArray -> for (i in 0 until node.length()) findAudio(node.opt(i))?.let { return it }
        }
        return null
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        fun le(v: Int, n: Int) { for (i in 0 until n) out.write((v shr (8 * i)) and 0xFF) }
        out.write("RIFF".toByteArray()); le(36 + pcm.size, 4); out.write("WAVEfmt ".toByteArray())
        le(16, 4); le(1, 2); le(1, 2); le(RATE, 4); le(RATE * 2, 4); le(2, 2); le(16, 2)
        out.write("data".toByteArray()); le(pcm.size, 4); out.write(pcm)
        return out.toByteArray()
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
