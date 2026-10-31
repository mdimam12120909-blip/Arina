package com.arina.assistant

/**
 * Values built into the app so nothing has to be typed again after an install or update.
 * A value typed in Settings still wins over these. Edit here and rebuild to change a default.
 */
object Defaults {
    /** Tavily web-search key (free dev key). Used when no key comes from the build or from Settings. */
    /** Cartesia voice IDs (not secret): Arina, the female voice, and Aurion, the male voice. */
    const val VOICE_ARINA = "db6b0ed5-d5d3-463d-ae85-518a07d3c2b4"
    const val VOICE_AURION = "4877b818-c7fe-4c89-b1cf-eadf8e23da72"

    /** Gemini Live voice names (from the documented list of 30; the page does not say which are female or male). */
    const val LIVE_VOICE_ARINA = "Kore"
    const val LIVE_VOICE_AURION = "Charon"

    const val TAVILY_KEY = "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"
}
