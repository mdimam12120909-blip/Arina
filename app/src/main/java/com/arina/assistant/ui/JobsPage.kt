package com.arina.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Every big task Entrion worked on: its steps, result and a Retry button. */
@Composable
fun JobsPage(vm: ArinaEngine) {
    if (vm.jobs.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No tasks yet", color = ArinaColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("Big tasks you give Entrion appear here with every step and the result.", color = ArinaColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(vm.jobs.toList(), key = { it.id }) { j ->
            var open by remember { mutableStateOf(false) }
            val color = when (j.status) { "done" -> Color(0xFF1E9E5A); "running" -> EntrionBlue; "stopped" -> ArinaColors.Muted; else -> Color(0xFFE5484D) }
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).clickable { open = !open }.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(j.goal, color = ArinaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = if (open) 6 else 2, modifier = Modifier.weight(1f))
                    Text(
                        j.status.replaceFirstChar { it.uppercase() }, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
                if (open) {
                    Spacer(Modifier.height(8.dp))
                    j.steps.takeLast(12).forEach { Text("• $it", color = ArinaColors.Muted, fontSize = 12.sp, lineHeight = 17.sp) }
                    if (j.result.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(j.result.take(900), color = ArinaColors.Text, fontSize = 12.5.sp, lineHeight = 18.sp)
                    }
                    if (j.status != "running") {
                        Spacer(Modifier.height(10.dp))
                        PillButton("Run again") { vm.retryJob(j) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)); PillButton("Clear list") { vm.clearJobs() }; Spacer(Modifier.height(20.dp)) }
    }
}
