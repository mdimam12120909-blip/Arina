package com.arina.assistant.ai

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class DNode(val id: Int, text: String, x: Float, y: Float) {
    var text by mutableStateOf(text)
    var x by mutableFloatStateOf(x)
    var y by mutableFloatStateOf(y)
}

class DEdge(val from: Int, val to: Int)

/** A diagram the user can edit by hand and Entrion can change by voice. Positions are in dp. */
class DiagramData(title: String) {
    var title by mutableStateOf(title)
    val nodes = mutableStateListOf<DNode>()
    val edges = mutableStateListOf<DEdge>()
    private var nextId = 1

    fun find(name: String) = nodes.firstOrNull { it.text.trim().equals(name.trim(), ignoreCase = true) }
    fun add(text: String, x: Float, y: Float): DNode = DNode(nextId++, text, x, y).also { nodes.add(it) }
    fun connect(a: Int, b: Int) { if (a != b && edges.none { it.from == a && it.to == b }) edges.add(DEdge(a, b)) }
    fun remove(id: Int) { nodes.removeAll { it.id == id }; edges.removeAll { it.from == id || it.to == id } }

    /**
     * Outline format: one idea per line, two spaces of indent = child of the line above.
     * "A -> B" draws an arrow between two named boxes (made if missing).
     * Only new boxes are placed; existing ones stay where the user put them.
     */
    fun addOutline(outline: String) {
        val fresh = HashSet<Int>()
        fun get(name: String): DNode { find(name)?.let { return it }; return add(name.trim().take(60), 0f, 0f).also { fresh += it.id } }
        val stack = ArrayList<Pair<Int, DNode>>()
        for (raw in outline.lines()) {
            if (raw.isBlank()) continue
            val indent = raw.takeWhile { it == ' ' || it == '\t' }.sumOf { if (it == '\t') 2 else 1 } / 2
            val line = raw.trim().trimStart('-', '*', '•').trim()
            if (line.contains("->")) {
                val chain = line.split("->").map { it.trim() }.filter { it.isNotEmpty() }
                for (i in 0 until chain.size - 1) connect(get(chain[i]).id, get(chain[i + 1]).id)
                continue
            }
            val n = get(line)
            while (stack.isNotEmpty() && stack.last().first >= indent) stack.removeAt(stack.size - 1)
            stack.lastOrNull()?.let { connect(it.second.id, n.id) }
            stack += indent to n
        }
        layout(fresh)
    }

    private fun layout(only: Set<Int>) {
        if (only.isEmpty()) return
        val depth = HashMap<Int, Int>()
        nodes.forEach { depth[it.id] = 0 }
        repeat(nodes.size.coerceAtMost(30)) { edges.forEach { e -> val d = (depth[e.from] ?: 0) + 1; if (d > (depth[e.to] ?: 0) && d < 30) depth[e.to] = d } }
        val rows = HashMap<Int, Int>()
        nodes.forEach { n -> if (n.id !in only) rows[depth[n.id] ?: 0] = maxOf(rows[depth[n.id] ?: 0] ?: 0, ((n.y - 20f) / 80f).toInt() + 1) }
        for (n in nodes) if (n.id in only) {
            val d = depth[n.id] ?: 0
            val r = rows[d] ?: 0
            rows[d] = r + 1
            n.x = 20f + d * 170f; n.y = 20f + r * 80f
        }
    }

    fun toJson(): String = JSONObject().put("title", title)
        .put("nodes", JSONArray().also { a -> nodes.forEach { a.put(JSONObject().put("id", it.id).put("t", it.text).put("x", it.x.toDouble()).put("y", it.y.toDouble())) } })
        .put("edges", JSONArray().also { a -> edges.forEach { a.put(JSONArray().put(it.from).put(it.to)) } }).toString()

    fun outlineText(): String = nodes.joinToString("; ") { it.text } + (if (edges.isEmpty()) "" else ". Links: " +
        edges.joinToString("; ") { e -> "${nodes.firstOrNull { it.id == e.from }?.text} -> ${nodes.firstOrNull { it.id == e.to }?.text}" })

    companion object {
        fun fromJson(s: String): DiagramData? = runCatching {
            val o = JSONObject(s)
            val d = DiagramData(o.optString("title", "Diagram"))
            val a = o.getJSONArray("nodes")
            var max = 0
            for (i in 0 until a.length()) { val n = a.getJSONObject(i); val id = n.getInt("id"); max = maxOf(max, id); d.nodes.add(DNode(id, n.getString("t"), n.getDouble("x").toFloat(), n.getDouble("y").toFloat())) }
            d.nextId = max + 1
            val e = o.getJSONArray("edges")
            for (i in 0 until e.length()) d.edges.add(DEdge(e.getJSONArray(i).getInt(0), e.getJSONArray(i).getInt(1)))
            d
        }.getOrNull()

        private fun file(ctx: Context) = File(ctx.filesDir, "diagram.json")
        fun load(ctx: Context): DiagramData? = runCatching { fromJson(file(ctx).readText()) }.getOrNull()
        fun save(ctx: Context, d: DiagramData) { runCatching { file(ctx).writeText(d.toJson()) } }

        /** Draws the diagram into a PNG in Pictures/Entrion. Returns true when saved. */
        fun savePng(ctx: Context, d: DiagramData): Boolean = runCatching {
            val k = ctx.resources.displayMetrics.density
            val w = 130f; val h = 48f
            val maxX = (d.nodes.maxOfOrNull { it.x } ?: 0f) + w + 30f
            val maxY = (d.nodes.maxOfOrNull { it.y } ?: 0f) + h + 30f
            val bmp = Bitmap.createBitmap((maxX * k).toInt().coerceAtLeast(300), (maxY * k).toInt().coerceAtLeast(300), Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp); c.drawColor(Color.WHITE)
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4F7DFF.toInt(); strokeWidth = 2f * k; style = Paint.Style.STROKE }
            val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE3ECFF.toInt() }
            val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4F7DFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f * k }
            val tx = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF14182B.toInt(); textSize = 13f * k; textAlign = Paint.Align.CENTER }
            for (e in d.edges) {
                val a = d.nodes.firstOrNull { it.id == e.from } ?: continue; val b = d.nodes.firstOrNull { it.id == e.to } ?: continue
                c.drawLine((a.x + w / 2) * k, (a.y + h / 2) * k, (b.x + w / 2) * k, (b.y + h / 2) * k, line)
            }
            for (n in d.nodes) {
                c.drawRoundRect(n.x * k, n.y * k, (n.x + w) * k, (n.y + h) * k, 12f * k, 12f * k, box)
                c.drawRoundRect(n.x * k, n.y * k, (n.x + w) * k, (n.y + h) * k, 12f * k, 12f * k, edge)
                c.drawText(n.text.take(16), (n.x + w / 2) * k, (n.y + h / 2) * k + 5f * k, tx)
            }
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Entrion_diagram_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Entrion")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)!!
            ctx.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        }.getOrDefault(false)
    }
}
