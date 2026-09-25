package com.motionlab.app.core.physics

/**
 * Pinch to zoom: clamped into `[min, max]`, with [RubberBand.apply] resisting
 * on whichever side you push past -- the same rubber-band formula as every
 * other bounded drag in this app, just applied to a scale factor instead of a
 * position. Always released back to 1x (spec: every interaction here ends at
 * a real rest state -- 0 for Spring Drag, the nearest target for Magnetic
 * Snap, a detent for the sheet; 1x plays that role here).
 */
object ZoomSolver {

    /** [raw] scale, resisted beyond [min]/[max] by [k]; below [min] never below 0. */
    fun visualScale(raw: Float, min: Float, max: Float, k: Float): Float = when {
        raw > max -> max + RubberBand.apply(raw - max, 0f, k)
        raw < min -> (min - RubberBand.apply(min - raw, 0f, k)).coerceAtLeast(0f)
        else -> raw
    }
}
