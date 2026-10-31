package com.arina.assistant.ai

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Stopwatch for one spoken exchange. [start] when the user finishes, [mark] at each stage (first mark of a
 * name wins), [report] gives one line a person can read in Settings. Used to see where the waiting time goes.
 */
object Timing {
    private val marks = ConcurrentHashMap<String, Long>()
    private val notes = ConcurrentHashMap<String, String>()
    @Volatile private var t0 = 0L

    fun start() {
        marks.clear()
        notes.clear()
        t0 = System.nanoTime()
    }

    fun mark(name: String) {
        marks.putIfAbsent(name, System.nanoTime())
    }

    fun note(key: String, value: String) {
        notes[key] = value
    }

    fun report(): String {
        if (t0 == 0L) return ""
        val steps = marks.entries.sortedBy { it.value }
            .joinToString("\n") { "+${(it.value - t0) / 1_000_000} ms  ${it.key}" }
        val extra = notes.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}: ${it.value}" }
        val text = listOf(steps, extra).filter { it.isNotBlank() }.joinToString("\n")
        Log.i("EntrionTiming", text)
        return text
    }
}
