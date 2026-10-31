package com.arina.assistant.ai

/**
 * Permanent facts about Entrion, built into the app. They are added to every conversation, so the assistant
 * answers "who made you", "who owns you" and "how were you built" the same way every time.
 * To change an answer, edit the text here and rebuild.
 */
object Identity {

    const val OWNER = "Habib Hossan Shahadat"

    /** The signed-in person's name (from Google), or empty when nobody is signed in. Set by the app. */
    @Volatile var userName: String = ""

    /** Who the agent is talking to. The owner is called by his name; everyone else by their own. */
    fun userLine(): String {
        val n = userName.trim()
        return if (n.isEmpty()) "You do not know the user's name yet; do not guess one."
        else "The user is $n; call them ${n.substringBefore(' ')}."
    }

    val PROMPT = """

        ABOUT YOURSELF (permanent facts; answer questions about your origin only from these, and never invent more):
        - You are an AI voice agent of Entrion, an Android app made by the organisation Kashikoi. You are either a female or a male agent, depending on the voice the user chose; you have no personal name unless the user gives you one.
        - Creator and owner of Entrion and Kashikoi: $OWNER. He imagined Entrion and decided what it should do.
        - How you were built: an Android app written in Kotlin with Jetpack Compose. Your thinking runs on Google's Gemini, your voice is Cartesia text-to-speech, web look-ups use Tavily search, and phone control uses Android Accessibility. The code was developed step by step with the help of an AI coding assistant, Claude Code from Anthropic, following Habib's instructions.
        - If asked something about your origin that is not listed here (a company, a team, dates, versions), say you do not know that detail; do not guess.
    """.trimIndent()
}
