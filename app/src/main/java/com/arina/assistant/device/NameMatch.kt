package com.arina.assistant.device

/**
 * Matches a spoken name against saved contacts across scripts and spellings: "মোহাম্মদ আলী", "Mohammad Ali" and
 * "Muhammad Ali" all reduce to the same consonant skeleton ("mhmd l"). Vowels are dropped because they are where
 * spellings differ; similar consonants (k/q/c, f/ph, b/v/w, j/z) are merged.
 */
object NameMatch {
    private val bn = mapOf(
        'ক' to 'k', 'খ' to 'k', 'গ' to 'g', 'ঘ' to 'g', 'ঙ' to 'n', 'চ' to 'c', 'ছ' to 'c', 'জ' to 'j', 'ঝ' to 'j', 'ঞ' to 'n',
        'ট' to 't', 'ঠ' to 't', 'ড' to 'd', 'ঢ' to 'd', 'ণ' to 'n', 'ত' to 't', 'থ' to 't', 'দ' to 'd', 'ধ' to 'd', 'ন' to 'n',
        'প' to 'p', 'ফ' to 'p', 'ব' to 'b', 'ভ' to 'b', 'ম' to 'm', 'য' to 'j', 'র' to 'r', 'ল' to 'l', 'শ' to 's', 'ষ' to 's',
        'স' to 's', 'হ' to 'h', 'ড়' to 'r', 'ঢ়' to 'r', 'ৎ' to 't', 'ং' to 'n'
    )
    private val hi = mapOf(
        'क' to 'k', 'ख' to 'k', 'ग' to 'g', 'घ' to 'g', 'च' to 'c', 'छ' to 'c', 'ज' to 'j', 'झ' to 'j', 'ट' to 't', 'ठ' to 't',
        'ड' to 'd', 'ढ' to 'd', 'ण' to 'n', 'त' to 't', 'थ' to 't', 'द' to 'd', 'ध' to 'd', 'न' to 'n', 'प' to 'p', 'फ' to 'p',
        'ब' to 'b', 'भ' to 'b', 'म' to 'm', 'य' to 'y', 'र' to 'r', 'ल' to 'l', 'व' to 'b', 'श' to 's', 'ष' to 's', 'स' to 's', 'ह' to 'h'
    )

    private fun latin(c: Char): Char? = when (c) {
        'k', 'c', 'q', 'x' -> 'k'
        'f' -> 'p'
        'v', 'w' -> 'b'
        'z' -> 'j'
        'g', 'j', 't', 'd', 'n', 'p', 'b', 'm', 'r', 'l', 's', 'h' -> c
        else -> null    // vowels, y and anything else carry no weight
    }

    /** One skeleton per word, e.g. "Mohammad Ali" -> ["mhmd", "l"]. */
    fun skeleton(name: String): List<String> {
        val words = name.lowercase().split(Regex("[^\\p{L}\\p{M}]+")).filter { it.isNotEmpty() }
        return words.map { w ->
            var s = w.replace("ph", "f").replace("sh", "s").replace("kh", "k").replace("gh", "g").replace("th", "t").replace("dh", "d").replace("bh", "b").replace("ch", "c")
            val sb = StringBuilder()
            for (ch in s) { val m = bn[ch] ?: hi[ch] ?: latin(ch) ?: continue; if (sb.isEmpty() || sb.last() != m) sb.append(m) }
            sb.toString()
        }.filter { it.isNotEmpty() }
    }

    /** True when every word of [query] matches the start of some word of [candidate]. */
    fun matches(query: String, candidate: String): Boolean {
        val q = skeleton(query)
        if (q.isEmpty()) return false
        val c = skeleton(candidate)
        return q.all { qw -> c.any { it.startsWith(qw) || qw.startsWith(it) && it.length >= 3 } }
    }
}
