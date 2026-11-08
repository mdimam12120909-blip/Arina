package com.arina.assistant.ai

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** One big task Entrion worked on: what it was, how far it got, and how it ended. Saved so nothing is lost if the app closes. */
data class Job(
    val id: String,
    val kind: String,          // "operate", "chat" or "workflow"
    val goal: String,
    val status: String,        // running, done, failed, stopped, interrupted
    val steps: List<String>,
    val result: String,
    val at: Long
)

/** Keeps the latest jobs for the signed-in account (the preferences file is per account). */
class JobStore(private val prefs: SharedPreferences) {
    fun load(): MutableList<Job> {
        val arr = runCatching { JSONArray(prefs.getString("jobs", "[]")) }.getOrDefault(JSONArray())
        val out = ArrayList<Job>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val st = o.optJSONArray("steps") ?: JSONArray()
            out += Job(
                o.optString("id"), o.optString("kind"), o.optString("goal"),
                // a job still "running" when the app starts was cut off
                o.optString("status").let { if (it == "running") "interrupted" else it },
                (0 until st.length()).map { st.optString(it) }, o.optString("result"), o.optLong("at")
            )
        }
        return out
    }

    fun save(jobs: List<Job>) {
        val arr = JSONArray()
        jobs.take(30).forEach { j ->
            arr.put(JSONObject().put("id", j.id).put("kind", j.kind).put("goal", j.goal).put("status", j.status)
                .put("steps", JSONArray(j.steps.takeLast(40))).put("result", j.result.take(4000)).put("at", j.at))
        }
        prefs.edit().putString("jobs", arr.toString()).apply()
    }
}
