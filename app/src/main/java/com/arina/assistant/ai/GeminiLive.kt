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
    private val cb: Callbacks
) {
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

    val active: Boolean get() = socket != null

    /** Opens the conversation if it is not open and un-mutes the microphone. */
    @Synchronized
    fun start() {
        micMuted = false
        if (socket != null) return
        val k = key().trim()
        if (k.isEmpty()) {
            cb.onError("Gemini API key is missing. Paste it in Settings and tap Save.")
            return
        }
        val mine = ++generation
        ready = false
        Timing.mark("live socket opening")
        socket = http.newWebSocket(Request.Builder().url("$URL?key=$k").build(), Listener(mine))
    }

    /** Stops sending the microphone but keeps the conversation (and its memory) open. */
    fun mute() {
        micMuted = true
    }

    /** Ends the conversation completely. */
    @Synchronized
    fun stop() {
        generation++
        ready = false
        runCatching { socket?.close(1000, "bye") }
        socket = null
        clearPlayback()
        runCatching { track?.release() }
        track = null
        userText.setLength(0); modelText.setLength(0)
        turnDonePending = false
    }

    /** The STOP button: silence whatever is being said. */
    fun interruptPlayback() = clearPlayback()

    fun sendText(text: String) {
        val ws = socket ?: return
        if (!ready) return
        ws.send(JSONObject().put("realtimeInput", JSONObject().put("text", text)).toString())
    }

    private fun setupMessage(): String {
        val gen = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put(
                "speechConfig", JSONObject().put(
                    "voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName()))
                )
            )
        val vad = JSONObject()
            .put("startOfSpeechSensitivity", "START_SENSITIVITY_LOW")
            .put("prefixPaddingMs", 100)
            .put("silenceDurationMs", SILENCE_MS)
        val setup = JSONObject()
            .put("model", "models/$MODEL")
            .put("generationConfig", gen)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system()))))
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools.declarations())))
            .put("realtimeInputConfig", JSONObject().put("automaticActivityDetection", vad))
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
        return JSONObject().put("setup", setup).toString()
    }

    private inner class Listener(val mine: Int) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Timing.mark("live socket open")
            webSocket.send(setupMessage())
        }

        override fun onMessage(webSocket: WebSocket, text: String) = handle(mine, text)
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(mine, bytes.utf8())

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (generation != mine) return
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
        if (msg.has("setupComplete")) {
            ready = true
            Timing.mark("live ready")
            startMic(mine)
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
        if (parts != null) for (i in 0 until parts.length()) {
            val data = parts.getJSONObject(i).optJSONObject("inlineData")?.optString("data").orEmpty()
            if (data.isEmpty()) continue
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
            if (!modelAudioThisTurn) finishTurn()   // nothing was said (a silent tool turn): nothing to wait for
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
                        )
                    )
                )
                socket?.send(reply.toString())
            }
        }
    }

    // ---- microphone --------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun startMic(mine: Int) {
        Thread {
            val min = AudioRecord.getMinBufferSize(IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val chunk = IN_RATE / 10   // 100 ms
            val rec = try {
                // VOICE_COMMUNICATION turns on the phone's echo cancellation, so Gemini does not hear itself.
                AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, IN_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(min, chunk * 4))
            } catch (e: Exception) { cb.onError("Could not open the microphone: ${e.message}"); return@Thread }
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); cb.onError("The microphone is busy or not allowed."); return@Thread }
            runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true }
            runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)?.enabled = true }
            val buf = ShortArray(chunk)
            try {
                rec.startRecording()
                while (generation == mine) {
                    var got = 0
                    while (got < chunk && generation == mine) {
                        val n = rec.read(buf, got, chunk - got)
                        if (n <= 0) break
                        got += n
                    }
                    if (got <= 0) continue
                    var sum = 0.0
                    for (i in 0 until got) sum += buf[i].toDouble() * buf[i]
                    val db = (20 * log10(sqrt(sum / got) / 32768.0 + 1e-9)).toFloat()
                    cb.onLevel(((db + 60f) / 40f).coerceIn(0f, 1f))
                    if (micMuted) continue
                    val bytes = ByteArray(got * 2)
                    for (i in 0 until got) { bytes[i * 2] = (buf[i].toInt() and 0xff).toByte(); bytes[i * 2 + 1] = (buf[i].toInt() shr 8).toByte() }
                    val msg = JSONObject().put(
                        "realtimeInput", JSONObject().put(
                            "audio", JSONObject()
                                .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                                .put("mimeType", "audio/pcm;rate=16000")
                        )
                    )
                    socket?.send(msg.toString())
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
            } else if (turnDonePending && modelAudioThisTurn) {
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
