package com.arina.assistant.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import android.util.Base64
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

/** This one key was refused (invalid, expired, blocked). Another key may still work. */
private class BadKey(message: String) : IOException(message)

/** Which job a Gemini call belongs to. Each job has its own key in Settings, so jobs never wait for each other's quota. */
enum class KeyRole(val label: String) {
    Talk("conversation"),
    Research("research"),
    Workflow("workflow and skills"),
    Agent("phone agent, camera and background")
}

/** Per-call options: ask for JSON, or allow a slow, long answer (reports). */
private data class Opts(val json: Boolean = false, val slow: Boolean = false, val stream: Boolean = false, val role: KeyRole = KeyRole.Talk)

/**
 * Talks to the Gemini API. Blocking calls: run on Dispatchers.IO.
 * Gemini decides which phone tool to call; we run it and feed the result back.
 */
class GeminiClient(
    private val keyFor: (KeyRole) -> String,
    private val tools: ToolRunner,
    private val memoryBlock: () -> String,
    private val personaIntro: () -> String,
    private val skillsBlock: () -> String = { "" }
) {

    /** True while a task is running in the background: noise and side talk are then likely, so be stricter. */
    @Volatile var busy: Boolean = false

    /**
     * When set, the answer is streamed and the Bengali (BN:) line is passed to this function piece by piece while
     * the model is still writing, so the voice can start early. Set it only around one conversation turn.
     */
    @Volatile var speakSink: ((String) -> Unit)? = null

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

        private val SUMMARY_PROMPT = """
            You are Arina, a warm female voice assistant. The user asked a question and a web search was done for it.
            Using ONLY the search material provided, write the answer to be spoken aloud: 2 to 4 short natural Bengali sentences with the key facts and numbers.
            If sources disagree or the material is thin, say so briefly. Never invent facts.

            FINAL ANSWER FORMAT (always exactly these three lines, nothing else, no markdown, no emojis):
            YOU: <very short English paraphrase of the question>
            BN: <the answer in natural spoken Bengali, Bangla script>
            EN: <short English version of the answer, it is the only text shown on screen>
        """.trimIndent()

        private val BUSY_NOTE = """

            A background task is running right now, so the microphone may pick up room noise or other people. Answer audio only if it is clearly addressed to you: a stop, cancel or change, or a new request. Otherwise reply YOU: (noise), BN: -, EN: -.
        """.trimIndent()

        /** Instructions for the live voice conversation: Gemini hears and answers in speech, so there is no BN/EN/YOU format. */
        private val LIVE_PROMPT = """
            {INTRO}
            The user is Habib Hossan Shahadat; call him Habib.${Identity.PROMPT}
            LANGUAGE: you are speaking aloud. Answer in the language he speaks to you (Bengali, English or any other); if unclear, use Bengali. Keep it natural and spoken.
            STYLE: sharp, confident, direct. Lead with the answer or the action. No greeting, no "sure/of course", no closing offer, no apologies. One or two short sentences unless he asks for detail. Never repeat an opening or filler you used before; do not say his name in every reply.
            NOISE: if what you hear is silence, noise, music, TV or people talking to each other and not to you, stay completely silent. Never ask him to repeat.
            When he asks you to do something on the phone, call the matching tool, then confirm in a few words. Only say an action worked if the tool result starts with OK; if it starts with ERROR, explain simply what went wrong.
            Pass app names to open_app in English exactly as on the phone. Pass phone-number digits as ASCII digits. Before send_sms make sure you know both recipient and message.
            QUALITY OVER SPEED for real tasks: for a big or multi-part job call run_workflow once with the complete goal; for pure research call deep_research; for things done inside apps call operate_phone; for saved skills call use_skill; to remember how to do something call save_skill.
            For fresh facts (news, prices, scores, latest, comparisons, anything unsure) call research instead of guessing. The app itself tells him you are looking it up BEFORE the work starts, so after a tool whose result says the user was already told, say nothing at all. Never make up an answer.
            When he asks you to look at him or something in front of the phone, call look_camera with his question and describe the result in your own words.
            When he wants to SEE something call show_image, show_gallery, show_file or show_link. When he wants a Termux command run call termux; he will be asked to allow each one.
        """.trimIndent()

        private val SYSTEM_PROMPT = """
            {INTRO}
            The user is Habib Hossan Shahadat; call him Habib.${Identity.PROMPT}
            When the user asks you to do something on the phone, call the matching tool, then confirm.
            Pass app names to open_app in English exactly as they appear on the phone (WhatsApp, YouTube, Camera, Chrome, Settings...).
            Pass phone-number digits as ASCII digits. Contact names can be Bengali or English, as spoken.
            Before send_sms make sure you know both the recipient and the message text; if either is missing, ask.
            Only say an action worked if the tool result starts with OK. If it starts with ERROR, explain simply what went wrong.
            If no tool is needed, just answer helpfully.
            When the user's message is audio, understand the speech (Bengali or English) and answer; put what he said, paraphrased in English, in the YOU line.
            Never repeat a sentence, opening or filler you already used earlier in this conversation; vary your wording every time, and do not say his name in every reply. Never announce the same action twice.
            QUALITY OVER SPEED: never rush a real task. Understand the goal first, think it through in order, check your own result before you present it, and give one clean, correct, professional answer. Short chat replies stay short; real work is done carefully.
            NOISE: if the audio is silence, noise, music, TV or talk not meant for you, or no clear request, never say you did not understand and never ask him to repeat; reply YOU: (noise), BN: -, EN: -.
            SKILLS: when a request fits a saved skill, call use_skill with its name and the full task; the skill's steps are then followed exactly. When he says to remember how to do something as a skill, call save_skill.
            STYLE: sharp, logical, confident, direct. Lead with the answer or the action. No greeting, no "sure/of course", no closing offer like "anything else?", no apologies or filler. State what is done or the key fact, then the one next step only if it matters.
            Write for the ear: short crisp sentences, one idea each, numbers and names stated plainly, no lists symbols or brackets in BN. Keep BN to about 12 words unless he asks for detail; when detail is asked, give at most 4 short pointer sentences, never a long paragraph.
            HOW YOU WORK: you do the work yourself, you do not just name it. For a one-step job call the matching tool and confirm in a few words. For a big or multi-part job (build, write, fix, compare, prepare, analyse, anything a person would need an hour for) call run_workflow once with the complete goal: it plans the steps, runs independent steps in parallel in the background and shows the finished result. Do not ask permission to plan; ask a question only if a missing detail would make the result wrong.
            When he asks you to look at him or something in front of the phone (how do I look, what is this, am I tired, read this), call look_camera with his question; describe what it returns in your own short words, never say you cannot see.
            Prefer run_workflow for mixed jobs, deep_research for pure research, operate_phone for things done inside apps. Never promise a time; say what you are doing.
            For anything that needs fresh or researched facts (news, prices, scores, "latest", comparisons, things you are unsure about) call research instead of guessing.
            research runs in the background and the answer is announced later. The app itself tells him by voice that you are looking it up, BEFORE the work starts, so after any tool whose result says the user was already told, reply with BN: - and EN: - and nothing else. Never make up the answer.
            Use web_search only when he wants to see the results in the browser himself.
            For long or big jobs call deep_research (many searches over several minutes, then a full report on screen).
            When he wants to SEE something (a photo of a person or thing, a picture, a gallery photo, a file, a web page) call show_image, show_gallery, show_file or show_link; the card stays on screen for one minute.
            When he wants you to operate the phone for him (play a video on YouTube, search inside an app, change a setting, send something through an app) call operate_phone with the full goal in English; he will be asked to allow it.
            When he wants a command run in Termux call termux; he will be asked to allow each command. Never run destructive commands unless he asked for exactly that.

            FINAL ANSWER FORMAT (always exactly these three lines in this order, nothing else, no markdown, no emojis):
            BN: <your reply in natural spoken Bengali, Bangla script, 1-2 short sentences, it will be read aloud>
            EN: <short English translation of your Bengali reply, it is the only text shown on screen>
            YOU: <very short English paraphrase of what the user asked>
        """.trimIndent()
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(ATTEMPT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(ATTEMPT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val httpSlow = http.newBuilder()
        .readTimeout(150, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()

    private val history = mutableListOf<JSONObject>()
    private var activeModel: String? = null

    // Models that rejected the "low thinking" setting; they are called without it.
    private val noThinkingConfig = mutableSetOf<String>()

    private class BadThinkingConfig : IOException("thinking config rejected")

    private fun callModel(model: String, system: String, contents: JSONArray, withTools: Boolean, o: Opts): JSONObject {
        if (model in noThinkingConfig) return send(model, false, system, contents, withTools, o)
        return try {
            send(model, true, system, contents, withTools, o)
        } catch (e: BadThinkingConfig) {
            noThinkingConfig += model
            send(model, false, system, contents, withTools, o)
        }
    }

    /**
     * One call uses the one key that belongs to its job (see [KeyRole]); keys are never tried in turn,
     * because every extra attempt costs time. A refused key is reported with the name of the job it belongs to.
     */
    private fun send(model: String, withThinking: Boolean, system: String, contents: JSONArray, withTools: Boolean, o: Opts): JSONObject {
        val key = keyFor(o.role).trim()
        if (key.isBlank()) throw IOException("No Gemini key for ${o.role.label}. Add one in Settings, Connections.")
        return sendOnce(key, model, withThinking, system, contents, withTools, o)
    }

    /**
     * Reads a streamGenerateContent SSE answer (each event is a normal response with the next piece of parts) and
     * returns it in the same shape as a plain generateContent answer. Text pieces are joined; function calls and
     * thought signatures are kept as they came, so the rest of the code does not need to know about streaming.
     */
    private fun readStream(resp: okhttp3.Response): JSONObject {
        val streamer = speakSink?.let { BnStreamer(it) }
        val parts = JSONArray()
        val source = resp.body?.source() ?: throw IOException("Gemini sent an empty stream.")
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val payload = line.substring(5).trim()
            if (payload.isEmpty() || payload == "[DONE]") continue
            val chunk = runCatching { JSONObject(payload) }.getOrNull() ?: continue
            val ps = chunk.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: continue
            for (i in 0 until ps.length()) {
                val p = ps.getJSONObject(i)
                val plainText = p.has("text") && !p.optBoolean("thought") && !p.has("functionCall")
                if (plainText) {
                    Timing.mark("gemini first text")
                    streamer?.feed(p.getString("text"))
                    val last = if (parts.length() > 0) parts.getJSONObject(parts.length() - 1) else null
                    if (last != null && last.has("text") && !last.optBoolean("thought") && !last.has("functionCall")) {
                        last.put("text", last.getString("text") + p.getString("text"))
                        if (p.has("thoughtSignature")) last.put("thoughtSignature", p.getString("thoughtSignature"))
                        continue
                    }
                }
                parts.put(p)
            }
        }
        if (parts.length() == 0) throw IOException("Gemini returned an empty answer. Try rephrasing.")
        Timing.mark("gemini finished")
        return JSONObject().put(
            "candidates",
            JSONArray().put(JSONObject().put("content", JSONObject().put("role", "model").put("parts", parts)))
        )
    }

    private fun sendOnce(key: String, model: String, withThinking: Boolean, system: String, contents: JSONArray, withTools: Boolean, o: Opts): JSONObject {
        val generation = JSONObject().put("temperature", 0.7)
        if (o.json) generation.put("responseMimeType", "application/json")
        // Lower thinking = faster answers. "minimal" is documented for 3.5-flash-lite and 3.6-flash; 3.8-flash and others take "low".
        if (withThinking) generation.put("thinkingConfig", JSONObject().put("thinkingLevel", if (o.slow) "high" else if (model.contains("3.5-flash-lite") || model.contains("3.6-flash")) "minimal" else "low"))

        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", generation)
        if (withTools) body.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools.declarations())))

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:" + if (o.stream) "streamGenerateContent?alt=sse" else "generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        Timing.mark("gemini request sent")
        Timing.note("model", model + if (o.stream) " (streaming)" else " (not streaming)")
        val (code, raw) = try {
            (if (o.slow) httpSlow else http).newCall(request).execute().use { resp ->
                Timing.mark("gemini answered headers")
                if (o.stream && resp.isSuccessful) return readStream(resp)
                resp.code to resp.body?.string().orEmpty()
            }
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
        val keyProblem = msg.contains("API key", ignoreCase = true) || code == 401 ||
            (code == 403 && !msg.contains("model", ignoreCase = true))
        if (keyProblem) throw BadKey("Gemini refused the ${o.role.label} key (ending ${key.takeLast(4)}): $msg")
        // Wrong/retired model, overloaded or per-model quota: try the next model. A bad key will not get better.
        if (!keyProblem && (code == 404 || code == 429 || code >= 500 ||
                ((code == 400 || code == 403) && msg.contains("model", ignoreCase = true)))
        ) throw ModelUnavailable(text)
        throw IOException(text)
    }

    private fun callApi(system: String, contents: JSONArray, withTools: Boolean, o: Opts = Opts()): JSONObject {
        var last: IOException? = null
        val deadline = System.currentTimeMillis() + if (o.slow) 170_000L else TOTAL_MILLIS
        val order = listOfNotNull(activeModel) + MODELS.filter { it != activeModel }
        for (m in order) {
            if (System.currentTimeMillis() > deadline) break
            try {
                val r = callModel(m, system, contents, withTools, o)
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

    @Synchronized
    fun reset() {
        history.clear()
    }

    @Synchronized
    /** The full instructions for a live voice conversation, with the persona, memory and skills at this moment. */
    fun liveSystemPrompt(): String =
        LIVE_PROMPT.replace("{INTRO}", personaIntro()) + memoryBlock() + skillsBlock()

    fun chat(userText: String): Reply =
        converse(JSONArray().put(JSONObject().put("text", userText)), audio = false)

    /** The user's voice (16 kHz mono WAV) goes straight to Gemini, which hears it and answers. */
    @Synchronized
    fun chatAudio(wav: ByteArray): Reply = converse(
        JSONArray()
            .put(JSONObject().put("inlineData", JSONObject()
                .put("mimeType", "audio/wav")
                .put("data", Base64.encodeToString(wav, Base64.NO_WRAP))))
            .put(JSONObject().put("text", "(The user just spoke to you. Listen carefully; it is Bengali or English. Answer as instructed. If this is only noise, silence, music, TV, or people talking to each other and not to you, or you cannot clearly make out a request meant for you, do NOT answer and do NOT ask him to repeat: reply exactly YOU: (noise) then BN: - then EN: -.)")),
        audio = true
    )

    private fun converse(userParts: JSONArray, audio: Boolean): Reply {
        if (keyFor(KeyRole.Talk).isBlank()) {
            throw IOException("Gemini API key is missing. Paste it in the Settings tab and tap Save.")
        }
        val userTurn = JSONObject().put("role", "user").put("parts", userParts)
        history += userTurn

        // Audio is bulky: once answered (or failed), keep only a short text note in the history.
        fun shrink(note: String) {
            if (!audio) return
            val i = history.indexOf(userTurn)
            if (i >= 0) history[i] = JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", "(voice) $note")))
        }
        try {
            return runTurns(::shrink)
        } catch (e: Exception) {
            shrink("(not understood)")
            throw e
        }
    }

    private fun runTurns(shrink: (String) -> Unit): Reply {
        repeat(6) {
            val resp = callApi(SYSTEM_PROMPT.replace("{INTRO}", personaIntro()) + memoryBlock() + skillsBlock() + (if (busy) BUSY_NOTE else ""), JSONArray(history), withTools = true, o = Opts(stream = speakSink != null))
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
                val reply = parse(text)
                shrink(reply.you.ifBlank { "(voice request)" })
                trimHistory()
                return reply
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

    /** Looks at one photo (JPEG) and answers the question about it in English. Plain call, no tools. */
    fun vision(jpeg: ByteArray, question: String): String {
        if (keyFor(KeyRole.Agent).isBlank()) throw IOException("Gemini API key is missing.")
        val parts = JSONArray()
            .put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP))))
            .put(JSONObject().put("text", question))
        val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", parts))
        val sys = "You are the eyes of a phone assistant. The photo was just taken by the phone's front camera, so it shows the user or what is in front of him. " +
            "Answer the question about the photo factually and briefly in English. Describe only what is visible; never guess identity, age or health; say so if the photo is dark or unclear."
        val resp = callApi(sys, contents, withTools = false, o = Opts(role = KeyRole.Agent))
        val out = resp.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IOException("Gemini returned an empty answer.")
        return (0 until out.length()).map { out.getJSONObject(it) }.filter { !it.optBoolean("thought") }
            .joinToString("\n") { it.optString("text") }.trim()
    }

    /** One plain model call without tools or conversation history. [json] asks for a JSON answer. */
    fun generate(system: String, user: String, json: Boolean = false, slow: Boolean = false, role: KeyRole = KeyRole.Talk): String {
        if (keyFor(role).isBlank()) throw IOException("No Gemini key for ${role.label}. Add one in Settings, Connections.")
        val contents = JSONArray().put(
            JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))
        )
        val resp = callApi(system, contents, withTools = false, o = Opts(json, slow, role = role))
        val parts = resp.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IOException("Gemini returned an empty answer.")
        return (0 until parts.length()).map { parts.getJSONObject(it) }
            .filter { !it.optBoolean("thought") }
            .joinToString("\n") { it.optString("text") }
            .trim()
    }

    /** Turns raw web-search material into a spoken Bengali answer. No tools, own conversation. */
    fun summarize(question: String, material: String): Reply {
        if (keyFor(KeyRole.Research).isBlank()) throw IOException("No Gemini key for research. Add one in Settings, Connections.")
        val contents = JSONArray().put(
            JSONObject().put("role", "user").put(
                "parts",
                JSONArray().put(JSONObject().put("text", "QUESTION: $question\n\nSEARCH MATERIAL:\n$material"))
            )
        )
        val resp = callApi(SUMMARY_PROMPT.replace("You are Arina, a warm female voice assistant.", personaIntro()) + memoryBlock(), contents, withTools = false, o = Opts(role = KeyRole.Research))
        val parts = resp.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IOException("Gemini returned an empty answer for the research.")
        val text = (0 until parts.length()).map { parts.getJSONObject(it) }
            .filter { !it.optBoolean("thought") }
            .joinToString("\n") { it.optString("text") }
            .trim()
        return parse(text)
    }

    /** Lets the normal conversation know a background research finished, so follow-up questions work. */
    @Synchronized
    fun noteResearch(question: String, reply: Reply) {
        history += JSONObject().put("role", "user").put(
            "parts", JSONArray().put(JSONObject().put("text", "[Background research finished for: $question]"))
        )
        history += JSONObject().put("role", "model").put(
            "parts", JSONArray().put(JSONObject().put("text", "YOU: ${reply.you}\nBN: ${reply.bn}\nEN: ${reply.en}"))
        )
        trimHistory()
    }
}
