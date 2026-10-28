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
    fun search(key: String, query: String, results: Int = 6): String =
        text(call(key, query, results, images = false))

    /** Image addresses for a query (Tavily include_images). */
    fun images(key: String, query: String): List<String> {
        val json = call(key, query, 5, images = true)
        val out = LinkedHashSet<String>()
        val top = json.optJSONArray("images")
        if (top != null) for (i in 0 until top.length()) {
            val o = top.opt(i)
            val u = if (o is JSONObject) o.optString("url") else o?.toString().orEmpty()
            if (u.startsWith("http")) out += u
        }
        return out.take(8)
    }

    /** Today's top headlines as (title, address) pairs. One search. */
    fun headlines(key: String, query: String = "top world news today", count: Int = 8): List<Pair<String, String>> {
        val json = call(key, query, count, images = false, news = true)
        val a = json.optJSONArray("results") ?: return emptyList()
        return (0 until a.length()).mapNotNull {
            val r = a.getJSONObject(it)
            val title = r.optString("title").trim()
            val url = r.optString("url")
            if (title.isNotEmpty() && url.startsWith("http")) title to url else null
        }
    }

    private fun call(key: String, query: String, results: Int, images: Boolean, news: Boolean = false): JSONObject {
        val body = JSONObject()
            .put("query", query)
            .put("search_depth", "basic")
            .put("max_results", results)
            .put("include_answer", !images)
            .put("include_images", images)
            .apply { if (news) put("topic", "news").put("time_range", "day") }
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
        return JSONObject(raw)
    }

    private fun text(json: JSONObject): String {
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
