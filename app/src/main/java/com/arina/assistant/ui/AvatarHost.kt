package com.arina.assistant.ui

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.mutableStateOf
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One 3D avatar page (assets/avatar/index.html, TalkingHead + HeadAudio) shared by the app screen and the
 * floating window. Whoever is on screen attaches [webView]. Voice audio is sent into the page so the mouth
 * moves with the sound.
 */
class AvatarHost(private val app: Application) {

    private val main = Handler(Looper.getMainLooper())

    /** True when the page and its lip-sync engine have started. */
    val ready = mutableStateOf(false)

    /** Last problem reported by the page, for the on-screen notice. */
    val problem = mutableStateOf<String?>(null)

    /** Identifies the composable that currently shows the WebView (see AvatarWebView). */
    var owner: Any? = null

    private var wantedAvatar: String? = null
    private var wantedView = "upper"
    private var loadedAvatar: String? = null
    private var speech: CancellableContinuation<Unit>? = null

    val webView: WebView by lazy { create() }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(): WebView {
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(app))
            .build()
        return WebView(app).apply {
            setBackgroundColor(0xFF020817.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val r = loader.shouldInterceptRequest(request.url) ?: return null
                    // Module scripts must be served with a JavaScript type.
                    val path = request.url.path.orEmpty()
                    if (path.endsWith(".mjs") || path.endsWith(".js")) r.mimeType = "text/javascript"
                    return r
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        problem.value = m.message().take(200)
                    }
                    return true
                }
            }
            addJavascriptInterface(Bridge(), "AndroidBridge")
            loadUrl("https://appassets.androidplatform.net/assets/avatar/index.html")
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun ready() = main.post {
            ready.value = true
            pushState()
        }

        @JavascriptInterface
        fun avatarReady(key: String) = main.post { loadedAvatar = key }

        @JavascriptInterface
        fun speechEnded() = main.post {
            speech?.let { if (it.isActive) it.resume(Unit) }
            speech = null
        }

        @JavascriptInterface
        fun error(message: String) = main.post { problem.value = message.take(200) }
    }

    private fun js(code: String) {
        webView.evaluateJavascript(code, null)
    }

    private fun pushState() {
        js("Arina.view('$wantedView')")
        val a = wantedAvatar
        if (a != null && a != loadedAvatar) js("Arina.load('$a')")
    }

    fun setAvatar(key: String) {
        wantedAvatar = key
        if (ready.value) pushState()
    }

    fun setView(view: String) {
        wantedView = view
        if (ready.value) js("Arina.view('$view')")
    }

    /** True when speech can be played through the avatar (page running and visible somewhere). */
    fun canSpeak(): Boolean = ready.value && webView.isAttachedToWindow && loadedAvatar != null

    /** Plays MP3 [bytes] through the avatar and returns when they finish. */
    suspend fun play(bytes: ByteArray) {
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        withTimeoutOrNull(60_000L) {
            suspendCancellableCoroutine<Unit> { cont ->
                speech = cont
                cont.invokeOnCancellation { main.post { js("Arina.stop()") } }
                js("Arina.play('$b64')")
            }
        }
        speech = null
    }

    fun stop() {
        speech?.let { if (it.isActive) it.resume(Unit) }
        speech = null
        if (ready.value) js("Arina.stop()")
    }
}
