package com.arina.assistant.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Web search through Tavily (free plan: 1,000 searches a month, no card). Blocking: call on Dispatchers.IO. */
object TavilySearch {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Returns the search results as plain text for the model to summarise. */
    fun search(key: String, query: String): String {
        val body = JSONObject()
            .put("query", query)
            .put("search_depth", "basic")
            .put("max_results", 6)
            .put("include_answer", true)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.tavily.com/search")
            .header("Authorization", "Bearer ${key.trim()}")
            .post(body)
            .build()

        val (code, raw) = try {
            http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw IOException("Web search failed: ${e.message ?: "no internet?"}")
        }
        if (code !in 200..299) {
            throw IOException("Tavily error $code: ${raw.take(200)}")
        }
        val json = JSONObject(raw)
        return buildString {
            val answer = json.optString("answer")
            if (answer.isNotBlank()) append("SEARCH ENGINE SUMMARY: ").append(answer).append("\n\n")
            val results = json.optJSONArray("results")
            if (results != null) {
                for (i in 0 until results.length()) {
                    val r = results.getJSONObject(i)
                    append(i + 1).append(". ").append(r.optString("title"))
                        .append(" (").append(r.optString("url")).append(")\n")
                        .append(r.optString("content").take(600)).append("\n\n")
                }
            }
        }.ifBlank { throw IOException("The search returned nothing.") }
    }
}
