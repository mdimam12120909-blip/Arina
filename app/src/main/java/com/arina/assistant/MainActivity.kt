package com.arina.assistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.arina.assistant.overlay.OverlayService
import com.arina.assistant.ui.ArinaEngine
import com.arina.assistant.ui.ArinaTheme
import com.arina.assistant.ui.MainScreen

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_FROM_OVERLAY = "from_overlay"
    }

    private val engine by lazy { ArinaEngine.get(this) }
    private var askedNotifications = false
    private var wantsToStart = false

    private val phonePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { engine.refreshSetup() }

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        engine.refreshSetup()
        if (wantsToStart) startArina() else if (it) engine.onMicTap()
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        engine.refreshSetup()
        if (wantsToStart) startArina()
    }

    private val overlayPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        engine.refreshSetup()
        if (wantsToStart) startArina()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        engine.refreshSetup()
        setContent {
            ArinaTheme {
                MainScreen(
                    vm = engine,
                    onMicClick = {
                        if (hasMic()) engine.onMicTap() else {
                            wantsToStart = false
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onPower = {
                        if (engine.overlayOn.value) OverlayService.stop(this)
                        else {
                            wantsToStart = true
                            startArina()
                        }
                    },
                    onGrantPhone = {
                        phonePermissions.launch(
                            arrayOf(
                                Manifest.permission.CALL_PHONE,
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.SEND_SMS
                            )
                        )
                    },
                    onGrantOverlay = {
                        wantsToStart = false
                        overlayPermission.launch(overlaySettingsIntent())
                    },
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }
        }
        // Opening the app turns Arina on in the background, unless we came here from the floating avatar.
        if (!intent.getBooleanExtra(EXTRA_FROM_OVERLAY, false) && hasMic() && Settings.canDrawOverlays(this)) {
            wantsToStart = true
            startArina()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        engine.refreshSetup()
    }

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == 0

    private fun overlaySettingsIntent() =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))

    /** Walks through the permissions one by one, then starts the floating avatar and hides the app. */
    private fun startArina() {
        when {
            !hasMic() -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
            !Settings.canDrawOverlays(this) -> overlayPermission.launch(overlaySettingsIntent())
            Build.VERSION.SDK_INT >= 33 && !engine.notificationsGranted() && !askedNotifications -> {
                askedNotifications = true
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> {
                wantsToStart = false
                OverlayService.start(this)
                moveTaskToBack(true)
            }
        }
    }
}
