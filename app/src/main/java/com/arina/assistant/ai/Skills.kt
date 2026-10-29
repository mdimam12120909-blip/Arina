package com.arina.assistant.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A named procedure: when the user asks for this kind of work, the steps are followed in order and checked at the end. */
class Skill(val name: String, val when_: String, val steps: String, val builtIn: Boolean = false)

/** Built-in skills plus the ones the user saves. Saved ones live in SharedPreferences as JSON. */
class SkillStore(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences("arina_skills", Context.MODE_PRIVATE)

    private val builtIn = listOf(
        Skill(
            "Professional Report", "a report, brief, analysis or write-up on a topic",
            """
            1. State the goal and the reader in one line; list any assumption you must make.
            2. Gather facts from the web; keep only what sources actually support, with numbers and dates.
            3. Draft: title, 3-line summary, headed sections, key numbers, risks or disagreements between sources.
            4. Check every claim against the gathered facts; remove or mark anything unsupported.
            5. Finish with a clear conclusion and a Sources list.
            """.trimIndent(), true
        ),
        Skill(
            "Code Task", "writing, fixing, reviewing or debugging code",
            """
            1. Restate the requirement and the language; list inputs, outputs and edge cases.
            2. Design the approach in a few lines before writing any code.
            3. Write the complete code, no placeholders.
            4. Walk through it with two normal inputs and one edge case; fix any bug found.
            5. Give the final code and one line on how to run it.
            """.trimIndent(), true
        ),
        Skill(
            "Compare And Decide", "choosing between options, which is better, buying advice",
            """
            1. List the options and the criteria that matter for this user.
            2. Look up current facts and prices for each option.
            3. Score each option per criterion, in a short table of plain lines.
            4. State one clear recommendation and the single biggest trade-off.
            """.trimIndent(), true
        )
    )

    private fun saved(): List<Skill> = runCatching {
        val a = JSONArray(prefs.getString("list", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Skill(o.getString("name"), o.optString("when"), o.optString("steps"))
        }
    }.getOrDefault(emptyList())

    fun all(): List<Skill> = builtIn + saved()

    fun find(name: String): Skill? {
        val n = name.trim().lowercase()
        if (n.isEmpty()) return null
        val list = all()
        return list.firstOrNull { it.name.lowercase() == n } ?: list.firstOrNull { it.name.lowercase().contains(n) || n.contains(it.name.lowercase()) }
    }

    /** Adds or replaces (same name) a user skill. */
    fun save(name: String, whenText: String, steps: String): Boolean {
        val nm = name.trim()
        if (nm.isEmpty() || steps.isBlank() || builtIn.any { it.name.equals(nm, true) }) return false
        val list = saved().filter { !it.name.equals(nm, true) } + Skill(nm, whenText.trim(), steps.trim())
        write(list)
        return true
    }

    fun remove(name: String): Boolean {
        val before = saved()
        val after = before.filter { !it.name.equals(name.trim(), true) }
        if (after.size == before.size) return false
        write(after)
        return true
    }

    private fun write(list: List<Skill>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("name", it.name).put("when", it.when_).put("steps", it.steps)) }
        prefs.edit().putString("list", a.toString()).apply()
    }

    /** One line per skill for the system prompt, so the model knows when to call use_skill. */
    fun index(): String =
        "\nSKILLS you have (call use_skill with the name when the user's request fits one):\n" +
            all().joinToString("\n") { "- ${it.name}: ${it.when_.ifBlank { "as the user describes" }}" }
}
