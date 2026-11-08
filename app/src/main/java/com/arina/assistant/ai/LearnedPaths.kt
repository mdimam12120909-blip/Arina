package com.arina.assistant.ai

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** One step of a route that worked, described by what was on screen (labels), not by element numbers. */
data class PathStep(val action: String, val label: String = "", val text: String = "", val arg: String = "")

/**
 * Routes that worked before, saved per account. The same goal is replayed without asking the model for each step;
 * if a step cannot be done (the screen is different now) the agent falls back to thinking from that point.
 */
class LearnedPaths(private val prefs: SharedPreferences) {
    private fun key(goal: String) = goal.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().take(120)

    fun find(goal: String): List<PathStep>? {
        val o = runCatching { JSONObject(prefs.getString("paths", "{}")) }.getOrNull() ?: return null
        val arr = o.optJSONObject(key(goal))?.optJSONArray("steps") ?: return null
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { PathStep(it.optString("a"), it.optString("l"), it.optString("t"), it.optString("g")) }
        }.takeIf { it.isNotEmpty() }
    }

    fun save(goal: String, steps: List<PathStep>) {
        if (steps.isEmpty() || steps.size > 25) return
        val all = runCatching { JSONObject(prefs.getString("paths", "{}")) }.getOrDefault(JSONObject())
        val arr = JSONArray()
        steps.forEach { arr.put(JSONObject().put("a", it.action).put("l", it.label).put("t", it.text).put("g", it.arg)) }
        all.put(key(goal), JSONObject().put("steps", arr).put("at", System.currentTimeMillis()))
        // keep the newest 40 routes
        if (all.length() > 40) {
            val oldest = all.keys().asSequence().minByOrNull { all.getJSONObject(it).optLong("at") }
            if (oldest != null) all.remove(oldest)
        }
        prefs.edit().putString("paths", all.toString()).apply()
    }

    fun forget(goal: String) {
        val all = runCatching { JSONObject(prefs.getString("paths", "{}")) }.getOrDefault(JSONObject())
        all.remove(key(goal))
        prefs.edit().putString("paths", all.toString()).apply()
    }
}
