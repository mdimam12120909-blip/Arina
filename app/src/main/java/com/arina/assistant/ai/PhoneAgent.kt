package com.arina.assistant.ai

import com.arina.assistant.device.ArinaAccessibilityService
import com.arina.assistant.device.DeviceActions
import org.json.JSONObject

/**
 * Operates the phone like a person: look at the screen (accessibility tree), ask Gemini for the next
 * single action, do it, repeat. Blocking: run on Dispatchers.IO. Stops when Gemini says done or fail,
 * after [MAX_STEPS], or when [cancelled] turns true.
 */
class PhoneAgent(private val gemini: GeminiClient, private val device: DeviceActions) {

    class Outcome(val bn: String, val en: String, val report: String = "")

    private companion object {
        const val MAX_STEPS = 30
        const val MAX_STEPS_CHAT = 70

        val SYSTEM = """
            You operate an Android phone for the user, one action at a time, by reading the screen as a numbered list.
            Reply with JSON only, exactly one action:
            {"action":"click","index":N}                      tap element N
            {"action":"type","index":N,"text":"...","submit":true|false}   put text in editable element N; submit presses Enter/Search
            {"action":"scroll","direction":"down"|"up"}
            {"action":"back"} | {"action":"home"} | {"action":"wait"}
            {"action":"open_app","app":"YouTube"}            app name as on the phone
            {"action":"open_url","url":"https://..."}          open a web address; the matching installed app opens it directly
            {"action":"wait_reply"}                            wait until the other AI finishes writing its reply (use right after sending a chat message)
            {"action":"note","text":"..."}                     save one key point from a reply; notes are kept for the final report
            {"action":"done","bn":"...","en":"...","report":"..."}           goal reached: bn = one short spoken Bengali sentence (Bangla script) saying what you did, en = same in English
            {"action":"fail","bn":"...","en":"..."}           cannot continue: say why in the same way
            Rules:
            - Use only element numbers that are in the current SCREEN list. Look at PREVIOUS ACTIONS and never repeat a step that did nothing; try something else (another element, scroll, back).
            - BE FAST. Use the fewest steps. To search, prefer open_url with a search address over clicking through the app:
              YouTube https://www.youtube.com/results?search_query=QUERY, Google https://www.google.com/search?q=QUERY, Google Maps https://www.google.com/maps/search/?api=1&query=QUERY (replace spaces with +). Then click the wanted result.
            - To search inside an app that has no such address: click the search icon/box, type the query with submit true, then click the wanted result.
            - Only use wait when the screen is clearly still loading.
            - Never send a message, post, buy, pay, delete or change account settings unless the GOAL says exactly that. If a screen asks for a password, OTP, payment or a decision only the user can make, use fail and explain.
            - When the goal is reached (for example the video is playing), use done.
            CHAT MODE (only when the task says CHAT MODE): you are talking to another AI app on the user's behalf, in plain text like the user would.
            Open the app, click the message box, type your first message about the topic (clear, specific, in English unless told otherwise) with submit true, or click the send button, then wait_reply, then read the reply on SCREEN and note the key points.
            Ask sensible follow-up questions (at most 6 messages in total), scroll when the reply is long, then finish with done. In done, "report" must be the complete result the user asked for (for example a step by step plan) in plain English text built from your notes, and bn/en are a short spoken summary.
        """.trimIndent()
    }

    fun run(goal: String, chat: Boolean, onStep: (String) -> Unit, cancelled: () -> Boolean): Outcome {
        val history = ArrayList<String>()
        val notes = ArrayList<String>()
        val maxSteps = if (chat) MAX_STEPS_CHAT else MAX_STEPS
        var last = ""
        var repeats = 0
        for (step in 1..maxSteps) {
            if (cancelled()) return Outcome("থামিয়ে দিলাম।", "Stopped.")
            val svc = ArinaAccessibilityService.instance
                ?: return Outcome("ডিভাইস কন্ট্রোল বন্ধ, চালু করুন।", "Device control is off. Turn it on in Settings.")
            val snap = if (chat) svc.snapshot(textMax = 700, maxNodes = 90) else svc.snapshot()
            if (snap == null) { Thread.sleep(800); continue }
            if (snap.app == "com.arina.assistant" && step <= 2) {
                svc.press("home"); Thread.sleep(800); continue
            }
            val prompt = buildString {
                append(if (chat) "CHAT MODE\n" else "").append("GOAL: ").append(goal).append("\nSTEP ").append(step).append(" of ").append(maxSteps)
                if (notes.isNotEmpty()) append("\nNOTES SO FAR:\n").append(notes.joinToString("\n") { "- $it" })
                append("\nCURRENT APP: ").append(snap.app)
                append("\nPREVIOUS ACTIONS:\n").append(history.takeLast(8).joinToString("\n").ifBlank { "(none yet)" })
                append("\n\nSCREEN:\n").append(snap.text.ifBlank { "(nothing readable)" })
            }
            val raw = gemini.generate(SYSTEM, prompt, json = true)
            val a = runCatching {
                JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
            }.getOrNull()
            if (a == null) { history += "(unreadable answer)"; continue }

            val action = a.optString("action")
            val idx = a.optInt("index", -1)
            val node = snap.nodes.getOrNull(idx)
            val desc = when (action) {
                "click" -> "click [$idx]"
                "type" -> "type \"${a.optString("text")}\" in [$idx]"
                "scroll" -> "scroll ${a.optString("direction")}"
                "open_app" -> "open ${a.optString("app")}"
                "open_url" -> "open link"
                else -> action
            }
            onStep("Step $step: $desc")
            if (desc == last) { if (++repeats >= 3) return Outcome("এই ধাপে আটকে গেছি, থামলাম।", "I got stuck, so I stopped.") } else { repeats = 0; last = desc }

            var ok = true
            val before = snap.text.hashCode()
            when (action) {
                "done", "fail" -> return Outcome(a.optString("bn").ifBlank { "শেষ।" }, a.optString("en").ifBlank { "Done." }, a.optString("report"))
                "note" -> a.optString("text").takeIf { it.isNotBlank() }?.let { notes += it.take(600) }
                "wait_reply" -> waitForStable(svc, cancelled)
                "click" -> ok = node != null && svc.click(node)
                "type" -> {
                    ok = node != null && svc.typeInto(node, a.optString("text"))
                    if (ok && a.optBoolean("submit")) { Thread.sleep(400); svc.pressEnter() }
                }
                "scroll" -> ok = svc.swipe(a.optString("direction") != "up")
                "back" -> ok = svc.press("back")
                "home" -> ok = svc.press("home")
                "open_app" -> ok = device.openApp(a.optString("app")).startsWith("OK")
                "open_url" -> ok = device.openUrl(a.optString("url")).startsWith("OK")
                "wait" -> Thread.sleep(1500)
                else -> ok = false
            }
            history += "$desc -> ${if (ok) "done" else "FAILED"}"
            if (action != "wait" && action != "wait_reply") settle(svc, before)
        }
        return Outcome("অনেক ধাপ হয়ে গেল, এখানেই থামলাম।", "I reached my step limit and stopped.", notes.joinToString("\n") { "- $it" })
    }

    /**
     * After an action: wait only as long as the screen needs. Polls every 150 ms; once the screen differs from
     * [before] and has stayed the same for two checks, go on. If nothing changes within 1.2 s, go on anyway.
     */
    private fun settle(svc: ArinaAccessibilityService, before: Int) {
        val start = System.currentTimeMillis()
        var last = before
        var stable = 0
        var changed = false
        while (System.currentTimeMillis() - start < 3500) {
            Thread.sleep(150)
            val now = svc.snapshot(textMax = 30, maxNodes = 40)?.text.orEmpty().hashCode()
            if (now != before) changed = true
            if (changed) { if (now == last) { if (++stable >= 2) return } else stable = 0 }
            last = now
            if (!changed && System.currentTimeMillis() - start > 1200) return
        }
    }

    /** Waits until the screen text stops changing (the other AI finished typing), at most 70 seconds. */
    private fun waitForStable(svc: ArinaAccessibilityService, cancelled: () -> Boolean) {
        var prev = ""
        var same = 0
        repeat(28) {
            Thread.sleep(2500)
            if (cancelled()) return
            val now = svc.snapshot(textMax = 200, maxNodes = 90)?.text.orEmpty()
            if (now == prev && now.isNotEmpty()) { if (++same >= 2) return } else same = 0
            prev = now
        }
    }
}
