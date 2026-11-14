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
    val context: Context get() = ctx

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

    /** Apps the user switched off in the Connectors page: Entrion must not open or operate them. */
    private fun disabledApps(): Set<String> =
        com.arina.assistant.cloud.Local.prefs(ctx, "arina_ui").getStringSet("conn_disabled", emptySet()).orEmpty()

    fun openApp(name: String): String {
        val pm = ctx.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(query, 0)
        val q = name.trim().lowercase()
        if (q.isEmpty()) return "ERROR: no app name given"
        fun label(i: android.content.pm.ResolveInfo) = i.loadLabel(pm).toString().lowercase()
        // Names people say that differ from the label on the home screen.
        val aliasPkg = when (q.replace("youtube", "yt").replace(" ", "")) {
            "ytstudio", "ytcreator", "studio" -> "com.google.android.apps.youtube.creator"
            else -> null
        }
        val match = (if (aliasPkg != null) apps.firstOrNull { it.activityInfo.packageName == aliasPkg } else null)
            ?: apps.firstOrNull { label(it) == q }
            ?: apps.filter { label(it).contains(q) }.minByOrNull { label(it).length }
            ?: apps.filter { label(it).length >= 3 && q.contains(label(it)) }.maxByOrNull { label(it).length }
            ?: return "ERROR: no installed app matches '$name'"
        // "YouTube Studio" asked but only plain YouTube matched: say so instead of silently opening the wrong app.
        if (aliasPkg != null && match.activityInfo.packageName != aliasPkg) return "ERROR: '$name' is not installed (the YouTube Studio app is missing). Offer the website studio.youtube.com in Chrome."
        if (match.activityInfo.packageName in disabledApps()) return "ERROR: the user has turned ${match.loadLabel(pm)} off for Entrion in Connectors. Ask them to allow it there."
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
            return first ?: findContacts(name).firstOrNull()?.second
        }
    }

    /** Every contact whose name contains [name]: (display name, number), one per distinct number. */
    fun findContacts(name: String): List<Pair<String, String>> {
        if (!has(Manifest.permission.READ_CONTACTS)) return emptyList()
        val out = LinkedHashMap<String, Pair<String, String>>()
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%${name.trim()}%"), null
        )?.use {
            while (it.moveToNext()) {
                val n = it.getString(0) ?: continue
                val num = it.getString(1) ?: continue
                out.putIfAbsent(num.filter { c -> c.isDigit() }, n to num)
            }
        }
        if (out.isEmpty()) {
            // The saved name may be spelled in another script ("Mohammad Ali" saved, "মোহাম্মদ আলী" spoken): match by sound.
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null
            )?.use {
                while (it.moveToNext()) {
                    val n = it.getString(0) ?: continue
                    val num = it.getString(1) ?: continue
                    if (NameMatch.matches(name, n)) out.putIfAbsent(num.filter { c -> c.isDigit() }, n to num)
                }
            }
        }
        return out.values.toList()
    }

    /** International digits for wa.me: a saved local number (0171...) gets this phone's country code. */
    fun internationalDigits(number: String): String {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        val iso = (tm?.simCountryIso?.takeIf { it.isNotBlank() } ?: tm?.networkCountryIso?.takeIf { it.isNotBlank() } ?: java.util.Locale.getDefault().country).uppercase()
        val e164 = runCatching { android.telephony.PhoneNumberUtils.formatNumberToE164(number, iso) }.getOrNull()
        return (e164 ?: number).filter { it.isDigit() }
    }

    fun openWhatsAppChat(digits: String, text: String): String? {
        val uri = Uri.parse("https://wa.me/$digits?text=" + Uri.encode(text))
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            if (runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.isSuccess) {
                launch(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg))
                return pkg
            }
        }
        return null
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
            ?: return "ERROR: Device Control (accessibility) is not enabled. Ask the user to enable it in Entrion's Setup tab."
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

    /** What is true about the phone right now, so Entrion can mention it naturally (low battery, night, no internet). */
    fun nowLine(): String {
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = runCatching { bm.isCharging }.getOrDefault(false)
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val net = when {
            caps == null -> "offline"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "on Wi-Fi"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "on mobile data"
            else -> "online"
        }
        val h = java.time.LocalTime.now().hour
        val part = when (h) { in 5..11 -> "morning"; in 12..16 -> "afternoon"; in 17..20 -> "evening"; else -> "night" }
        return "Battery $pct%${if (charging) " (charging)" else ""}, $net, it is $part (${java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))})."
    }

    fun status(): String {
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, hh:mm a"))
        return "Time: $now. Battery: $battery%."
    }

    // ---- Direct phone controls: each one changes the setting, then reads it back so the result is the real state. ----

    private val audio get() = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** One entry point for the phone's own switches. [value] depends on the action. */
    @Suppress("DEPRECATION")
    fun control(action: String, value: String): String {
        val v = value.trim().lowercase()
        return when (action.trim().lowercase()) {
            "brightness" -> {
                if (!Settings.System.canWrite(ctx)) return needs("Modify system settings", Settings.ACTION_MANAGE_WRITE_SETTINGS)
                val pct = v.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 100) ?: return "ERROR: give a brightness from 0 to 100."
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (pct * 255 / 100).coerceIn(1, 255))
                val now = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 0) * 100 / 255
                "OK: brightness is now about $now%"
            }
            "volume_media", "volume_ring", "volume_alarm", "volume_call" -> {
                val stream = when (action.trim().lowercase()) {
                    "volume_ring" -> AudioManager.STREAM_RING
                    "volume_alarm" -> AudioManager.STREAM_ALARM
                    "volume_call" -> AudioManager.STREAM_VOICE_CALL
                    else -> AudioManager.STREAM_MUSIC
                }
                val pct = v.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 100) ?: return "ERROR: give a volume from 0 to 100."
                val max = audio.getStreamMaxVolume(stream)
                audio.setStreamVolume(stream, Math.round(max * pct / 100f), 0)
                "OK: ${action.removePrefix("volume_")} volume is now ${audio.getStreamVolume(stream) * 100 / max.coerceAtLeast(1)}%"
            }
            "ringer" -> {
                val mode = when (v) {
                    "normal", "ring", "loud" -> AudioManager.RINGER_MODE_NORMAL
                    "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
                    "silent", "mute" -> AudioManager.RINGER_MODE_SILENT
                    else -> return "ERROR: ringer must be normal, vibrate or silent."
                }
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (mode == AudioManager.RINGER_MODE_SILENT && !nm.isNotificationPolicyAccessGranted)
                    return needs("Do Not Disturb access", Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                audio.ringerMode = mode
                "OK: ringer mode is now ${listOf("silent", "vibrate", "normal")[audio.ringerMode.coerceIn(0, 2)]}"
            }
            "dnd" -> {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (!nm.isNotificationPolicyAccessGranted) return needs("Do Not Disturb access", Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                val on = v in setOf("on", "true", "yes", "1")
                nm.setInterruptionFilter(if (on) android.app.NotificationManager.INTERRUPTION_FILTER_NONE else android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
                "OK: Do Not Disturb is now ${if (nm.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_ALL) "off" else "on"}"
            }
            "rotation" -> {
                if (!Settings.System.canWrite(ctx)) return needs("Modify system settings", Settings.ACTION_MANAGE_WRITE_SETTINGS)
                val on = v in setOf("on", "true", "yes", "1", "auto")
                Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
                "OK: auto-rotate is now ${if (Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1) "on" else "off"}"
            }
            "media" -> {
                val key = when (v) {
                    "play", "pause", "toggle", "play_pause" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    "next" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
                    "previous", "prev" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    "stop" -> android.view.KeyEvent.KEYCODE_MEDIA_STOP
                    else -> return "ERROR: media must be play, pause, next, previous or stop."
                }
                audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key))
                audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key))
                "OK: sent $v to the media player (music is ${if (audio.isMusicActive) "playing" else "not playing"} now)"
            }
            "answer_call" -> {
                if (!has(Manifest.permission.ANSWER_PHONE_CALLS)) return "ERROR: the Phone permission is off. Ask the user to allow it in Permissions."
                val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                tm.acceptRingingCall()
                "OK: answered the call"
            }
            "end_call" -> {
                if (!has(Manifest.permission.ANSWER_PHONE_CALLS)) return "ERROR: the Phone permission is off. Ask the user to allow it in Permissions."
                val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                val ended = tm.endCall()
                if (ended) "OK: call ended" else "ERROR: there is no call to end"
            }
            "speaker" -> {
                val on = v in setOf("on", "true", "yes", "1")
                audio.isSpeakerphoneOn = on
                "OK: speaker is now ${if (audio.isSpeakerphoneOn) "on" else "off"}"
            }
            "wifi", "bluetooth", "hotspot", "airplane", "location", "mobile_data" -> {
                // Android no longer lets apps flip these directly. Open the right screen; the agent then flips the one switch.
                val r = openSettings(if (action == "hotspot") "mobile_data" else action.trim().lowercase())
                "$r NEXT: use operate_phone with the goal 'turn ${action.replace('_', ' ')} ${if (v in setOf("on", "true", "yes", "1")) "on" else "off"} on this screen, then confirm the switch shows that state'."
            }
            else -> "ERROR: unknown control '$action'."
        }
    }

    private fun needs(what: String, settingsAction: String): String {
        runCatching {
            launch(Intent(settingsAction).apply { if (settingsAction == Settings.ACTION_MANAGE_WRITE_SETTINGS) data = Uri.parse("package:${ctx.packageName}") })
        }
        return "ERROR: needs '$what' access. I opened the page; ask the user to switch it on for Entrion, then try again."
    }

    /** Opens the installed app whose name appears in [goal], so the agent starts on the right screen at once. Null if none. */
    fun openMentionedApp(goal: String): String? {
        val pm = ctx.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val off = disabledApps()
        val hit = pm.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != ctx.packageName && it.activityInfo.packageName !in off }
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .filter { (label, _) -> label.length >= 3 && Regex("(?i)(^|[^a-z0-9])${Regex.escape(label)}($|[^a-z0-9])").containsMatchIn(goal) }
            .maxByOrNull { it.first.length } ?: return null
        val intent = pm.getLaunchIntentForPackage(hit.second) ?: return null
        launch(intent)
        return hit.first
    }

    /** Opens an app straight to what is needed with a ready-made intent, no tapping. The person still presses the final send. */
    fun appAction(action: String, a: String, b: String, c: String): String = try {
        when (action.trim().lowercase()) {
            "email" -> { launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(a))).putExtra(Intent.EXTRA_SUBJECT, b).putExtra(Intent.EXTRA_TEXT, c)); "OK: opened a draft email to $a with the subject and text filled in. Nothing was sent; the user must press Send." }
            "whatsapp" -> { launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + a.filter { it.isDigit() } + "?text=" + Uri.encode(b)))); "OK: opened WhatsApp with the message ready. Nothing was sent; the user must press Send." }
            "maps" -> { launch(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(a)))); "OK: started navigation to $a" }
            "youtube_search" -> { launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(a)))); "OK: opened YouTube results for $a" }
            "web_search" -> { launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(a)))); "OK: opened a web search for $a" }
            "share" -> { launch(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, a), "Share")); "OK: opened the share sheet. The user chooses where to send it." }
            "timer" -> { launch(Intent(android.provider.AlarmClock.ACTION_SET_TIMER).putExtra(android.provider.AlarmClock.EXTRA_LENGTH, a.filter { it.isDigit() }.toIntOrNull() ?: 60).putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, b).putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)); "OK: timer started for ${a.filter { it.isDigit() }} seconds" }
            "calendar_event" -> { launch(Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI).putExtra(android.provider.CalendarContract.Events.TITLE, a).putExtra(android.provider.CalendarContract.Events.DESCRIPTION, b)); "OK: opened a new calendar event titled $a. The user picks the time and saves." }
            else -> "ERROR: unknown app action '$action'."
        }
    } catch (e: Exception) { "ERROR: could not do that (${e.message ?: e.javaClass.simpleName})." }
}
