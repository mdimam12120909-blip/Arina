package com.arina.assistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.arina.assistant.overlay.OverlayService
import com.arina.assistant.ui.SignInScreen
import com.arina.assistant.ui.IntroScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.arina.assistant.permissions.PermItem
import com.arina.assistant.permissions.PermKind
import com.arina.assistant.permissions.PermissionCenter
import com.arina.assistant.ui.ArinaEngine
import com.arina.assistant.ui.ArinaTheme
import com.arina.assistant.ui.MainScreen

class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_FROM_OVERLAY = "from_overlay"
        const val EXTRA_FROM_ASSIST = "from_assist"
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
        engine.refreshCloud()
        // Signing in, signing out or switching account: swap to a fresh engine and redraw, without leaving the app.
        engine.onAccountSwitched = {
            runOnUiThread {
                OverlayService.stop(this)
                runCatching { java.io.File(cacheDir, "cards").deleteRecursively() }   // pictures shown for the previous account
                ArinaEngine.discard()
                recreate()
            }
        }
        setContent {
            ArinaTheme {
                var introDone by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
                if (!introDone) {
                    IntroScreen { introDone = true }
                    return@ArinaTheme
                }
                com.arina.assistant.ui.UpdatePrompt(engine)
                if (!engine.signedIn.value && !engine.skippedSignIn.value) {
                    SignInScreen(engine)
                    return@ArinaTheme
                }
                // First time on a new account: a short run of questions that sets Entrion up.
                var onboarded by androidx.compose.runtime.saveable.rememberSaveable {
                    androidx.compose.runtime.mutableStateOf(engine.isAdmin.value || engine.uiPrefs.getBoolean("onboarded", false) || engine.geminiKeySet)
                }
                if (engine.signedIn.value && !onboarded) {
                    com.arina.assistant.ui.OnboardingScreen(engine, onPermission = ::requestItem, onAssistant = { openSafely(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }) { onboarded = true }
                    return@ArinaTheme
                }
                // An admin gets the console only: no assistant, no microphone, no floating orb.
                if (engine.signedIn.value && engine.isAdmin.value) {
                    androidx.compose.runtime.LaunchedEffect(Unit) { OverlayService.stop(this@MainActivity) }
                    if (!engine.adminUnlocked.value) com.arina.assistant.ui.AdminGate(engine) { unlockAdmin() }
                    else com.arina.assistant.ui.AdminConsole(engine) { engine.adminUnlocked.value = false }
                    return@ArinaTheme
                }
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
        // The floating orb no longer starts by itself; it only runs when the person switches it on.
        handleAssist(intent)
    }

    /** Opened from a long press on Home (default assistant): go to Home and start listening straight away. */
    private fun handleAssist(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_FROM_ASSIST, false) != true) return
        i.removeExtra(EXTRA_FROM_ASSIST)
        if (engine.isAdmin.value || !engine.signedIn.value) return
        if (!hasMic()) { micPermission.launch(Manifest.permission.RECORD_AUDIO); return }
        if (!engine.micOn.value) engine.onMicTap()
    }

    /** Face (or fingerprint, or screen lock) check before the Admin Console opens. The server still decides who is an admin. */
    private fun unlockAdmin() {
        // Face or fingerprint only: no PIN or pattern fallback.
        val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            engine.notice.value = "Add a fingerprint or face in your phone's Settings first. The Admin Console stays locked until you do."
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                engine.notice.value = null
                engine.adminUnlocked.value = true
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                engine.notice.value = errString.toString().take(120)
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Admin Console")
            .setSubtitle("Use your face or fingerprint")
            .setAllowedAuthenticators(auth)
            .setNegativeButtonText("Cancel")
            .build()
        prompt.authenticate(info)
    }

    override fun onStop() {
        super.onStop()
        engine.adminUnlocked.value = false   // the console locks itself whenever the app leaves the screen
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAssist(intent)
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
