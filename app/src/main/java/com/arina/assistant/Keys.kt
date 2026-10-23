package com.arina.assistant

import android.content.Context

/**
 * API keys. Keys typed into the app's Setup tab are stored on the phone and win over
 * the keys baked in at build time, so they can be fixed without rebuilding.
 */
object Keys {
    private fun prefs(c: Context) = c.getSharedPreferences("arina_keys", Context.MODE_PRIVATE)

    private fun pick(c: Context, name: String, fallback: String): String =
        prefs(c).getString(name, null)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

    fun gemini(c: Context) = pick(c, "gemini", BuildConfig.GEMINI_API_KEY)
    fun eleven(c: Context) = pick(c, "eleven", BuildConfig.ELEVENLABS_API_KEY)
    fun voiceId(c: Context) = pick(c, "voice", BuildConfig.ELEVENLABS_VOICE_ID)

    fun save(c: Context, gemini: String, eleven: String, voiceId: String) {
        prefs(c).edit()
            .putString("gemini", gemini.trim())
            .putString("eleven", eleven.trim())
            .putString("voice", voiceId.trim())
            .apply()
    }
}
