package com.arina.assistant.voice

import com.arina.assistant.ai.SmartChunker
import com.arina.assistant.ai.Timing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the engine needs from a speaker that talks while the answer is still being written. */
interface Speaker {
    val started: Boolean
    fun feed(piece: String)
    suspend fun finish()
    fun cancel()
}

/**
 * Speaks through [CartesiaLive]: phrases go out the moment the chunker has them and the audio plays as it is
 * generated. If the live connection gives nothing at all, the whole text is spoken the ordinary way, so a failure
 * of the fast path never leaves the user without a voice.
 */
class LiveSpeaker(
    private val live: CartesiaLive,
    private val voice: VoiceOutput,
    voiceId: String,
    scope: CoroutineScope,
    beforeFirstPlay: suspend () -> Unit,
    onFirstPlay: () -> Unit
) : Speaker {
    private val chunker = SmartChunker()
    private val spoken = StringBuilder()
    private val turn: CartesiaLive.Turn? = live.begin(voiceId, onFirstPlay)
    @Volatile override var started = false
        private set

    init {
        // Do not talk over a spoken "I am looking it up" line.
        scope.launch {
            try {
                beforeFirstPlay()
            } finally {
                turn?.openGate()
            }
        }
    }

    @Synchronized
    override fun feed(piece: String) {
        for (phrase in chunker.feed(piece)) send(phrase)
    }

    private fun send(phrase: String) {
        if (!started) Timing.mark("first phrase ready")
        started = true
        spoken.append(phrase).append(' ')
        turn?.send(phrase, true)
    }

    override suspend fun finish() {
        synchronized(this) {
            for (phrase in chunker.flush()) send(phrase)
            turn?.send("", false)
        }
        try {
            if (turn == null) throw LiveVoiceUnavailable("no live voice")
            turn.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: LiveVoiceUnavailable) {
            // Nothing was heard: say it the ordinary way.
            Timing.mark("live voice failed, using normal voice")
            Timing.note("live voice", e.message.orEmpty())
            voice.speak(spoken.toString().trim())
        }
    }

    override fun cancel() {
        turn?.cancel()
    }
}
