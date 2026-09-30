package com.klynstudios.hapture.core.doodle

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Shared by the on-screen painter, the PNG export and the SVG export, so all three agree. */
object DoodleGeometry {

    data class Quad(val cx: Float, val cy: Float, val ex: Float, val ey: Float)

    /**
     * Smooth path through [p]: start at the first point, then quadratic curves
     * whose control points are the sampled points and whose ends are the
     * midpoints between them. Null for fewer than two points (a dot).
     */
    fun smooth(p: List<Pt>): Pair<Pt, List<Quad>>? {
        if (p.size < 2) return null
        val quads = ArrayList<Quad>()
        if (p.size == 2) {
            quads += Quad((p[0].x + p[1].x) / 2, (p[0].y + p[1].y) / 2, p[1].x, p[1].y)
            return p[0] to quads
        }
        for (i in 1 until p.size - 1) {
            quads += Quad(p[i].x, p[i].y, (p[i].x + p[i + 1].x) / 2, (p[i].y + p[i + 1].y) / 2)
        }
        val last = p.last()
        quads += Quad(last.x, last.y, last.x, last.y)
        return p[0] to quads
    }

    /**
     * Deterministic spray dots: the same stroke always sprays the same speckle,
     * so a reopened or exported doodle matches what was drawn. Positions are
     * canvas-relative; [radius] is a fraction of canvas width.
     */
    fun sprayDots(points: List<Pt>, radius: Float, dotsPerStep: Int = 14): List<Pt> {
        if (points.isEmpty()) return emptyList()
        val rng = Random(points.size * 31 + (points.first().x * 10_000).toInt())
        val spacing = (radius * 0.45f).coerceAtLeast(0.0005f)
        val out = ArrayList<Pt>()
        fun burst(c: Pt) {
            repeat(dotsPerStep) {
                val a = rng.nextFloat() * 6.2831855f
                val d = radius * kotlin.math.sqrt(rng.nextFloat())
                out += Pt(c.x + cos(a) * d, c.y + sin(a) * d)
            }
        }
        burst(points.first())
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val steps = (hypot(b.x - a.x, b.y - a.y) / spacing).toInt().coerceAtLeast(1)
            for (s in 1..steps) {
                val t = s / steps.toFloat()
                burst(Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
            }
        }
        return out
    }

    /** Drops points closer than [minDist] to the previous kept one, so a slow drag doesn't store hundreds of near-duplicates. */
    fun thinned(points: List<Pt>, minDist: Float = 0.002f): List<Pt> {
        if (points.size < 3) return points
        val out = ArrayList<Pt>()
        out += points.first()
        for (i in 1 until points.size - 1) {
            val l = out.last()
            if (abs(points[i].x - l.x) + abs(points[i].y - l.y) >= minDist) out += points[i]
        }
        out += points.last()
        return out
    }

    /** (width multiplier, alpha) layers that stack into a soft-edged paint stroke. */
    val PAINT_LAYERS: List<Pair<Float, Float>> = listOf(1.0f to 0.22f, 0.75f to 0.28f, 0.5f to 0.4f, 0.25f to 0.65f)

    const val MARKER_ALPHA = 0.5f
    const val MARKER_WIDTH = 1.8f
    const val ERASER_WIDTH = 1.6f
    const val SPRAY_RADIUS = 2.2f
}

/** Slider (0f..1f) to stroke width as a fraction of canvas width. */
fun doodleWidthFor(t: Float): Float = 0.004f + (0.07f - 0.004f) * t.coerceIn(0f, 1f)
