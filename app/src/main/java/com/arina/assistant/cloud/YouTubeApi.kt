package com.arina.assistant.cloud

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.closeQuietly
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Uploads a video to the signed-in user's own YouTube channel (resumable upload, YouTube Data API v3). */
object YouTubeApi {
    const val SCOPE = "https://www.googleapis.com/auth/youtube.upload"
    private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).writeTimeout(10, TimeUnit.MINUTES).readTimeout(10, TimeUnit.MINUTES).build()

    /** Returns the new video's address, or throws with a plain reason. */
    fun upload(token: String, file: File, title: String, description: String, tags: List<String>, privacy: String): String {
        val meta = JSONObject()
            .put("snippet", JSONObject().put("title", title.take(100)).put("description", description.take(4900)).put("tags", JSONArray(tags.take(30))).put("categoryId", "22"))
            .put("status", JSONObject().put("privacyStatus", privacy).put("selfDeclaredMadeForKids", false))
        val init = Request.Builder()
            .url("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
            .header("Authorization", "Bearer $token")
            .header("X-Upload-Content-Type", "video/*")
            .header("X-Upload-Content-Length", file.length().toString())
            .post(meta.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())).build()
        val loc = http.newCall(init).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException(explain(r.code, r.body?.string().orEmpty()))
            r.header("Location") ?: throw java.io.IOException("YouTube gave no upload address.")
        }
        val body = object : RequestBody() {
            override fun contentType() = "video/*".toMediaType()
            override fun contentLength() = file.length()
            override fun writeTo(sink: BufferedSink) { val s = file.source(); try { sink.writeAll(s) } finally { s.closeQuietly() } }
        }
        http.newCall(Request.Builder().url(loc).put(body).build()).execute().use { r ->
            val txt = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw java.io.IOException(explain(r.code, txt))
            val id = JSONObject(txt).optString("id")
            return "https://youtu.be/$id"
        }
    }

    private fun explain(code: Int, body: String): String {
        val msg = runCatching { JSONObject(body).getJSONObject("error").optString("message") }.getOrDefault("")
        return when {
            body.contains("quotaExceeded") -> "YouTube's daily upload limit for this app is used up. Try again tomorrow."
            code == 401 -> "The YouTube connection expired. Connect YouTube again."
            code == 403 -> "YouTube refused: ${msg.ifBlank { "not allowed" }}"
            else -> "YouTube error $code ${msg.take(120)}"
        }
    }
}
