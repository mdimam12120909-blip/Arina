package com.arina.assistant.ai

/**
 * The model answers in lines "BN: ...", "EN: ...", "YOU: ...", Bengali first. This watches the streamed text and
 * passes on only the Bengali line, piece by piece, as soon as it appears, so speaking can start while the other
 * two lines are still being written. "BN: -" means "say nothing" and is never passed on.
 */
class BnStreamer(private val onText: (String) -> Unit) {
    private val label = Regex("(?im)^\\s*BN\\s*:[ \\t]*")
    private val full = StringBuilder()
    private var start = -1
    private var emitted = 0
    private var closed = false

    fun feed(delta: String) {
        if (closed) return
        full.append(delta)
        if (start < 0) {
            val m = label.find(full) ?: return
            start = m.range.last + 1
            emitted = start
        }
        val nl = full.indexOf("\n", start)
        val end = if (nl >= 0) nl else full.length
        val pending = full.substring(emitted, end)
        if (emitted == start) {
            val t = pending.trim()
            if (nl < 0 && t.length < 2) return        // too little to tell a real sentence from a lone dash
            if (t == "-") { closed = true; return }   // the model chose silence
        }
        if (pending.isNotEmpty()) {
            onText(pending)
            emitted = end
        }
        if (nl >= 0) closed = true
    }
}
