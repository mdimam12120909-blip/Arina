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

    /** True only when a player is up AND something on screen carries a word of the requested song, so an old song still playing is not mistaken for the new one. */
    private fun playingThis(words: List<String>): Boolean {
        val s = ArinaAccessibilityService.instance?.snapshot(textMax = 160, maxNodes = 160) ?: return false
        if (!s.app.contains("youtube")) return false
        val has = s.nodes.any { val l = label(it).lowercase(); l == "pause video" || l.startsWith("pause") || l.contains("minimize") || l.contains("miniplayer") }
        if (!has) return false
        val all = s.nodes.joinToString(" ") { label(it).lowercase() }
        return words.isEmpty() || words.any { all.contains(it) }
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
        val words = q.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }
        var fallbackDone = false
        var tapped = false
        val end = System.currentTimeMillis() + 9000
        while (System.currentTimeMillis() < end) {
            Thread.sleep(250)
            if (!fallbackDone && !tapped && System.currentTimeMillis() - t0 > 4000 && !playingThis(words)) {
                // The direct "play" request only opened YouTube's home: search the normal way instead.
                fallbackDone = true
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                Thread.sleep(1500)
            }
            if (playingThis(words)) { Diag.log("metric song ms=${System.currentTimeMillis() - t0} tapped=$tapped"); ExecTrace.set(ExecState.COMPLETED, q); return "OK: CONFIRMED. '$q' is playing in YouTube (a player with a Pause control is on screen)." }
            if (svc != null && !tapped) {
                val s = svc.snapshot(textMax = 120, maxNodes = 160) ?: continue
                if (!s.app.contains("youtube")) continue
                val first = s.nodes.firstOrNull { val l = label(it).lowercase(); (l.contains("play video") || l.contains("go to channel")) && !l.contains("shorts") && !l.contains("sponsored") && !l.contains("ad ·") }
                if (first != null && svc.click(first)) { tapped = true; ExecTrace.set(ExecState.VERIFYING, "result tapped") }
            }
        }
        if (playing()) { Diag.log("metric song ms=${System.currentTimeMillis() - t0} unsure"); ExecTrace.set(ExecState.COMPLETED, q); return "UNSURE: a YouTube player is on screen but I cannot see the name of '$q' in it, so it may be a different song. Say that honestly in one short sentence and offer to check with look_screen." }
        Diag.log("metric song ms=${System.currentTimeMillis() - t0} ok=false")
        ExecTrace.set(ExecState.FAILED, "no player seen")
        return "NOT CONFIRMED: YouTube was opened for '$q' but I could not see it playing. Do not say it is playing; say what the screen shows (look_screen) or ask him."
    }
}
