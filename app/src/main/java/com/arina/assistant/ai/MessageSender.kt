package com.arina.assistant.ai

import android.view.accessibility.AccessibilityNodeInfo
import com.arina.assistant.device.ArinaAccessibilityService
import com.arina.assistant.device.DeviceActions

/**
 * Sends a WhatsApp message by name in one tool call, with every step checked against the real screen:
 * who it goes to, that the right text is in the box, that Send was pressed, and that the message really appeared.
 * It says "CONFIRMED" only when it saw the proof, and never sends the same text to the same person twice within two minutes.
 */
class MessageSender(private val device: DeviceActions) {
    private val sentAt = HashMap<String, Long>()

    private fun label(n: AccessibilityNodeInfo) = listOfNotNull(n.text?.toString(), n.contentDescription?.toString()).joinToString(" ").trim()

    private fun shot() = ArinaAccessibilityService.instance?.snapshot(textMax = 4000, maxNodes = 160)

    private fun fail(msg: String): String { ExecTrace.set(ExecState.FAILED, msg.take(100)); return msg }

    fun send(to: String, text: String, reallySend: Boolean): String {
        ExecTrace.set(ExecState.UNDERSTANDING, "message to ${to.take(40)}")
        val msg = text.trim()
        if (to.isBlank() || msg.isBlank()) return fail("ERROR: I need both who to send to and what to say.")
        val svc = ArinaAccessibilityService.instance ?: return fail("ERROR: Device control is off. Tell him to turn it on in Settings.")

        // 1. Who exactly. Several different people with that name is a question for the user, never a guess.
        var display = to.trim()
        var number = to.trim()
        if (to.count { it.isDigit() } < 7) {
            val found = device.findContacts(to)
            val exact = found.firstOrNull { it.first.equals(to.trim(), ignoreCase = true) }
            when {
                found.isEmpty() -> return fail("ERROR: no contact named '$to' (or contacts permission is off). Ask him for the number.")
                exact != null -> { display = exact.first; number = exact.second }
                found.size == 1 -> { display = found[0].first; number = found[0].second }
                else -> {
                    ExecTrace.set(ExecState.WAITING_FOR_APPROVAL, "which contact?")
                    return "ASK: several contacts match '$to': " + found.take(5).joinToString("; ") { it.first } + ". Ask him which one. Nothing was sent."
                }
            }
        }
        val digits = device.internationalDigits(number)
        if (digits.length < 8) return fail("ERROR: '$number' does not look like a full phone number.")

        // 2. Never twice.
        val key = digits + "|" + msg.lowercase()
        val prev = sentAt[key]
        if (prev != null && System.currentTimeMillis() - prev < 120_000) {
            return "DUPLICATE: this exact message was already sent to $display ${(System.currentTimeMillis() - prev) / 1000} s ago, so I did not send it again."
        }

        // 3. Open the chat with the text already in the box.
        ExecTrace.set(ExecState.PLANNING, "chat with $display")
        ExecTrace.set(ExecState.EXECUTING, "opening WhatsApp")
        device.openWhatsAppChat(digits, msg) ?: return fail("ERROR: WhatsApp is not installed on this phone.")
        val probe = msg.take(20)
        var draft = false
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 9000) {
            Thread.sleep(120)
            val s = shot() ?: continue
            if (s.app.startsWith("com.whatsapp") && s.nodes.any { it.isEditable && (it.text?.toString() ?: "").contains(probe) }) { draft = true; break }
        }
        Diag.log("message: draft ${if (draft) "loaded" else "NOT loaded"} after ${System.currentTimeMillis() - t0} ms")
        if (!draft) return fail("ERROR: WhatsApp opened but the message did not appear in the box (it may be showing a different screen). Nothing was sent.")

        // 4. Is it the right chat?
        ExecTrace.set(ExecState.VERIFYING, "recipient")
        val seen = shot()?.nodes?.joinToString(" | ") { label(it) }.orEmpty()
        val tail = digits.takeLast(7)
        val rightChat = seen.contains(display, ignoreCase = true) || seen.filter { it.isDigit() }.contains(tail)
        if (!rightChat) {
            ExecTrace.set(ExecState.WAITING_FOR_APPROVAL, "could not confirm recipient")
            return "ASK: the chat is open with the message ready, but I could not see $display on the screen to be sure it is the right person. Nothing was sent. Ask him to check and say 'send'."
        }
        if (!reallySend) {
            ExecTrace.set(ExecState.WAITING_FOR_APPROVAL, "draft ready")
            return "OK: the chat with $display is open and the message is typed. Nothing was sent: he asked only to prepare it. Tell him so and ask if he wants it sent."
        }

        // 5. Press Send, then check that it really went.
        ExecTrace.set(ExecState.EXECUTING, "pressing Send")
        var pressed = false
        var attempts = 0
        var cleared = false
        var bubble = false
        while (attempts < 2 && !(cleared && bubble)) {
            attempts++
            val s = shot()
            val btn = s?.nodes?.firstOrNull { val l = label(it); !it.isEditable && (l.equals("Send", true) || l == "পাঠান") }
            if (btn != null && svc.click(btn)) {
                pressed = true
                sentAt[key] = System.currentTimeMillis()
            } else if (!pressed) {
                Thread.sleep(500); continue
            }
            ExecTrace.set(ExecState.VERIFYING, "did it send?")
            val t1 = System.currentTimeMillis()
            while (System.currentTimeMillis() - t1 < 4500) {
                Thread.sleep(150)
                val v = shot() ?: continue
                cleared = v.nodes.none { it.isEditable && (it.text?.toString() ?: "").contains(probe) }
                bubble = v.nodes.any { !it.isEditable && label(it).contains(msg.take(40)) }
                if (cleared && bubble) break
            }
            if (cleared) break
            ExecTrace.set(ExecState.RECOVERING, "text still in the box")
        }
        return when {
            !pressed -> fail("ERROR: I could not find the Send button. The message is typed in the chat with $display but NOT sent.")
            cleared && bubble -> { ExecTrace.set(ExecState.COMPLETED, "sent to $display"); "OK: CONFIRMED. The message is in the chat with $display and the box is empty." }
            cleared -> { ExecTrace.set(ExecState.COMPLETED, "likely sent"); "OK: the box emptied after Send, so it most likely went to $display, but I could not read the message on screen to prove it. Say 'probably sent'." }
            else -> fail("ERROR: I pressed Send but the text is still in the box, so it is NOT confirmed as sent. Look at the screen before trying again; do not send twice.")
        }
    }
}
