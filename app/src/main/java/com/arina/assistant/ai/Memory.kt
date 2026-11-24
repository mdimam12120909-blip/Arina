package com.arina.assistant.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Arina's long-term memory. Stored only on this phone (private SharedPreferences), never in the repo.
 *  - profile: free text the user pastes on the Memory page
 *  - facts: short sentences Arina saves herself with the remember tool
 * Everything is injected into Gemini's system instruction on every request.
 */
class Memory(ctx: Context) {

    data class Fact(val id: Long, val text: String)

    private companion object {
        const val MAX_FACTS = 200
        const val PROMPT_FACTS = 80
        const val MAX_PROFILE = 6000
    }

    private val prefs = com.arina.assistant.cloud.Local.prefs(ctx, "arina_memory")

    @Synchronized
    fun profile(): String = prefs.getString("profile", "").orEmpty()

    @Synchronized
    fun setProfile(text: String) {
        prefs.edit().putString("profile", text.trim().take(MAX_PROFILE)).apply()
    }

    @Synchronized
    fun facts(): List<Fact> {
        purgeWrongAge()
        val arr = runCatching { JSONArray(prefs.getString("facts", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull {
            val o = arr.optJSONObject(it) ?: return@mapNotNull null
            Fact(o.optLong("id"), o.optString("text")).takeIf { f -> f.text.isNotBlank() }
        }
    }

    /** An old wrong note ("14 years old") must never come back: the owner is 23. Removed once, quietly. */
    private fun purgeWrongAge() {
        val raw = prefs.getString("facts", "[]").orEmpty()
        if (!raw.contains("14 years", ignoreCase = true) && !raw.contains("14-year", ignoreCase = true)) return
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val t = o.optString("text")
            if (t.contains("14 years", ignoreCase = true) || t.contains("14-year", ignoreCase = true)) continue
            out.put(o)
        }
        prefs.edit().putString("facts", out.toString()).apply()
    }

    private fun save(list: List<Fact>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text)) }
        prefs.edit().putString("facts", arr.toString()).apply()
    }

    /** Returns false when the same fact is already stored. */
    @Synchronized
    fun add(text: String): Boolean {
        val t = text.trim().take(300)
        if (t.isEmpty()) return false
        val list = facts().toMutableList()
        if (list.any { it.text.equals(t, ignoreCase = true) }) return false
        list += Fact(System.currentTimeMillis(), t)
        while (list.size > MAX_FACTS) list.removeAt(0)
        save(list)
        return true
    }

    @Synchronized
    fun remove(id: Long) = save(facts().filter { it.id != id })

    /** Removes every fact containing [match] (case-insensitive). Returns how many were removed. */
    @Synchronized
    fun forget(match: String): Int {
        val m = match.trim()
        if (m.isEmpty()) return 0
        val list = facts()
        val kept = list.filter { !it.text.contains(m, ignoreCase = true) }
        if (kept.size != list.size) save(kept)
        return list.size - kept.size
    }

    data class Episode(val at: Long, val text: String)

    @Synchronized
    fun episodes(): List<Episode> {
        val arr = runCatching { JSONArray(prefs.getString("episodes", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let { o -> Episode(o.optLong("at"), o.optString("t")) } }.filter { it.text.isNotBlank() }
    }

    @Synchronized
    fun addEpisode(text: String) {
        val t = text.trim().take(500)
        if (t.isEmpty()) return
        val list = episodes().toMutableList()
        list += Episode(System.currentTimeMillis(), t)
        while (list.size > 40) list.removeAt(0)
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("at", it.at).put("t", it.text)) }
        prefs.edit().putString("episodes", arr.toString()).apply()
    }

    private fun ago(at: Long): String {
        val d = (System.currentTimeMillis() - at) / 86_400_000L
        return when { d <= 0L -> "earlier today"; d == 1L -> "yesterday"; d < 7L -> "$d days ago"; d < 30L -> "${d / 7} weeks ago"; else -> "${d / 30} months ago" }
    }

    /** Text added to the system instruction. */
    fun promptBlock(): String {
        val p = profile()
        val f = facts().takeLast(PROMPT_FACTS)
        return buildString {
            append("\nPRIVATE MEMORY ABOUT THE USER (from his Memory page; use it naturally, never recite it unprompted):\n")
            append(if (p.isBlank()) "(no profile saved yet)\n" else p + "\n")
            if (f.isNotEmpty()) {
                append("\nTHINGS YOU REMEMBER (saved by you earlier):\n")
                f.forEach { append("- ").append(it.text).append('\n') }
            }
            val ep = episodes().takeLast(12)
            if (ep.isNotEmpty()) {
                append("\nOUR EARLIER TALKS (your private diary, oldest first):\n")
                ep.forEach { append("- ").append(ago(it.at)).append(": ").append(it.text.replace('\n', ' ')).append('\n') }
            }
            append(
                "\nHOW TO USE MEMORY LIKE A CLOSE FRIEND: bring earlier things into the present talk when they fit (\"আগে যে বলেছিলে ... সেটা কী হলো?\", \"তুমি তো এসব পছন্দ করো না, তাই...\"), connect his old plans, worries and habits to what he says now, notice patterns (always late at night, always tired before exams, always the same music) and mention them gently and rarely. Remember exactly what he said; if unsure, say you are not sure instead of guessing. " +
                "When he seems to be wasting time or worrying too much, you may sometimes change tone, a little worried and warm, and say what he should do instead (\"আরে, তোমাকে নিয়ে চিন্তা হয়। এসব এখন থাক, আগে ... কাজটা করো\"). Joke and tease lightly when he is relaxed; ask how his day is going now and then, not every time.\n" +
                "\nMEMORY RULES: When he tells you something worth keeping for the long term (a preference, plan, decision, " +
                    "person, project progress, important date), call remember with one short English sentence. Also save his preferences, likes and dislikes, habits and daily patterns, people around him, goals and fears, and how he likes you to talk. " +
                    "Do NOT save small talk, one-off requests, quiz or exam scores, single right or wrong answers, the details of what you just did, or secrets like passwords. Save only what will still matter in a week: a lasting weak subject, a goal, a habit. Never write a memory in the same breath as speaking it aloud: after remember say nothing extra about it. " +
                    "Before changing something important you already know, ask him first. " +
                    "If he asks you to forget something, call forget. Never invent memories."
            )
        }
    }
}
