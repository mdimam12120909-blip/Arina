package com.arina.assistant.ai

import com.arina.assistant.device.DeviceActions

/**
 * Recognises a few unmistakable one-step commands (torch, media volume, open an app) so they run on the phone at once,
 * without a round trip to the model. Anything with extra words, or any doubt, returns null and goes to Gemini as before.
 * A command that the phone refuses also falls back to Gemini, so this can only make things faster, never worse.
 */
object LocalRouter {
    class Match(val label: String, val bn: String, val en: String, val run: (DeviceActions) -> String)

    private val ON = Regex("(?i)\\b(on|turn on|switch on)\\b|জ্বালা|চালু|অন\\b")
    private val OFF = Regex("(?i)\\b(off|turn off|switch off)\\b|নিভা|বন্ধ|অফ")
    private val TORCH = Regex("(?i)torch|flash ?light|টর্চ|ফ্ল্যাশ")
    private val VOL = Regex("(?i)(volume|ভলিউম|সাউন্ড)\\D{0,12}(\\d{1,3})")
    private val OPEN_EN = Regex("(?i)^(?:please )?(?:open|launch|start)\\s+([\\p{L}\\p{N} .&+-]{2,30})$")
    private val OPEN_BN = Regex("^([\\p{L}\\p{N} .&+-]{2,30}?)\\s*(?:খোলো|খুলো|খুলুন|ওপেন করো|ওপেন কর|চালু করো)$")

    fun match(raw: String): Match? {
        val t = raw.trim().trimEnd('.', '!', '।', '?')
        if (t.isEmpty() || t.split(Regex("\\s+")).size > 6) return null
        if (TORCH.containsMatchIn(t)) {
            val on = ON.containsMatchIn(t); val off = OFF.containsMatchIn(t)
            if (on == off) return null
            return if (on) Match("torch on", "টর্চ জ্বালানো হয়েছে।", "Torch is on.") { it.flashlight(true) }
            else Match("torch off", "টর্চ নিভিয়েছি।", "Torch is off.") { it.flashlight(false) }
        }
        VOL.find(t)?.let { m ->
            val p = m.groupValues[2].toIntOrNull()?.takeIf { it in 0..100 } ?: return null
            return Match("volume $p", "ভলিউম $p% করেছি।", "Volume is $p%.") { it.setVolume(p) }
        }
        val name = (OPEN_EN.find(t) ?: OPEN_BN.find(t))?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (name.split(' ').size > 3) return null
        return Match("open $name", "$name খুলেছি।", "Opened $name.") { it.openApp(name) }
    }
}
