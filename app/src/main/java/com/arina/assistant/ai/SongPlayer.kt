package com.arina.assistant.ai

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import com.arina.assistant.device.ArinaAccessibilityService
import com.arina.assistant.device.DeviceActions

/**
 * Plays a song in one step: asks YouTube to play it directly, and if it only shows search results, taps the
 * first real video result itself. Says OK only when a player (Pause control) is on screen.
 */
class SongPlayer(private val ctx: android.content.Context, private val device: DeviceActions) {
    private fun label(n: AccessibilityNodeInfo) = listOfNotNull(n.text?.toString(), n.contentDescription?.toString()).joinToString(" ")

    private fun playing(): Boolean {
        val s = ArinaAccessibilityService.instance?.snapshot(textMax = 80, maxNodes = 120) ?: return false
        return s.app.contains("youtube") && s.nodes.any { val l = label(it).lowercase(); l == "pause video" || l.startsWith("pause") || l.contains("minimize") || l.contains("miniplayer") }
    }

    fun play(query: String): String {
        val q = query.trim()
        if (q.isEmpty()) return "ERROR: which song?"
        ExecTrace.set(ExecState.EXECUTING, "play $q")
        val t0 = System.currentTimeMillis()
        val pm = ctx.packageManager
        val pkg = listOf("com.google.android.youtube", "com.google.android.apps.youtube.music").firstOrNull { runCatching { pm.getPackageInfo(it, 0) }.isSuccess }
            ?: return "ERROR: YouTube is not installed."
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, q).putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        var opened = runCatching { ctx.startActivity(intent) }.isSuccess
        if (!opened) opened = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!opened) { ExecTrace.set(ExecState.FAILED, "could not open YouTube"); return "ERROR: could not open YouTube." }
        val svc = ArinaAccessibilityService.instance
        var tapped = false
        val end = System.currentTimeMillis() + 9000
        while (System.currentTimeMillis() < end) {
            Thread.sleep(250)
            if (playing()) { Diag.log("metric song ms=${System.currentTimeMillis() - t0} tapped=$tapped"); ExecTrace.set(ExecState.COMPLETED, q); return "OK: CONFIRMED. '$q' is playing in YouTube (a player with a Pause control is on screen)." }
            if (svc != null && !tapped) {
                val s = svc.snapshot(textMax = 120, maxNodes = 160) ?: continue
                if (!s.app.contains("youtube")) continue
                val first = s.nodes.firstOrNull { val l = label(it).lowercase(); (l.contains("play video") || l.contains("go to channel")) && !l.contains("shorts") && !l.contains("sponsored") && !l.contains("ad ·") }
                if (first != null && svc.click(first)) { tapped = true; ExecTrace.set(ExecState.VERIFYING, "result tapped") }
            }
        }
        Diag.log("metric song ms=${System.currentTimeMillis() - t0} ok=false")
        ExecTrace.set(ExecState.FAILED, "no player seen")
        return "NOT CONFIRMED: YouTube was opened for '$q' but I could not see it playing. Do not say it is playing; say what the screen shows (look_screen) or ask him."
    }
}
