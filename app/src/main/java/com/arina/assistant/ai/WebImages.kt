package com.arina.assistant.ai

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Finds pictures on the web for a query and saves them to Pictures/Entrion/<topic> in the gallery. Blocking. */
object WebImages {
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()

    fun save(ctx: Context, query: String, count: Int): String {
        if (query.isBlank()) return "ERROR: tell me what the pictures should show."
        val want = count.coerceIn(1, 8)
        val urls = try { TavilySearch.images(query) } catch (e: Exception) { return "ERROR: ${e.message}" }
        if (urls.isEmpty()) return "ERROR: no pictures found for '$query'."
        val folder = query.lowercase().replace(Regex("[^a-z0-9\\u0980-\\u09FF]+"), "_").trim('_').take(30).ifBlank { "pictures" }
        var saved = 0
        val failed = ArrayList<String>()
        for (u in urls) {
            if (saved >= want) break
            try {
                http.newCall(Request.Builder().url(u).header("User-Agent", "Mozilla/5.0").build()).execute().use { r ->
                    val type = r.body?.contentType()?.toString().orEmpty()
                    val bytes = r.body?.bytes()
                    if (!r.isSuccessful || bytes == null || bytes.size < 5000 || !type.startsWith("image/")) { failed += u.take(60); return@use }
                    val ext = if (type.contains("png")) "png" else if (type.contains("webp")) "webp" else "jpg"
                    val v = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "${folder}_${System.currentTimeMillis()}_$saved.$ext")
                        put(MediaStore.Images.Media.MIME_TYPE, type.substringBefore(';'))
                        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Entrion/$folder")
                    }
                    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
                    if (uri == null) { failed += "gallery"; return@use }
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    saved++
                }
            } catch (e: Exception) { failed += u.take(60) }
        }
        return if (saved == 0) "ERROR: found ${urls.size} links but none could be downloaded (blocked or not pictures)."
        else "CONFIRMED: saved $saved picture(s) in Pictures/Entrion/$folder." + if (saved < want) " Only $saved of the $want asked could be downloaded." else ""
    }
}
