package com.arina.assistant.ai

import android.content.Context
import android.content.Intent
import android.os.Build

/** Keeps the last events (tool calls, failures) so a problem can be reported with real facts instead of a description. */
object Diag {
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun log(line: String) {
        if (lines.size >= 80) lines.removeFirst()
        lines.addLast(java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date()) + "  " + line.take(300))
    }

    @Synchronized
    fun text(): String = lines.joinToString("\n")

    /** Opens the share sheet with a ready report (device, build, recent events, last chat lines). No keys or passwords are included. */
    fun share(ctx: Context, build: String, chat: List<String>) {
        val body = buildString {
            append("Entrion problem report\n")
            append("Build: ").append(build).append('\n')
            append("Phone: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(", Android ").append(Build.VERSION.RELEASE).append('\n')
            append("\nWhat went wrong (write here): \n\nRECENT EVENTS\n").append(text().ifBlank { "(none)" })
            append("\n\nLAST CHAT\n").append(chat.takeLast(12).joinToString("\n").ifBlank { "(none)" })
        }
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Entrion problem report").putExtra(Intent.EXTRA_TEXT, body)
        ctx.startActivity(Intent.createChooser(send, "Send report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
