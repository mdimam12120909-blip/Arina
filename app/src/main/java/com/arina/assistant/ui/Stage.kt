package com.arina.assistant.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** What a card on the stage shows. Images are always local files or content:// addresses. */
enum class CardKind { Images, Text, Web }

class Card(
    val kind: CardKind,
    val title: String,
    val images: List<String> = emptyList(),
    val text: String = "",
    val url: String = "",
    /** Seconds the card stays; 0 = until closed. */
    val seconds: Int = 60
)

/** A big action waiting for the user's yes or no. */
class Approval(val title: String, val detail: String, val answer: CompletableDeferred<Boolean>)

private val Good = Color(0xFF3DDC97)
private val Bad = Color(0xFFFF5C6C)

/** Decodes an image file or content address, scaled down so it never blows up memory. */
suspend fun loadBitmap(ctx: android.content.Context, src: String, max: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val uri = if (src.startsWith("content:")) Uri.parse(src) else Uri.fromFile(java.io.File(src))
        fun open() = ctx.contentResolver.openInputStream(uri)!!
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > max || bounds.outHeight / sample > max) sample *= 2
        open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }.getOrNull()
}

@Composable
private fun StageImage(src: String) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, src) { value = loadBitmap(ctx, src) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Text("Loading…", color = ArinaColors.Muted, fontSize = 13.sp)
    }
}

/** The popup layer: an approval question and/or a presented card. Used in the app and in the floating window. */
@Composable
fun Stage(engine: ArinaEngine, modifier: Modifier = Modifier) {
    val approval = engine.approval.value
    val card = engine.card.value
    if (approval == null && card == null && !engine.agentActive.value) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (approval != null) ApprovalCard(approval) { engine.answerApproval(it) }
        if (engine.agentActive.value) AgentBar(engine.agentLine.value) { engine.stopAgent() }
        if (card != null) PresentCard(card) { engine.closeCard() }
    }
}

private fun panel(strong: Boolean = false): Modifier =
    Modifier
        .clip(RoundedCornerShape(22.dp))
        .background(Brush.verticalGradient(if (strong) listOf(Color(0xF00B2A55), Color(0xF2041634)) else listOf(Color(0xEE0A1E40), Color(0xF2041020))))
        .border(1.dp, Brush.linearGradient(listOf(ArinaColors.Glow.copy(alpha = 0.7f), ArinaColors.Deep.copy(alpha = 0.2f))), RoundedCornerShape(22.dp))

@Composable
private fun ApprovalCard(a: Approval, onAnswer: (Boolean) -> Unit) {
    Column(panel(strong = true).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("PERMISSION NEEDED", color = ArinaColors.Glow, fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
        Text(a.title, color = ArinaColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            a.detail, color = ArinaColors.Muted, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Allow", color = ArinaColors.Background, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(Good)
                    .clickable { onAnswer(true) }.padding(vertical = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                "Deny", color = ArinaColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .border(1.dp, Bad, RoundedCornerShape(50))
                    .clickable { onAnswer(false) }.padding(vertical = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun AgentBar(line: String, onStop: () -> Unit) {
    Row(panel().fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFFB84D)))
        Spacer(Modifier.width(10.dp))
        Text(line.ifBlank { "Working on the phone…" }, color = ArinaColors.Text, fontSize = 13.sp, maxLines = 2, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(
            "STOP", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Bad).clickable(onClick = onStop).padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PresentCard(card: Card, onClose: () -> Unit) {
    var left by remember(card) { mutableLongStateOf(card.seconds.toLong()) }
    LaunchedEffect(card) {
        if (card.seconds > 0) {
            while (left > 0) { delay(1000); left-- }
            onClose()
        }
    }
    Column(panel().fillMaxWidth().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.title, color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            if (card.seconds > 0) Text("${left}s", color = ArinaColors.Muted, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF020817))) {
            when (card.kind) {
                CardKind.Images -> {
                    val pager = rememberPagerState { card.images.size }
                    HorizontalPager(pager, Modifier.fillMaxSize()) { StageImage(card.images[it]) }
                    if (card.images.size > 1) {
                        Row(Modifier.align(Alignment.BottomCenter).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(card.images.size) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(if (it == pager.currentPage) ArinaColors.Glow else Color(0x55FFFFFF)))
                            }
                        }
                    }
                }
                CardKind.Text -> Text(
                    card.text, color = ArinaColors.Text, fontSize = 14.sp, lineHeight = 20.sp,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)
                )
                CardKind.Web -> AndroidView(
                    factory = { c ->
                        WebView(c).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            loadUrl(card.url)
                        }
                    },
                    onRelease = { it.destroy() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
