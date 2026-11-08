package com.arina.assistant.assist

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.arina.assistant.MainActivity
import com.arina.assistant.ui.ArinaColors
import com.arina.assistant.ui.ArinaEngine
import com.arina.assistant.ui.EntrionBlue
import com.arina.assistant.ui.EntrionMark
import com.arina.assistant.ui.EntrionViolet
import com.arina.assistant.ui.GlassField

/**
 * Makes Entrion selectable as the phone's default digital assistant. A long press on Home opens a small Entrion
 * pop-up at the bottom (like other assistants); pulling it up opens the full app.
 */
class EntrionAssistService : VoiceInteractionService()

class EntrionAssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = EntrionAssistSession(this)
}

class EntrionAssistSession(context: Context) : VoiceInteractionSession(context), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    init {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    private fun openApp() {
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_FROM_ASSIST, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try { startAssistantActivity(open) } catch (_: Throwable) { runCatching { context.startActivity(open) } }
        finish()
    }

    override fun onCreateContentView(): View {
        val view = ComposeView(context)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        (window as? android.app.Dialog)?.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        view.setContent { AssistPopup(onOpenApp = { openApp() }, onClose = { finish() }) }
        return view
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        registry.currentState = Lifecycle.State.RESUMED
    }

    override fun onHide() {
        registry.currentState = Lifecycle.State.CREATED
        super.onHide()
    }

    override fun onDestroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }
}

@Composable
private fun AssistPopup(onOpenApp: () -> Unit, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val engine = remember { ArinaEngine.get(ctx) }
    var text by remember { mutableStateOf("") }
    var drag by remember { mutableFloatStateOf(0f) }
    val hasMic = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val ready = engine.signedIn.value && !engine.isAdmin.value

    androidx.compose.runtime.LaunchedEffect(Unit) {
        // like other assistants: start listening at once when allowed, otherwise the person can type or open the app
        if (ready && hasMic && !engine.micOn.value) engine.onMicTap()
    }

    Box(
        Modifier.fillMaxSize().background(Color(0x66000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)
                .graphicsLayer { translationY = drag.coerceAtMost(0f) }
                .clip(RoundedCornerShape(28.dp)).background(Color(0xFFF7F8FD))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (drag < -160f) onOpenApp() else drag = 0f },
                        onDragCancel = { drag = 0f }
                    ) { _, d -> drag += d }
                }
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(Color(0x33000000)))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(EntrionBlue, EntrionViolet))),
                    contentAlignment = Alignment.Center
                ) { EntrionMark(Modifier.size(32.dp), color = Color.White, bars = if (engine.micOn.value) 1f else 0.4f) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (!ready) "Open Entrion to sign in" else if (engine.micOn.value) "Listening…" else "How can I help?",
                        color = Color(0xFF0E1330), fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text("Pull up to open Entrion", color = Color(0xFF8A90A8), fontSize = 12.sp)
                }
            }
            if (ready) {
                Spacer(Modifier.height(14.dp))
                GlassField(text, { text = it }, "Or type to Entrion")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (engine.micOn.value) "Stop mic" else "Speak", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Brush.linearGradient(listOf(EntrionBlue, EntrionViolet)))
                            .clickable { if (hasMic) engine.onMicTap() else onOpenApp() }.padding(horizontal = 22.dp, vertical = 10.dp)
                    )
                    if (text.isNotBlank()) Text(
                        "Send", color = Color(0xFF4F5BFF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x1A4F7DFF))
                            .clickable { engine.sendText(text.trim()); text = ""; onClose() }.padding(horizontal = 22.dp, vertical = 10.dp)
                    )
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Open", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Brush.linearGradient(listOf(EntrionBlue, EntrionViolet)))
                        .clickable { onOpenApp() }.padding(horizontal = 28.dp, vertical = 10.dp)
                )
            }
        }
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
