package com.arina.assistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale as dscale

val EntrionBlue = Color(0xFF4F7DFF)
val EntrionIndigo = Color(0xFF6D5BFF)
val EntrionViolet = Color(0xFF9B5CFF)

/**
 * Entrion's mark, one design everywhere (app icon, intro, sign-in): a headset-wearing assistant with a voice
 * wave on its face. Drawn in a 512 space (same geometry as res/drawable/ic_launcher_foreground.xml) and fitted
 * to the canvas. [color] null = blue-violet gradient. [bars] 0..1 pulses the voice bars.
 */
@Composable
fun EntrionMark(modifier: Modifier = Modifier, color: Color? = null, bars: Float = 1f) {
    Canvas(modifier) {
        // visible bounds of the artwork inside the 512 space
        val bx = 91f; val by = 113f; val bw = 330f; val bh = 371f
        val k = minOf(size.width / bw, size.height / bh)
        val ox = (size.width - bw * k) / 2f - bx * k
        val oy = (size.height - bh * k) / 2f - by * k
        translate(ox, oy) {
            dscale(k, k, pivot = Offset.Zero) {
                val brush = Brush.linearGradient(
                    listOf(EntrionBlue, EntrionIndigo, EntrionViolet), Offset(100f, 100f), Offset(420f, 480f)
                )
                fun st(w: Float) = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
                fun arcPath(p: Path.() -> Unit) = Path().apply(p)
                val head = arcPath { moveTo(146f, 236f); arcTo(androidx.compose.ui.geometry.Rect(146f - 0f, 126f, 366f, 346f), 180f, 180f, false) }
                val mic = arcPath { moveTo(132f, 304f); quadraticBezierTo(132f, 356f, 196f, 356f) }
                val shoulders = arcPath { moveTo(122f, 484f); quadraticBezierTo(122f, 404f, 256f, 404f); quadraticBezierTo(390f, 404f, 390f, 484f); close() }
                fun paintAll(b: Brush?, c: Color?) {
                    if (b != null) {
                        drawPath(head, b, style = st(26f))
                        drawRoundRect(b, Offset(172f, 172f), Size(168f, 148f), CornerRadius(50f), style = st(26f))
                        drawPath(mic, b, style = st(14f))
                        drawPath(shoulders, b)
                        drawRoundRect(b, Offset(104f, 214f), Size(42f, 96f), CornerRadius(21f))
                        drawRoundRect(b, Offset(366f, 214f), Size(42f, 96f), CornerRadius(21f))
                        drawCircle(b, 14f, Offset(204f, 356f))
                    } else if (c != null) {
                        drawPath(head, c, style = st(26f))
                        drawRoundRect(c, Offset(172f, 172f), Size(168f, 148f), CornerRadius(50f), style = st(26f))
                        drawPath(mic, c, style = st(14f))
                        drawPath(shoulders, c)
                        drawRoundRect(c, Offset(104f, 214f), Size(42f, 96f), CornerRadius(21f))
                        drawRoundRect(c, Offset(366f, 214f), Size(42f, 96f), CornerRadius(21f))
                        drawCircle(c, 14f, Offset(204f, 356f))
                    }
                }
                paintAll(if (color == null) brush else null, color)
                // voice bars, centred on y=246
                val a = bars.coerceIn(0f, 1f)
                val hs = listOf(40f, 80f, 40f)
                val xs = listOf(214f, 246f, 278f)
                for (i in 0..2) {
                    val h = 20f + (hs[i] - 20f) * a
                    val y = 246f - h / 2f
                    if (color == null) drawRoundRect(brush, Offset(xs[i], y), Size(20f, h), CornerRadius(10f))
                    else drawRoundRect(color, Offset(xs[i], y), Size(20f, h), CornerRadius(10f))
                }
            }
        }
    }
}

/** The mark on a rounded blue-violet tile (sign-in, menus). */
@Composable
fun LogoTile(modifier: Modifier = Modifier, tile: Boolean = true) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (tile) {
            Canvas(Modifier.matchParentSize()) {
                val s = size.minDimension
                drawRoundRect(
                    Brush.linearGradient(listOf(EntrionBlue, EntrionIndigo, EntrionViolet), Offset.Zero, Offset(s, s)),
                    size = Size(s, s), cornerRadius = CornerRadius(s * 0.26f)
                )
            }
            EntrionMark(Modifier.fillMaxSize(0.58f), color = Color.White)
        } else EntrionMark(Modifier.fillMaxSize())
    }
}
