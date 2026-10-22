package com.arina.assistant.ai

import com.arina.assistant.device.DeviceActions
import org.json.JSONArray
import org.json.JSONObject

/** Describes the phone tools to Gemini and runs them when Gemini asks. */
class ToolRunner(private val device: DeviceActions) {

    private fun decl(
        name: String,
        description: String,
        props: List<Triple<String, String, String>> = emptyList(), // name, TYPE, description
        required: List<String> = props.map { it.first }
    ): JSONObject {
        val d = JSONObject().put("name", name).put("description", description)
        if (props.isNotEmpty()) {
            val p = JSONObject()
            props.forEach { (n, t, desc) ->
                p.put(n, JSONObject().put("type", t).put("description", desc))
            }
            d.put(
                "parameters",
                JSONObject().put("type", "OBJECT").put("properties", p).put("required", JSONArray(required))
            )
        }
        return d
    }

    fun declarations(): JSONArray = JSONArray().apply {
        put(decl("open_app", "Open an installed app.", listOf(Triple("app_name", "STRING", "App name in English as shown on the phone, e.g. WhatsApp, YouTube, Camera, Chrome."))))
        put(decl("call_contact", "Call a saved contact or a phone number.", listOf(Triple("name_or_number", "STRING", "Contact name as spoken, or digits of a phone number."))))
        put(decl("send_sms", "Send an SMS text message.", listOf(
            Triple("recipient", "STRING", "Contact name or phone number."),
            Triple("message", "STRING", "The text to send.")
        )))
        put(decl("set_volume", "Set the media volume.", listOf(Triple("percent", "INTEGER", "0 to 100."))))
        put(decl("toggle_flashlight", "Turn the flashlight on or off.", listOf(Triple("on", "BOOLEAN", "true for on, false for off."))))
        put(decl("press_button", "Press a system button (needs Device Control enabled).", listOf(Triple("button", "STRING", "One of: home, back, recents, notifications, quick_settings, screenshot, lock_screen."))))
        put(decl("tap_text", "Tap an on-screen item that shows the given text (needs Device Control enabled).", listOf(Triple("text", "STRING", "Visible text of the button or item."))))
        put(decl("open_settings", "Open a phone settings screen. Cannot flip switches itself.", listOf(Triple("section", "STRING", "One of: wifi, bluetooth, display, sound, battery, airplane, location, apps, mobile_data, accessibility, main."))))
        put(decl("web_search", "Search the web in the browser.", listOf(Triple("query", "STRING", "Search query."))))
        put(decl("open_url", "Open a web address in the browser.", listOf(Triple("url", "STRING", "Website address."))))
        put(decl("set_alarm", "Set an alarm.", listOf(
            Triple("hour", "INTEGER", "Hour in 24-hour time, 0-23."),
            Triple("minute", "INTEGER", "Minute 0-59."),
            Triple("label", "STRING", "Optional alarm name.")
        ), required = listOf("hour", "minute")))
        put(decl("get_status", "Get the current date, time and battery level."))
    }

    fun run(name: String, args: JSONObject): String = try {
        when (name) {
            "open_app" -> device.openApp(args.optString("app_name"))
            "call_contact" -> device.call(args.optString("name_or_number"))
            "send_sms" -> device.sendSms(args.optString("recipient"), args.optString("message"))
            "set_volume" -> device.setVolume(args.optInt("percent", 50))
            "toggle_flashlight" -> device.flashlight(args.optBoolean("on", true))
            "press_button" -> device.pressButton(args.optString("button"))
            "tap_text" -> device.tapText(args.optString("text"))
            "open_settings" -> device.openSettings(args.optString("section", "main"))
            "web_search" -> device.webSearch(args.optString("query"))
            "open_url" -> device.openUrl(args.optString("url"))
            "set_alarm" -> device.setAlarm(args.optInt("hour"), args.optInt("minute"), args.optString("label", "Arina"))
            "get_status" -> device.status()
            else -> "ERROR: unknown tool $name"
        }
    } catch (e: Exception) {
        "ERROR: ${e.message}"
    }
}
