package com.arina.assistant.ai

import com.arina.assistant.Defaults
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Web search. The request goes to Entrion's own cloud function, which holds the search key (the admin sets it);
 * the key never reaches the phone. Needs a signed-in account. Blocking: call on Dispatchers.IO.
 */
object TavilySearch {

    /** Gives a fresh sign-in token, or null when nobody is signed in. Set by the app. */
    @Volatile var token: () -> String? = { null }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Returns the search results as plain text for the model to summarise. */
    fun search(query: String, results: Int = 6): String =
        text(call(query, results, images = false))

    /** Image addresses for a query (Tavily include_images). */
    fun images(query: String): List<String> {
        val json = call(query, 5, images = true)
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
    fun headlines(query: String = "top world news today", count: Int = 8): List<Pair<String, String>> {
        val json = call(query, count, images = false, news = true)
        val a = json.optJSONArray("results") ?: return emptyList()
        return (0 until a.length()).mapNotNull {
            val r = a.getJSONObject(it)
            val title = r.optString("title").trim()
            val url = r.optString("url")
            if (title.isNotEmpty() && url.startsWith("http")) title to url else null
        }
    }

    private fun call(query: String, results: Int, images: Boolean, news: Boolean = false): JSONObject {
        val jwt = token() ?: throw IOException("Sign in to use web search.")
        val body = JSONObject()
            .put("query", query)
                        .put("max_results", results)
            .put("include_answer", !images)
            .put("include_images", images)
            .apply { if (news) put("topic", "news") }
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(Defaults.SUPABASE_URL + "/functions/v1/search")
            .header("apikey", Defaults.SUPABASE_KEY)
            .header("Authorization", "Bearer $jwt")
            .post(body)
            .build()

        val (code, raw) = try {
            http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw IOException("Web search failed: ${e.message ?: "no internet?"}")
        }
        if (code !in 200..299) {
            throw IOException("Web search: " + (runCatching { JSONObject(raw).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() } ?: "error $code"))
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
