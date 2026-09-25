package com.motionlab.app.core.model

/** One step laid out on a timeline: a silent [gapBeforeMs], then [durationMs] of motion. */
data class TimelineSegment(val gapBeforeMs: Int, val durationMs: Int, val label: String)

/** Which step is playing at a given moment, and how far into its own motion. */
data class ActiveTimelineStep(val index: Int, val progress: Float)

object TimelineMath {

    /** Sum of every gap and duration -- how long the whole sequence takes. */
    fun totalMs(segments: List<TimelineSegment>): Int = segments.sumOf { it.gapBeforeMs + it.durationMs }

    /** Each step's start time (after its own gap), in the same order as [segments]. */
    fun startTimes(segments: List<TimelineSegment>): List<Int> {
        var cursor = 0
        return segments.map { seg ->
            cursor += seg.gapBeforeMs
            val start = cursor
            cursor += seg.durationMs
            start
        }
    }

    /**
     * Which step [playheadMs] falls inside, and how far through that step's own
     * duration (0f..1f). Null while in a gap, before the first step, or at/after
     * the end -- there's nothing actively moving at those moments.
     */
    fun activeStepAt(playheadMs: Float, segments: List<TimelineSegment>): ActiveTimelineStep? {
        var cursor = 0
        segments.forEachIndexed { i, seg ->
            cursor += seg.gapBeforeMs
            val start = cursor
            cursor += seg.durationMs
            val end = cursor
            if (playheadMs >= start && playheadMs < end) {
                val progress = if (seg.durationMs <= 0) 1f else ((playheadMs - start) / seg.durationMs).coerceIn(0f, 1f)
                return ActiveTimelineStep(i, progress)
            }
        }
        return null
    }
}
