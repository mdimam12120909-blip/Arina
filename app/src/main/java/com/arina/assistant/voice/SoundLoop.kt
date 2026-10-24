package com.arina.assistant.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * A soft, slowly pulsing two-note hum played while Arina researches in the background.
 * Generated in code (no audio file needed) and streamed through an AudioTrack.
 */
class SoundLoop {

    @Volatile private var generation = 0
    @Volatile private var running = false

    @Synchronized
    fun start() {
        if (running) return
        running = true
        val mine = ++generation
        Thread {
            val rate = 22050
            val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuf, 4096))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (e: Exception) {
                running = false
                return@Thread
            }
            try {
                track.play()
                val block = ShortArray(1024)
                var n = 0L
                while (running && generation == mine) {
                    for (i in block.indices) {
                        val t = n.toDouble() / rate
                        val pulse = 0.55 + 0.45 * sin(2 * PI * 0.6 * t)
                        val tone = sin(2 * PI * 392.0 * t) * 0.6 + sin(2 * PI * 587.33 * t) * 0.4
                        block[i] = (tone * pulse * 0.07 * 32767).toInt().toShort()
                        n++
                    }
                    track.write(block, 0, block.size)
                }
            } catch (_: Exception) {
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }.apply { isDaemon = true }.start()
    }

    @Synchronized
    fun stop() {
        running = false
        generation++
    }
}
