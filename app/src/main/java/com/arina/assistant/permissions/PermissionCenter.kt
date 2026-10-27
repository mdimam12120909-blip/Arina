package com.arina.assistant.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.arina.assistant.device.ArinaAccessibilityService

enum class PermKind { Runtime, Special }

data class PermItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: PermKind,
    /** Runtime permissions this row asks for (only those that exist on this Android version). */
    val perms: List<String> = emptyList(),
    /** Needs the matching runtime permission row first (background location). */
    val needs: String? = null
)

/** Everything Arina can be given. Rows are shown in the Settings tab; the order here is the order shown. */
object PermissionCenter {

    private val sdk = Build.VERSION.SDK_INT

    private fun media(): List<String> =
        if (sdk >= 33) listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        ) else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun nearby(): List<String> = buildList {
        if (sdk >= 31) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (sdk >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }

    val runtime: List<PermItem> = buildList {
        add(PermItem("mic", "Microphone", "Hear your voice", PermKind.Runtime, listOf(Manifest.permission.RECORD_AUDIO)))
        add(PermItem("contacts", "Contacts", "Find, read and add contacts", PermKind.Runtime,
            listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)))
        add(PermItem("phone", "Phone and calls", "Make and answer calls, read call history", PermKind.Runtime, buildList {
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.WRITE_CALL_LOG)
            if (sdk >= 26) add(Manifest.permission.ANSWER_PHONE_CALLS)
        }))
        add(PermItem("termux", "Run commands in Termux", "Lets AriAuri run shell commands in Termux; it asks you before every command", PermKind.Runtime, listOf("com.termux.permission.RUN_COMMAND")))
        add(PermItem("sms", "SMS", "Send and read text messages", PermKind.Runtime,
            listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)))
        add(PermItem("calendar", "Calendar", "Read and create events", PermKind.Runtime,
            listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)))
        add(PermItem("camera", "Camera", "Use the camera and flashlight", PermKind.Runtime, listOf(Manifest.permission.CAMERA)))
        add(PermItem("location", "Location", "Know where you are", PermKind.Runtime,
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)))
        if (sdk >= 29) {
            add(PermItem("bglocation", "Location, all the time", "Needs Location first; choose \"Allow all the time\"",
                PermKind.Runtime, listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), needs = "location"))
        }
        if (sdk >= 33) {
            add(PermItem("notifications", "Notifications", "Show the Arina status notification", PermKind.Runtime,
                listOf(Manifest.permission.POST_NOTIFICATIONS)))
        }
        add(PermItem("media", "Photos, videos and audio", "Read your media files", PermKind.Runtime, media()))
        if (nearby().isNotEmpty()) {
            add(PermItem("nearby", "Nearby devices", "Bluetooth and nearby Wi-Fi", PermKind.Runtime, nearby()))
        }
        add(PermItem("sensors", "Body sensors", "Heart-rate style sensors", PermKind.Runtime, buildList {
            add(Manifest.permission.BODY_SENSORS)
            if (sdk >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
        }))
    }

    val special: List<PermItem> = buildList {
        add(PermItem("overlay", "Show over other apps", "Needed for the floating orb", PermKind.Special))
        add(PermItem("accessibility", "Device control", "Back, Home, screenshots and tapping on screen", PermKind.Special))
        add(PermItem("notif_access", "Notification access", "Read your notifications aloud", PermKind.Special))
        if (sdk >= 30) add(PermItem("all_files", "All files access", "Open and manage any file", PermKind.Special))
        add(PermItem("write_settings", "Change system settings", "Brightness, ringtone and similar", PermKind.Special))
        add(PermItem("dnd", "Do Not Disturb access", "Turn silent mode on and off", PermKind.Special))
        add(PermItem("usage", "Usage access", "See which apps you use", PermKind.Special))
        add(PermItem("battery", "Run without battery limits", "Keeps Arina alive in the background", PermKind.Special))
        if (sdk >= 31) add(PermItem("alarms", "Exact alarms", "Alarms and reminders on time", PermKind.Special))
    }

    private fun granted(ctx: Context, p: String) =
        ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun isGranted(ctx: Context, item: PermItem): Boolean = when (item.kind) {
        PermKind.Runtime -> item.perms.all { granted(ctx, it) }
        PermKind.Special -> when (item.id) {
            "overlay" -> Settings.canDrawOverlays(ctx)
            "accessibility" -> accessibilityEnabled(ctx)
            "notif_access" -> NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
            "all_files" -> sdk >= 30 && Environment.isExternalStorageManager()
            "write_settings" -> Settings.System.canWrite(ctx)
            "dnd" -> (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
            "usage" -> usageAllowed(ctx)
            "battery" -> (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
            "alarms" -> sdk < 31 || (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            else -> false
        }
    }

    fun accessibilityEnabled(ctx: Context): Boolean {
        val mine = ComponentName(ctx, ArinaAccessibilityService::class.java)
        val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return list.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    private fun usageAllowed(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (sdk >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** The settings screen for a special-access row. */
    fun intentFor(ctx: Context, item: PermItem): Intent {
        val pkg = Uri.parse("package:${ctx.packageName}")
        return when (item.id) {
            "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "notif_access" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            "all_files" -> Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg)
            "write_settings" -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg)
            "dnd" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            "usage" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            "battery" -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            "alarms" -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            else -> appInfoIntent(ctx)
        }
    }

    fun appInfoIntent(ctx: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    /** Runtime permissions not yet granted, except background location (asked on its own). */
    fun missingRuntime(ctx: Context): Array<String> =
        runtime.filter { it.id != "bglocation" }
            .flatMap { it.perms }
            .filter { !granted(ctx, it) }
            .distinct()
            .toTypedArray()
}
