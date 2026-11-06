package com.arina.assistant.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Soft liquid "bloop" played when the navigation bar changes tab. Synthesised in code, so there is no sound file. */
object NavSound {
    private const val RATE = 22050
    private val tracks = arrayOfNulls<AudioTrack>(5)
    @Volatile var enabled = true

    private fun build(i: Int): AudioTrack? = try {
        val base = 520.0 + i * 70.0
        val n = (RATE * 0.11).toInt()
        val pcm = ShortArray(n)
        var phase = 0.0
        for (k in 0 until n) {
            val t = k.toDouble() / n
            val f = base * (1.0 + 0.9 * t)                 // pitch glides up: a water-drop feel
            phase += 2 * PI * f / RATE
            val env = (1 - exp(-t * 60)) * exp(-t * 7.5)   // soft attack, quick fade
            pcm[k] = (sin(phase) * env * 0.30 * Short.MAX_VALUE).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(n * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
        track.write(pcm, 0, n)
        track
    } catch (_: Throwable) { null }

    fun tick(slot: Int) {
        if (!enabled) return
        try {
            val i = slot.coerceIn(0, 4)
            val t = tracks[i] ?: build(i).also { tracks[i] = it } ?: return
            t.stop(); t.reloadStaticData(); t.play()
        } catch (_: Throwable) { }
    }
}
