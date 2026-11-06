package com.arina.assistant.assist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import com.arina.assistant.MainActivity

/**
 * Makes Entrion selectable as the phone's default digital assistant, so a long press on Home (or the power button,
 * depending on the phone) opens Entrion the way it opens Gemini.
 */
class EntrionAssistService : VoiceInteractionService()

class EntrionAssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = EntrionAssistSession(this)
}

class EntrionAssistSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_FROM_ASSIST, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try { startAssistantActivity(open) } catch (_: Throwable) { runCatching { context.startActivity(open) } }
        finish()
    }
}

/** Android only lists a voice-interaction app as an assistant when it also names a recognition service. It is never used for real. */
class EntrionRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        try { listener?.error(SpeechRecognizer.ERROR_CLIENT) } catch (_: Throwable) { }
    }
    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}
