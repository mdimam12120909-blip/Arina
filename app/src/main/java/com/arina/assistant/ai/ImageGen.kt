package com.arina.assistant.ai

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Makes a picture from a description (or changes the last one) with the user's own Gemini key, and saves it to the gallery. */
class ImageGen(private val ctx: Context, private val key: () -> String) {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS).callTimeout(80, TimeUnit.SECONDS).build()
    private val dir get() = File(ctx.cacheDir, "cards").apply { mkdirs() }
    @Volatile var last: File? = null; private set

    class Result(val file: File?, val error: String)

    fun make(prompt: String, changeLast: Boolean): Result {
        val k = key().trim()
        if (k.isEmpty()) return Result(null, "No Gemini key. Add one in Settings, Connections.")
        val ref = if (changeLast) last?.takeIf { it.exists() } else null
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        ref?.let { parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/png").put("data", Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)))) }
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("IMAGE").put("TEXT")))
            .toString().toRequestBody("application/json".toMediaType())
        var err = "no image model answered"
        for (m in MODELS) {
            try {
                val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent").header("x-goog-api-key", k).post(body).build()
                http.newCall(req).execute().use { r ->
                    val txt = r.body?.string().orEmpty()
                    if (!r.isSuccessful) { err = "$m: HTTP ${r.code} ${runCatching { JSONObject(txt).getJSONObject("error").optString("message") }.getOrDefault("").take(120)}"; Diag.log("image: $err"); return@use }
                    val ps = JSONObject(txt).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return@use
                    for (i in 0 until ps.length()) {
                        val d = ps.getJSONObject(i).let { it.optJSONObject("inlineData") ?: it.optJSONObject("inline_data") }?.optString("data").orEmpty()
                        if (d.isNotEmpty()) {
                            val bytes = Base64.decode(d, Base64.DEFAULT)
                            dir.listFiles()?.filter { it.name.startsWith("gen_") }?.forEach { it.delete() }
                            val f = File(dir, "gen_${System.currentTimeMillis()}.png").also { it.writeBytes(bytes) }
                            last = f
                            saveToGallery(bytes)
                            return Result(f, "")
                        }
                    }
                    err = "$m answered without a picture (it may have refused the request)"
                }
            } catch (e: Exception) { err = "$m: ${e.message}" }
        }
        return Result(null, err)
    }

    private fun saveToGallery(bytes: ByteArray) {
        runCatching {
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Entrion_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Entrion")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }
    }

    companion object { private val MODELS = listOf("gemini-3.1-flash-image-preview", "gemini-2.5-flash-image") }
}
