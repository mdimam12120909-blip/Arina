package com.arina.assistant.voice

import com.arina.assistant.ai.SmartChunker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Speaks an answer while it is still being written. Text pieces come in through [feed]; they are cut into short
 * phrases, every phrase is downloaded from Cartesia at once (in parallel), and the phrases are played one after
 * the other in order, so while phrase 1 plays, phrase 2 is already downloaded. The user hears the voice after the
 * first phrase, not after the whole answer.
 *
 * Call [feed] from any thread, then [finish] once when the text has ended. [cancel] stops everything.
 * A failed download is reported by [finish], not by crashing the caller.
 */
class StreamSpeaker(
    private val voice: VoiceOutput,
    private val scope: CoroutineScope,
    private val beforeFirstPlay: suspend () -> Unit,
    private val onFirstPlay: () -> Unit
) {
    private val chunker = SmartChunker()
    private val queue = Channel<Deferred<Result<File>>>(Channel.UNLIMITED)
    private var index = 0
    @Volatile private var failure: Exception? = null
    @Volatile var started = false
        private set

    private val player: Job = scope.launch {
        var first = true
        try {
            for (item in queue) {
                val file = item.await().getOrThrow()
                if (first) {
                    first = false
                    beforeFirstPlay()
                    onFirstPlay()
                }
                try {
                    voice.play(file)
                } finally {
                    runCatching { file.delete() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = e
            queue.cancel()
        }
    }

    @Synchronized
    fun feed(piece: String) {
        for (phrase in chunker.feed(piece)) enqueue(phrase)
    }

    @Synchronized
    private fun enqueue(phrase: String) {
        started = true
        val n = index++
        queue.trySend(scope.async(Dispatchers.IO) { runCatching { voice.prepare(phrase, n) } })
    }

    /** The text has ended: speak what is left and wait until everything has been played. */
    suspend fun finish() {
        synchronized(this) {
            for (phrase in chunker.flush()) enqueue(phrase)
            queue.close()
        }
        player.join()
        failure?.let { throw it }
    }

    fun cancel() {
        queue.cancel()
        player.cancel()
    }
}
