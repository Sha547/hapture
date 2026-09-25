package com.motionlab.app.export

import com.motionlab.app.core.model.CustomPoint
import com.motionlab.app.core.model.closedSmoothSegments

/**
 * The design spec's hand-off for a [com.motionlab.app.core.model.ObjectShape.CUSTOM]
 * object: the exact same smoothed curve the app renders it with ([closedSmoothSegments]),
 * as an SVG path `d` attribute a designer can paste straight into Figma or a browser.
 */
object SvgPath {

    /** A 0f..[viewBox] square path; null if [points] can't close into a shape. */
    fun of(points: List<CustomPoint>, viewBox: Float = 100f): String? {
        val segments = closedSmoothSegments(points)
        if (segments.isEmpty()) return null

        val start = points[0]
        val sb = StringBuilder()
        sb.append("M ").append(fmt(start.x * viewBox)).append(' ').append(fmt(start.y * viewBox))
        for (seg in segments) {
            sb.append(" C ")
                .append(fmt(seg.c1.x * viewBox)).append(' ').append(fmt(seg.c1.y * viewBox)).append(", ")
                .append(fmt(seg.c2.x * viewBox)).append(' ').append(fmt(seg.c2.y * viewBox)).append(", ")
                .append(fmt(seg.end.x * viewBox)).append(' ').append(fmt(seg.end.y * viewBox))
        }
        sb.append(" Z")
        return sb.toString()
    }

    private fun fmt(v: Float) = DesignSpec.num(v, 2)
}
