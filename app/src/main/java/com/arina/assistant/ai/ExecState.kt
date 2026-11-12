package com.arina.assistant.ai

/** The explicit stages of any phone task. Only VERIFYING with evidence may lead to COMPLETED. */
enum class ExecState { IDLE, UNDERSTANDING, PLANNING, EXECUTING, VERIFYING, WAITING_FOR_APPROVAL, COMPLETED, FAILED, RECOVERING }

/** What the task is doing now. Written by the agent and the message sender, read by task_status and the problem report. */
object ExecTrace {
    @Volatile var state: ExecState = ExecState.IDLE; private set
    @Volatile var detail: String = ""; private set
    @Volatile var since: Long = 0L; private set

    fun set(s: ExecState, d: String = "") {
        if (s != state || d != detail) Diag.log("state ${s.name}${if (d.isNotBlank()) ": $d" else ""}")
        if (s != state) since = System.currentTimeMillis()
        state = s; detail = d
    }

    fun line(): String = if (state == ExecState.IDLE) "" else "State ${state.name}${if (detail.isNotBlank()) " (${detail.take(120)})" else ""} for ${(System.currentTimeMillis() - since) / 1000} s."
}
