package com.arina.assistant

import android.content.Context

/**
 * API keys and voice names. Values typed in Settings are stored on the phone and win over the keys baked in
 * at build time, so they can be fixed without rebuilding. Gemini has five slots; each request uses the next one.
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
    /** Set by the app: the signed-in account's access token, used to talk to Entrion's server. */
    @Volatile var authToken: () -> String? = { null }

    /** Names of the other brains the server holds keys for (names only; the keys never leave the server). */
    @Volatile var brains: Set<String> = emptySet()

    private fun signedIn(c: Context) = c.applicationContext.getSharedPreferences("cloud", Context.MODE_PRIVATE).getString("refresh", "").orEmpty().isNotEmpty()

    /** Signed in: five placeholders, one per job; the real keys are on the server. Otherwise nothing. */
    fun geminiSlots(c: Context): List<String> = List(GEMINI_SLOTS) { i -> if (signedIn(c)) "proxy:$i" else "" }

    /** All non-empty Gemini keys joined by new lines, the form the clients read. */
    fun gemini(c: Context): String = geminiSlots(c).filter { it.isNotBlank() }.joinToString("\n")

    /** Keys for the other AI brains and the Cartesia voice. Only what this account typed; empty means not used. */
    val PROVIDERS = listOf("openai", "anthropic", "groq", "deepseek", "cartesia")
    fun provider(c: Context, name: String): String = if (name in brains) "proxy" else ""
    fun saveProviders(c: Context, values: Map<String, String>) {
        val e = prefs(c).edit()
        PROVIDERS.forEach { e.putString("provider_$it", values[it].orEmpty().trim()) }
        e.apply()
    }

    fun save(c: Context, gemini: List<String>) {
        val e = prefs(c).edit()
        for (i in 0 until GEMINI_SLOTS) e.putString(slotName(i), gemini.getOrNull(i).orEmpty().trim())
        e.apply()
    }
}
