package com.arina.assistant.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import com.arina.assistant.ai.Timing
import kotlinx.coroutines.CompletableDeferred
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Nothing was heard yet: the caller should speak the text the ordinary way. */
class LiveVoiceUnavailable(message: String) : IOException(message)

/**
 * Cartesia streaming text-to-speech over one WebSocket (wss://api.cartesia.ai/tts/websocket), played as it arrives.
 *
 * From Cartesia's documentation: text goes in as several messages with the same context_id and "continue": true,
 * the last with "continue": false; audio comes back as JSON "chunk" messages (base64 raw PCM) and a "done" message.
 * The connection stays open between answers, so there is no connect delay after the first one, and [warmUp]
 * opens it while the user is still speaking. Audio is raw 16-bit mono at 24 kHz, written to an AudioTrack at once.
 *
 * Not verified on a real device: whether an empty closing message is accepted (if not, the audio simply ends
 * 2 seconds after the last chunk) and the best max_buffer_delay_ms.
 */
class CartesiaLive(private val credentials: () -> Pair<String, String>) {

    companion object {
        const val URL = "wss://api.cartesia.ai/tts/websocket?cartesia_version=2026-08-14"
        const val API_VERSION = "2026-08-14"
        const val MODEL = "sonic-3.6"
        const val LANGUAGE = "bn"
        const val RATE = 24000
        const val MAX_BUFFER_DELAY_MS = 100
        const val PREBUFFER_FRAMES = RATE * 15 / 100          // 150 ms of audio before playing, against stutter
        const val IDLE_END_MS = 2000L                          // no audio for this long after the last text = finished
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val lock = Any()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var alive = false
    @Volatile private var current: Turn? = null

    /** Opens the connection ahead of time (while the user speaks or Gemini thinks). Failure is ignored. */
    fun warmUp() {
        runCatching { ensure() }
    }

    private fun ensure(): WebSocket? = synchronized(lock) {
        val existing = socket
        if (existing != null && alive) return existing
        val (key, _) = credentials()
        if (key.isBlank()) return null
        val request = Request.Builder()
            .url(URL)
            .header("X-API-Key", key.trim())
            .header("Cartesia-Version", API_VERSION)
            .build()
        alive = true
        Timing.mark("voice socket opening")
        val ws = http.newWebSocket(request, listener)
        socket = ws
        ws
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Timing.mark("voice socket open")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
            val turn = current ?: return
            if (msg.optString("context_id") != turn.id) return
            when (msg.optString("type")) {
                "chunk" -> {
                    val data = msg.optString("data")
                    if (data.isNotEmpty()) turn.onAudio(Base64.decode(data, Base64.DEFAULT))
                }
                "done" -> turn.onServerDone()
                "error" -> turn.onError(msg.optString("title") + ": " + msg.optString("message"))
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (socket === webSocket) alive = false
            current?.onError("connection failed: ${t.message}")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (socket === webSocket) alive = false
            current?.onError("connection closed ($code)")
        }
    }

    /** Starts one spoken answer. Returns null when there is no Cartesia key. */
    fun begin(voiceId: String, onFirstPlay: () -> Unit): Turn? {
        val ws = ensure() ?: return null
        if (voiceId.isBlank()) return null
        current?.cancel()
        val turn = Turn(ws, voiceId, onFirstPlay)
        current = turn
        return turn
    }

    inner class Turn(private val ws: WebSocket, private val voiceId: String, private val onFirstPlay: () -> Unit) {
        val id: String = UUID.randomUUID().toString().replace("-", "")
        private val audio = LinkedBlockingQueue<ByteArray>()
        private val gate = CountDownLatch(1)
        private val finished = CompletableDeferred<Unit>()
        @Volatile private var cancelled = false
        @Volatile private var gotAudio = false
        @Volatile private var finalSent = false
        @Volatile private var serverDone = false
        @Volatile private var failure: String? = null
        @Volatile private var lastAudioAt = 0L

        private val player = Thread({ playLoop() }, "cartesia-live").apply { isDaemon = true; start() }

        /** Audio may play once this is called (the spoken "I am looking it up" line has to end first). */
        fun openGate() = gate.countDown()

        fun send(text: String, more: Boolean) {
            if (cancelled) return
            val msg = JSONObject()
                .put("model_id", MODEL)
                .put("transcript", text)
                .put("voice", JSONObject().put("id", voiceId))
                .put("language", LANGUAGE)
                .put("context_id", id)
                .put("output_format", JSONObject().put("container", "raw").put("encoding", "pcm_s16le").put("sample_rate", RATE))
                .put("continue", more)
                .put("max_buffer_delay_ms", MAX_BUFFER_DELAY_MS)
            if (!more) finalSent = true
            if (text.isNotEmpty()) Timing.mark("voice text sent")
            if (!ws.send(msg.toString())) onError("socket is closed")
        }

        internal fun onAudio(pcm: ByteArray) {
            if (!gotAudio) Timing.mark("voice first audio")
            gotAudio = true
            lastAudioAt = System.nanoTime()
            audio.put(pcm)
        }

        internal fun onServerDone() {
            serverDone = true
        }

        internal fun onError(message: String) {
            if (failure == null) failure = message
            serverDone = true
        }

        /** Waits until everything has been spoken. Throws [LiveVoiceUnavailable] if nothing was heard at all. */
        suspend fun await() {
            try {
                finished.await()
            } finally {
                if (!finished.isCompleted) cancel()
            }
        }

        fun cancel() {
            if (cancelled) return
            cancelled = true
            gate.countDown()
            runCatching { ws.send(JSONObject().put("context_id", id).put("cancel", true).toString()) }
            if (current === this) current = null
            finished.cancel()
        }

        private fun newTrack(): AudioTrack {
            val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            return AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setBufferSizeInBytes(maxOf(min, RATE / 5 * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }

        private fun playLoop() {
            var track: AudioTrack? = null
            var frames = 0L
            var playing = false
            try {
                gate.await()
                while (!cancelled) {
                    val chunk = audio.poll(100, TimeUnit.MILLISECONDS)
                    if (chunk == null) {
                        val idle = lastAudioAt != 0L && (System.nanoTime() - lastAudioAt) / 1_000_000 > IDLE_END_MS
                        if (serverDone || (finalSent && gotAudio && idle)) break
                        if (!gotAudio && failure != null) break
                        continue
                    }
                    if (track == null) track = newTrack()
                    track.write(chunk, 0, chunk.size)
                    frames += chunk.size / 2
                    if (!playing && frames >= PREBUFFER_FRAMES) {
                        track.play(); playing = true
                        Timing.mark("voice playing")
                        onFirstPlay()
                    }
                }
                // Draining: everything the server sent before "done" is still in the queue.
                while (!cancelled) {
                    val chunk = audio.poll() ?: break
                    if (track == null) track = newTrack()
                    track.write(chunk, 0, chunk.size)
                    frames += chunk.size / 2
                }
                if (cancelled) return
                if (track == null) {
                    finished.completeExceptionally(LiveVoiceUnavailable(failure ?: "no audio came back"))
                    return
                }
                if (!playing) {
                    track.play(); playing = true
                    Timing.mark("voice playing")
                    onFirstPlay()
                }
                track.stop()   // in streaming mode this lets the written audio finish playing
                val deadline = System.nanoTime() + (frames * 1000 / RATE + 1500) * 1_000_000
                while (!cancelled && track.playbackHeadPosition < frames && System.nanoTime() < deadline) Thread.sleep(20)
                finished.complete(Unit)
            } catch (e: Exception) {
                finished.completeExceptionally(
                    if (gotAudio) IOException("voice stopped: ${e.message}") else LiveVoiceUnavailable(e.message ?: "voice failed")
                )
            } finally {
                runCatching { track?.pause(); track?.flush() }
                runCatching { track?.release() }
                if (current === this@Turn) current = null
            }
        }
    }
}
