package com.arina.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** How a listening attempt ended badly. */
enum class SpeechProblem {
    /** Nobody spoke: just listen again. */
    Silence,
    /** Temporary glitch: listen again, but give up after several in a row. */
    Transient,
    /** Needs the user to fix something: stop. */
    Fatal
}

/** Bengali (bn-BD) speech recognition using Android's built-in recognizer. Call from the main thread. */
class SpeechInput(private val ctx: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun listen(
        onLevel: (Float) -> Unit,
        onFinal: (String) -> Unit,
        onProblem: (SpeechProblem, String) -> Unit
    ) {
        cancel()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onProblem(SpeechProblem.Fatal, "Speech recognition is not available. Install or enable the Google app and try again.")
            return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        onProblem(SpeechProblem.Silence, "")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        onProblem(SpeechProblem.Fatal, "Microphone permission is missing.")
                    12, 13 ->
                        onProblem(SpeechProblem.Fatal, "Bengali speech is not available. In the Google app open Settings > Voice > Languages and add Bengali.")
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        onProblem(SpeechProblem.Transient, "Network problem while listening.")
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT ->
                        onProblem(SpeechProblem.Transient, "Speech service is busy.")
                    else ->
                        onProblem(SpeechProblem.Transient, "Listening failed (code $error).")
                }
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                onFinal(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        r.startListening(intent)
    }

    fun cancel() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }
}
