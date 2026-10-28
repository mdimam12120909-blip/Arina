package com.arina.assistant.companion

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.arina.assistant.ui.ArinaEngine
import java.util.Calendar

/**
 * Keeps an eye on how the user uses the phone (only on the phone, nothing is sent anywhere except the short
 * situation line given to Gemini to word a friendly nudge) and speaks up when it is worth it.
 */
class Buddy(private val ctx: Context, private val engine: ArinaEngine) {

    private companion object {
        val SCROLLING = setOf(
            "com.instagram.android", "com.instagram.lite", "com.facebook.katana", "com.facebook.lite",
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.snapchat.android",
            "com.google.android.youtube", "com.twitter.android", "com.reddit.frontpage"
        )
        const val SCROLL_NUDGE_MIN = 20
        const val SCREEN_NUDGE_MIN = 60
        const val COOLDOWN_MIN = 30
    }

    private var scrollMs = 0L
    private var screenMs = 0L
    private var offSince = 0L
    private var lastNudge = 0L
    private var lastTick = System.currentTimeMillis()
    private var lastNightNudgeDay = -1

    private fun usageAllowed(): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** The app the user is looking at right now, from the usage log of the last two minutes. */
    private fun foregroundApp(): String? {
        if (!usageAllowed()) return null
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(now - 120_000, now)
        val e = UsageEvents.Event()
        var pkg: String? = null
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) pkg = e.packageName
            if (e.eventType == UsageEvents.Event.ACTIVITY_PAUSED && e.packageName == pkg) pkg = null
        }
        return pkg
    }

    /** Called about every 30 seconds while AriAuri runs in the background. */
    fun tick() {
        val now = System.currentTimeMillis()
        val dt = (now - lastTick).coerceIn(0, 120_000)
        lastTick = now
        val pm = ctx.getSystemService(PowerManager::class.java)
        if (!pm.isInteractive) {
            if (offSince == 0L) offSince = now
            if (now - offSince > 5 * 60_000L) { screenMs = 0; scrollMs = 0 }
            return
        }
        offSince = 0L
        screenMs += dt
        val fg = foregroundApp()
        if (fg != null && fg in SCROLLING) scrollMs += dt else if (fg != null && fg != ctx.packageName) scrollMs = (scrollMs - dt).coerceAtLeast(0)

        if (!engine.proactiveOn.value || !engine.canSpeakUnprompted()) return
        val cooled = now - lastNudge > COOLDOWN_MIN * 60_000L
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        when {
            hour in 1..4 && day != lastNightNudgeDay && cooled -> {
                lastNightNudgeDay = day; lastNudge = now
                engine.nudge("It is ${hour}:00 at night and he is still on the phone. Tell him kindly to sleep.")
            }
            scrollMs >= SCROLL_NUDGE_MIN * 60_000L && cooled -> {
                val m = scrollMs / 60_000; scrollMs = 0; lastNudge = now
                engine.nudge("He has been scrolling reels / short videos / social media for about $m minutes. Ask playfully how much longer, and suggest reading something or doing some research on his YouTube automation plan instead.")
            }
            screenMs >= SCREEN_NUDGE_MIN * 60_000L && cooled -> {
                val m = screenMs / 60_000; screenMs = 0; lastNudge = now
                engine.nudge("He has been using the phone nonstop for about $m minutes. Suggest a short break: water, stretch, rest his eyes.")
            }
        }
    }
}
