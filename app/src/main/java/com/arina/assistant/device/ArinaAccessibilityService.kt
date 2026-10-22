package com.arina.assistant.device

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Lets Arina press Back/Home/Recents, open the notification shade, take screenshots
 * and tap on-screen items by their visible text. The user must enable it manually in
 * Settings > Accessibility.
 */
class ArinaAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ArinaAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun press(button: String): Boolean {
        val action = when (button) {
            "home" -> GLOBAL_ACTION_HOME
            "back" -> GLOBAL_ACTION_BACK
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "screenshot" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_TAKE_SCREENSHOT else return false
            "lock_screen" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_LOCK_SCREEN else return false
            else -> return false
        }
        return performGlobalAction(action)
    }

    fun tapText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isClickable) {
                    return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                current = current.parent
            }
        }
        return false
    }
}
