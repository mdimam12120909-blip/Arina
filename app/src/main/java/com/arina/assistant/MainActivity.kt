package com.arina.assistant

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.arina.assistant.ui.ArinaTheme
import com.arina.assistant.ui.ArinaViewModel
import com.arina.assistant.ui.MainScreen

class MainActivity : ComponentActivity() {

    private val vm: ArinaViewModel by viewModels()

    private val phonePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refreshSetup() }

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        vm.refreshSetup()
        if (granted) vm.onMicTap()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ArinaTheme {
                MainScreen(
                    vm = vm,
                    onMicClick = {
                        if (vm.micGranted.value || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == 0) {
                            vm.onMicTap()
                        } else {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
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
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshSetup()
    }
}
