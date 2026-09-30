package com.klynstudios.hapture.core.physics

/**
 * Android's predictive back, as the page sees it: while the swipe is held, a
 * progress of 0..1 shrinks the page and leans it toward the edge the finger
 * came from; on release the system says commit or cancel, and a spring
 * finishes the exit or brings the page back. The same shape Material's own
 * back animation has, with the scale, lean and spring left to tune.
 *
 * Pure so the editor's preview and the exported code can't disagree.
 */
object PredictiveBackSolver {

    /** Largest corner radius the page reaches, in dp, as it lifts off the screen. */
    const val MAX_CORNER_DP = 32f

    /** Page scale at [progress] of the swipe. */
    fun scale(progress: Float, minScale: Float): Float = 1f - (1f - minScale) * progress.coerceIn(0f, 1f)

    /**
     * Sideways shift in px: the lean toward the edge while swiping, plus the exit once committed
     * ([exit] 0..1 carries the page fully off). [fromLeft] is the edge the swipe started at.
     */
    fun shiftPx(progress: Float, exit: Float, widthPx: Float, shiftFraction: Float, fromLeft: Boolean): Float {
        val dir = if (fromLeft) 1f else -1f
        return dir * widthPx * (shiftFraction * progress.coerceIn(0f, 1f) + exit)
    }

    /** Corners round up quickly (by a fifth of the swipe) so the page reads as lifted right away. */
    fun cornerDp(progress: Float): Float = MAX_CORNER_DP * (progress.coerceIn(0f, 1f) * 5f).coerceAtMost(1f)

    /** How much the page underneath is dimmed: fully at rest, clearing as the swipe goes on. */
    fun scrimAlpha(progress: Float, exit: Float): Float = (0.32f * (1f - progress.coerceIn(0f, 1f)) * (1f - exit)).coerceIn(0f, 0.32f)

    /**
     * The preview's stand-in for the system's decision (the real one is the OS's): past a third of the way, or
     * a quick flick toward the far side, commits. [velocityPerSec] is progress per second, positive = inward.
     */
    fun commits(progress: Float, velocityPerSec: Float): Boolean = progress >= 0.35f || velocityPerSec > 1.2f
}
