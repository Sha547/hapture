package com.klynstudios.hapture.core.doodle

import java.util.Locale

/** A doodle as a standalone SVG, for design tools. Mirrors the on-screen painter brush for brush. */
object DoodleSvg {

    fun of(strokes: List<DoodleStroke>, background: DoodleBackground, size: Int = 1080): String {
        val body = StringBuilder()
        strokes.forEach { s -> body.append(stroke(s, background, size)) }
        return buildString {
            append("""<svg xmlns="http://www.w3.org/2000/svg" width="$size" height="$size" viewBox="0 0 $size $size">""").append('\n')
            append("""  <rect width="$size" height="$size" fill="${hex(background.argb)}"/>""").append('\n')
            append(body)
            append("</svg>\n")
        }
    }

    private fun stroke(s: DoodleStroke, bg: DoodleBackground, size: Int): String {
        val color = if (s.brush == Brush.ERASER) bg.argb else s.color
        val w = s.width * size
        return when (s.brush) {
            Brush.PEN, Brush.ERASER -> line(s, color, if (s.brush == Brush.ERASER) w * DoodleGeometry.ERASER_WIDTH else w, 1f, size)
            Brush.MARKER -> line(s, color, w * DoodleGeometry.MARKER_WIDTH, DoodleGeometry.MARKER_ALPHA, size)
            Brush.PAINT -> DoodleGeometry.PAINT_LAYERS.joinToString("") { (m, a) -> line(s, color, w * 1.4f * m, a, size) }
            Brush.SPRAY -> {
                val dotR = (w * 0.12f).coerceAtLeast(1f)
                DoodleGeometry.sprayDots(s.points, s.width * DoodleGeometry.SPRAY_RADIUS)
                    .joinToString("") { """  <circle cx="${n(it.x * size)}" cy="${n(it.y * size)}" r="${n(dotR)}" fill="${hex(color)}"/>""" + "\n" }
            }
        }
    }

    private fun line(s: DoodleStroke, color: Int, width: Float, alpha: Float, size: Int): String {
        val op = if (alpha < 1f) """ stroke-opacity="${n(alpha)}"""" else ""
        val smooth = DoodleGeometry.smooth(s.points)
            ?: return """  <circle cx="${n(s.points[0].x * size)}" cy="${n(s.points[0].y * size)}" r="${n(width / 2)}" fill="${hex(color)}"${op.replace("stroke-opacity", "fill-opacity")}/>""" + "\n"
        val (start, quads) = smooth
        val d = buildString {
            append("M${n(start.x * size)} ${n(start.y * size)}")
            quads.forEach { append(" Q${n(it.cx * size)} ${n(it.cy * size)} ${n(it.ex * size)} ${n(it.ey * size)}") }
        }
        return """  <path d="$d" fill="none" stroke="${hex(color)}" stroke-width="${n(width)}" stroke-linecap="round" stroke-linejoin="round"$op/>""" + "\n"
    }

    private fun hex(argb: Int) = String.format(Locale.US, "#%06X", argb and 0xFFFFFF)
    private fun n(v: Float) = String.format(Locale.US, "%.2f", v)
}
