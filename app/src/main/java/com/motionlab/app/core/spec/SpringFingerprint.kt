package com.motionlab.app.core.spec

import com.motionlab.app.export.SpringMath
import kotlin.math.ln

/**
 * A small visual signature for a spring, from its own step response: the shape of
 * the curve (bounce and overshoot show as bumps) plus a speed bar, since the curve
 * alone is stretched to fill the glyph and would make a slow and a fast spring of
 * the same damping look identical.
 */
data class SpringFingerprint(val shape: List<Float>, val speed: Float) {

    companion object {
        private const val FAST_MS = 80f
        private const val SLOW_MS = 1500f

        fun of(spring: SpringSpec, points: Int = 24): SpringFingerprint {
            val settle = spring.settleMs.toFloat().coerceIn(FAST_MS, SLOW_MS)
            // 0 = fastest, 1 = slowest, on a log scale so 100 -> 200 ms matters as much as 700 -> 1400 ms.
            val speed = ((ln(settle) - ln(FAST_MS)) / (ln(SLOW_MS) - ln(FAST_MS))).coerceIn(0f, 1f)
            return SpringFingerprint(SpringMath.samples(spring.stiffness, spring.dampingRatio, points), speed)
        }
    }
}
