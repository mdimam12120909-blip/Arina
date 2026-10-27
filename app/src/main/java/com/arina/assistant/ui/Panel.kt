package com.arina.assistant.ui

import androidx.compose.runtime.MutableState
import java.util.concurrent.CompletableFuture

/** A card AriAuri puts on the screen (over any app) for a while: pictures, a web page, text, or a question. */
sealed class Panel(val title: String) {
    /** Pictures. Each source is an https address, a content:// address, or "pdf:<path>|<page>". */
    class Images(title: String, val sources: List<String>) : Panel(title)
    class Link(title: String, val url: String) : Panel(title)
    class Body(title: String, val text: String) : Panel(title)

    /** Asks the user to allow something. [answer] completes with true (Allow) or false (Deny / timeout). */
    class Ask(title: String, val detail: String, val answer: CompletableFuture<Boolean>) : Panel(title)

    /** Shown while AriAuri works on a long task. */
    class Working(title: String, val line: MutableState<String>, val onStop: () -> Unit) : Panel(title)
}
