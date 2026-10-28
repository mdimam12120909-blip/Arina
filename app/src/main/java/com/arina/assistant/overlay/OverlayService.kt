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
import com.arina.assistant.R
import com.arina.assistant.ui.ArinaTheme
import com.arina.assistant.ui.Aura
import com.arina.assistant.companion.Buddy
import com.arina.assistant.ui.FloatingAvatar
import com.arina.assistant.ui.Stage
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
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
    private var stageView: ComposeView? = null
    private var glowView: ComposeView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val engine = ArinaEngine.get(this)
        if (!Settings.canDrawOverlays(this)) {
            engine.notice.value = "Allow 'Display over other apps' for AriAuri first."
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
        if (container == null) {
            addOverlay(engine)
            watchStage(engine)
            val buddy = Buddy(this, engine)
            scope.launch {
                while (true) {
                    delay(30_000)
                    runCatching { buddy.tick() }
                }
            }
        }
        engine.overlayOn.value = true
        engine.glow.intValue++
        return START_NOT_STICKY
    }

    private fun promoteToForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "AriAuri is running", NotificationManager.IMPORTANCE_LOW)
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AriAuri is on")
            .setContentText("Tap the floating orb to talk.")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(0, "Turn off", off).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            // Camera type only when the camera is allowed, or Android refuses to start the service.
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (Build.VERSION.SDK_INT >= 30 && checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            startForeground(NOTIFICATION_ID, notification, type)
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

    private fun composeView(content: @androidx.compose.runtime.Composable () -> Unit): ComposeView =
        ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { ArinaTheme { content() } }
        }

    /** Shows the permission / picture / phone-control popups in their own window, and the edge glow on demand. */
    private fun watchStage(engine: ArinaEngine) {
        val wm = windowManager ?: return
        val width = (resources.displayMetrics.widthPixels * 0.94f).toInt()

        scope.launch {
            snapshotFlow { engine.approval.value != null || engine.card.value != null || engine.agentActive.value }
                .collect { visible ->
                    if (visible && stageView == null) {
                        val lp = WindowManager.LayoutParams(
                            width, WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                            PixelFormat.TRANSLUCENT
                        ).apply { gravity = Gravity.CENTER }
                        val v = composeView { Stage(engine) }
                        runCatching { wm.addView(v, lp); stageView = v }
                    } else if (!visible) {
                        stageView?.let { runCatching { wm.removeView(it) } }
                        stageView = null
                    }
                }
        }

        // The aura (edge light and top pill) lives in its own window that never takes a touch.
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        val v = composeView { Aura(engine) }
        runCatching { wm.addView(v, lp); glowView = v }
    }

    override fun onDestroy() {
        scope.cancel()
        stageView?.let { runCatching { windowManager?.removeView(it) } }
        glowView?.let { runCatching { windowManager?.removeView(it) } }
        stageView = null
        glowView = null
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
