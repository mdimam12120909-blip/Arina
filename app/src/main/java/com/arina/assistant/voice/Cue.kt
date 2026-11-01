package com.arina.assistant.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Tiny sound effects for the floating orb, made in code (no audio files): a short rising shimmer when the orb
 * appears and a soft falling one when it melts away. Quiet on purpose.
 */
object Cue {
    private const val RATE = 22050

    fun appear() = play(from = 520.0, to = 1320.0, seconds = 0.32, volume = 0.16)
    fun fade() = play(from = 900.0, to = 420.0, seconds = 0.5, volume = 0.10)

    private fun play(from: Double, to: Double, seconds: Double, volume: Double) {
        Thread {
            val n = (RATE * seconds).toInt()
            val pcm = ShortArray(n)
            var phase = 0.0
            for (i in 0 until n) {
                val p = i.toDouble() / n
                val f = from + (to - from) * p * p
                phase += 2 * PI * f / RATE
                val env = sin(PI * p).let { it * it }                 // soft in, soft out
                val tone = sin(phase) * 0.75 + sin(phase * 2.0) * 0.25
                pcm[i] = (tone * env * volume * 32767).toInt().toShort()
            }
            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                    )
                    .setBufferSizeInBytes(n * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            } catch (e: Exception) {
                return@Thread
            }
            try {
                track.write(pcm, 0, n)
                track.play()
                Thread.sleep((seconds * 1000).toLong() + 120)
            } catch (_: Exception) {
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }.apply { isDaemon = true }.start()
    }
}
