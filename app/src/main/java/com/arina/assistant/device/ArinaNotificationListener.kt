package com.arina.assistant.device

import android.service.notification.NotificationListenerService

/** Lets Arina read the notifications on the phone once the user grants Notification access. */
class ArinaNotificationListener : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: ArinaNotificationListener? = null
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
