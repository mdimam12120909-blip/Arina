package com.arina.assistant.ai

import com.arina.assistant.Defaults
import com.arina.assistant.Keys
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * All AI traffic goes through Entrion's server. The real keys live only there: this phone sends its sign-in token
 * and a request, and gets an answer (or, for live voice, a one-use token that expires in minutes). A "key" in the
 * app is only the text "proxy:N" naming which of the five jobs the request belongs to.
 */
object Gateway {
    const val PREFIX = "proxy:"
    private val JSON = "application/json".toMediaType()
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    val client: OkHttpClient get() = http
    @Volatile var useAlphaLive = false

    fun isProxy(key: String) = key.startsWith(PREFIX)
    private fun slot(key: String) = key.removePrefix(PREFIX).toIntOrNull() ?: 0

    private fun call(payload: JSONObject): Request {
        val tok = Keys.authToken() ?: throw IOException("Sign in first.")
        return Request.Builder().url(Defaults.SUPABASE_URL + "/functions/v1/ai-gateway")
            .header("apikey", Defaults.SUPABASE_KEY).header("Authorization", "Bearer $tok")
            .post(payload.toString().toRequestBody(JSON)).build()
    }

    /** A Gemini generateContent / streamGenerateContent request, sent through the server. */
    fun gen(key: String, model: String, stream: Boolean, body: JSONObject): Request =
        call(JSONObject().put("action", "gen").put("slot", slot(key)).put("model", model).put("stream", stream).put("body", body))

    /** A one-use token for a live voice session. */
    fun liveToken(key: String): String {
        http.newCall(call(JSONObject().put("action", "live_token").put("slot", slot(key)))).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) { Diag.log("live token failed: HTTP ${r.code} ${t.take(160)}"); throw IOException("The voice service is not ready (HTTP ${r.code}).") }
            return JSONObject(t).getString("token")
        }
    }

    fun liveUrl(token: String, alpha: Boolean): String =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.${if (alpha) "v1alpha" else "v1beta"}.GenerativeService.BidiGenerateContentConstrained?access_token=$token"

    /** One sentence of speech from the voice service, as a raw 24 kHz 16-bit mono stream. */
    fun tts(text: String, voice: String, model: String, lang: String): Request =
        call(JSONObject().put("action", "tts").put("text", text).put("voice", voice).put("model", model).put("lang", lang))

    /** Another brain (openai, groq, deepseek, anthropic) through the server. Returns the raw answer body. */
    fun brain(provider: String, body: JSONObject): String {
        http.newCall(call(JSONObject().put("action", "brain").put("provider", provider).put("body", body))).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return t
        }
    }

    /** Names of the other brains the server has keys for (never the keys). */
    fun status(): Set<String> {
        http.newCall(call(JSONObject().put("action", "status"))).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val a = JSONObject(t).optJSONArray("providers") ?: return emptySet()
            return (0 until a.length()).map { a.getString(it) }.toSet()
        }
    }
}
