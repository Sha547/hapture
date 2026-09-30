package com.klynstudios.hapture.core.physics

/**
 * Pull to refresh: how far down you pull is [RubberBand.apply], the same
 * formula every other rubber-banded drag in this app uses; the only thing
 * specific to this interaction is deciding whether a pull counted.
 */
object PullRefreshSolver {
    /** True once you've pulled at least [triggerPx] -- the release decides whether that pull sticks. */
    fun triggered(offsetPx: Float, triggerPx: Float): Boolean = offsetPx >= triggerPx

    /** k (0.01..0.08) becomes a fraction of the trigger distance: at 8x, resistance 0.045 is 0.36. */
    const val RESISTANCE_SCALE = 8f

    /**
     * How far the content follows a pull of [raw] px. Resistance is relative to [triggerPx]: the plain
     * `d / (1 + k d)` tops out at 1/k (22 px by default), which is far short of a 40 to 120 dp trigger,
     * so the pull could never be completed. This tops out at trigger / (k * 8), always past the line.
     */
    fun visual(raw: Float, k: Float, triggerPx: Float): Float =
        raw / (1f + k * RESISTANCE_SCALE / triggerPx.coerceAtLeast(1f) * kotlin.math.abs(raw))
}
