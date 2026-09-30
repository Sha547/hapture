package com.klynstudios.hapture.core.capture

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Follows one solid-coloured object through video frames (as ARGB pixel
 * arrays), by colour: the pixels near the chosen colour, their centre and their
 * count per frame. Enough for the usual UI mock recording, a flat shape moving
 * over a plain background; it is not general object tracking.
 */
object ObjectTracker {

    data class Sample(val timeSec: Float, val cx: Float, val cy: Float, val area: Float)

    /** Squared-distance-free check: every channel within [tolerance] of the target. */
    fun near(pixel: Int, target: Int, tolerance: Int): Boolean =
        abs(((pixel shr 16) and 0xFF) - ((target shr 16) and 0xFF)) <= tolerance &&
            abs(((pixel shr 8) and 0xFF) - ((target shr 8) and 0xFF)) <= tolerance &&
            abs((pixel and 0xFF) - (target and 0xFF)) <= tolerance

    /** cx, cy are 0..1 of the frame; area is the fraction of pixels matched. Null if the object isn't in the frame. */
    fun locate(pixels: IntArray, width: Int, height: Int, target: Int, tolerance: Int = 40, minPixels: Int = 6): Triple<Float, Float, Float>? {
        var n = 0
        var sx = 0L
        var sy = 0L
        for (i in pixels.indices) {
            if (near(pixels[i], target, tolerance)) {
                n++
                sx += i % width
                sy += i / width
            }
        }
        if (n < minPixels) return null
        return Triple(sx.toFloat() / n / width, sy.toFloat() / n / height, n.toFloat() / (width * height))
    }

    /**
     * Turns tracked samples into a motion curve for [SpringFit]: picks whichever
     * signal moved most (horizontal or vertical position as a fraction of the
     * frame, or size as a ratio), normalises it to 0 at the start and 1 at rest,
     * and re-times it to begin at the first visible movement. Null if nothing
     * moved enough to fit.
     */
    fun toCurve(samples: List<Sample>): List<Pair<Float, Float>>? = toTimedCurve(samples)?.points

    /** Like [toCurve], but also says when the object started moving, so two objects' timing can be compared. */
    fun toTimedCurve(samples: List<Sample>): TimedCurve? {
        if (samples.size < 6) return null
        fun med(v: List<Float>) = v.sorted()[v.size / 2]
        val head = samples.take(2)
        val tail = samples.takeLast(3)

        val startSize = med(head.map { sqrt(it.area) })
        val endSize = med(tail.map { sqrt(it.area) })
        val signals = listOf(
            Triple(samples.map { it.cx }, abs(med(tail.map { it.cx }) - med(head.map { it.cx })), 0.03f),
            Triple(samples.map { it.cy }, abs(med(tail.map { it.cy }) - med(head.map { it.cy })), 0.03f),
            Triple(samples.map { sqrt(it.area) }, if (startSize > 0f) abs(endSize - startSize) / startSize else 0f, 0.06f),
        )
        val (values, travel, min) = signals.maxByOrNull { it.second / it.third } ?: return null
        if (travel < min) return null
        return normalizeTimed(samples.map { it.timeSec }, values)
    }

    /** A normalised curve plus the moment (in the recording's own clock) the object started to move. */
    data class TimedCurve(val points: List<Pair<Float, Float>>, val onsetSec: Float)

    /**
     * One measured signal over time -> the 0-to-1 curve [SpringFit] takes: 0 at the
     * start, 1 at rest, re-timed to begin at the first visible movement. Shared by
     * every capture input (video tracking, sketching). Null if it never went anywhere.
     */
    fun normalize(times: List<Float>, values: List<Float>): List<Pair<Float, Float>>? = normalizeTimed(times, values)?.points

    fun normalizeTimed(times: List<Float>, values: List<Float>): TimedCurve? {
        fun med(v: List<Float>) = v.sorted()[v.size / 2]
        val v0 = med(values.take(2))
        val vEnd = med(values.takeLast(3))
        if (abs(vEnd - v0) < 1e-6f) return null
        val norm = values.map { (it - v0) / (vEnd - v0) }
        val onset = norm.indexOfFirst { it > 0.02f }.let { if (it < 0) 0 else maxOf(it - 1, 0) }
        val t0 = times[onset]
        return TimedCurve((onset until times.size).map { times[it] - t0 to norm[it] }, t0)
    }
}
