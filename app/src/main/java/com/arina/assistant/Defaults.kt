package com.arina.assistant

/**
 * Values built into the app so nothing has to be typed again after an install or update.
 * A value typed in Settings still wins over these. Edit here and rebuild to change a default.
 */
/** Names shown to people. The agent itself has no personal name unless the user gives it one. */
object Brand {
    const val APP = "Entrion"
    const val COMPANY = "Pravat"
}

object Defaults {
    /** Entrion's cloud (Supabase). The publishable key is meant to be public; the database rules decide what it may do. */
    const val SUPABASE_URL = "https://xzwlhegzneeujlpbjjba.supabase.co"
    const val SUPABASE_KEY = "sb_publishable_zIbBj3vtEGB5inLQDJGrAw_pwD4fVB-"
    /** Google OAuth "Web application" client ID (public). Supabase checks Google's tokens against it. */
    const val GOOGLE_WEB_CLIENT_ID = "740811641546-6fl4fmrbg928ovbl7cj3dro683tv15bp.apps.googleusercontent.com"

    /** Gemini Live voice names (from the documented list of 30; the page does not say which are female or male). */
    const val LIVE_VOICE_ARINA = "Kore"
    const val LIVE_VOICE_AURION = "Charon"

}
