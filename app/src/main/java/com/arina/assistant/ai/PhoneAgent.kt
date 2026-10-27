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

    class Outcome(val bn: String, val en: String)

    private companion object {
        const val MAX_STEPS = 30

        val SYSTEM = """
            You operate an Android phone for the user, one action at a time, by reading the screen as a numbered list.
            Reply with JSON only, exactly one action:
            {"action":"click","index":N}                      tap element N
            {"action":"type","index":N,"text":"...","submit":true|false}   put text in editable element N; submit presses Enter/Search
            {"action":"scroll","direction":"down"|"up"}
            {"action":"back"} | {"action":"home"} | {"action":"wait"}
            {"action":"open_app","app":"YouTube"}            app name as on the phone
            {"action":"done","bn":"...","en":"..."}           goal reached: bn = one short spoken Bengali sentence (Bangla script) saying what you did, en = same in English
            {"action":"fail","bn":"...","en":"..."}           cannot continue: say why in the same way
            Rules:
            - Use only element numbers that are in the current SCREEN list. Look at PREVIOUS ACTIONS and never repeat a step that did nothing; try something else (another element, scroll, back).
            - To search inside an app: click the search icon/box, type the query with submit true, then click the wanted result.
            - Never send a message, post, buy, pay, delete or change account settings unless the GOAL says exactly that. If a screen asks for a password, OTP, payment or a decision only the user can make, use fail and explain.
            - When the goal is reached (for example the video is playing), use done.
        """.trimIndent()
    }

    fun run(goal: String, onStep: (String) -> Unit, cancelled: () -> Boolean): Outcome {
        val history = ArrayList<String>()
        var last = ""
        var repeats = 0
        for (step in 1..MAX_STEPS) {
            if (cancelled()) return Outcome("থামিয়ে দিলাম।", "Stopped.")
            val svc = ArinaAccessibilityService.instance
                ?: return Outcome("ডিভাইস কন্ট্রোল বন্ধ, চালু করুন।", "Device control is off. Turn it on in Settings.")
            val snap = svc.snapshot()
            if (snap == null) { Thread.sleep(800); continue }
            if (snap.app == "com.arina.assistant" && step <= 2) {
                svc.press("home"); Thread.sleep(800); continue
            }
            val prompt = buildString {
                append("GOAL: ").append(goal).append("\nSTEP ").append(step).append(" of ").append(MAX_STEPS)
                append("\nCURRENT APP: ").append(snap.app)
                append("\nPREVIOUS ACTIONS:\n").append(history.takeLast(8).joinToString("\n").ifBlank { "(none yet)" })
                append("\n\nSCREEN:\n").append(snap.text.ifBlank { "(nothing readable)" })
            }
            val raw = gemini.generate(SYSTEM, prompt, json = true)
            val a = runCatching {
                JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
            }.getOrNull() ?: run { history += "(unreadable answer)"; continue }

            val action = a.optString("action")
            val idx = a.optInt("index", -1)
            val node = snap.nodes.getOrNull(idx)
            val desc = when (action) {
                "click" -> "click [$idx]"
                "type" -> "type \"${a.optString("text")}\" in [$idx]"
                "scroll" -> "scroll ${a.optString("direction")}"
                "open_app" -> "open ${a.optString("app")}"
                else -> action
            }
            onStep("Step $step: $desc")
            if (desc == last) { if (++repeats >= 3) return Outcome("এই ধাপে আটকে গেছি, থামলাম।", "I got stuck, so I stopped.") } else { repeats = 0; last = desc }

            var ok = true
            when (action) {
                "done", "fail" -> return Outcome(a.optString("bn").ifBlank { "শেষ।" }, a.optString("en").ifBlank { "Done." })
                "click" -> ok = node != null && svc.click(node)
                "type" -> {
                    ok = node != null && svc.typeInto(node, a.optString("text"))
                    if (ok && a.optBoolean("submit")) { Thread.sleep(400); svc.pressEnter() }
                }
                "scroll" -> ok = svc.swipe(a.optString("direction") != "up")
                "back" -> ok = svc.press("back")
                "home" -> ok = svc.press("home")
                "open_app" -> ok = device.openApp(a.optString("app")).startsWith("OK")
                "wait" -> Thread.sleep(1500)
                else -> ok = false
            }
            history += "$desc -> ${if (ok) "done" else "FAILED"}"
            Thread.sleep(1200)
        }
        return Outcome("অনেক ধাপ হয়ে গেল, এখানেই থামলাম।", "I reached my step limit and stopped.")
    }
}
