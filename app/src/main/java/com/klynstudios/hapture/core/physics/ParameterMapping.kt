package com.klynstudios.hapture.core.physics

import androidx.compose.animation.core.Spring

/**
 * Hapture does not confront the user with raw engineering values on the
 * default sliders (spec §12). Each slider is 0f..1f and gets mapped here to
 * the range the underlying Compose AnimationSpec actually expects.
 *
 * These ranges deliberately span Compose's own named constants
 * (Spring.StiffnessVeryLow..StiffnessHigh, Spring.DampingRatio*) so "Advanced
 * mode" can later show the exact same numbers Compose ships with.
 */
object ParameterMapping {

    /** 0f (soft) .. 1f (firm) -> stiffness, ~50..3000 */
    fun stiffness(t: Float): Float = lerp(Spring.StiffnessVeryLow, 3000f, t.coerceIn(0f, 1f))

    /** Inverse of [stiffness], clamped: the slider position closest to a given stiffness. */
    fun stiffnessT(k: Float): Float = ((k - Spring.StiffnessVeryLow) / (3000f - Spring.StiffnessVeryLow)).coerceIn(0f, 1f)

    /** Inverse of [dampingRatio], clamped. */
    fun dampingT(z: Float): Float = ((z - 0.15f) / (1.5f - 0.15f)).coerceIn(0f, 1f)

    /** 0f (bouncy) .. 1f (firm, no overshoot) -> damping ratio, ~0.15..1.5 */
    fun dampingRatio(t: Float): Float = lerp(0.15f, 1.5f, t.coerceIn(0f, 1f))

    /** 0f (no resistance) .. 1f (very resistant) -> rubber-band constant k */
    fun resistance(t: Float): Float = lerp(0.01f, 0.08f, t.coerceIn(0f, 1f))

    /** 0f (no pull) .. 1f (locks on almost immediately) -> magnetic strength */
    fun magneticStrength(t: Float): Float = lerp(0f, 0.9f, t.coerceIn(0f, 1f))

    /** 0f (tiny capture radius) .. 1f (captures from far away), in px */
    fun magneticThresholdPx(t: Float, maxPx: Float): Float = lerp(maxPx * 0.15f, maxPx, t.coerceIn(0f, 1f))

    /** 0f (short) .. 1f (long) -> how far you must drag before release dismisses, in dp */
    fun flingDistanceDp(t: Float): Float = lerp(48f, 140f, t.coerceIn(0f, 1f))

    /** 0f (needs a hard flick) .. 1f (a light flick dismisses) -> velocity threshold, in dp/s */
    fun flingVelocityDpPerSec(t: Float): Float = lerp(1400f, 250f, t.coerceIn(0f, 1f))

    /** 0f (coasts far) .. 1f (stops quickly) -> exponentialDecay frictionMultiplier */
    fun flingFriction(t: Float): Float = lerp(0.5f, 3f, t.coerceIn(0f, 1f))

    /** 0f (flat) .. 1f (leans hard) -> maximum rotation in degrees */
    fun flingTiltDegrees(t: Float): Float = lerp(0f, 20f, t.coerceIn(0f, 1f))

    /** Highest a sheet may open, as a fraction of its container. */
    const val SHEET_FULL_FRACTION = 0.92f

    /** 0f (barely peeking) .. 1f (a third of the container) -> lowest detent, as a fraction of the container */
    fun sheetPeekFraction(t: Float): Float = lerp(0.14f, 0.34f, t.coerceIn(0f, 1f))

    /** 0f (low) .. 1f (high) -> middle detent, as a fraction of the container. Always above the peek and below full. */
    fun sheetMidFraction(t: Float): Float = lerp(0.42f, 0.72f, t.coerceIn(0f, 1f))

    /** 0f (settles where you let go) .. 1f (a flick carries it to the next detent) -> look-ahead in seconds */
    fun sheetMomentumSec(t: Float): Float = lerp(0.04f, 0.24f, t.coerceIn(0f, 1f))

    /** 0f (hard to dismiss) .. 1f (a slight pull down dismisses) -> dismiss line, as a fraction of the peek height */
    fun sheetDismissLineFraction(t: Float): Float = lerp(0.25f, 0.85f, t.coerceIn(0f, 1f))

    /** 0f (barely) .. 1f (a full pull) -> how far you must pull before release triggers a refresh, in dp */
    fun pullTriggerDp(t: Float): Float = lerp(40f, 120f, t.coerceIn(0f, 1f))

    /** 0f (instant) .. 1f (a couple of seconds) -> how long a triggered refresh holds before springing back, in ms */
    fun pullHoldMs(t: Float): Float = lerp(200f, 2000f, t.coerceIn(0f, 1f))

    /** 0f (barely zooms out) .. 1f (zooms well out) -> lowest scale before it resists, below 1x */
    fun zoomMinScale(t: Float): Float = lerp(0.9f, 0.4f, t.coerceIn(0f, 1f))

    /** 0f (barely zooms in) .. 1f (zooms way in) -> highest scale before it resists, above 1x */
    fun zoomMaxScale(t: Float): Float = lerp(1.15f, 3f, t.coerceIn(0f, 1f))

    /** 0f (must cross almost the whole item) .. 1f (a slight nudge swaps) -> swap threshold, as a fraction of item height */
    fun reorderThresholdFraction(t: Float): Float = lerp(0.85f, 0.2f, t.coerceIn(0f, 1f))

    /** 0f (barely) .. 1f (a lot) -> the page's scale at a full back swipe. Material's own is 0.9. */
    fun backMinScale(t: Float): Float = lerp(0.96f, 0.8f, t.coerceIn(0f, 1f))

    /** 0f (stays centred) .. 1f (leans well over) -> sideways shift at a full swipe, as a fraction of the page width */
    fun backShiftFraction(t: Float): Float = lerp(0f, 0.12f, t.coerceIn(0f, 1f))

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
