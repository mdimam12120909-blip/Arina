package com.arina.assistant

import android.content.Context

/**
 * API keys. Keys typed into the app's Setup tab are stored on the phone and win over
 * the keys baked in at build time, so they can be fixed without rebuilding.
 */
object Keys {
    private fun prefs(c: Context) = c.getSharedPreferences("arina_keys", Context.MODE_PRIVATE)

    /**
     * A key typed in the Setup tab wins over the build key, except right after a rebuild whose
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

    fun gemini(c: Context) = pick(c, "gemini", BuildConfig.GEMINI_API_KEY)
    fun cartesia(c: Context) = pick(c, "cartesia", BuildConfig.CARTESIA_API_KEY)
    fun voiceId(c: Context) = pick(c, "cartesia_voice", BuildConfig.CARTESIA_VOICE_ID)

    fun save(c: Context, gemini: String, cartesia: String, voiceId: String) {
        prefs(c).edit()
            .putString("gemini", gemini.trim())
            .putString("cartesia", cartesia.trim())
            .putString("cartesia_voice", voiceId.trim())
            .apply()
    }
}
