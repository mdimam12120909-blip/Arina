package com.arina.assistant.device

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Gets pictures and files ready for the on-screen card. Blocking: call on a background thread. */
class ShowTools(private val ctx: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val dir get() = File(ctx.cacheDir, "cards").apply { mkdirs() }

    /** Downloads the first [want] web images that really decode. Returns local file paths. */
    fun downloadImages(urls: List<String>, want: Int = 4): List<String> {
        dir.listFiles()?.forEach { it.delete() }
        val pool = Executors.newFixedThreadPool(4)
        try {
            val jobs = urls.take(8).mapIndexed { i, u ->
                pool.submit(Callable<String?> {
                    runCatching {
                        http.newCall(Request.Builder().url(u).header("User-Agent", "Mozilla/5.0").build()).execute().use { r ->
                            val bytes = r.body?.bytes()
                            if (!r.isSuccessful || bytes == null || bytes.size < 4000) return@use null
                            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                            if (o.outWidth < 200 || o.outHeight < 200) return@use null
                            File(dir, "web_$i.img").also { it.writeBytes(bytes) }.path
                        }
                    }.getOrNull()
                })
            }
            return jobs.mapNotNull { runCatching { it.get(12, TimeUnit.SECONDS) }.getOrNull() }.take(want)
        } finally {
            pool.shutdownNow()
        }
    }

    fun hasPhotoAccess(): Boolean {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        return ctx.checkSelfPermission(p) == 0
    }

    /** Newest gallery photos as content:// addresses, optionally only from an album whose name contains [album]. */
    fun gallery(album: String, count: Int): List<String> {
        val out = ArrayList<String>()
        val sel = if (album.isBlank()) null else "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?"
        val args = if (album.isBlank()) null else arrayOf("%${album.trim()}%")
        ctx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), sel, args,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            while (c.moveToNext() && out.size < count.coerceIn(1, 8)) {
                out += ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)).toString()
            }
        }
        return out
    }

    /** Looks through the usual folders for a file whose name contains [name]. */
    fun findFile(name: String): File? {
        val q = name.trim().lowercase()
        if (q.isEmpty()) return null
        val root = Environment.getExternalStorageDirectory()
        val folders = listOf("Download", "Documents", "Pictures", "DCIM", "Movies", "Music", "Telegram", "WhatsApp")
        var best: File? = null
        var seen = 0
        fun walk(f: File, depth: Int) {
            if (seen > 6000 || depth > 4) return
            val kids = f.listFiles() ?: return
            for (k in kids) {
                seen++
                if (k.isDirectory) walk(k, depth + 1)
                else if (k.name.lowercase().contains(q) && (best == null || k.lastModified() > best!!.lastModified())) best = k
            }
        }
        folders.forEach { walk(File(root, it), 0) }
        return best
    }

    /** First pages of a PDF as image files. */
    fun pdfPages(file: File, max: Int = 4): List<String> {
        val out = ArrayList<String>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                for (i in 0 until minOf(max, r.pageCount)) {
                    r.openPage(i).use { p ->
                        val w = 1000
                        val h = (w.toFloat() * p.height / p.width).toInt()
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val f = File(dir, "pdf_$i.png")
                        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                        out += f.path
                    }
                }
            }
        }
        return out
    }

    fun textPreview(file: File): String = file.bufferedReader().use { r ->
        val buf = CharArray(8000)
        val n = r.read(buf)
        if (n <= 0) "" else String(buf, 0, n)
    }
}
