package com.skyqlike.iptv.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Small line icons for the side menu, drawn in code so the app needs no extra files.
 * Everything is drawn on a 0 to 1 grid and scaled to the size asked for.
 */
@Composable
fun MenuIcon(name: String, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val line = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun at(x: Float, y: Float) = Offset(x * w, y * w)
        fun seg(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, at(x1, y1), at(x2, y2), strokeWidth = line.width, cap = StrokeCap.Round)
        fun box(x: Float, y: Float, bw: Float, bh: Float, r: Float = 0.08f) =
            drawRoundRect(tint, at(x, y), Size(bw * w, bh * w), CornerRadius(r * w), style = line)
        fun ring(cx: Float, cy: Float, r: Float) = drawCircle(tint, r * w, at(cx, cy), style = line)
        fun dot(cx: Float, cy: Float, r: Float) = drawCircle(tint, r * w, at(cx, cy), style = Fill)
        fun poly(fill: Boolean, vararg p: Float) {
            val path = Path()
            path.moveTo(p[0] * w, p[1] * w)
            var i = 2
            while (i < p.size) {
                path.lineTo(p[i] * w, p[i + 1] * w)
                i += 2
            }
            path.close()
            drawPath(path, tint, style = if (fill) Fill else line)
        }

        when (name) {
            "home" -> {
                poly(false, 0.12f, 0.50f, 0.50f, 0.16f, 0.88f, 0.50f)
                seg(0.22f, 0.46f, 0.22f, 0.84f)
                seg(0.78f, 0.46f, 0.78f, 0.84f)
                seg(0.22f, 0.84f, 0.78f, 0.84f)
            }
            "guide" -> {
                seg(0.14f, 0.28f, 0.86f, 0.28f)
                seg(0.14f, 0.50f, 0.64f, 0.50f)
                seg(0.14f, 0.72f, 0.78f, 0.72f)
            }
            "live" -> {
                ring(0.5f, 0.5f, 0.36f)
                dot(0.5f, 0.5f, 0.12f)
            }
            "all" -> {
                box(0.14f, 0.14f, 0.30f, 0.30f)
                box(0.56f, 0.14f, 0.30f, 0.30f)
                box(0.14f, 0.56f, 0.30f, 0.30f)
                box(0.56f, 0.56f, 0.30f, 0.30f)
            }
            "catchup" -> {
                drawArc(tint, 40f, 290f, false, at(0.15f, 0.15f), Size(0.7f * w, 0.7f * w), style = line)
                seg(0.5f, 0.5f, 0.5f, 0.3f)
                seg(0.5f, 0.5f, 0.64f, 0.58f)
            }
            "movies" -> {
                box(0.10f, 0.20f, 0.80f, 0.60f)
                poly(true, 0.42f, 0.36f, 0.64f, 0.50f, 0.42f, 0.64f)
            }
            "moviechannels" -> {
                box(0.10f, 0.28f, 0.80f, 0.54f)
                seg(0.10f, 0.40f, 0.90f, 0.40f)
                seg(0.22f, 0.16f, 0.34f, 0.28f)
                seg(0.50f, 0.16f, 0.62f, 0.28f)
            }
            "library" -> {
                box(0.14f, 0.16f, 0.72f, 0.20f)
                box(0.14f, 0.40f, 0.72f, 0.20f)
                box(0.14f, 0.64f, 0.72f, 0.20f)
            }
            "sports" -> {
                ring(0.5f, 0.5f, 0.36f)
                seg(0.5f, 0.14f, 0.5f, 0.86f)
                seg(0.14f, 0.5f, 0.86f, 0.5f)
            }
            "entertainment" -> {
                box(0.10f, 0.18f, 0.80f, 0.52f)
                seg(0.34f, 0.84f, 0.66f, 0.84f)
                seg(0.5f, 0.70f, 0.5f, 0.84f)
            }
            "news" -> {
                box(0.14f, 0.16f, 0.72f, 0.68f)
                seg(0.26f, 0.36f, 0.74f, 0.36f)
                seg(0.26f, 0.52f, 0.74f, 0.52f)
                seg(0.26f, 0.68f, 0.52f, 0.68f)
            }
            "music" -> {
                dot(0.34f, 0.72f, 0.13f)
                seg(0.47f, 0.72f, 0.47f, 0.22f)
                seg(0.47f, 0.22f, 0.80f, 0.32f)
            }
            "docs" -> {
                box(0.20f, 0.14f, 0.60f, 0.72f)
                seg(0.36f, 0.14f, 0.36f, 0.86f)
            }
            "kids" -> {
                ring(0.5f, 0.5f, 0.36f)
                dot(0.38f, 0.42f, 0.05f)
                dot(0.62f, 0.42f, 0.05f)
                drawArc(tint, 25f, 130f, false, at(0.32f, 0.34f), Size(0.36f * w, 0.36f * w), style = line)
            }
            "globe" -> {
                ring(0.5f, 0.5f, 0.36f)
                seg(0.14f, 0.5f, 0.86f, 0.5f)
                drawOval(tint, at(0.34f, 0.14f), Size(0.32f * w, 0.72f * w), style = line)
            }
            "star" -> {
                val pts = FloatArray(20)
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 0.38f else 0.17f
                    val a = -PI / 2 + i * PI / 5
                    pts[i * 2] = 0.5f + (r * cos(a)).toFloat()
                    pts[i * 2 + 1] = 0.53f + (r * sin(a)).toFloat()
                }
                poly(false, *pts)
            }
            "settings" -> {
                ring(0.5f, 0.5f, 0.17f)
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    seg(
                        0.5f + (0.28f * cos(a)).toFloat(), 0.5f + (0.28f * sin(a)).toFloat(),
                        0.5f + (0.40f * cos(a)).toFloat(), 0.5f + (0.40f * sin(a)).toFloat(),
                    )
                }
            }
            "categories" -> {
                seg(0.16f, 0.28f, 0.84f, 0.28f)
                seg(0.16f, 0.50f, 0.84f, 0.50f)
                seg(0.16f, 0.72f, 0.84f, 0.72f)
            }
            else -> ring(0.5f, 0.5f, 0.3f)
        }
    }
}
