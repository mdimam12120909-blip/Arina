package com.arina.assistant.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * What Arina answers: [bn] is spoken aloud (Bengali), [en] is the only text shown in the UI,
 * [you] is an English paraphrase of what the user asked.
 */
data class Reply(val you: String, val bn: String, val en: String)

private class ModelUnavailable(message: String) : IOException(message)

/**
 * Talks to the Gemini API. Blocking calls: run on Dispatchers.IO.
 * Gemini decides which phone tool to call; we run it and feed the result back.
 */
class GeminiClient(private val apiKeyProvider: () -> String, private val tools: ToolRunner) {

    companion object {
        // Tried in this order until one works for the user's key. Google retires models often.
        private val MODELS = listOf(
            "gemini-3.5-flash-lite", // documented as thinking "minimal" by default: the fastest
            "gemini-3.6-flash",
            "gemini-3.8-flash",
            "gemini-flash-latest",
            "gemini-2.5-flash"
        )
        private const val ATTEMPT_SECONDS = 15L
        private const val TOTAL_MILLIS = 45_000L
        private const val MAX_HISTORY = 30

        private val SYSTEM_PROMPT = """
            You are Arina, a warm, friendly female AI voice assistant living on the user's Android phone.
            The user is Habib Hossan Shahadat; call him Habib.
            When the user asks you to do something on the phone, call the matching tool, then confirm.
            Pass app names to open_app in English exactly as they appear on the phone (WhatsApp, YouTube, Camera, Chrome, Settings...).
            Pass phone-number digits as ASCII digits. Contact names can be Bengali or English, as spoken.
            Before send_sms make sure you know both the recipient and the message text; if either is missing, ask.
            Only say an action worked if the tool result starts with OK. If it starts with ERROR, explain simply what went wrong.
            If no tool is needed, just answer helpfully.
            Be quick and natural, like a person in conversation: keep BN to about 12 words unless the user asks for detail.

            FINAL ANSWER FORMAT (always exactly these three lines, nothing else, no markdown, no emojis):
            YOU: <very short English paraphrase of what the user asked>
            BN: <your reply in natural spoken Bengali, Bangla script, 1-2 short sentences, it will be read aloud>
            EN: <short English translation of your Bengali reply, it is the only text shown on screen>
        """.trimIndent()
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(ATTEMPT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(ATTEMPT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val history = mutableListOf<JSONObject>()
    private var activeModel: String? = null

    // Models that rejected the "low thinking" setting; they are called without it.
    private val noThinkingConfig = mutableSetOf<String>()

    private class BadThinkingConfig : IOException("thinking config rejected")

    private fun callModel(model: String): JSONObject {
        if (model in noThinkingConfig) return send(model, withThinking = false)
        return try {
            send(model, withThinking = true)
        } catch (e: BadThinkingConfig) {
            noThinkingConfig += model
            send(model, withThinking = false)
        }
    }

    private fun send(model: String, withThinking: Boolean): JSONObject {
        val generation = JSONObject().put("temperature", 0.7)
        // Lower thinking = faster answers. "low" is the lowest level documented for every current Flash model.
        if (withThinking) generation.put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))

        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))))
            .put("contents", JSONArray(history))
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools.declarations())))
            .put("generationConfig", generation)

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKeyProvider().trim())
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val (code, raw) = try {
            http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: InterruptedIOException) {
            throw ModelUnavailable("Gemini ($model) was too slow and was skipped.")
        } catch (e: IOException) {
            throw IOException("Network problem reaching Gemini: ${e.message ?: "no internet?"}")
        }
        if (code in 200..299) return JSONObject(raw)

        val msg = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }
            .getOrDefault(raw).take(180)
        if (withThinking && code == 400 && msg.contains("thinking", ignoreCase = true)) throw BadThinkingConfig()

        val text = "Gemini error $code ($model): $msg"
        val keyProblem = msg.contains("API key", ignoreCase = true)
        // Wrong/retired model, overloaded or per-model quota: try the next model. A bad key will not get better.
        if (!keyProblem && (code == 404 || code == 429 || code >= 500 ||
                ((code == 400 || code == 403) && msg.contains("model", ignoreCase = true)))
        ) throw ModelUnavailable(text)
        throw IOException(text)
    }

    private fun callApi(): JSONObject {
        var last: IOException? = null
        val deadline = System.currentTimeMillis() + TOTAL_MILLIS
        val order = listOfNotNull(activeModel) + MODELS.filter { it != activeModel }
        for (m in order) {
            if (System.currentTimeMillis() > deadline) break
            try {
                val r = callModel(m)
                activeModel = m
                return r
            } catch (e: ModelUnavailable) {
                last = e
            }
        }
        activeModel = null
        throw last ?: IOException("No Gemini model answered in time.")
    }

    private fun trimHistory() {
        while (history.size > MAX_HISTORY) history.removeAt(0)
        // History must start with a plain user text turn, never a tool result or a model turn.
        while (history.isNotEmpty()) {
            val first = history[0]
            val isUser = first.optString("role") == "user"
            val parts = first.optJSONArray("parts")
            val hasTool = parts != null && (0 until parts.length()).any {
                parts.getJSONObject(it).has("functionResponse")
            }
            if (isUser && !hasTool) break
            history.removeAt(0)
        }
    }

    private fun parse(text: String): Reply {
        fun field(tag: String): String? =
            Regex("(?im)^\\s*" + tag + "\\s*:\\s*(.+)$").find(text)?.groupValues?.get(1)?.trim()
        val bn = field("BN")
        return if (bn != null) Reply(field("YOU").orEmpty(), bn, field("EN") ?: "Done.")
        else Reply("", text, "Done.")
    }

    fun chat(userText: String): Reply {
        if (apiKeyProvider().isBlank()) {
            throw IOException("Gemini API key is missing. Paste it in the Setup tab and tap Save.")
        }
        history += JSONObject().put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", userText)))

        repeat(6) {
            val resp = callApi()
            val content = resp.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                ?: throw IOException("Gemini returned an empty answer. Try rephrasing.")
            val parts = content.optJSONArray("parts") ?: JSONArray()
            history += content // keep the model turn exactly as returned

            val calls = (0 until parts.length()).map { parts.getJSONObject(it) }.filter { it.has("functionCall") }
            if (calls.isEmpty()) {
                val text = (0 until parts.length()).map { parts.getJSONObject(it) }
                    .filter { !it.optBoolean("thought") }
                    .joinToString("\n") { it.optString("text") }
                    .trim()
                trimHistory()
                return parse(text)
            }

            val responses = JSONArray()
            for (part in calls) {
                val fc = part.getJSONObject("functionCall")
                val name = fc.getString("name")
                val result = tools.run(name, fc.optJSONObject("args") ?: JSONObject())
                responses.put(
                    JSONObject().put(
                        "functionResponse",
                        JSONObject().put("name", name).put("response", JSONObject().put("result", result))
                    )
                )
            }
            history += JSONObject().put("role", "user").put("parts", responses)
        }
        trimHistory()
        throw IOException("I could not finish that task. Please try again.")
    }
}
