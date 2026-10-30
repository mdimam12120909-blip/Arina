package com.arina.assistant.ai

/**
 * Turns streamed text into small, speakable phrases, so the voice can start after the first few words
 * instead of after the whole answer. A sentence end (. ? ! । ॥ or a new line) closes a chunk; a comma-like break
 * closes it once it has a few words; with no punctuation it is cut after a maximum number of words.
 * The first chunk uses smaller limits because it decides how soon the user hears the voice.
 * A '.' or ',' only counts when whitespace follows, so "3.5" and "1,000" are never split.
 */
class SmartChunker(
    private val firstSoftWords: Int = 3,
    private val softWords: Int = 4,
    private val firstMaxWords: Int = 5,
    private val maxWords: Int = 9,
    private val minSentenceWords: Int = 2
) {
    private companion object {
        val STRONG = setOf('?', '!', '।', '॥')
        val SOFT = setOf(',', ';', ':', '—', '–')
        val MARKDOWN = Regex("[*_#`>~|]+")
    }

    private var buf = ""
    private var emitted = 0

    /** Adds streamed text; returns the chunks that are ready now (maybe none). */
    fun feed(text: String): List<String> {
        buf += MARKDOWN.replace(text, "")
        val out = ArrayList<String>()
        while (true) {
            val cut = findCut() ?: break
            val piece = buf.substring(0, cut).trim()
            buf = buf.substring(cut).trimStart()
            if (piece.isNotEmpty()) {
                out += piece
                emitted++
            }
        }
        return out
    }

    /** The stream ended: whatever is left is the last chunk. */
    fun flush(): List<String> {
        val rest = buf.trim()
        buf = ""
        if (rest.isEmpty()) return emptyList()
        emitted++
        return listOf(rest)
    }

    private fun findCut(): Int? {
        val first = emitted == 0
        val softMin = if (first) firstSoftWords else softWords
        val maxW = if (first) firstMaxWords else maxWords
        val sentMin = if (first) 1 else minSentenceWords

        var words = 0
        var inWord = false
        for (i in buf.indices) {
            val ch = buf[i]
            if (ch.isWhitespace()) {
                if (inWord) {
                    inWord = false
                    if (words >= maxW) return i
                }
                if (ch == '\n' && words >= sentMin) return i + 1
                continue
            }
            if (!inWord) {
                inWord = true
                words++
            }
            val next: Char? = if (i + 1 < buf.length) buf[i + 1] else null
            when {
                ch in STRONG -> {
                    if (next != null && next in STRONG) continue // "?!" stays together
                    if (words >= sentMin) return i + 1
                }
                ch == '.' -> if (next != null && next.isWhitespace() && words >= sentMin) return i + 1
                ch in SOFT -> if (next != null && next.isWhitespace() && words >= softMin) return i + 1
            }
        }
        return null
    }
}
