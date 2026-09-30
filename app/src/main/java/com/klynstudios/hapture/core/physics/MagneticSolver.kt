package com.klynstudios.hapture.core.physics

import kotlin.math.abs

/**
 * Magnetic attraction (spec §14): as a dragged object nears a target within
 * [threshold], it's pulled toward it, F = strength * (1 - d/threshold).
 *
 * This is a *visual assist* applied to the followed position while still
 * under the finger -- it's the half that happens before release. What
 * happens on release (deciding which target won and animating into it) is
 * [SnapPoints] handing off to the same real spring() the drag/rubber-band
 * screen uses, for the same fidelity reason documented there.
 */
object MagneticSolver {
    fun pulledPosition(raw: Float, targetX: Float, threshold: Float, strength: Float): Float {
        if (threshold <= 0f) return raw
        val distance = abs(raw - targetX)
        if (distance >= threshold) return raw
        val pull = (strength * (1f - distance / threshold)).coerceIn(0f, 1f)
        return raw + (targetX - raw) * pull
    }
}
