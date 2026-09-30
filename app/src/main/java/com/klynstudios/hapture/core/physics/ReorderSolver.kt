package com.klynstudios.hapture.core.physics

import kotlin.math.floor

/**
 * Drag to reorder: a dragged item swaps with a neighbor once it's crossed
 * [thresholdPx] into that neighbor's slot. [itemHeightPx] is the fixed slot
 * spacing; a smaller threshold swaps sooner (spec: "1f (a slight nudge
 * swaps)" on the slider, mapped to a *smaller* threshold).
 */
object ReorderSolver {

    /**
     * How many slots [offsetPx] (signed: positive = dragged down) has crossed
     * far enough to count as moved. Moving one slot needs `offsetPx >=
     * thresholdPx`; each further slot needs another full [itemHeightPx] on
     * top of that. Symmetric for a negative (upward) offset.
     */
    fun slotsMoved(offsetPx: Float, itemHeightPx: Float, thresholdPx: Float): Int {
        if (itemHeightPx <= 0f) return 0
        val direction = if (offsetPx >= 0f) 1 else -1
        val magnitude = kotlin.math.abs(offsetPx)
        if (magnitude < thresholdPx) return 0
        return direction * (floor((magnitude - thresholdPx) / itemHeightPx).toInt() + 1)
    }

    /** Where item [draggedIndex] (of [count] items, 0-based) currently belongs, clamped to a real index. */
    fun targetIndex(draggedIndex: Int, offsetPx: Float, itemHeightPx: Float, thresholdPx: Float, count: Int): Int {
        if (count <= 0) return 0
        val moved = slotsMoved(offsetPx, itemHeightPx, thresholdPx)
        return (draggedIndex + moved).coerceIn(0, count - 1)
    }

    /** [items] with whatever is at [fromIndex] moved to [toIndex]; every other item shifts to close the gap. */
    fun <T> reordered(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
        if (items.isEmpty() || fromIndex !in items.indices) return items
        val target = toIndex.coerceIn(0, items.size - 1)
        if (target == fromIndex) return items
        val mutable = items.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(target, moved)
        return mutable
    }
}
