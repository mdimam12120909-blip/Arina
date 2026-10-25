package com.arina.assistant.ui

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Shows the shared 3D avatar page. Only one place can show it at a time; the newest one takes it.
 * While the page is still loading, Arina's still portrait is shown instead.
 */
@Composable
fun AvatarWebView(engine: ArinaEngine, view: String, modifier: Modifier = Modifier) {
    val host = engine.avatar
    val token = remember { Any() }
    Box(modifier) {
        AndroidView(
            factory = {
                val wv = host.webView
                (wv.parent as? ViewGroup)?.removeView(wv)
                host.owner = token
                host.setView(view)
                host.setAvatar(engine.persona.value.avatarKey)
                wv
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { wv ->
                if (host.owner === token) {
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    host.owner = null
                }
            }
        )
        if (!host.ready.value && engine.persona.value == Persona.Arina) {
            AvatarView(
                status = engine.status.value,
                level = engine.level.floatValue,
                compact = view == "head",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
