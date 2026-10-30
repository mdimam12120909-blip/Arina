package com.arina.assistant.ai

import org.json.JSONObject

/**
 * A long research job: plan searches, read the results, find the gaps, search again (up to four rounds),
 * then write a full report and a short spoken summary. Blocking: run it on Dispatchers.IO.
 */
object DeepResearch {

    class Result(val report: String, val bn: String, val en: String, val sources: Int)

    private const val ROUNDS = 4
    private const val MATERIAL_CAP = 70_000

    private val PLAN = """
        You plan web research. Reply with JSON only: {"queries":["...", ...]}.
        Give 5 diverse, specific English web search queries (3 to 9 words each) that together cover the topic well:
        basics, latest news or numbers, comparisons, opposing views, and primary sources where useful.
    """.trimIndent()

    private val REFLECT = """
        You are checking research progress. Given the topic and what has been found so far, decide if important gaps remain.
        Reply with JSON only: {"enough": true|false, "queries": ["...", ...]} with up to 4 NEW specific English search queries that fill the gaps (empty if enough).
    """.trimIndent()

    private val REPORT = """
        You are a careful research analyst. Use ONLY the research material given; never invent facts or numbers.
        Reply with JSON only: {"report": "...", "bn": "...", "en": "..."}.
        report: a thorough, well organised plain-text report in English: a title line, a short summary, then clearly headed sections with the key findings and numbers, disagreements or uncertainty between sources, a conclusion, and a final "Sources" list with the web addresses you used. Use plain text and dashes, no markdown symbols.
        bn: 3 to 5 natural spoken Bengali sentences (Bangla script) giving the most important findings.
        en: the same summary in short English.
    """.trimIndent()

    private fun queriesOf(json: String): List<String> = runCatching {
        val o = JSONObject(json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        val a = o.optJSONArray("queries")
        (0 until (a?.length() ?: 0)).map { a!!.getString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    fun run(
        topic: String,
        tavilyKey: String,
        gemini: GeminiClient,
        onProgress: (round: Int, total: Int, sources: Int) -> Unit,
        cancelled: () -> Boolean
    ): Result {
        val material = StringBuilder()
        val urls = LinkedHashSet<String>()
        val done = HashSet<String>()
        var queries = queriesOf(gemini.generate(PLAN, "TOPIC: $topic", json = true, role = KeyRole.Research)).take(5).ifEmpty { listOf(topic) }

        for (round in 1..ROUNDS) {
            if (cancelled()) throw InterruptedException("cancelled")
            for (q in queries) {
                if (!done.add(q.lowercase())) continue
                if (cancelled()) throw InterruptedException("cancelled")
                val text = runCatching { TavilySearch.search(tavilyKey, q, 6) }.getOrNull() ?: continue
                Regex("\\((https?://[^)\\s]+)\\)").findAll(text).forEach { urls += it.groupValues[1] }
                material.append("### SEARCH: ").append(q).append('\n').append(text.take(5000)).append("\n\n")
            }
            onProgress(round, ROUNDS, urls.size)
            if (round == ROUNDS || material.isEmpty()) break
            val digest = material.toString().takeLast(12_000)
            queries = queriesOf(
                gemini.generate(REFLECT, "TOPIC: $topic\n\nSEARCHES DONE: ${done.joinToString("; ")}\n\nFOUND SO FAR (tail):\n$digest", json = true, role = KeyRole.Research)
            ).take(4)
            if (queries.isEmpty()) break
        }
        if (material.isEmpty()) throw java.io.IOException("The web searches returned nothing.")

        val raw = gemini.generate(
            REPORT, "TOPIC: $topic\n\nRESEARCH MATERIAL:\n${material.toString().take(MATERIAL_CAP)}",
            json = true, slow = true, role = KeyRole.Research
        )
        val o = JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        return Result(o.optString("report").ifBlank { raw }, o.optString("bn"), o.optString("en"), urls.size)
    }
}
