package com.klynstudios.hapture.core.capture

import kotlin.math.hypot

/**
 * Turns a finger dragged across a fake screen, with the time of every point, into the
 * curve [SpringFit] takes: the third way to capture a feel, for people who would
 * rather show a motion than tune it. Positions are fractions of the screen.
 *
 * Progress is measured along the line from where the stroke starts to where it comes
 * to rest, so dragging past the target and back reads as overshoot (above 1), and how
 * quickly the finger arrives and settles carries the timing.
 */
object SketchMotion {

    data class TimedPoint(val x: Float, val y: Float, val tMs: Int)

    const val MIN_DURATION_MS = 120
    const val MIN_TRAVEL = 0.06f
    private const val RESAMPLE_HZ = 60f

    fun toCurve(points: List<TimedPoint>): List<Pair<Float, Float>>? {
        if (points.size < 6) return null
        val sorted = points.sortedBy { it.tMs }
        if (sorted.last().tMs - sorted.first().tMs < MIN_DURATION_MS) return null

        val r = resample(sorted)
        if (r.size < 6) return null
        fun med(v: List<Float>) = v.sorted()[v.size / 2]
        val sx = med(r.take(2).map { it.x }); val sy = med(r.take(2).map { it.y })
        val ex = med(r.takeLast(3).map { it.x }); val ey = med(r.takeLast(3).map { it.y })
        val ax = ex - sx; val ay = ey - sy
        val len = hypot(ax, ay)
        if (len < MIN_TRAVEL) return null

        val values = r.map { ((it.x - sx) * ax + (it.y - sy) * ay) / (len * len) }
        return ObjectTracker.normalize(r.map { it.tMs / 1000f }, values)
    }

    /** Touch events arrive unevenly; fitting wants an even clock, so interpolate to a steady rate. */
    private fun resample(p: List<TimedPoint>): List<TimedPoint> {
        val step = 1000f / RESAMPLE_HZ
        val out = ArrayList<TimedPoint>()
        var i = 0
        var t = p.first().tMs.toFloat()
        val end = p.last().tMs
        while (t <= end) {
            while (i < p.size - 2 && p[i + 1].tMs < t) i++
            val a = p[i]; val b = p[i + 1]
            val span = (b.tMs - a.tMs).toFloat()
            val f = if (span <= 0f) 0f else ((t - a.tMs) / span).coerceIn(0f, 1f)
            out += TimedPoint(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, t.toInt())
            t += step
        }
        return out
    }
}
