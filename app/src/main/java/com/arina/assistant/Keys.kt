package com.arina.assistant

import android.content.Context

/**
 * API keys and voice names. Values typed in Settings are stored on the phone and win over the keys baked in
 * at build time, so they can be fixed without rebuilding. Gemini has five slots; each request uses the next one. The voice is Cartesia.
 */
object Keys {
    const val GEMINI_SLOTS = 5

    private fun prefs(c: Context) = com.arina.assistant.cloud.Local.prefs(c, "arina_keys")

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

    fun tavily(c: Context) = pick(c, "tavily", BuildConfig.TAVILY_API_KEY.ifBlank { Defaults.TAVILY_KEY })
    fun cartesia(c: Context) = pick(c, "cartesia", BuildConfig.CARTESIA_API_KEY)
    fun voiceArina(c: Context) = pick(c, "cartesia_voice", com.arina.assistant.cloud.Cloud.cfg(c, "voice_female", BuildConfig.CARTESIA_VOICE_ID.ifBlank { Defaults.VOICE_ARINA }))
    fun voiceAurion(c: Context) = pick(c, "cartesia_voice_aurion", com.arina.assistant.cloud.Cloud.cfg(c, "voice_male", Defaults.VOICE_AURION))

    fun save(c: Context, gemini: List<String>, cartesia: String, tavily: String, voiceArina: String, voiceAurion: String) {
        val e = prefs(c).edit()
        for (i in 0 until GEMINI_SLOTS) e.putString(slotName(i), gemini.getOrNull(i).orEmpty().trim())
        e.putString("cartesia", cartesia.trim())
            .putString("tavily", tavily.trim())
            .putString("cartesia_voice", voiceArina.trim())
            .putString("cartesia_voice_aurion", voiceAurion.trim())
            .apply()
    }
}
