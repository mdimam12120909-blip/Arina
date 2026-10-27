package com.arina.assistant.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Loads pictures for the cards: web addresses, phone files and PDF pages. */
private object Pics {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun decode(bytes: ByteArray, maxPx: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (o.outWidth / sample > maxPx * 2 || o.outHeight / sample > maxPx * 2) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun load(ctx: android.content.Context, src: String, maxPx: Int): Bitmap? {
        cache.get(src)?.let { return it }
        val bmp: Bitmap? = try {
            when {
                src.startsWith("http") -> {
                    val req = Request.Builder().url(src).header("User-Agent", "Mozilla/5.0 (Android) AriAuri").build()
                    http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) null else {
                            val b = r.body?.bytes()
                            if (b == null || b.size > 12_000_000) null else decode(b, maxPx)
                        }
                    }
                }
                src.startsWith("pdf:") -> {
                    val path = src.removePrefix("pdf:").substringBefore('|')
                    val page = src.substringAfter('|', "0").toIntOrNull() ?: 0
                    ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                        PdfRenderer(fd).use { pr ->
                            if (page >= pr.pageCount) null else pr.openPage(page).use { pg ->
                                val scale = maxPx.toFloat() / pg.width
                                val b = Bitmap.createBitmap(maxPx, (pg.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                                b.eraseColor(android.graphics.Color.WHITE)
                                pg.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                b
                            }
                        }
                    }
                }
                else -> {
                    val uri = if (src.startsWith("/")) Uri.fromFile(File(src)) else Uri.parse(src)
                    ctx.contentResolver.openInputStream(uri)?.use { decode(it.readBytes(), maxPx) }
                }
            }
        } catch (e: Exception) {
            null
        }
        if (bmp != null) cache.put(src, bmp)
        return bmp
    }
}

private val Good = Color(0xFF3DDC97)
private val Bad = Color(0xFFFF5C6C)

/** The card itself. Shown both inside the app and in the floating window over other apps. */
@Composable
fun PanelCard(engine: ArinaEngine, modifier: Modifier = Modifier) {
    val panel = engine.panel.value ?: return
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xF2041634), Color(0xF2020817))))
            .border(1.dp, Brush.linearGradient(listOf(ArinaColors.Glow, ArinaColors.Deep)), shape)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                panel.title, color = ArinaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, modifier = Modifier.weight(1f)
            )
            val left = engine.panelLeft.intValue
            if (left > 0) Text("${left}s", color = ArinaColors.Muted, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable { engine.dismissPanel() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Close, "Close", tint = ArinaColors.Text, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        when (panel) {
            is Panel.Images -> ImagesBody(panel)
            is Panel.Link -> LinkBody(panel)
            is Panel.Body -> Text(
                panel.text, color = ArinaColors.Text, fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())
            )
            is Panel.Ask -> AskBody(panel, engine)
            is Panel.Working -> {
                Text(panel.line.value, color = ArinaColors.Text, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                Pill("Stop", Bad) { panel.onStop() }
            }
        }
    }
}

@Composable
private fun ImagesBody(panel: Panel.Images) {
    val ctx = LocalContext.current
    val pager = rememberPagerState { panel.sources.size }
    Box(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF020817))) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val src = panel.sources[page]
            val bmp by produceState<Bitmap?>(null, src) { value = withContext(Dispatchers.IO) { Pics.load(ctx, src, 1400) } }
            val b = bmp
            if (b != null) {
                Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", color = ArinaColors.Muted, fontSize = 12.sp)
                }
            }
        }
        if (panel.sources.size > 1) {
            Text(
                "${pager.currentPage + 1} / ${panel.sources.size}", color = ArinaColors.Text, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp)
                    .clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LinkBody(panel: Panel.Link) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(14.dp)),
        factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                loadUrl(panel.url)
            }
        },
        onRelease = { it.destroy() }
    )
}

@Composable
private fun AskBody(panel: Panel.Ask, engine: ArinaEngine) {
    Text(
        panel.detail, color = ArinaColors.Text, fontSize = 13.sp, lineHeight = 18.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(10.dp))
            .background(Color(0x33000000)).verticalScroll(rememberScrollState()).padding(10.dp)
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pill("Allow", Good) { engine.answerAsk(panel, true) }
        Pill("Deny", Bad) { engine.answerAsk(panel, false) }
    }
}

@Composable
private fun Pill(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text, color = Color(0xFF020817), fontSize = 14.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color).clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 9.dp)
    )
}
