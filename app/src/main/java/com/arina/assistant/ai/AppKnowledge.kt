package com.arina.assistant.ai

/**
 * What an expert already knows about how common apps are used. Handed to the phone agent so it does not have to
 * discover each app by trial and error. Short on purpose: facts that change which element it picks.
 */
object AppKnowledge {
    val TEXT = """
        HOW COMMON APPS WORK (use this before exploring):
        - Any AI chat app (Gemini, NotebookLM, ChatGPT and similar): the message box is an editable field near the bottom, usually labelled "Message", "Ask", "Type a message" or "Ask anything". Type there, then press the send arrow button that appears next to it (or submit with Enter). After sending, use wait_reply, then read the new reply on SCREEN. Long replies need scroll down to read all.
        - NotebookLM: a notebook holds sources. The chat is the "Chat" tab; the "Studio" tab has Audio Overview, Video Overview, Mind Map and Reports. To make a video: open the notebook, go to Studio, tap Video Overview (or its customize/pencil icon to give instructions), then Generate. It takes minutes; it appears in the Studio list when ready. If the notebook has no sources, add one first (the "Add source" button, then paste text or choose a source).
        - Gemini app: new chat is the "+" or pencil button; the input is "Ask Gemini". Tools menu is the "+" next to the input.
        - YouTube: search icon top right; results are a list; tap the title. Video controls appear on tap. Upload and Studio need the signed-in account.
        - WhatsApp / Messenger / Telegram: first tap the chat (search icon to find the person), then the message box ("Message"), then the send button. Never send unless the GOAL says to.
        - Gmail: compose is the pencil/"Compose" button; fields are To, Subject, then the body. Send is the paper-plane icon top right.
        - Chrome: the address bar is at the top ("Search or type URL"); use open_url instead when you have the address.
        - Settings app: use the search icon at the top and type the setting name; toggles are Switch elements on the right of each row; confirm the state after tapping.
        - Camera: shutter is the large round button at the bottom centre; Gallery shows the newest photo first.
        - Maps: search box at the top, then the Directions or Start button at the bottom.
        - Play Store: search icon, then the app, then Install/Update/Open. Only install if the GOAL says so.
        GENERAL: icons often have no text; read their content description. A loading spinner means wait. A permission pop-up asking for something the GOAL did not require should be declined or reported, not accepted. If an app asks to sign in, use fail and tell the user.
    """.trimIndent()
}
