package com.arina.assistant.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Downloads a published Entrion.apk and hands it to Android's installer. Only links from our own GitHub releases are accepted. */
object Updater {
    private const val PREFIX = "https://github.com/mdimam12120909-blip/Arina/releases/download/"
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).followRedirects(true).build()

    fun allowed(url: String) = url.startsWith(PREFIX) && url.endsWith("/Entrion.apk")

    fun canInstall(c: Context) = c.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(c: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Blocking. Calls [progress] with 0..1 while downloading. Returns the finished file. */
    fun download(c: Context, url: String, progress: (Float) -> Unit): File {
        if (!allowed(url)) throw IOException("This update link is not trusted.")
        val dir = File(c.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "Entrion.apk")
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Download failed (HTTP ${r.code}).")
            val body = r.body ?: throw IOException("Empty download.")
            val total = body.contentLength().coerceAtLeast(1L)
            var done = 0L
            body.byteStream().use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n); done += n
                        progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (done < 1_000_000L) throw IOException("The downloaded file is too small to be the app.")
        }
        return out
    }

    fun install(c: Context, apk: File) {
        val uri = FileProvider.getUriForFile(c, c.packageName + ".files", apk)
        c.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
