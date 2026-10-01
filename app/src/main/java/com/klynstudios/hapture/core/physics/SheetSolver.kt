package com.klynstudios.hapture.core.physics

import kotlin.math.abs

/**
 * Bottom-sheet decisions. Heights are "how much of the sheet is showing", in
 * px, 0 = hidden and increasing upward. The settle animation is a real
 * `spring()`; only where to settle and the resistance past full live here.
 * Same policy as the other solvers: one formula, copied as is into the
 * exported Kotlin.
 */
object SheetSolver {

    /** Where the sheet is heading if let go now: current height plus a short look-ahead of its momentum. */
    fun projected(height: Float, velocityUp: Float, lookaheadSec: Float): Float =
        height + velocityUp * lookaheadSec

    /**
     * The height to settle at: the nearest of [detents] (ascending) to where the
     * sheet is heading, or 0f to dismiss when it is heading below [dismissLine].
     */
    fun settleHeight(
        height: Float,
        velocityUp: Float,
        lookaheadSec: Float,
        detents: FloatArray,
        dismissLine: Float,
    ): Float {
        val target = projected(height, velocityUp, lookaheadSec)
        if (target < dismissLine) return 0f
        var best = detents[0]
        for (d in detents) if (abs(target - d) < abs(target - best)) best = d
        return best
    }

    /** What's drawn while dragging: 1:1 up to [full], rubber-band resistance above it, never below hidden. */
    fun visualHeight(raw: Float, full: Float, k: Float): Float =
        if (raw > full) full + RubberBand.apply(raw - full, 0f, k) else raw.coerceAtLeast(0f)
}
