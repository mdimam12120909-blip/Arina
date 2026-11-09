package com.arina.assistant.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private val iconHttp = OkHttpClient()
private val iconCache = HashMap<String, ImageBitmap?>()

/** A site's own icon, fetched once and kept for the session; null while loading or if the site has none. */
@Composable
private fun rememberFavicon(domain: String): ImageBitmap? {
    var bmp by remember(domain) { mutableStateOf(iconCache[domain]) }
    LaunchedEffect(domain) {
        if (iconCache.containsKey(domain)) { bmp = iconCache[domain]; return@LaunchedEffect }
        val got = withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url("https://www.google.com/s2/favicons?domain=$domain&sz=128").build()
                iconHttp.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } else null }
            }.getOrNull()
        }
        iconCache[domain] = got
        bmp = got
    }
    return bmp
}

@Composable
private fun Tile(label: String, image: ImageBitmap?) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (image == null) Brush.linearGradient(listOf(EntrionBlue, EntrionViolet)) else Brush.linearGradient(listOf(Color.White, Color.White))), contentAlignment = Alignment.Center) {
        if (image != null) Image(image, label, Modifier.size(30.dp))
        else Text(label.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun SoftField(value: String, hint: String, onChange: (String) -> Unit) {
    GlassField(value, onChange, hint)
}

/** Connectors: the apps on this phone and the websites Entrion may use. */
@Composable
fun ConnectorsPage(vm: ArinaEngine) {
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(4.dp)) {
            listOf("Device apps", "Websites", "MCP").forEachIndexed { i, n ->
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (tab == i) Brush.linearGradient(listOf(EntrionBlue, EntrionViolet)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                        .clickable { tab = i }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) { Text(n, color = if (tab == i) Color.White else ArinaColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Spacer(Modifier.height(10.dp))
        when (tab) {
            0 -> DeviceApps(vm)
            1 -> Websites(vm)
            else -> McpServers(vm)
        }
    }
}

@Composable
private fun DeviceApps(vm: ArinaEngine) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    var query by remember { mutableStateOf("") }
    val off = remember { mutableStateListOf<String>().also { it.addAll(vm.uiPrefs.getStringSet("conn_disabled", emptySet()).orEmpty()) } }
    val apps = remember {
        val q = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(q, 0).filter { it.activityInfo.packageName != ctx.packageName }
            .map { Triple(it.loadLabel(pm).toString(), it.activityInfo.packageName, runCatching { it.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull()) }
            .distinctBy { it.second }.sortedBy { it.first.lowercase() }
    }
    val shown = apps.filter { query.isBlank() || it.first.contains(query, true) }
    SoftField(query, "Search apps") { query = it }
    Spacer(Modifier.height(8.dp))
    Text("ON THIS PHONE · ${apps.size}", color = ArinaColors.Muted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
    Spacer(Modifier.height(6.dp))
    LazyColumn(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp))) {
        items(shown, key = { it.second }) { (label, pkg, icon) ->
            val allowed = pkg !in off
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) Image(icon, label, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))) else Tile(label, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, color = ArinaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(if (allowed) "Entrion may open and use it" else "Blocked for Entrion", color = ArinaColors.Muted, fontSize = 11.sp)
                }
                Switch(
                    checked = allowed,
                    onCheckedChange = { on ->
                        if (on) off.remove(pkg) else off.add(pkg)
                        vm.uiPrefs.edit().putStringSet("conn_disabled", off.toSet()).apply()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = EntrionBlue, checkedThumbColor = Color.White)
                )
            }
        }
    }
}

@Composable
private fun Websites(vm: ArinaEngine) {
    var url by remember { mutableStateOf("") }
    val sites = remember { mutableStateListOf<String>().also { it.addAll(vm.uiPrefs.getStringSet("conn_sites", emptySet()).orEmpty().sorted()) } }
    fun save() = vm.uiPrefs.edit().putStringSet("conn_sites", sites.toSet()).apply()
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add a website", color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Entrion can read it, or work on it for you. It asks before it signs in or changes anything.", color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 17.sp)
                SoftField(url, "example.com") { url = it }
                PillButton("Add website") {
                    val host = url.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/').lowercase()
                    if (host.contains('.') && host !in sites) { sites.add(host); sites.sort(); save(); url = "" }
                }
            }
        }
        item { Text("YOUR WEBSITES · ${sites.size}", color = ArinaColors.Muted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp)) }
        if (sites.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp))) {
                sites.toList().forEachIndexed { i, host ->
                    if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 66.dp).height(1.dp).background(ArinaColors.Muted.copy(alpha = 0.14f)))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Tile(host, rememberFavicon(host))
                        Spacer(Modifier.width(12.dp))
                        Text(host, color = ArinaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("Remove", color = ArinaColors.Muted, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { sites.remove(host); save() }.padding(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun McpServers(vm: ArinaEngine) {
    val store = remember { com.arina.assistant.ai.McpStore(vm.uiPrefs) }
    val list = remember { mutableStateListOf<com.arina.assistant.ai.McpServer>().also { it.addAll(store.load()) } }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connect an MCP server", color = ArinaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Paste the server's web address. Entrion reads its tools and can use them when you ask. Add a key only if the server needs one.", color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 17.sp)
                SoftField(name, "Name, e.g. Notes") { name = it }
                SoftField(url, "https://example.com/mcp") { url = it }
                SoftField(token, "Access key (optional)") { token = it }
                PillButton(if (busy) "Checking…" else "Connect") {
                    if (!busy && name.isNotBlank() && url.trim().startsWith("http")) {
                        busy = true; status = ""
                        val sv = com.arina.assistant.ai.McpServer(name.trim(), url.trim(), token.trim())
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { vm.mcp.test(sv) }
                            if (r.startsWith("OK")) {
                                list.removeAll { it.name == sv.name }; list.add(sv); store.save(list.toList())
                                name = ""; url = ""; token = ""; vm.refreshMcp()
                            }
                            status = r; busy = false
                        }
                    }
                }
                if (status.isNotBlank()) Text(status, color = if (status.startsWith("OK")) ArinaColors.Glow else ArinaColors.Muted, fontSize = 12.sp)
            }
        }
        item { Text("YOUR MCP SERVERS · ${list.size}", color = ArinaColors.Muted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp)) }
        if (list.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp))) {
                list.toList().forEachIndexed { i, sv ->
                    if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(ArinaColors.Muted.copy(alpha = 0.14f)))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sv.name, color = ArinaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(sv.url, color = ArinaColors.Muted, fontSize = 11.sp, maxLines = 1)
                        }
                        Text("Remove", color = ArinaColors.Muted, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { list.remove(sv); store.save(list.toList()); vm.refreshMcp() }.padding(8.dp))
                    }
                }
            }
        }
    }
}
