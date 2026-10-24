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

    private val prefs = ctx.getSharedPreferences("arina_memory", Context.MODE_PRIVATE)

    @Synchronized
    fun profile(): String = prefs.getString("profile", "").orEmpty()

    @Synchronized
    fun setProfile(text: String) {
        prefs.edit().putString("profile", text.trim().take(MAX_PROFILE)).apply()
    }

    @Synchronized
    fun facts(): List<Fact> {
        val arr = runCatching { JSONArray(prefs.getString("facts", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull {
            val o = arr.optJSONObject(it) ?: return@mapNotNull null
            Fact(o.optLong("id"), o.optString("text")).takeIf { f -> f.text.isNotBlank() }
        }
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
            append(
                "\nMEMORY RULES: When he tells you something worth keeping for the long term (a preference, plan, decision, " +
                    "person, project progress, important date), call remember with one short English sentence. " +
                    "Do not save small talk, one-off requests, or secrets like passwords. " +
                    "Before changing something important you already know, ask him first. " +
                    "If he asks you to forget something, call forget. Never invent memories."
            )
        }
    }
}
