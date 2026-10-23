package com.arina.assistant.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.arina.assistant.MainActivity
import com.arina.assistant.ui.ArinaEngine
import com.arina.assistant.ui.ArinaTheme
import com.arina.assistant.ui.FloatingAvatar
import kotlin.math.hypot

/** Keeps Arina alive in the background and shows the floating 1:1 avatar over other apps. */
class OverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.arina.assistant.action.STOP"
        private const val CHANNEL = "arina_running"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private val owner = OverlayOwner()
    private var container: DragLayout? = null
    private var windowManager: WindowManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val engine = ArinaEngine.get(this)
        if (!Settings.canDrawOverlays(this)) {
            engine.notice.value = "Allow 'Display over other apps' for Arina first."
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            promoteToForeground()
        } catch (e: Exception) {
            engine.notice.value = "Could not start in background: ${e.message}"
            stopSelf()
            return START_NOT_STICKY
        }
        if (container == null) addOverlay(engine)
        engine.overlayOn.value = true
        return START_NOT_STICKY
    }

    private fun promoteToForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Arina is running", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_FROM_OVERLAY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val off = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        @Suppress("DEPRECATION")
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Arina is on")
            .setContentText("Tap the floating avatar to talk.")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(0, "Turn off", off).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun addOverlay(engine: ArinaEngine) {
        val wm = getSystemService(WindowManager::class.java)
        windowManager = wm
        val density = resources.displayMetrics.density
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (24 * density).toInt()
            y = (160 * density).toInt()
        }

        owner.create()
        val compose = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                ArinaTheme {
                    FloatingAvatar(
                        engine = engine,
                        onTap = { engine.onMicTap() },
                        onOpenApp = {
                            startActivity(
                                Intent(this@OverlayService, MainActivity::class.java)
                                    .putExtra(MainActivity.EXTRA_FROM_OVERLAY, true)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            )
                        },
                        onClose = { stopSelf() }
                    )
                }
            }
        }
        val layout = DragLayout(this, wm, lp)
        layout.setViewTreeLifecycleOwner(owner)
        layout.setViewTreeViewModelStoreOwner(owner)
        layout.setViewTreeSavedStateRegistryOwner(owner)
        layout.addView(compose)
        wm.addView(layout, lp)
        container = layout
    }

    override fun onDestroy() {
        container?.let { runCatching { windowManager?.removeView(it) } }
        container = null
        owner.destroy()
        val engine = ArinaEngine.get(this)
        engine.overlayOn.value = false
        engine.stopAll()
        super.onDestroy()
    }
}

/** Lets a plain Android window host Compose (it needs a lifecycle, saved-state and view-model owner). */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun create() {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/** Moves the whole floating window when dragged; plain taps still reach the Compose buttons inside. */
private class DragLayout(
    context: Context,
    private val wm: WindowManager,
    private val lp: WindowManager.LayoutParams
) : FrameLayout(context) {

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                startX = lp.x
                startY = lp.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE ->
                if (!dragging && hypot(e.rawX - downX, e.rawY - downY) > slop) dragging = true
        }
        return dragging
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (dragging) {
                lp.x = startX + (e.rawX - downX).toInt()
                lp.y = startY + (e.rawY - downY).toInt()
                wm.updateViewLayout(this, lp)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }
}
