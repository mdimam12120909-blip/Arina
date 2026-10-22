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
 * Talks to the Gemini API (free tier). Blocking calls: run on Dispatchers.IO.
 * Gemini decides which phone tool to call; we run it and feed the result back.
 */
class GeminiClient(private val apiKey: String, private val tools: ToolRunner) {

    companion object {
        // If this model name is ever retired, change it to another free Gemini Flash model.
        const val MODEL = "gemini-2.5-flash"
        private const val MAX_HISTORY = 30

        private val SYSTEM_PROMPT = """
            You are Arina (অ্যারিনা), a warm, friendly female AI voice assistant living on the user's Android phone.
            The user is Habib Hossan Shahadat; call him Habib (হাবিব).
            ALWAYS reply in natural spoken Bengali (Bangla script). Keep replies to 1-2 short sentences because they are read aloud.
            No markdown, no emojis, no lists.
            When the user asks you to do something on the phone, call the matching tool, then confirm briefly in Bengali.
            Pass app names to open_app in English exactly as they appear on the phone (WhatsApp, YouTube, Camera, Chrome, Settings...).
            Pass phone-number digits as ASCII digits. Contact names can be Bengali or English, as spoken.
            Before send_sms make sure you know both the recipient and the message text; if either is missing, ask.
            Only say an action worked if the tool result starts with OK. If it starts with ERROR, explain simply what went wrong and what the user can do.
            If no tool is needed, just answer helpfully.
        """.trimIndent()
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val history = mutableListOf<JSONObject>()

    private fun textContent(role: String, text: String): JSONObject =
        JSONObject().put("role", role)
            .put("parts", JSONArray().put(JSONObject().put("text", text)))

    private fun callApi(): JSONObject {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))))
            .put("contents", JSONArray(history))
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools.declarations())))
            .put(
                "generationConfig", JSONObject()
                    .put("temperature", 0.7)
                    .put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            )

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }.getOrDefault(raw)
                throw IOException(
                    if (resp.code == 429) "Gemini free limit reached for now. Try again in a minute."
                    else "Gemini error ${resp.code}: $msg"
                )
            }
            return JSONObject(raw)
        }
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

    fun chat(userText: String): String {
        if (apiKey.isBlank()) {
            return "Gemini API key সেট করা নেই। local.properties ফাইলে GEMINI_API_KEY বসান।"
        }
        history += textContent("user", userText)

        repeat(6) {
            val resp = callApi()
            val content = resp.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                ?: return "দুঃখিত, আমি এটার উত্তর দিতে পারছি না।"
            val parts = content.optJSONArray("parts") ?: JSONArray()
            history += content // keep the model turn exactly as returned

            val calls = (0 until parts.length()).map { parts.getJSONObject(it) }.filter { it.has("functionCall") }
            if (calls.isEmpty()) {
                val text = (0 until parts.length()).map { parts.getJSONObject(it) }
                    .filter { !it.optBoolean("thought") }
                    .joinToString(" ") { it.optString("text") }
                    .trim()
                trimHistory()
                return text.ifBlank { "ঠিক আছে।" }
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
        return "কাজটি শেষ করতে পারিনি, আবার বলবেন?"
    }
}
