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
import androidx.compose.material.icons.outlined.Shield
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

/** A system setting the user must switch on by hand. [intent] opens the right settings page; [granted] says when to close the card. */
class PermRequest(val title: String, val detail: String, val intent: android.content.Intent, val granted: () -> Boolean)

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
    val perm = engine.permAsk.value
    if (approval == null && card == null && perm == null && !engine.agentActive.value) return
    val ctx = LocalContext.current
    // Close the card by itself once the user has switched the setting on.
    if (perm != null) LaunchedEffect(perm) {
        while (!perm.granted()) delay(700)
        engine.permAsk.value = null
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (perm != null) PermissionRequestCard(perm,
            onOpen = { runCatching { ctx.startActivity(perm.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } },
            onDismiss = { engine.permAsk.value = null })
        if (approval != null) ApprovalCard(approval) { engine.answerApproval(it) }
        if (engine.agentActive.value) AgentBar(engine.agentLine.value) { engine.stopAgent() }
        if (card != null) PresentCard(card) { engine.closeCard() }
    }
}

private fun panel(strong: Boolean = false): Modifier =
    Modifier
        .clip(RoundedCornerShape(28.dp))
        .background(ArinaColors.Surface.copy(alpha = 0.99f))
        .border(if (strong) 1.5.dp else 1.dp, Brand, RoundedCornerShape(28.dp))

private val Brand = Brush.linearGradient(listOf(Color(0xFF4F7DFF), Color(0xFF6D5BFF), Color(0xFF9B5CFF)))

/** The round badge at the top of a permission card. */
@Composable
private fun Badge(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Brand), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun CardButtons(primary: String, secondary: String, onPrimary: () -> Unit, onSecondary: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            secondary, color = ArinaColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).border(1.dp, ArinaColors.Muted.copy(alpha = 0.4f), RoundedCornerShape(50))
                .clickable(onClick = onSecondary).padding(vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            primary, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            modifier = Modifier.weight(1.3f).clip(RoundedCornerShape(50)).background(Brand)
                .clickable(onClick = onPrimary).padding(vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun ApprovalCard(a: Approval, onAnswer: (Boolean) -> Unit) {
    Column(panel(strong = true).fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(Icons.Outlined.Shield)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("ENTRION NEEDS YOUR OK", color = ArinaColors.Glow, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                Text(a.title, color = ArinaColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            }
        }
        Text(
            a.detail, color = ArinaColors.Muted, fontSize = 13.sp, lineHeight = 19.sp,
            modifier = Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())
        )
        CardButtons("Allow", "Not now", { onAnswer(true) }, { onAnswer(false) })
    }
}

/** Entrion needs an Android setting that only the user can switch on (Accessibility, display over apps...). Works over any app. */
@Composable
private fun PermissionRequestCard(r: PermRequest, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Column(panel(strong = true).fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(Icons.Outlined.Shield)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("PERMISSION NEEDED", color = ArinaColors.Glow, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                Text(r.title, color = ArinaColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            }
        }
        Text(r.detail, color = ArinaColors.Muted, fontSize = 13.sp, lineHeight = 19.sp)
        CardButtons("Open settings", "Not now", onOpen, onDismiss)
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
                Modifier.size(34.dp).clip(CircleShape).background(ArinaColors.Muted.copy(alpha = 0.2f)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Close, "Close", tint = ArinaColors.Text, modifier = Modifier.size(20.dp)) }
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
