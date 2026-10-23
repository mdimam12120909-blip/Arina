package com.arina.assistant.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
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
            "gemini-flash-latest",
            "gemini-3.8-flash",
            "gemini-3.6-flash",
            "gemini-3.5-flash-lite",
            "gemini-2.5-flash"
        )
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

            FINAL ANSWER FORMAT (always exactly these three lines, nothing else, no markdown, no emojis):
            YOU: <very short English paraphrase of what the user asked>
            BN: <your reply in natural spoken Bengali, Bangla script, 1-2 short sentences, it will be read aloud>
            EN: <short English translation of your Bengali reply, it is the only text shown on screen>
        """.trimIndent()
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val history = mutableListOf<JSONObject>()
    private var activeModel: String? = null

    private fun callModel(model: String): JSONObject {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))))
            .put("contents", JSONArray(history))
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools.declarations())))
            .put("generationConfig", JSONObject().put("temperature", 0.7))

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKeyProvider().trim())
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (resp.isSuccessful) return JSONObject(raw)

            val msg = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }
                .getOrDefault(raw).take(180)
            val text = "Gemini error ${resp.code} ($model): $msg"
            val keyProblem = msg.contains("API key", ignoreCase = true)
            // Wrong/retired model or per-model quota: try the next model. A bad key will not get better.
            if (!keyProblem && (resp.code == 404 || resp.code == 429 || resp.code >= 500 ||
                    ((resp.code == 400 || resp.code == 403) && msg.contains("model", ignoreCase = true)))
            ) throw ModelUnavailable(text)
            throw IOException(text)
        }
    }

    private fun callApi(): JSONObject {
        var last: IOException? = null
        // Overloaded or retired model: move on to the next one. If every model is busy, wait a moment and go round once more.
        for (round in 0..1) {
            val order = listOfNotNull(activeModel) + MODELS.filter { it != activeModel }
            for (m in order) {
                try {
                    val r = callModel(m)
                    activeModel = m
                    return r
                } catch (e: ModelUnavailable) {
                    last = e
                }
            }
            if (round == 0) Thread.sleep(1500)
        }
        activeModel = null
        throw last ?: IOException("No Gemini model is available for this API key.")
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
