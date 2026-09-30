package com.klynstudios.hapture.core.model

import com.klynstudios.hapture.core.spec.SpringSpec
import kotlin.math.abs
import kotlin.math.ln

/**
 * The spatial springs of Material 3's motion schemes, the values Compose's
 * `MaterialTheme.motionScheme` hands out (`fastSpatialSpec()` and so on).
 * Spatial only: the effects springs (colour, opacity) are critically damped
 * and stiffer than the sliders reach, and nothing here moves in that way.
 */
enum class MaterialSpring(
    val scheme: String,
    val speed: String,
    val stiffness: Float,
    val dampingRatio: Float,
    /** The Compose call that returns this spring. */
    val composeCall: String,
) {
    STANDARD_FAST("Standard", "fast", 1400f, 0.9f, "fastSpatialSpec()"),
    STANDARD_DEFAULT("Standard", "default", 700f, 0.9f, "defaultSpatialSpec()"),
    STANDARD_SLOW("Standard", "slow", 300f, 0.9f, "slowSpatialSpec()"),
    EXPRESSIVE_FAST("Expressive", "fast", 800f, 0.6f, "fastSpatialSpec()"),
    EXPRESSIVE_DEFAULT("Expressive", "default", 380f, 0.8f, "defaultSpatialSpec()"),
    EXPRESSIVE_SLOW("Expressive", "slow", 200f, 0.8f, "slowSpatialSpec()");

    val label: String get() = "$scheme $speed"
    val spring: SpringSpec get() = SpringSpec(stiffness, dampingRatio)
}

/** How far a spring is from the nearest Material 3 one, in words a designer can act on. */
data class MaterialMatch(val nearest: MaterialSpring, val distance: Float) {
    enum class Closeness { EXACT, CLOSE, NEAR, FAR }

    val closeness: Closeness
        get() = when {
            distance < EXACT_BELOW -> Closeness.EXACT
            distance < CLOSE_BELOW -> Closeness.CLOSE
            distance < NEAR_BELOW -> Closeness.NEAR
            else -> Closeness.FAR
        }

    val summary: String
        get() = when (closeness) {
            Closeness.EXACT -> "M3 ${nearest.label}"
            Closeness.CLOSE -> "Near M3 ${nearest.label}"
            Closeness.NEAR -> "Nearest M3: ${nearest.label}"
            Closeness.FAR -> "Off the M3 scale"
        }

    companion object {
        const val EXACT_BELOW = 0.03f
        const val CLOSE_BELOW = 0.15f
        const val NEAR_BELOW = 0.4f
    }
}

object MaterialMotion {

    /**
     * Distance between two springs. Stiffness counts on a log scale (doubling
     * it feels like the same step anywhere on the range), damping ratio linearly,
     * weighted so a 0.1 change in damping counts about as much as a 25% change
     * in stiffness.
     */
    fun distance(a: SpringSpec, b: SpringSpec): Float {
        val dk = abs(ln(a.stiffness / b.stiffness))
        val dz = abs(a.dampingRatio - b.dampingRatio) * 2.2f
        return dk + dz
    }

    fun nearest(spring: SpringSpec): MaterialMatch =
        MaterialSpring.entries
            .map { MaterialMatch(it, distance(spring, it.spring)) }
            .minBy { it.distance }
}
