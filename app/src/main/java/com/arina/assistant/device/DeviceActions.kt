package com.arina.assistant.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Everything Arina can actually do on the phone. Each function returns a short result string for Gemini. */
class DeviceActions(private val ctx: Context) {

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    private fun has(permission: String) =
        ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Converts Bengali/Arabic-Indic digits to ASCII and keeps only digits and '+'. */
    private fun cleanNumber(raw: String): String = buildString {
        for (c in raw) {
            val d = Character.digit(c, 10)
            if (d >= 0) append(d) else if (c == '+') append(c)
        }
    }

    fun openApp(name: String): String {
        val pm = ctx.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(query, 0)
        val q = name.trim().lowercase()
        if (q.isEmpty()) return "ERROR: no app name given"
        fun label(i: android.content.pm.ResolveInfo) = i.loadLabel(pm).toString().lowercase()
        val match = apps.firstOrNull { label(it) == q }
            ?: apps.firstOrNull { label(it).contains(q) }
            ?: apps.firstOrNull { label(it).length >= 3 && q.contains(label(it)) }
            ?: return "ERROR: no installed app matches '$name'"
        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return "ERROR: cannot launch '$name'"
        launch(intent)
        return "OK: opened ${match.loadLabel(pm)}"
    }

    private fun lookupNumber(name: String): String? {
        if (!has(Manifest.permission.READ_CONTACTS)) return null
        val cursor = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%${name.trim()}%"),
            null
        ) ?: return null
        cursor.use {
            var first: String? = null
            while (it.moveToNext()) {
                val n = it.getString(0) ?: continue
                val num = it.getString(1) ?: continue
                if (n.equals(name.trim(), ignoreCase = true)) return num
                if (first == null) first = num
            }
            return first
        }
    }

    private fun resolveNumber(target: String): String? {
        val digits = cleanNumber(target)
        return if (digits.count { it.isDigit() } >= 5) digits else lookupNumber(target)
    }

    fun call(target: String): String {
        val number = resolveNumber(target)
            ?: return "ERROR: contact '$target' not found (or contacts permission not granted)"
        val action = if (has(Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        launch(Intent(action, Uri.parse("tel:" + Uri.encode(number))))
        return if (action == Intent.ACTION_CALL) "OK: calling $target" else "OK: dialer opened for $target (call permission missing)"
    }

    fun sendSms(recipient: String, message: String): String {
        if (!has(Manifest.permission.SEND_SMS)) return "ERROR: SMS permission not granted"
        val number = resolveNumber(recipient) ?: return "ERROR: contact '$recipient' not found"
        return try {
            val sms = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            sms.sendMultipartTextMessage(number, null, sms.divideMessage(message), null, null)
            "OK: message sent to $recipient"
        } catch (e: Exception) {
            "ERROR: could not send SMS: ${e.message}"
        }
    }

    fun setVolume(percent: Int): String {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val p = percent.coerceIn(0, 100)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, p * max / 100, AudioManager.FLAG_SHOW_UI)
        return "OK: media volume set to $p%"
    }

    fun flashlight(on: Boolean): String {
        return try {
            val cm = ctx.getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.firstOrNull {
                val c = cm.getCameraCharacteristics(it)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return "ERROR: no flashlight on this device"
            cm.setTorchMode(id, on)
            "OK: flashlight ${if (on) "on" else "off"}"
        } catch (e: Exception) {
            "ERROR: flashlight failed: ${e.message}"
        }
    }

    fun pressButton(button: String): String {
        val svc = ArinaAccessibilityService.instance
            ?: return "ERROR: Device Control (accessibility) is not enabled. Ask the user to enable it in Arina's Setup tab."
        return if (svc.press(button)) "OK: pressed $button" else "ERROR: could not press $button"
    }

    fun tapText(text: String): String {
        val svc = ArinaAccessibilityService.instance
            ?: return "ERROR: Device Control (accessibility) is not enabled."
        return if (svc.tapText(text)) "OK: tapped '$text'" else "ERROR: nothing clickable with text '$text' found on screen"
    }

    fun openSettings(section: String): String {
        val action = when (section.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "mobile_data" -> Settings.ACTION_WIRELESS_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        launch(Intent(action))
        return "OK: opened $section settings (Android does not allow apps to flip this switch directly; the user must toggle it)"
    }

    fun webSearch(query: String): String {
        launch(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", query))
        return "OK: searching the web for '$query'"
    }

    fun openUrl(url: String): String {
        val u = if (url.startsWith("http")) url else "https://$url"
        launch(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        return "OK: opened $u"
    }

    fun setAlarm(hour: Int, minute: Int, label: String): String {
        return try {
            launch(
                Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
                    .putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
                    .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            )
            "OK: alarm set for %02d:%02d".format(hour, minute)
        } catch (e: Exception) {
            "ERROR: could not set alarm: ${e.message}"
        }
    }

    fun status(): String {
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, hh:mm a"))
        return "Time: $now. Battery: $battery%."
    }
}
