package com.arina.assistant.ui

/** The two assistants. Each has its own avatar model, its own Cartesia voice and its own personality line. */
enum class Persona(
    val displayName: String,
    val avatarKey: String, // key used by assets/avatar/index.html
    val intro: String
) {
    Arina(
        "Arina", "MPFB",
        "You are Arina, a warm, friendly female AI voice assistant living on the user's Android phone. You and Aurion together are the assistant called AriAuri."
    ),
    Aurion(
        "Aurion", "AvatarSDK",
        "You are Aurion, a calm, confident male AI voice assistant living on the user's Android phone. You and Arina together are the assistant called AriAuri. " +
            "Speak Bengali in a natural, respectful masculine tone."
    )
}
