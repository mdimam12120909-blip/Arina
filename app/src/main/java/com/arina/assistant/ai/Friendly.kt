package com.arina.assistant.ai

/** Ordinary users never see technical errors or the names of the services behind Entrion. Admins see the raw text. */
object Friendly {
    private val technical = Regex(
        "gemini|cartesia|fish|supabase|google|openai|deepseek|anthropic|groq|tavily|lyria|veo|api key|http\\s*\\d{3}|\\b\\d{3}\\b.*(error|fail)|exception|unable to resolve|socket|quota|billing|ping|pong|token|json|timeout|timed out|stack|null|gateway|server error|unauthorized|forbidden",
        RegexOption.IGNORE_CASE
    )

    fun clean(text: String?, admin: Boolean): String? {
        if (text.isNullOrBlank() || admin) return text
        return if (technical.containsMatchIn(text)) "Something went wrong. Please try again in a moment." else text
    }
}
