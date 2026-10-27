package com.arina.assistant.ai

import com.arina.assistant.device.ArinaNotificationListener
import com.arina.assistant.device.DeviceActions
import org.json.JSONArray
import org.json.JSONObject

/** Things only the app engine can do (screen cards, background jobs). Each returns a short result for Gemini. */
interface ToolHooks {
    fun research(question: String): String
    fun deepResearch(topic: String): String
    fun showImage(query: String): String
    fun showGallery(album: String, count: Int): String
    fun showFile(name: String): String
    fun showLink(url: String): String
    fun operatePhone(goal: String): String
    fun termux(command: String): String
}

/** Describes the phone tools to Gemini and runs them when Gemini asks. */
class ToolRunner(
    private val device: DeviceActions,
    private val memory: Memory,
    private val onMemoryChanged: () -> Unit,
    private val hooks: ToolHooks
) {

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
        put(decl("research", "Look something up on the web in the background and announce the answer when ready. Use for news, prices, scores, current facts, comparisons or anything you are not sure about. Returns at once; the answer is spoken later.", listOf(Triple("question", "STRING", "The full question to research, in English or Bengali."))))
        put(decl("remember", "Save one important fact about the user to long-term memory.", listOf(Triple("fact", "STRING", "One short English sentence, e.g. 'Habib prefers short answers.'"))))
        put(decl("forget", "Delete remembered facts that contain this text.", listOf(Triple("text", "STRING", "Word or phrase that identifies the fact to delete."))))
        put(decl("web_search", "Open a web search in the browser so the user can see the results himself.", listOf(Triple("query", "STRING", "Search query."))))
        put(decl("open_url", "Open a web address in the browser.", listOf(Triple("url", "STRING", "Website address."))))
        put(decl("set_alarm", "Set an alarm.", listOf(
            Triple("hour", "INTEGER", "Hour in 24-hour time, 0-23."),
            Triple("minute", "INTEGER", "Minute 0-59."),
            Triple("label", "STRING", "Optional alarm name.")
        ), required = listOf("hour", "minute")))
        put(decl("read_notifications", "Read the latest notifications on the phone (needs Notification access)."))
        put(decl("get_status", "Get the current date, time and battery level."))
        put(decl("deep_research", "Start a long, thorough research job in the background (many web searches over a few minutes, then a full written report on screen and a spoken summary). Use for big questions, comparisons, 'research X for me', study-style tasks. Returns at once.", listOf(Triple("topic", "STRING", "The full topic or question to research, with any details the user gave."))))
        put(decl("show_image", "Find pictures on the web and show them on screen for one minute. Use when the user asks to see a photo or picture of someone or something.", listOf(Triple("query", "STRING", "Web image search query in English, e.g. 'Lionel Messi photo'."))))
        put(decl("show_gallery", "Show the user's own recent photos from the phone gallery on screen for one minute.", listOf(
            Triple("album", "STRING", "Album/folder name such as Camera, Screenshots, WhatsApp; empty for all photos."),
            Triple("count", "INTEGER", "How many newest photos, 1 to 8.")
        ), required = listOf("count")))
        put(decl("show_file", "Find a file on the phone by (part of) its name and show it on screen: images, PDFs and text files can be previewed.", listOf(Triple("name", "STRING", "Part of the file name."))))
        put(decl("show_link", "Show a web page on screen inside a card for one minute.", listOf(Triple("url", "STRING", "Website address."))))
        put(decl("operate_phone", "Operate the phone by itself like a person: open apps, search, tap, type, scroll, e.g. play a video on YouTube. Runs in the background after the user allows it. Needs Device control enabled.", listOf(Triple("goal", "STRING", "The complete goal in English with every detail, e.g. 'Open YouTube, search for Arijit Singh songs and play the first video'."))))
        put(decl("termux", "Run one shell command in Termux. The user is asked to allow every command before it runs. Returns the output.", listOf(Triple("command", "STRING", "The bash command line."))))
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
            "research" -> hooks.research(args.optString("question"))
            "deep_research" -> hooks.deepResearch(args.optString("topic"))
            "show_image" -> hooks.showImage(args.optString("query"))
            "show_gallery" -> hooks.showGallery(args.optString("album"), args.optInt("count", 4))
            "show_file" -> hooks.showFile(args.optString("name"))
            "show_link" -> hooks.showLink(args.optString("url"))
            "operate_phone" -> hooks.operatePhone(args.optString("goal"))
            "termux" -> hooks.termux(args.optString("command"))
            "remember" -> {
                val added = memory.add(args.optString("fact"))
                onMemoryChanged()
                if (added) "OK: remembered." else "OK: already remembered."
            }
            "forget" -> {
                val n = memory.forget(args.optString("text"))
                onMemoryChanged()
                if (n > 0) "OK: forgot $n item(s)." else "ERROR: nothing matched."
            }
            "web_search" -> device.webSearch(args.optString("query"))
            "open_url" -> device.openUrl(args.optString("url"))
            "set_alarm" -> device.setAlarm(args.optInt("hour"), args.optInt("minute"), args.optString("label", "Arina"))
            "read_notifications" -> ArinaNotificationListener.instance?.summary()
                ?: "ERROR: Notification access is off. Ask the user to turn it on in the Settings tab."
            "get_status" -> device.status()
            else -> "ERROR: unknown tool $name"
        }
    } catch (e: Exception) {
        "ERROR: ${e.message}"
    }
}
