package com.arina.assistant.ai

import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Plan-then-execute for one big instruction. The planner splits the goal into steps with dependencies;
 * steps whose inputs are ready run in parallel (that is where the time is saved), then one final pass
 * merges everything into the deliverable. Blocking: run it on Dispatchers.IO.
 */
object Workflow {

    class Step(val id: String, val kind: String, val task: String, val needs: List<String>)
    class Result(val output: String, val bn: String, val en: String, val steps: Int)

    private const val MAX_STEPS = 8
    private const val NOTE_CAP = 6_000

    private val PLAN = """
        You are a planner. Split the user's goal into 2 to $MAX_STEPS small steps that together finish it.
        Reply with JSON only: {"steps":[{"id":"s1","kind":"search|think|code","task":"...","needs":["s?"]}]}.
        kind search: look facts up on the web (task = one English search query).
        kind think: analyse, compare, decide or write using earlier results (task = exact instruction).
        kind code: write or fix code (task = exact requirements, language, file name).
        needs lists ids of steps whose output this step requires. Steps that need nothing run at the same time, so make as many steps independent as the goal allows. No cycles.
    """.trimIndent()

    private val WORK = """
        You execute one step of a larger job. Use only the given inputs and your own solid knowledge; never invent facts, numbers or sources.
        Be exact and compact. For code: output the complete working code only, then one line on how to run it.
    """.trimIndent()

    private val MERGE = """
        You deliver the final result of a multi-step job. Merge the step outputs into one clean deliverable.
        Reply with JSON only: {"output":"...","bn":"...","en":"..."}.
        output: the finished result in plain text (code stays as code), organised with short headed sections, no filler, no intro or outro.
        bn: 1 to 3 crisp spoken Bengali sentences (Bangla script): what is done and the key result.
        en: the same in short English.
    """.trimIndent()

    private fun clean(s: String) = s.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    private fun plan(goal: String, gemini: GeminiClient): List<Step> = runCatching {
        val a = JSONObject(clean(gemini.generate(PLAN, "GOAL: $goal", json = true))).getJSONArray("steps")
        val raw = (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val needs = o.optJSONArray("needs")
            Step(
                o.optString("id", "s${it + 1}"), o.optString("kind", "think"), o.optString("task"),
                (0 until (needs?.length() ?: 0)).map { n -> needs!!.getString(n) }
            )
        }.filter { it.task.isNotBlank() }.take(MAX_STEPS)
        val ids = raw.map { it.id }.toSet()
        raw.map { Step(it.id, it.kind, it.task, it.needs.filter { n -> n in ids && n != it.id }) }
    }.getOrDefault(emptyList())

    fun run(
        goal: String,
        tavilyKey: String,
        gemini: GeminiClient,
        onProgress: (done: Int, total: Int, line: String) -> Unit,
        cancelled: () -> Boolean
    ): Result {
        val steps = plan(goal, gemini).ifEmpty { listOf(Step("s1", "think", goal, emptyList())) }
        val out = HashMap<String, String>()
        val pending = steps.toMutableList()
        val pool = Executors.newFixedThreadPool(4)
        try {
            while (pending.isNotEmpty()) {
                if (cancelled()) throw InterruptedException("cancelled")
                var wave = pending.filter { s -> s.needs.all { it in out } }
                if (wave.isEmpty()) wave = listOf(pending.first()) // broken dependency: run anyway
                val jobs = wave.map { s ->
                    s to pool.submit(Callable {
                        val inputs = s.needs.joinToString("\n\n") { "RESULT ${it}:\n${out[it].orEmpty()}" }
                        runCatching {
                            if (s.kind == "search" && tavilyKey.isNotBlank())
                                TavilySearch.search(tavilyKey, s.task, 6).take(NOTE_CAP)
                            else gemini.generate(WORK, "OVERALL GOAL: $goal\nYOUR STEP: ${s.task}\n\n$inputs", slow = s.kind == "code").take(NOTE_CAP)
                        }.getOrElse { "(step failed: ${it.message})" }
                    })
                }
                jobs.forEach { (s, f) -> out[s.id] = f.get(); pending.remove(s) }
                onProgress(out.size, steps.size, wave.last().task.take(60))
            }
        } finally {
            pool.shutdownNow()
        }
        val material = steps.joinToString("\n\n") { "## ${it.id} (${it.kind}): ${it.task}\n${out[it.id].orEmpty()}" }
        val raw = gemini.generate(MERGE, "GOAL: $goal\n\nSTEP OUTPUTS:\n${material.take(60_000)}", json = true, slow = true)
        val o = JSONObject(clean(raw))
        return Result(o.optString("output").ifBlank { raw }, o.optString("bn"), o.optString("en"), steps.size)
    }
}
