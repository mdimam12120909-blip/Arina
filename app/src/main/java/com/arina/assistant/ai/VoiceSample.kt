package com.arina.assistant.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Plays one short sentence in a given Cartesia voice, so a voice can be heard (and checked) before it is chosen. */
object VoiceSample {
    @Volatile private var playing: AudioTrack? = null

    /** Runs on a background thread; [done] gets "" on success or a short problem text. */
    fun play(voice: String, model: String, bengali: Boolean, done: (String) -> Unit) {
        Thread {
            val msg = try {
                runCatching { playing?.stop(); playing?.release() }
                val text = if (bengali) "হ্যালো, আমি এই কণ্ঠে কথা বলব। শুনতে কেমন লাগছে?" else "Hello, this is how I will sound. How does it feel?"
                val bytes = Gateway.client.newCall(Gateway.tts(text, voice.trim(), model.ifBlank { "sonic-3" }, if (bengali) "bn" else "en")).execute().use { r ->
                    if (!r.isSuccessful) return@use "HTTP ${r.code}: ${r.body?.string().orEmpty().take(140)}"
                    r.body?.bytes() ?: ByteArray(0)
                }
                if (bytes is String) bytes
                else {
                    val pcm = bytes as ByteArray
                    val t = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(maxOf(pcm.size, 4096)).setTransferMode(AudioTrack.MODE_STATIC).build()
                    t.write(pcm, 0, pcm.size)
                    playing = t
                    t.play()
                    ""
                }
            } catch (e: Exception) { e.message?.take(140) ?: "failed" }
            done(msg)
        }.apply { isDaemon = true }.start()
    }
}
