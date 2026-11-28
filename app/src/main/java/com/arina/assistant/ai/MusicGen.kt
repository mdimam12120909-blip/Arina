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
import java.util.concurrent.TimeUnit

/** Makes a song from a description with a Gemini key (Google's music model) and saves it to Music/Entrion. */
class MusicGen(private val ctx: Context, private val key: () -> String) {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(150, TimeUnit.SECONDS).callTimeout(160, TimeUnit.SECONDS).build()

    class Result(val name: String, val lyrics: String, val error: String)

    fun make(prompt: String): Result {
        val k = key().trim()
        if (k.isEmpty()) return Result("", "", "No Gemini key.")
        val bodyJson = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("AUDIO").put("TEXT")))
            .toString()
        val keys = if (Gateway.isProxy(k)) listOf(k) + (0..4).map { "${Gateway.PREFIX}$it" }.filter { it != k }.take(3) else listOf(k)
        var err = "no music model answered"
        var quota = false
        for (kk in keys) {
            quota = false
            for (m in MODELS) {
                try {
                    val req = if (Gateway.isProxy(kk)) Gateway.gen(kk, m, false, JSONObject(bodyJson))
                    else Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent").header("x-goog-api-key", kk).post(bodyJson.toRequestBody("application/json".toMediaType())).build()
                    http.newCall(req).execute().use { r ->
                        val txt = r.body?.string().orEmpty()
                        if (!r.isSuccessful) {
                            if (r.code == 429) quota = true
                            err = "$m: HTTP ${r.code} ${runCatching { JSONObject(txt).getJSONObject("error").optString("message") }.getOrDefault("").take(120)}"
                            Diag.log("music: $err"); return@use
                        }
                        val ps = JSONObject(txt).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return@use
                        var audio: ByteArray? = null; var mime = "audio/mpeg"; val words = StringBuilder()
                        for (i in 0 until ps.length()) {
                            val p = ps.getJSONObject(i)
                            val inl = p.optJSONObject("inlineData") ?: p.optJSONObject("inline_data")
                            val d = inl?.optString("data").orEmpty()
                            if (d.isNotEmpty()) { audio = Base64.decode(d, Base64.DEFAULT); mime = inl?.optString("mimeType").orEmpty().ifBlank { inl?.optString("mime_type").orEmpty() }.ifBlank { "audio/mpeg" } }
                            else if (p.has("text") && !p.optBoolean("thought")) words.append(p.optString("text")).append('\n')
                        }
                        if (audio != null) {
                            val name = save(audio, mime) ?: return Result("", "", "The song was made but could not be saved on the phone (storage permission?).")
                            return Result(name, words.toString().trim().take(1500), "")
                        }
                        err = "$m answered without music (it may have refused the request)"
                    }
                } catch (e: Exception) { err = "$m: ${e.message}" }
            }
            if (!quota) break
        }
        if (quota) return Result("", "", "QUOTA")
        return Result("", "", err)
    }

    private fun save(bytes: ByteArray, mime: String): String? = runCatching {
        val ext = when { mime.contains("wav") -> "wav"; mime.contains("ogg") -> "ogg"; else -> "mp3" }
        val name = "Entrion_${System.currentTimeMillis()}.$ext"
        val v = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Entrion")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, v) ?: return null
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
        name
    }.getOrNull()

    companion object { private val MODELS = listOf("lyria-3.5", "lyria-3-clip-preview") }
}
