package com.klynstudios.hapture.feature.doodle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.klynstudios.hapture.core.doodle.Brush
import com.klynstudios.hapture.core.doodle.DoodleBackground
import com.klynstudios.hapture.core.doodle.DoodleGeometry
import com.klynstudios.hapture.core.doodle.DoodleStroke
import java.io.ByteArrayOutputStream

/** The one painter: the live canvas and the PNG export both call this, so what you see is what you export. */
fun DrawScope.drawDoodle(strokes: List<DoodleStroke>, background: DoodleBackground) {
    drawRect(Color(background.argb))
    strokes.forEach { drawDoodleStroke(it, background) }
}

fun DrawScope.drawDoodleStroke(s: DoodleStroke, background: DoodleBackground) {
    val w = size.width
    val h = size.height
    val color = Color(if (s.brush == Brush.ERASER) background.argb else s.color)
    val width = s.width * w

    fun line(c: Color, strokeWidth: Float) {
        val smooth = DoodleGeometry.smooth(s.points)
        if (smooth == null) {
            drawCircle(c, strokeWidth / 2f, Offset(s.points[0].x * w, s.points[0].y * h))
            return
        }
        val (start, quads) = smooth
        val path = Path().apply {
            moveTo(start.x * w, start.y * h)
            quads.forEach { quadraticTo(it.cx * w, it.cy * h, it.ex * w, it.ey * h) }
        }
        drawPath(path, c, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    when (s.brush) {
        Brush.PEN -> line(color, width)
        Brush.ERASER -> line(color, width * DoodleGeometry.ERASER_WIDTH)
        Brush.MARKER -> line(color.copy(alpha = DoodleGeometry.MARKER_ALPHA), width * DoodleGeometry.MARKER_WIDTH)
        Brush.PAINT -> DoodleGeometry.PAINT_LAYERS.forEach { (m, a) -> line(color.copy(alpha = a), width * 1.4f * m) }
        Brush.SPRAY -> {
            val dotR = (width * 0.12f).coerceAtLeast(1f)
            DoodleGeometry.sprayDots(s.points, s.width * DoodleGeometry.SPRAY_RADIUS).forEach {
                drawCircle(color, dotR, Offset(it.x * w, it.y * h))
            }
        }
    }
}

/** Renders the doodle off-screen at [px] x [px] and encodes it as PNG. */
fun renderDoodlePng(strokes: List<DoodleStroke>, background: DoodleBackground, px: Int = 1080): ByteArray {
    val bitmap = ImageBitmap(px, px)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
        drawDoodle(strokes, background)
    }
    val out = ByteArrayOutputStream()
    bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}
