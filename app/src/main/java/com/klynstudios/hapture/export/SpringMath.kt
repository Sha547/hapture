package com.klynstudios.hapture.export

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Closed-form maths for the same spring Compose animates with (mass = 1,
 * stiffness k, damping ratio zeta), so a design tool can be handed numbers it
 * understands without re-simulating anything.
 *
 * Everything here describes a step from 0 to 1 with zero initial velocity.
 * A real drag releases with some velocity, which changes the path but not the
 * spring, so the settle time and overshoot are nominal, not exact per release.
 */
object SpringMath {

    /** Absolute damping coefficient c = 2 * zeta * sqrt(k * m). Figma's custom spring takes this directly. */
    fun damping(stiffness: Float, dampingRatio: Float, mass: Float = 1f): Float =
        2f * dampingRatio * sqrt(stiffness * mass)

    /** Position of a unit step at time [t] seconds. */
    fun stepResponse(t: Double, stiffness: Double, dampingRatio: Double, mass: Double = 1.0): Double {
        if (t <= 0.0) return 0.0
        val w0 = sqrt(stiffness / mass)
        val z = dampingRatio
        return when {
            abs(z - 1.0) < 1e-4 -> 1.0 - exp(-w0 * t) * (1.0 + w0 * t)
            z < 1.0 -> {
                val wd = w0 * sqrt(1.0 - z * z)
                1.0 - exp(-z * w0 * t) * (cos(wd * t) + (z * w0 / wd) * sin(wd * t))
            }
            else -> {
                val root = w0 * sqrt(z * z - 1.0)
                val s1 = -z * w0 + root
                val s2 = -z * w0 - root
                1.0 - (s2 * exp(s1 * t) - s1 * exp(s2 * t)) / (s2 - s1)
            }
        }
    }

    /** First time after which the spring stays within [tolerance] of its target, in ms (capped at 10s). */
    fun settleMs(stiffness: Float, dampingRatio: Float, tolerance: Double = 0.001): Int {
        var last = 0
        for (ms in 0..MAX_MS step 2) {
            val x = stepResponse(ms / 1000.0, stiffness.toDouble(), dampingRatio.toDouble())
            if (abs(1.0 - x) > tolerance) last = ms
        }
        return (last + 2).coerceAtMost(MAX_MS)
    }

    /**
     * First time, in ms, the step response reaches [fraction] of the travel; if it never does
     * (a fraction above the peak), the settle time. What a chained transition waits for.
     */
    fun timeToReachMs(stiffness: Float, dampingRatio: Float, fraction: Float): Int {
        val target = fraction.coerceIn(0.01f, 0.99f)
        for (ms in 0..MAX_MS) {
            if (stepResponse(ms / 1000.0, stiffness.toDouble(), dampingRatio.toDouble()) >= target) return ms
        }
        return settleMs(stiffness, dampingRatio)
    }

    /** Peak overshoot as a fraction of the travel (0 for critically damped or over). */
    fun overshoot(dampingRatio: Float): Float =
        if (dampingRatio >= 1f) 0f
        else exp(-PI * dampingRatio / sqrt(1.0 - dampingRatio * dampingRatio)).toFloat()

    /** [count] evenly spaced samples of the step response across the settle time; first is 0, last is exactly 1. */
    fun samples(stiffness: Float, dampingRatio: Float, count: Int = 48): List<Float> {
        val total = settleMs(stiffness, dampingRatio) / 1000.0
        return List(count) { i ->
            when (i) {
                0 -> 0f
                count - 1 -> 1f
                else -> stepResponse(total * i / (count - 1), stiffness.toDouble(), dampingRatio.toDouble()).toFloat()
            }
        }
    }

    private const val MAX_MS = 10_000
}
