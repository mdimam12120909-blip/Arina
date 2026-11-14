package com.arina.assistant.ai

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * One live voice conversation with Gemini over a WebSocket (Gemini Live API).
 *
 * The microphone streams to Gemini all the time (16 kHz, 16-bit PCM); Gemini itself decides when the user has
 * finished, hears the speech directly and answers with speech (24 kHz PCM) that is played as it arrives. There is
 * no recording step, no text step and no separate voice service in between, which is why it is fast.
 * Phone tools are offered as function declarations and run here when Gemini calls them.
 *
 * Not verified on a device yet: the exact voice names and the quality of Bengali speech.
 */
class GeminiLive(
    private val key: () -> String,
    private val system: () -> String,
    private val tools: ToolRunner,
    private val voiceName: () -> String,
    private val cb: Callbacks,
    private val tts: () -> TtsCfg? = { null }
) {
    /** Cartesia mode: the model answers in text (still hearing, thinking and using tools live) and each sentence is spoken by the voice service. */
    data class TtsCfg(val voice: String, val model: String, val textModel: String)

    interface Callbacks {
        fun onLevel(level: Float)
        fun onUserSpeaking()
        fun onModelSpeaking()
        fun onToolRunning(name: String)
        fun onTurn(user: String, model: String)
        fun onError(message: String)
    }

    companion object {
        const val MODEL = "gemini-3.8-live"
        const val URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        const val IN_RATE = 16000
        const val OUT_RATE = 24000
        const val SILENCE_MS = 600          // documented range 500-800 ms
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val toolPool = Executors.newCachedThreadPool()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var ready = false
    @Volatile private var micMuted = false
    @Volatile private var generation = 0
    private val userText = StringBuilder()
    private val modelText = StringBuilder()
    @Volatile private var modelAudioThisTurn = false
    @Volatile private var turnDonePending = false
    private val out = LinkedBlockingQueue<ByteArray>()
    @Volatile private var track: AudioTrack? = null
    @Volatile private var framesWritten = 0L

    @Volatile private var connecting = false
    @Volatile private var pendingToken: String? = null
    val active: Boolean get() = socket != null || connecting

    // ---- Cartesia mode: sentences are fetched one by one while earlier ones are already playing ----
    @Volatile private var ttsCfg: TtsCfg? = null
    @Volatile private var cartesiaBroken = false   // the text-mode session failed to open: use the Gemini voice for now
    private val sentences = LinkedBlockingQueue<Pair<Int, String>>()
    private val ttsPending = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var ttsGen = 0
    private var chunker = SmartChunker()
    private val ttsWorker = Thread {
        while (true) {
            val (g, text) = try { sentences.take() } catch (e: InterruptedException) { return@Thread }
            try { if (g == ttsGen) speakOne(g, text) } catch (e: Exception) { Diag.log("voice: sentence failed: ${e.message?.take(120)}") }
            finally { ttsPending.updateAndGet { maxOf(0, it - 1) }; afterSpeech() }
        }
    }.apply { isDaemon = true; start() }

    private fun speakOne(g: Int, text: String) {
        val c = ttsCfg ?: return
        val lang = if (text.any { it in '\u0980'..'\u09FF' }) "bn" else "en"
        val t0 = System.currentTimeMillis()
        Gateway.client.newCall(Gateway.tts(text, c.voice, c.model, lang)).execute().use { r ->
            if (!r.isSuccessful) { Diag.log("voice: HTTP ${r.code} ${r.body?.string().orEmpty().take(120)}"); return }
            val ins = r.body?.byteStream() ?: return
            val buf = ByteArray(4800); var carry = -1; var first = true
            while (g == ttsGen) {
                val n = ins.read(buf); if (n <= 0) break
                if (first) { first = false; Diag.log("metric voice sentenceFirstByteMs=${System.currentTimeMillis() - t0}") }
                var chunk = if (carry >= 0) byteArrayOf(carry.toByte()) + buf.copyOf(n) else buf.copyOf(n)
                carry = -1
                if (chunk.size % 2 == 1) { carry = chunk[chunk.size - 1].toInt() and 0xff; chunk = chunk.copyOf(chunk.size - 1) }
                if (chunk.isNotEmpty()) audioIn(chunk)
            }
        }
    }

    private fun audioIn(pcm: ByteArray) {
        spokenSince = System.currentTimeMillis()
        if (!modelAudioThisTurn) { modelAudioThisTurn = true; Timing.mark("live first audio"); cb.onModelSpeaking() }
        playChunk(pcm)
    }

    private fun speak(parts: List<String>) {
        for (p in parts) { ttsPending.incrementAndGet(); sentences.put(ttsGen to p) }
    }

    /** All queued sentences are done: if the model has also finished and nothing ever played, close the turn. */
    private fun afterSpeech() {
        if (ttsPending.get() == 0 && turnDonePending && !modelAudioThisTurn) finishTurn()
    }

    // Keeps a long conversation alive: Google can hand back a resume token, so a dropped connection continues with its memory.
    @Volatile private var resumeHandle: String? = null
    @Volatile private var usedHandleNotReady = false
    @Volatile private var connectStartedAt = 0L

    /** Opens the conversation if it is not open and un-mutes the microphone. */
    @Synchronized
    fun start() {
        micMuted = false
        if (socket != null || connecting) return
        val k = key().trim()
        if (k.isEmpty()) {
            cb.onError("Gemini API key is missing. Paste it in Settings and tap Save.")
            return
        }
        val mine = ++generation
        ttsCfg = if (cartesiaBroken) null else tts()
        Diag.log("voice: ${ttsCfg?.let { "Cartesia, voice ${it.voice.take(8)}, model ${it.model}, text model ${it.textModel}" } ?: "Gemini"}")
        ready = false
        startMic(mine)   // listens from the first moment; what is said while the session opens is kept and sent once it is ready
        usedHandleNotReady = resumeHandle != null
        Timing.mark("live socket opening")
        connectStartedAt = System.currentTimeMillis()
        if (Gateway.isProxy(k)) {
            // The real key stays on Entrion's server; a one-use token opens this session.
            connecting = true
            Thread {
                val tok = try { Gateway.liveToken(k) } catch (e: Exception) {
                    synchronized(this) { connecting = false }
                    if (generation == mine) cb.onError(e.message ?: "The voice service is not ready.")
                    return@Thread
                }
                synchronized(this) {
                    connecting = false
                    if (generation != mine) return@Thread
                    pendingToken = tok
                    socket = http.newWebSocket(Request.Builder().url(Gateway.liveUrl(tok, Gateway.useAlphaLive)).build(), Listener(mine))
                }
            }.apply { isDaemon = true }.start()
            return
        }
        socket = http.newWebSocket(Request.Builder().url("$URL?key=$k").build(), Listener(mine))
    }

    /** Stops sending the microphone but keeps the conversation (and its memory) open. */
    fun mute() {
        micMuted = true
    }

    /** Ends the conversation completely. */
    @Synchronized
    fun stop() {
        cartesiaBroken = false
        resumeHandle = null
        generation++
        ready = false
        runCatching { socket?.close(1000, "bye") }
        socket = null
        clearPlayback()
        runCatching { track?.release() }
        track = null
        userText.setLength(0); modelText.setLength(0)
        turnDonePending = false
        waiting.clear()
    }

    /** The STOP button: silence whatever is being said. */
    fun interruptPlayback() = clearPlayback()

    private val waiting = java.util.concurrent.ConcurrentLinkedQueue<String>()

    /** Says something to the model as text. If the conversation is still opening, it is sent as soon as it is ready. */
    fun sendText(text: String) {
        val ws = socket ?: return
        if (!ready) { waiting.add(text); return }
        ws.send(JSONObject().put("realtimeInput", JSONObject().put("text", text)).toString())
    }

    private fun setupMessage(): String {
        val cart = ttsCfg
        val gen = JSONObject().put("responseModalities", JSONArray().put(if (cart != null) "TEXT" else "AUDIO"))
        if (cart == null) gen.put(
            "speechConfig", JSONObject().put(
                "voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName()))
            )
        )
        val vad = JSONObject()
            .put("startOfSpeechSensitivity", "START_SENSITIVITY_HIGH")   // LOW missed quiet or short speech
            .put("prefixPaddingMs", 250)
            .put("silenceDurationMs", SILENCE_MS)
        val setup = JSONObject()
            .put("model", "models/${cart?.textModel ?: MODEL}")
            .put("generationConfig", gen)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system()))))
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools.declarations())))
            .put("realtimeInputConfig", JSONObject().put("automaticActivityDetection", vad))
            .put("inputAudioTranscription", JSONObject())
            // Without compression an audio session is cut after about 15 minutes.
            .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
            .put("sessionResumption", JSONObject().apply { resumeHandle?.let { put("handle", it) } })
        if (cart == null) setup.put("outputAudioTranscription", JSONObject())
        return JSONObject().put("setup", setup).toString()
    }

    private inner class Listener(val mine: Int) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Thread {
                try { Thread.sleep(12000) } catch (e: InterruptedException) { return@Thread }
                if (generation == mine && !ready) { runCatching { webSocket.cancel() }; end(mine, "Gemini Live closed: the voice session did not start in time") }
            }.apply { isDaemon = true }.start()
            Timing.mark("live socket open")
            webSocket.send(setupMessage())
        }

        override fun onMessage(webSocket: WebSocket, text: String) = handle(mine, text)
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(mine, bytes.utf8())

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (generation != mine) return
            val tok = pendingToken
            if (tok != null && !ready && !Gateway.useAlphaLive && (response?.code == 404 || response?.code == 400)) {
                // The token endpoint may live under the other API version: try that once.
                Gateway.useAlphaLive = true
                synchronized(this) { if (generation == mine) socket = http.newWebSocket(Request.Builder().url(Gateway.liveUrl(tok, true)).build(), Listener(mine)) }
                return
            }
            end(mine, "Gemini Live failed: ${t.message ?: "no connection"}${response?.let { " (HTTP ${it.code})" }.orEmpty()}")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (generation != mine) return
            end(mine, "Gemini Live closed ($code): ${reason.ifBlank { "no reason given" }}")
        }
    }

    private fun end(mine: Int, message: String) {
        synchronized(this) {
            if (generation != mine) return
            if (!ready && ttsCfg != null) { cartesiaBroken = true; Diag.log("voice: Cartesia mode could not open (${message.take(140)}); falling back to the Gemini voice") }
            if (usedHandleNotReady) { resumeHandle = null; usedHandleNotReady = false }   // a stale token must not trap the next try
            generation++
            ready = false
            socket = null
            clearPlayback()
        }
        cb.onError(message)
    }

    private fun handle(mine: Int, raw: String) {
        if (generation != mine) return
        val msg = runCatching { JSONObject(raw) }.getOrNull() ?: return
        msg.optJSONObject("sessionResumptionUpdate")?.let { u ->
            if (u.optBoolean("resumable", false)) u.optString("newHandle").takeIf { it.isNotEmpty() }?.let { resumeHandle = it }
        }
        msg.optJSONObject("goAway")?.let { Diag.log("live: server will close the session soon (${it.optString("timeLeft")}); resume token ${if (resumeHandle != null) "saved" else "missing"}") }
        if (msg.has("setupComplete")) {
            usedHandleNotReady = false
            ready = true
            Timing.mark("live ready")
            Timing.liveReadyMs = System.currentTimeMillis() - connectStartedAt
            while (true) { val t = waiting.poll() ?: break; socket?.send(JSONObject().put("realtimeInput", JSONObject().put("text", t)).toString()) }
            return
        }
        msg.optJSONObject("toolCall")?.let { runTools(it.optJSONArray("functionCalls") ?: JSONArray()) }
        val sc = msg.optJSONObject("serverContent") ?: return

        sc.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let {
            if (userText.isEmpty()) { Timing.start(); cb.onUserSpeaking() }
            userText.append(it)
        }
        sc.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { modelText.append(it) }

        val parts = sc.optJSONObject("modelTurn")?.optJSONArray("parts")
        if (parts != null && ttsCfg != null) for (i in 0 until parts.length()) {
            val pt = parts.getJSONObject(i)
            val txt = pt.optString("text")
            if (txt.isNotEmpty() && !pt.optBoolean("thought")) { modelText.append(txt); speak(chunker.feed(txt)) }
        }
        else if (parts != null) for (i in 0 until parts.length()) {
            val data = parts.getJSONObject(i).optJSONObject("inlineData")?.optString("data").orEmpty()
            if (data.isEmpty()) continue
            spokenSince = System.currentTimeMillis()
            if (!modelAudioThisTurn) {
                modelAudioThisTurn = true
                Timing.mark("live first audio")
                cb.onModelSpeaking()
            }
            playChunk(Base64.decode(data, Base64.DEFAULT))
        }
        if (sc.optBoolean("interrupted")) {
            clearPlayback()
            finishTurn()
        } else if (sc.optBoolean("turnComplete")) {
            turnDonePending = true
            if (ttsCfg != null) speak(chunker.flush())
            if (!modelAudioThisTurn && ttsPending.get() == 0) finishTurn()   // nothing was said (a silent tool turn): nothing to wait for
        }
    }

    private fun finishTurn() {
        turnDonePending = false
        modelAudioThisTurn = false
        val u = userText.toString().trim()
        val m = modelText.toString().trim()
        userText.setLength(0); modelText.setLength(0)
        Timing.mark("live turn done")
        cb.onTurn(u, m)
    }

    // ---- phone tools -------------------------------------------------------------------------

    private fun runTools(calls: JSONArray) {
        for (i in 0 until calls.length()) {
            val fc = calls.getJSONObject(i)
            val id = fc.optString("id")
            val name = fc.optString("name")
            val args = fc.optJSONObject("args") ?: JSONObject()
            cb.onToolRunning(name)
            toolPool.execute {
                val result = try { tools.run(name, args) } catch (e: Exception) { "ERROR: ${e.message}" }
                val reply = JSONObject().put(
                    "toolResponse", JSONObject().put(
                        "functionResponses", JSONArray().put(
                            JSONObject().put("id", id).put("name", name).put("response", JSONObject().put("result", result))
                                // gemini-3.8-live runs tools asynchronously: say how the answer should be delivered.
                                .put("scheduling", when {
                                    result.contains("ALREADY told") || result.startsWith("BUSY") -> "SILENT"
                                    name in MUST_SPEAK -> "INTERRUPT"
                                    else -> "WHEN_IDLE"
                                })
                        )
                    )
                )
                socket?.send(reply.toString())
                guardSpeech(name, result)
            }
        }
    }

    /** Tools whose result the user is waiting to hear. If the model stays silent after one, it is nudged to say it. */
    private val MUST_SPEAK = setOf("play_song", "generate_image", "diagram", "send_message", "look_screen", "look_camera", "get_status", "task_status", "read_notifications", "list_tasks", "list_routines",
        "list_skills", "phone_control", "app_action", "scan_phone", "termux", "tap_text", "press_button", "open_settings")
    @Volatile private var spokenSince = 0L
    private val guardHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun guardSpeech(name: String, result: String) {
        if (name !in MUST_SPEAK || result.contains("ALREADY told")) return
        val mark = System.currentTimeMillis()
        guardHandler.postDelayed({
            if (spokenSince < mark && ready && socket != null) {
                sendText("You did not tell the user the result of $name yet. Say it now in one or two short sentences, in the language he speaks. Result: ${result.take(500)}")
            }
        }, 6500)
    }

    // ---- microphone --------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun startMic(mine: Int) {
        Thread {
            val min = AudioRecord.getMinBufferSize(IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val chunk = IN_RATE / 25   // 40 ms: Google advises 20-40 ms chunks
            val rec = try {
                // VOICE_COMMUNICATION turns on the phone's echo cancellation, so Gemini does not hear itself.
                AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, IN_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(min, chunk * 4))
            } catch (e: Exception) { cb.onError("Could not open the microphone: ${e.message}"); return@Thread }
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); cb.onError("The microphone is busy or not allowed."); return@Thread }
            runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true }
            runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)?.enabled = true }
            val buf = ShortArray(chunk)
            val pre = java.util.ArrayDeque<String>()
            var sent = 0; var silentBlocks = 0; var emptyReads = 0; var peak = -100f; var lastLog = System.currentTimeMillis()
            try {
                rec.startRecording()
                Diag.log("mic: recording started (state ${rec.recordingState}, source ${rec.audioSource})")
                while (generation == mine) {
                    var got = 0
                    while (got < chunk && generation == mine) {
                        val n = rec.read(buf, got, chunk - got)
                        if (n <= 0) break
                        got += n
                    }
                    if (got <= 0) {
                        if (++emptyReads > 150) { cb.onError("The microphone stopped giving sound. Another app may be using it."); break }
                        Thread.sleep(20); continue
                    }
                    emptyReads = 0
                    var sum = 0.0
                    for (i in 0 until got) sum += buf[i].toDouble() * buf[i]
                    val db = (20 * log10(sqrt(sum / got) / 32768.0 + 1e-9)).toFloat()
                    cb.onLevel(((db + 60f) / 40f).coerceIn(0f, 1f))
                    if (db > peak - 100f && db > peak) peak = db
                    if (db < -70f) silentBlocks++
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastLog > 8000) { Diag.log("mic: sent=$sent chunks, silent=$silentBlocks, peak=${"%.0f".format(peak)} dB, muted=$micMuted, ready=$ready"); sent = 0; silentBlocks = 0; peak = -100f; lastLog = nowMs }
                    if (micMuted) continue
                    val bytes = ByteArray(got * 2)
                    for (i in 0 until got) { bytes[i * 2] = (buf[i].toInt() and 0xff).toByte(); bytes[i * 2 + 1] = (buf[i].toInt() shr 8).toByte() }
                    val msg = JSONObject().put(
                        "realtimeInput", JSONObject().put(
                            "audio", JSONObject()
                                .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                                .put("mimeType", "audio/pcm;rate=16000")
                        )
                    ).toString()
                    if (!ready) { pre.addLast(msg); while (pre.size > 200) pre.removeFirst(); continue }
                    while (pre.isNotEmpty()) socket?.send(pre.removeFirst())
                    if (socket?.send(msg) == true) sent++
                }
            } catch (e: Exception) {
                if (generation == mine) cb.onError("Microphone problem: ${e.message}")
            } finally {
                runCatching { rec.stop() }
                runCatching { rec.release() }
            }
        }.apply { isDaemon = true }.start()
    }

    // ---- playback ----------------------------------------------------------------------------

    private val player = Thread {
        while (true) {
            val pcm = try { out.poll(100, TimeUnit.MILLISECONDS) } catch (e: InterruptedException) { return@Thread }
            if (pcm != null) {
                val t = track ?: newTrack().also { track = it; it.play() }
                t.write(pcm, 0, pcm.size)
                framesWritten += pcm.size / 2
            } else if (turnDonePending && modelAudioThisTurn && ttsPending.get() == 0) {
                val t = track
                val deadline = System.nanoTime() + 3_000_000_000L
                while (t != null && t.playbackHeadPosition < framesWritten && out.isEmpty() && System.nanoTime() < deadline) Thread.sleep(20)
                if (out.isEmpty() && turnDonePending) finishTurn()
            }
        }
    }.apply { isDaemon = true; start() }

    private fun playChunk(pcm: ByteArray) {
        out.put(pcm)
    }

    private fun clearPlayback() {
        ttsGen++; sentences.clear(); ttsPending.set(0); chunker = SmartChunker()
        out.clear()
        runCatching { track?.pause(); track?.flush(); track?.play() }
        framesWritten = 0
        modelAudioThisTurn = false
    }

    private fun newTrack(): AudioTrack {
        val min = AudioTrack.getMinBufferSize(OUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        framesWritten = 0
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(OUT_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(min, OUT_RATE / 5 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }
}
