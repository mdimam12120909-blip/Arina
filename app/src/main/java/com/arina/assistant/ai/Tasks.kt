package com.arina.assistant.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The user's to-do list, kept only on this phone. */
class Tasks(ctx: Context) {
    data class Task(val id: Long, val text: String, val done: Boolean)

    private val prefs = ctx.getSharedPreferences("arina_tasks", Context.MODE_PRIVATE)

    fun all(): List<Task> = runCatching {
        val a = JSONArray(prefs.getString("list", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); Task(o.getLong("id"), o.getString("text"), o.optBoolean("done")) }
    }.getOrDefault(emptyList())

    private fun save(list: List<Task>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done)) }
        prefs.edit().putString("list", a.toString()).apply()
    }

    fun add(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        val list = all()
        if (list.any { it.text.equals(t, true) && !it.done }) return false
        save(list + Task(System.currentTimeMillis(), t.take(120), false))
        return true
    }

    fun toggle(id: Long) = save(all().map { if (it.id == id) it.copy(done = !it.done) else it })

    fun remove(id: Long) = save(all().filter { it.id != id })

    /** Marks the first open task whose text contains [text] as done. */
    fun complete(text: String): Boolean {
        val q = text.trim().lowercase()
        val list = all()
        val hit = list.firstOrNull { !it.done && it.text.lowercase().contains(q) } ?: return false
        save(list.map { if (it.id == hit.id) it.copy(done = true) else it })
        return true
    }

    fun summary(): String {
        val open = all().filter { !it.done }
        return if (open.isEmpty()) "No open tasks." else open.joinToString("\n") { "- ${it.text}" }
    }
}
