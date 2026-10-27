package com.arina.assistant.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Listens through the microphone itself (no Android speech service, so no start/stop beep) and cuts out one
 * spoken phrase using loudness. The phrase is delivered as a 16 kHz mono WAV for Gemini to understand.
 * Listening ends after one phrase; call [start] again for the next.
 */
class MicListener {

    private companion object {
        const val RATE = 16000
        const val FRAME = 320                 // 20 ms
        const val PRE_ROLL_FRAMES = 15        // 300 ms kept from before the speech started
        const val START_FRAMES = 4            // 80 ms of loud sound = speech started
        const val END_SILENCE_FRAMES = 27     // 540 ms of quiet = phrase finished (faster replies)
        const val MIN_SPEECH_FRAMES = 15      // ignore bursts shorter than 300 ms
        const val MAX_FRAMES = 1000           // 20 s cap
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var generation = 0

    @SuppressLint("MissingPermission")
    fun start(onLevel: (Float) -> Unit, onSpeech: (ByteArray) -> Unit, onError: (String) -> Unit) {
        stop()
        val mine = ++generation
        Thread {
            val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    max(minBuf, FRAME * 2 * 8)
                )
            } catch (e: Exception) {
                main.post { if (generation == mine) onError("Could not open the microphone: ${e.message}") }
                return@Thread
            }
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                main.post { if (generation == mine) onError("The microphone is busy or not allowed.") }
                return@Thread
            }
            val frame = ShortArray(FRAME)
            val preRoll = ArrayDeque<ShortArray>()
            val speech = ArrayList<ShortArray>()
            var noise = -60f
            var calibrated = 0
            var loud = 0
            var quiet = 0
            var inSpeech = false
            var total = 0
            try {
                rec.startRecording()
                while (generation == mine) {
                    val n = rec.read(frame, 0, FRAME)
                    if (n <= 0) continue
                    var sum = 0.0
                    for (i in 0 until n) sum += frame[i].toDouble() * frame[i]
                    val rms = sqrt(sum / n)
                    val db = (20 * log10(rms / 32768.0 + 1e-9)).toFloat()
                    val copy = frame.copyOf(n)

                    val level = ((db + 60f) / 40f).coerceIn(0f, 1f)
                    main.post { if (generation == mine) onLevel(level) }

                    if (calibrated < 25) { // first half second: learn the room's background noise
                        noise = if (calibrated == 0) db else noise * 0.9f + db * 0.1f
                        calibrated++
                        continue
                    }
                    val startThr = max(noise + 10f, -48f)
                    val endThr = max(noise + 6f, -52f)

                    if (!inSpeech) {
                        preRoll.addLast(copy)
                        if (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
                        if (db > startThr) loud++ else { loud = 0; noise = noise * 0.98f + db * 0.02f }
                        if (loud >= START_FRAMES) {
                            inSpeech = true
                            speech.addAll(preRoll)
                            preRoll.clear()
                            quiet = 0
                            total = speech.size
                        }
                    } else {
                        speech.add(copy)
                        total++
                        if (db > endThr) quiet = 0 else quiet++
                        if (quiet >= END_SILENCE_FRAMES || total >= MAX_FRAMES) {
                            val voiced = total - quiet
                            if (voiced >= MIN_SPEECH_FRAMES) {
                                val wav = toWav(speech)
                                main.post { if (generation == mine) onSpeech(wav) }
                                break
                            }
                            // too short: a click or cough. Start over.
                            speech.clear(); inSpeech = false; loud = 0; quiet = 0; total = 0
                        }
                    }
                }
            } catch (e: Exception) {
                main.post { if (generation == mine) onError("Microphone problem: ${e.message}") }
            } finally {
                runCatching { rec.stop() }
                runCatching { rec.release() }
            }
        }.apply { isDaemon = true }.start()
    }

    fun stop() {
        generation++
    }

    private fun toWav(frames: List<ShortArray>): ByteArray {
        val samples = frames.sumOf { it.size }
        val dataLen = samples * 2
        val out = ByteArrayOutputStream(44 + dataLen)
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        h.put("data".toByteArray()).putInt(dataLen)
        out.write(h.array())
        val body = ByteBuffer.allocate(dataLen).order(ByteOrder.LITTLE_ENDIAN)
        for (f in frames) for (s in f) body.putShort(s)
        out.write(body.array())
        return out.toByteArray()
    }
}
