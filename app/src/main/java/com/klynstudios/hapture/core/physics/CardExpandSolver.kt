package com.klynstudios.hapture.core.physics

/**
 * A card growing into a full page (Material's container transform). One spring drives a progress value:
 * 0 is the card in its grid cell, 1 is the page filling its container. The bounds are a straight blend of
 * the two rectangles, which is exactly what Compose's shared-bounds transition produces when every edge
 * follows the same spring, so the preview and the exported `SharedTransitionLayout` move the same way.
 */
object CardExpandSolver {

    /** An axis-aligned rectangle in px. */
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /** The rectangle [progress] of the way from [from] to [to]. Past 1 (a springy overshoot) it keeps going. */
    fun lerp(from: Box, to: Box, progress: Float): Box = Box(
        from.left + (to.left - from.left) * progress,
        from.top + (to.top - from.top) * progress,
        from.right + (to.right - from.right) * progress,
        from.bottom + (to.bottom - from.bottom) * progress,
    )

    /** The card's corners straighten as it opens; never negative, even while a springy overshoot runs past 1. */
    fun cornerPx(cardCornerPx: Float, progress: Float): Float = (cardCornerPx * (1f - progress)).coerceAtLeast(0f)

    /**
     * Cell [index] of a [columns]-wide grid inside a container [widthPx] wide, [padPx] from its edges, cells
     * [cellHeightPx] tall with [gapPx] between them.
     */
    fun cell(index: Int, columns: Int, widthPx: Float, padPx: Float, gapPx: Float, cellHeightPx: Float): Box {
        val cellW = (widthPx - 2 * padPx - (columns - 1) * gapPx) / columns
        val col = index % columns
        val row = index / columns
        val left = padPx + col * (cellW + gapPx)
        val top = padPx + row * (cellHeightPx + gapPx)
        return Box(left, top, left + cellW, top + cellHeightPx)
    }

    /**
     * How much of the card's own contents still shows, given how far the cross-fade has run (0..1). The page's
     * contents show the rest. The card's fade out over the first half and the page's in over the second, so the
     * two never sit on top of each other half-visible.
     */
    fun cardContentAlpha(fade: Float): Float = (1f - fade.coerceIn(0f, 1f) * 2f).coerceIn(0f, 1f)

    fun pageContentAlpha(fade: Float): Float = (fade.coerceIn(0f, 1f) * 2f - 1f).coerceIn(0f, 1f)
}
