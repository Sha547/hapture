package com.motionlab.app.core.physics

/**
 * Pull to refresh: how far down you pull is [RubberBand.apply], the same
 * formula every other rubber-banded drag in this app uses; the only thing
 * specific to this interaction is deciding whether a pull counted.
 */
object PullRefreshSolver {
    /** True once you've pulled at least [triggerPx] -- the release decides whether that pull sticks. */
    fun triggered(offsetPx: Float, triggerPx: Float): Boolean = offsetPx >= triggerPx
}
