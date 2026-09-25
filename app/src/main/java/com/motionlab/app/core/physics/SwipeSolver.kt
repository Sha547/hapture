package com.motionlab.app.core.physics

import kotlin.math.abs

/**
 * Swipe-to-dismiss decisions. The fling itself is Compose's real
 * `exponentialDecay` and the return is a real `spring()` (see SwipeFlingScreen),
 * so only the *decision* and the tilt live here. Same policy as the other
 * solvers: one formula, copy-pasted verbatim into the exported Kotlin.
 */
object SwipeSolver {

    /**
     * -1 (dismiss left), +1 (dismiss right) or 0 (spring back).
     * A fast enough flick wins over how far you dragged; otherwise going past
     * [distance] is enough.
     */
    fun dismissDirection(offset: Float, velocity: Float, distance: Float, velocityThreshold: Float): Int {
        if (abs(velocity) >= velocityThreshold) return if (velocity > 0f) 1 else -1
        if (abs(offset) >= distance) return if (offset > 0f) 1 else -1
        return 0
    }

    /** Card rotation: proportional to drag, reaching [maxTilt] degrees at 3x the dismiss distance. */
    fun tiltDegrees(offset: Float, distance: Float, maxTilt: Float): Float =
        maxTilt * (offset / (distance * 3f)).coerceIn(-1f, 1f)
}
