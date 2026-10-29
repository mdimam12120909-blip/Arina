package com.arina.assistant

import android.content.Context

/**
 * API keys and voice names. Values typed in Settings are stored on the phone and win over the keys baked in
 * at build time, so they can be fixed without rebuilding. Gemini has five slots; each request uses the next one.
 */
object Keys {
    const val GEMINI_SLOTS = 5

    private fun prefs(c: Context) = c.getSharedPreferences("arina_keys", Context.MODE_PRIVATE)

    /**
     * A key typed in Settings wins over the build key, except right after a rebuild whose
     * key changed: then the new build key replaces the old saved one (so updating a GitHub secret works).
     */
    private fun pick(c: Context, name: String, fallback: String): String {
        val p = prefs(c)
        if (fallback.isNotBlank() && p.getString("build_$name", null) != fallback) {
            p.edit().putString("build_$name", fallback).remove(name).apply()
            return fallback
        }
        return p.getString(name, null)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback
    }

    private fun slotName(i: Int) = if (i == 0) "gemini" else "gemini${i + 1}"

    /** The five Gemini key slots, in order; empty strings for unused slots. Slot 1 can come from the build. */
    fun geminiSlots(c: Context): List<String> = List(GEMINI_SLOTS) { i ->
        if (i == 0) pick(c, "gemini", BuildConfig.GEMINI_API_KEY)
        else prefs(c).getString(slotName(i), "")?.trim().orEmpty()
    }

    /** All non-empty Gemini keys joined by new lines, the form the clients read. */
    fun gemini(c: Context): String = geminiSlots(c).filter { it.isNotBlank() }.joinToString("\n")

    fun tavily(c: Context) = pick(c, "tavily", BuildConfig.TAVILY_API_KEY)
    fun voiceArina(c: Context): String = prefs(c).getString("tts_voice_arina", null)?.trim()?.takeIf { it.isNotEmpty() } ?: "Kore"
    fun voiceAurion(c: Context): String = prefs(c).getString("tts_voice_aurion", null)?.trim()?.takeIf { it.isNotEmpty() } ?: "Charon"

    fun save(c: Context, gemini: List<String>, tavily: String, voiceArina: String, voiceAurion: String) {
        val e = prefs(c).edit()
        for (i in 0 until GEMINI_SLOTS) e.putString(slotName(i), gemini.getOrNull(i).orEmpty().trim())
        e.putString("tavily", tavily.trim())
            .putString("tts_voice_arina", voiceArina.trim())
            .putString("tts_voice_aurion", voiceAurion.trim())
            .apply()
    }
}
