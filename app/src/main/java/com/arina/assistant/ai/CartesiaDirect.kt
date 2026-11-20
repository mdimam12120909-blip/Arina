package com.arina.assistant.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Speaks with the person's own Cartesia key straight from the phone; the key never goes to Entrion's server. */
object CartesiaDirect {
    fun request(key: String, text: String, voice: String, model: String, lang: String): Request {
        val body = JSONObject()
            .put("model_id", model.ifBlank { "sonic-3" })
            .put("transcript", text)
            .put("voice", JSONObject().put("mode", "id").put("id", voice.trim()))
            .put("language", lang)
            .put("output_format", JSONObject().put("container", "raw").put("encoding", "pcm_s16le").put("sample_rate", 24000))
        return Request.Builder().url("https://api.cartesia.ai/tts/bytes")
            .header("X-API-Key", key.trim()).header("Cartesia-Version", "2025-04-16")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
    }

    /** A short sentence the person can act on. */
    fun explain(code: Int, body: String, own: Boolean): String = when {
        own && (code == 401 || code == 403) -> "Your Cartesia key was refused. Check it in My voice and save it again."
        own && code == 402 -> "Your Cartesia credits have run out."
        own && (code == 404 || body.contains("not found", true)) -> "That voice ID was not found in your Cartesia account. Check the ID."
        own && code == 429 -> "Cartesia is busy or your limit is reached. Try again in a moment."
        own -> "Cartesia could not speak (code $code). Check the key and voice ID in My voice."
        else -> "The voice service failed (code $code), so the built-in voice is used. Tap the mic to continue."
    }
}
