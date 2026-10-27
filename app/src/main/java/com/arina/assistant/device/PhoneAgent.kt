package com.arina.assistant.device

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

/**
 * Operates the phone for the user: looks at the screen through the accessibility service, asks Gemini for the
 * next single action, performs it, and repeats until the goal is done. Runs on a background dispatcher.
 */
class PhoneAgent(
    private val device: DeviceActions,
    private val ask: (system: String, user: String) -> String,        // blocking Gemini call returning JSON text
    private val confirm: (title: String, detail: String) -> Boolean,  // blocking: asks the user to allow
    private val progress: (String) -> Unit
) {
    class Outcome(val ok: Boolean, val en: String, val bn: String)

    private companion object {
        const val MAX_STEPS = 30

        val SYSTEM = """
            You operate an Android phone for the user. Each turn you see the current screen as a numbered list of elements and you choose exactly ONE next action.
            Reply with JSON only, no markdown:
            {"thought":"short reasoning","action":"click|type|enter|scroll_down|scroll_up|back|home|open_app|wait|confirm|done|fail","index":<element number for click/type>,"text":"<text to type, or app name for open_app, or the question for confirm>","seconds":<seconds for wait>,"message_en":"<for done/fail: what happened, one sentence>","message_bn":"<for done/fail: the same in 1-2 short natural spoken Bengali sentences, Bangla script>"}
            Rules:
            - Work only toward the user's goal. Do the smallest sensible step each turn.
            - To start an app use open_app with its name as shown on the phone, then look at the next screen.
            - To search inside an app: click the search icon or field, type the query, then use enter. If enter does not seem to work, click the visible search suggestion or magnifier.
            - YouTube: open_app YouTube, click Search (magnifier), type the query, enter, then click the video that best matches. When a video is playing, you are done.
            - Never type passwords, card numbers or one-time codes. Never buy, pay, delete, post publicly or send a message without first using confirm with a clear question; continue only if the user allowed it.
            - If the screen did not change after your action, try something different. If you are stuck after several tries, use fail and explain.
            - Use done as soon as the goal is achieved. Use fail if it cannot be done.
        """.trimIndent()
    }

    suspend fun run(goal: String): Outcome {
        val history = ArrayList<String>()
        for (step in 1..MAX_STEPS) {
            if (!coroutineContext.isActive) return Outcome(false, "Stopped.", "থামিয়ে দিয়েছি।")
            val svc = ArinaAccessibilityService.instance
                ?: return Outcome(false, "Device control is off.", "ডিভাইস কন্ট্রোল বন্ধ আছে, সেটিংসে চালু করুন।")
            val snap = svc.snapshot()
            val prompt = buildString {
                append("GOAL: ").append(goal).append("\n\n")
                append("STEP ").append(step).append(" of ").append(MAX_STEPS).append('\n')
                if (history.isNotEmpty()) append("PREVIOUS ACTIONS:\n").append(history.takeLast(8).joinToString("\n")).append("\n\n")
                if (snap == null) append("SCREEN: (not readable right now; try wait, back, or home)\n")
                else append("CURRENT APP: ").append(snap.app).append("\nSCREEN:\n").append(snap.text.ifBlank { "(empty)" })
            }
            val raw = try {
                ask(SYSTEM, prompt)
            } catch (e: Exception) {
                return Outcome(false, "I lost the connection: ${e.message}", "ইন্টারনেটে সমস্যা হয়েছে, কাজটা শেষ করতে পারিনি।")
            }
            val j = try {
                JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
            } catch (e: Exception) {
                history += "step $step: (unreadable answer, retrying)"
                continue
            }
            val action = j.optString("action")
            val idx = j.optInt("index", -1)
            val text = j.optString("text")
            val thought = j.optString("thought")
            progress("Step $step: ${thought.ifBlank { action }}".take(140))

            val note: String = when (action) {
                "done" -> return Outcome(true, j.optString("message_en", "Done."), j.optString("message_bn", "কাজ শেষ।"))
                "fail" -> return Outcome(false, j.optString("message_en", "I could not do it."), j.optString("message_bn", "কাজটা করতে পারলাম না।"))
                "open_app" -> device.openApp(text).also { delay(1800) }
                "back" -> if (svc.press("back")) "OK: back" else "ERROR: back failed"
                "home" -> if (svc.press("home")) "OK: home" else "ERROR: home failed"
                "scroll_down" -> if (svc.swipe(true)) "OK: scrolled down" else "ERROR: scroll failed"
                "scroll_up" -> if (svc.swipe(false)) "OK: scrolled up" else "ERROR: scroll failed"
                "wait" -> { delay((j.optDouble("seconds", 2.0).coerceIn(0.5, 10.0) * 1000).toLong()); "OK: waited" }
                "enter" -> if (svc.pressEnter()) "OK: pressed enter" else "ERROR: enter key not available; click the search button or suggestion instead"
                "confirm" -> {
                    val yes = confirm("AriAuri asks", text.ifBlank { "Continue?" })
                    if (yes) "OK: the user allowed it" else return Outcome(false, "You did not allow the next step, so I stopped.", "আপনি অনুমতি দেননি, তাই থেমে গেলাম।")
                }
                "click", "type" -> {
                    val node = snap?.nodes?.getOrNull(idx)
                    when {
                        node == null -> "ERROR: no element with index $idx"
                        action == "click" -> if (svc.click(node)) "OK: clicked [$idx]" else "ERROR: click failed"
                        else -> if (svc.typeInto(node, text)) "OK: typed \"$text\"" else "ERROR: could not type there"
                    }
                }
                else -> "ERROR: unknown action '$action'"
            }
            history += "step $step: $action ${if (idx >= 0) "[$idx] " else ""}${text.take(40)} -> $note"
            delay(if (action == "open_app") 400 else 1000)
        }
        return Outcome(false, "I ran out of steps before finishing.", "অনেক ধাপ পেরিয়ে গেছে, কাজটা শেষ করতে পারিনি।")
    }
}
