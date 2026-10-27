package com.arina.assistant.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
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

    // ---- Screen reading and acting, used by the phone-operating agent ---------------------------

    class Snapshot(val app: String, val text: String, val nodes: List<AccessibilityNodeInfo>)

    /** A numbered text list of what is on screen right now, plus the matching nodes. */
    fun snapshot(textMax: Int = 70, maxNodes: Int = 70): Snapshot? {
        val root = rootInActiveWindow ?: return null
        val nodes = ArrayList<AccessibilityNodeInfo>()
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || nodes.size >= maxNodes || depth > 40) return
            if (n.isVisibleToUser) {
                val text = n.text?.toString()?.trim().orEmpty()
                val desc = n.contentDescription?.toString()?.trim().orEmpty()
                val useful = text.isNotEmpty() || desc.isNotEmpty() || n.isClickable || n.isEditable || n.isScrollable
                if (useful) {
                    val i = nodes.size
                    nodes += n
                    val cls = n.className?.toString()?.substringAfterLast('.').orEmpty()
                    val id = n.viewIdResourceName?.substringAfterLast('/').orEmpty()
                    sb.append('[').append(i).append("] ").append(cls)
                    if (text.isNotEmpty()) sb.append(" text=\"").append(text.take(textMax)).append('"')
                    if (desc.isNotEmpty()) sb.append(" desc=\"").append(desc.take(90)).append('"')
                    if (id.isNotEmpty()) sb.append(" id=").append(id)
                    if (n.isClickable) sb.append(" clickable")
                    if (n.isEditable) sb.append(" editable")
                    if (n.isScrollable) sb.append(" scrollable")
                    if (n.isChecked) sb.append(" checked")
                    sb.append('\n')
                }
            }
            for (k in 0 until n.childCount) walk(n.getChild(k), depth + 1)
        }
        walk(root, 0)
        return Snapshot(root.packageName?.toString().orEmpty(), sb.toString(), nodes)
    }

    private fun center(n: AccessibilityNodeInfo): Pair<Float, Float> {
        val r = Rect()
        n.getBoundsInScreen(r)
        return r.exactCenterX() to r.exactCenterY()
    }

    private fun gesture(path: Path, ms: Long): Boolean =
        dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build(),
            null, null
        )

    fun tapAt(x: Float, y: Float): Boolean = gesture(Path().apply { moveTo(x, y) }, 60)

    /** Clicks the node, or its nearest clickable parent, or taps its centre as a last resort. */
    fun click(node: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = node
        while (cur != null) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            cur = cur.parent
        }
        val (x, y) = center(node)
        return tapAt(x, y)
    }

    fun typeInto(node: AccessibilityNodeInfo, text: String): Boolean {
        var target: AccessibilityNodeInfo? = node
        if (!node.isEditable) {
            target = focusedInput() ?: return false
        }
        target?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return target?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) == true
    }

    /** Presses the keyboard's Enter/Search key on the focused field (Android 11+). */
    fun pressEnter(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        val f = focusedInput() ?: return false
        return f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    fun swipe(down: Boolean): Boolean {
        val m = resources.displayMetrics
        val x = m.widthPixels / 2f
        val a = m.heightPixels * 0.72f
        val b = m.heightPixels * 0.30f
        val p = Path()
        if (down) { p.moveTo(x, a); p.lineTo(x, b) } else { p.moveTo(x, b); p.lineTo(x, a) }
        return gesture(p, 350)
    }

    private fun focusedInput(): AccessibilityNodeInfo? = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
}
