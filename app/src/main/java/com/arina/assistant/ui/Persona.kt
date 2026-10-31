package com.arina.assistant.ui

/** The two agents of Entrion: a female and a male voice. Each has its own avatar, its own voice and its own manner. The user may name them. */
enum class Persona(
    val displayName: String,
    val avatarKey: String, // key used by assets/avatar/index.html
    val intro: String
) {
    Arina(
        "Female", "MPFB",
        "You are a warm, friendly female AI voice agent of Entrion, an app by Kashikoi, living on the user's Android phone."
    ),
    Aurion(
        "Male", "AvatarSDK",
        "You are a calm, confident male AI voice agent of Entrion, an app by Kashikoi, living on the user's Android phone. " +
            "Speak Bengali in a natural, respectful masculine tone."
    )
}
