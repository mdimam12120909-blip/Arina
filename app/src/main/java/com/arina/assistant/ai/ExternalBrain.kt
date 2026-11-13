package com.arina.assistant.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Other AI brains, used only when Gemini has no key or fails. Each is tried in order if its key is set.
 * OpenAI, Groq and DeepSeek share one request format; Anthropic has its own.
 * Keys come from Settings (Keys). With no key set, nothing here ever runs.
 */
object ExternalBrain {
    /** Set by the app: returns the saved key for a provider name, or empty. */
    @Volatile var keyOf: (String) -> String = { "" }

    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()
    private val JSON = "application/json".toMediaType()

    private class Spec(val name: String, val url: String, val model: String)
    private val compat = listOf(
        Spec("groq", "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile"),
        Spec("openai", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini"),
        Spec("deepseek", "https://api.deepseek.com/chat/completions", "deepseek-chat")
    )

    fun available(): Boolean = (compat.map { it.name } + "anthropic").any { keyOf(it).isNotBlank() }

    /** Returns the first answer any configured brain gives, or null. */
    fun complete(system: String, user: String, json: Boolean): String? {
        for (c in compat) {
            val k = keyOf(c.name); if (k.isBlank()) continue
            val t0 = System.currentTimeMillis()
            val r = runCatching { openAiStyle(c, k, system, user, json) }.onFailure { Diag.log("brain ${c.name} failed: ${it.message?.take(100)}") }.getOrNull()
            Diag.log("metric model=${c.name}/${c.model} ms=${System.currentTimeMillis() - t0} ok=${r != null}")
            if (!r.isNullOrBlank()) return r
        }
        val ak = keyOf("anthropic")
        if (ak.isNotBlank()) {
            val t0 = System.currentTimeMillis()
            val r = runCatching { anthropic(ak, system, user) }.onFailure { Diag.log("brain anthropic failed: ${it.message?.take(100)}") }.getOrNull()
            Diag.log("metric model=anthropic ms=${System.currentTimeMillis() - t0} ok=${r != null}")
            if (!r.isNullOrBlank()) return r
        }
        return null
    }

    private fun openAiStyle(c: Spec, key: String, system: String, user: String, json: Boolean): String {
        val body = JSONObject().put("model", c.model)
            .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system)).put(JSONObject().put("role", "user").put("content", user)))
        if (json) body.put("response_format", JSONObject().put("type", "json_object"))
        if (key == "proxy") return JSONObject(Gateway.brain(c.name, body)).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim()
        val req = Request.Builder().url(c.url).header("Authorization", "Bearer $key").post(body.toString().toRequestBody(JSON)).build()
        http.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            return JSONObject(t).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim()
        }
    }

    private fun anthropic(key: String, system: String, user: String): String {
        val body = JSONObject().put("model", "claude-haiku-4-5").put("max_tokens", 2000).put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
        if (key == "proxy") { val a = JSONObject(Gateway.brain("anthropic", body)).getJSONArray("content"); return (0 until a.length()).joinToString("") { a.getJSONObject(it).optString("text") }.trim() }
        val req = Request.Builder().url("https://api.anthropic.com/v1/messages").header("x-api-key", key).header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON)).build()
        http.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            val a = JSONObject(t).getJSONArray("content")
            return (0 until a.length()).joinToString("") { a.getJSONObject(it).optString("text") }.trim()
        }
    }
}
