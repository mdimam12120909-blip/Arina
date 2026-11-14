package com.arina.assistant.device

import android.service.notification.NotificationListenerService

/** Lets Arina read the notifications on the phone once the user grants Notification access. */
class ArinaNotificationListener : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: ArinaNotificationListener? = null

        /** Set by the app: called with (app, sender, text) when a new chat message arrives. */
        @Volatile var onMessage: ((String, String, String) -> Unit)? = null
        private val CHAT_APPS = setOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.facebook.orca", "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms", "org.thoughtcrime.securesms", "com.instagram.android")
        private val seen = LinkedHashSet<String>()
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName !in CHAT_APPS || n.isOngoing) return
        val nt = n.notification
        if (nt.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return
        val e = nt.extras
        val title = e.getCharSequence("android.title")?.toString().orEmpty().trim()
        val text = e.getCharSequence("android.text")?.toString().orEmpty().trim()
        if (title.isEmpty() || text.isEmpty() || Regex("^\\d+ (new )?messages?$", RegexOption.IGNORE_CASE).containsMatchIn(text)) return
        val id = "${n.packageName}|$title|$text|${n.postTime / 3000}"
        synchronized(seen) { if (!seen.add(id)) return; if (seen.size > 40) seen.remove(seen.first()) }
        val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(n.packageName, 0)).toString() }.getOrDefault("a chat app")
        onMessage?.invoke(app, title.take(60), text.take(300))
    }

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    /** A short readable list of what is in the notification shade now. */
    fun summary(limit: Int = 8): String {
        val list = runCatching { activeNotifications }.getOrNull().orEmpty()
            .filter { !it.isOngoing }
            .sortedByDescending { it.postTime }
            .take(limit)
        if (list.isEmpty()) return "OK: there are no notifications."
        return "OK: " + list.joinToString(" | ") { n ->
            val e = n.notification.extras
            val app = runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(n.packageName, 0)).toString()
            }.getOrDefault(n.packageName)
            val title = e.getCharSequence("android.title")?.toString().orEmpty()
            val text = e.getCharSequence("android.text")?.toString().orEmpty()
            "$app: $title $text".trim().take(160)
        }
    }
}
