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
import com.arina.assistant.permissions.PermItem
import com.arina.assistant.permissions.PermKind
import com.arina.assistant.permissions.PermissionCenter
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
                    onPermission = ::requestItem,
                    onGrantAll = {
                        val missing = PermissionCenter.missingRuntime(this)
                        if (missing.isNotEmpty()) phonePermissions.launch(missing)
                        else openNextSpecial()
                    },
                    onAppInfo = { openSafely(PermissionCenter.appInfoIntent(this)) }
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

    /** One row of the permission list was tapped. */
    private fun requestItem(item: PermItem) {
        wantsToStart = false
        if (item.kind == PermKind.Runtime) {
            val need = item.perms.filter { checkSelfPermission(it) != 0 }.toTypedArray()
            if (need.isNotEmpty()) phonePermissions.launch(need) else openSafely(PermissionCenter.appInfoIntent(this))
        } else {
            openSafely(PermissionCenter.intentFor(this, item))
        }
    }

    private fun openNextSpecial() {
        val next = PermissionCenter.special.firstOrNull { engine.perms[it.id] != true } ?: return
        requestItem(next)
    }

    /** Settings screens differ between phones; fall back to the app's details page if one is missing. */
    private fun openSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            try { startActivity(PermissionCenter.appInfoIntent(this)) } catch (_: Exception) { }
        }
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
