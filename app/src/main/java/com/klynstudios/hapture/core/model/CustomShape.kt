package com.klynstudios.hapture.core.model

import kotlin.math.hypot

/** A point in a shape's own 0f..1f unit square. No Compose/Android dependency -- pure geometry. */
data class CustomPoint(val x: Float, val y: Float)

/** One cubic Bezier segment: start is implicit (the previous segment's end, or the path's first point). */
data class CubicSegment(val c1: CustomPoint, val c2: CustomPoint, val end: CustomPoint)

/**
 * Closed Catmull-Rom spline through [points], expressed as cubic Bezier
 * segments -- smooth like a drawn line, not a jagged polygon connecting raw
 * finger-tracked samples. The same segments drive both the on-screen shape
 * (a Compose [androidx.compose.ui.graphics.Path], built in the UI layer,
 * which this file has no dependency on) and the SVG path exported in the
 * design spec, so the two can never drift apart.
 *
 * Fewer than 3 points can't close into a shape; returns empty.
 */
fun closedSmoothSegments(points: List<CustomPoint>): List<CubicSegment> {
    val n = points.size
    if (n < 3) return emptyList()
    return List(n) { i ->
        val p0 = points[(i - 1 + n) % n]
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        val p3 = points[(i + 2) % n]
        CubicSegment(
            c1 = CustomPoint(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f),
            c2 = CustomPoint(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f),
            end = p2,
        )
    }
}

/**
 * Re-samples a closed polyline to exactly [count] points, evenly spaced by
 * arc length. Drawing slowly vs. quickly produces very different raw point
 * densities along a stroke; this makes storage size and smoothing quality
 * independent of how the shape was drawn.
 */
fun resampleClosed(points: List<CustomPoint>, count: Int): List<CustomPoint> {
    if (points.size < 3 || count < 3) return points
    val closed = points + points[0]
    val cumulative = DoubleArray(closed.size)
    for (i in 1 until closed.size) {
        val dx = (closed[i].x - closed[i - 1].x).toDouble()
        val dy = (closed[i].y - closed[i - 1].y).toDouble()
        cumulative[i] = cumulative[i - 1] + hypot(dx, dy)
    }
    val total = cumulative.last()
    if (total <= 1e-6) return points.take(count)

    return List(count) { k ->
        val target = total * k / count
        var i = 1
        while (i < cumulative.size - 1 && cumulative[i] < target) i++
        val segStart = cumulative[i - 1]
        val segLen = (cumulative[i] - segStart).coerceAtLeast(1e-6)
        val t = ((target - segStart) / segLen).toFloat().coerceIn(0f, 1f)
        val a = closed[i - 1]
        val b = closed[i]
        CustomPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }
}

/** Encodes/decodes a [CustomPoint] list as the compact string an [ObjectStyle]'s shape is stored and shared as. */
object CustomShapeCodec {
    private const val MAX_POINTS = 64

    fun encode(points: List<CustomPoint>): String =
        points.joinToString(";") { "${round4(it.x)},${round4(it.y)}" }

    /** Malformed segments are dropped and coordinates are clamped, never thrown on -- see [com.klynstudios.hapture.data.ProjectFile]'s policy. */
    fun decode(text: String?): List<CustomPoint> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(";")
            .mapNotNull { pair ->
                val parts = pair.split(",")
                if (parts.size != 2) return@mapNotNull null
                val x = parts[0].toFloatOrNull() ?: return@mapNotNull null
                val y = parts[1].toFloatOrNull() ?: return@mapNotNull null
                CustomPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
            }
            .take(MAX_POINTS)
    }

    private fun round4(v: Float): Float = kotlin.math.round(v * 10000f) / 10000f
}
