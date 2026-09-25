package com.motionlab.app.data

/**
 * Pure position-list arithmetic for [TimelineRepository], split out so it's
 * testable without a database: given the current steps (already sorted by
 * position), what should each one's position become after an edit.
 */
internal object TimelineOrdering {

    /** Positions 0..size-1, contiguous, in whatever order [steps] is already in. */
    fun repositioned(steps: List<TimelineStepEntity>): List<TimelineStepEntity> =
        steps.mapIndexed { i, s -> if (s.position == i) s else s.copy(position = i) }

    /**
     * [steps] sorted by position, with the one at [fromIndex] moved to
     * [toIndex] (clamped into range), and every position renumbered to match.
     * Moving the first step up, or the last step down, is a no-op.
     */
    fun moved(steps: List<TimelineStepEntity>, fromIndex: Int, toIndex: Int): List<TimelineStepEntity> {
        if (steps.isEmpty()) return steps
        val target = toIndex.coerceIn(0, steps.size - 1)
        if (target == fromIndex || fromIndex !in steps.indices) return repositioned(steps)
        val mutable = steps.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(target, item)
        return repositioned(mutable)
    }
}
