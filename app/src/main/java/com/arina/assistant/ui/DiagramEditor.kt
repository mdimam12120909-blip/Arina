package com.arina.assistant.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arina.assistant.ai.DNode
import com.arina.assistant.ai.DiagramData

private val NodeW = 130.dp
private val NodeH = 48.dp

/** Full-screen diagram: drag boxes, rename the selected one, add or delete boxes. Every change is saved. */
@Composable
fun DiagramEditor(engine: ArinaEngine, data: DiagramData) {
    val ctx = LocalContext.current
    val dens = LocalDensity.current
    var selected by remember(data) { mutableStateOf<DNode?>(null) }
    fun save() = DiagramData.save(ctx, data)

    Column(Modifier.fillMaxSize().background(ArinaColors.Background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(data.title, color = ArinaColors.Text, fontSize = 17.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            DiaButton(Icons.Outlined.Add, "Add box") {
                val n = data.add("New idea", 20f + (data.nodes.size % 4) * 30f, 20f + (data.nodes.size % 5) * 60f)
                selected?.let { data.connect(it.id, n.id) }
                selected = n; save()
            }
            Spacer(Modifier.width(8.dp))
            DiaButton(Icons.Outlined.Delete, "Delete box") { selected?.let { data.remove(it.id); selected = null; save() } }
            Spacer(Modifier.width(8.dp))
            DiaButton(Icons.Outlined.SaveAlt, "Save picture") {
                val ok = DiagramData.savePng(ctx, data)
                Toast.makeText(ctx, if (ok) "Saved to Pictures/Entrion" else "Could not save the picture", Toast.LENGTH_SHORT).show()
            }
            Spacer(Modifier.width(8.dp))
            DiaButton(Icons.Outlined.Close, "Close") { save(); engine.diagram.value = null }
        }
        Box(Modifier.weight(1f).fillMaxWidth().horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
            Box(Modifier.size(1400.dp, 1400.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = NodeW.toPx(); val h = NodeH.toPx()
                    data.edges.forEach { e ->
                        val a = data.nodes.firstOrNull { it.id == e.from } ?: return@forEach
                        val b = data.nodes.firstOrNull { it.id == e.to } ?: return@forEach
                        drawLine(Color(0xFF6C8CFF), Offset(a.x.dp.toPx() + w / 2, a.y.dp.toPx() + h / 2), Offset(b.x.dp.toPx() + w / 2, b.y.dp.toPx() + h / 2), strokeWidth = 2.dp.toPx())
                    }
                }
                data.nodes.forEach { n ->
                    val on = selected?.id == n.id
                    Box(
                        Modifier.offset(n.x.dp, n.y.dp).size(NodeW, NodeH)
                            .clip12().background(if (on) ArinaColors.Glow else ArinaColors.Surface)
                            .border(1.5.dp, ArinaColors.Glow, RoundedCornerShape(12.dp))
                            .pointerInput(n.id) {
                                detectDragGestures(
                                    onDragStart = { selected = n },
                                    onDragEnd = { save() }
                                ) { change, drag ->
                                    change.consume()
                                    n.x = (n.x + drag.x / dens.density).coerceAtLeast(0f)
                                    n.y = (n.y + drag.y / dens.density).coerceAtLeast(0f)
                                }
                            }
                            .clickable { selected = n },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(n.text, color = if (on) Color.White else ArinaColors.Text, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
                    }
                }
            }
        }
        val s = selected
        if (s != null) {
            OutlinedTextField(
                value = s.text, onValueChange = { s.text = it.take(60); save() },
                label = { Text("Rename the selected box") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(14.dp)
            )
        } else {
            Text("Drag a box to move it. Tap one to rename it. You can also tell Entrion to change this diagram.", color = ArinaColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(14.dp))
        }
    }
}

private fun Modifier.clip12() = this.then(Modifier.clip(RoundedCornerShape(12.dp)))

@Composable
private fun DiaButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(ArinaColors.Surface).border(1.dp, ArinaColors.Deep, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = ArinaColors.Text, modifier = Modifier.size(20.dp))
    }
}
