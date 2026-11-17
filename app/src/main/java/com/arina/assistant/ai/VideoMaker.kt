package com.arina.assistant.ai

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.provider.MediaStore
import java.io.File

/** Makes a silent slideshow video (vertical 720x1280, H.264) from pictures in the gallery. Blocking, takes some seconds. */
object VideoMaker {
    private const val W = 720
    private const val H = 1280
    private const val FPS = 10

    /** Newest pictures saved by Entrion (optionally only the folder whose name contains [topic]). */
    private fun pictures(ctx: Context, topic: String, max: Int): List<android.net.Uri> {
        val out = ArrayList<android.net.Uri>()
        val t = topic.lowercase().replace(Regex("[^a-z0-9\\u0980-\\u09FF]+"), "_").trim('_')
        val sel = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val arg = if (t.isBlank()) "Pictures/Entrion/%" else "Pictures/Entrion/%$t%"
        ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), sel, arrayOf(arg), "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
            while (c.moveToNext() && out.size < max) out += android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
        }
        return out
    }

    private fun load(ctx: Context, uri: android.net.Uri): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (o.outWidth / (s * 2) >= W && o.outHeight / (s * 2) >= H) s *= 2
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
    }.getOrNull()

    /** Centre-crops the picture to fill the vertical frame. */
    private fun frame(src: Bitmap): Bitmap {
        val dst = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(dst); c.drawColor(Color.BLACK)
        val sr = src.width.toFloat() / src.height
        val dr = W.toFloat() / H
        val r = if (sr > dr) { val w = (src.height * dr).toInt(); Rect((src.width - w) / 2, 0, (src.width + w) / 2, src.height) }
                else { val h = (src.width / dr).toInt(); Rect(0, (src.height - h) / 2, src.width, (src.height + h) / 2) }
        c.drawBitmap(src, r, Rect(0, 0, W, H), null)
        return dst
    }

    fun make(ctx: Context, topic: String, secondsEach: Int): String {
        val uris = pictures(ctx, topic, 12)
        if (uris.isEmpty()) return "ERROR: no saved pictures${if (topic.isBlank()) "" else " for '$topic'"}. Use save_web_images or generate_image first."
        val frames = uris.mapNotNull { load(ctx, it)?.let { b -> frame(b) } }
        if (frames.isEmpty()) return "ERROR: the saved pictures could not be opened."
        val hold = secondsEach.coerceIn(1, 8) * FPS
        val tmp = File(ctx.cacheDir, "slideshow_${System.currentTimeMillis()}.mp4")
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, 2_500_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val cd = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec = cd
            cd.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            cd.start()
            val mx = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mx
            var track = -1
            var started = false
            val info = MediaCodec.BufferInfo()

            fun drain(end: Boolean) {
                while (true) {
                    val i = cd.dequeueOutputBuffer(info, if (end) 20_000 else 0)
                    when {
                        i == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!end) return
                        i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = mx.addTrack(cd.outputFormat); mx.start(); started = true }
                        i >= 0 -> {
                            val buf = cd.getOutputBuffer(i)
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                            if (info.size > 0 && started && buf != null) { buf.position(info.offset); buf.limit(info.offset + info.size); mx.writeSampleData(track, buf, info) }
                            cd.releaseOutputBuffer(i, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }

            val px = IntArray(W * H)
            var n = 0L
            for (f in frames) {
                f.getPixels(px, 0, W, 0, 0, W, H)
                val y = ByteArray(W * H); val u = ByteArray(W * H / 4); val v = ByteArray(W * H / 4)
                for (j in 0 until H) for (i in 0 until W) {
                    val p = px[j * W + i]; val r = p shr 16 and 255; val g = p shr 8 and 255; val b = p and 255
                    y[j * W + i] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(16, 235).toByte()
                    if (j % 2 == 0 && i % 2 == 0) {
                        val k = (j / 2) * (W / 2) + i / 2
                        u[k] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(16, 240).toByte()
                        v[k] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(16, 240).toByte()
                    }
                }
                repeat(hold) {
                    var idx = cd.dequeueInputBuffer(20_000)
                    while (idx < 0) { drain(false); idx = cd.dequeueInputBuffer(20_000) }
                    val img = cd.getInputImage(idx) ?: throw IllegalStateException("encoder gave no input image")
                    val planes = img.planes
                    val src = arrayOf(y, u, v)
                    val pw = intArrayOf(W, W / 2, W / 2); val ph = intArrayOf(H, H / 2, H / 2)
                    for (k in 0..2) {
                        val pl = planes[k]; val bb = pl.buffer
                        for (row in 0 until ph[k]) for (col in 0 until pw[k]) bb.put(row * pl.rowStride + col * pl.pixelStride, src[k][row * pw[k] + col])
                    }
                    cd.queueInputBuffer(idx, 0, W * H * 3 / 2, n * 1_000_000L / FPS, 0)
                    n++
                    drain(false)
                }
            }
            var idx = cd.dequeueInputBuffer(20_000)
            while (idx < 0) { drain(false); idx = cd.dequeueInputBuffer(20_000) }
            cd.queueInputBuffer(idx, 0, 0, n * 1_000_000L / FPS, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
            mx.stop()
        } catch (e: Exception) {
            return "ERROR: could not make the video (${e.message ?: e.javaClass.simpleName})."
        } finally {
            runCatching { codec?.stop() }; runCatching { codec?.release() }; runCatching { muxer?.release() }
        }
        return try {
            val v = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "Entrion_slideshow_${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Entrion")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v) ?: return "ERROR: could not save the video to the gallery."
            ctx.contentResolver.openOutputStream(uri)?.use { o -> tmp.inputStream().use { it.copyTo(o) } }
            tmp.delete()
            "CONFIRMED: made a ${frames.size * hold / FPS} second silent slideshow video from ${frames.size} pictures, saved in Movies/Entrion."
        } catch (e: Exception) { "ERROR: the video was made but could not be saved (${e.message})." }
    }
}
