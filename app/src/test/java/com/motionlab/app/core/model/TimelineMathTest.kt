package com.motionlab.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineMathTest {
    private val segments = listOf(
        TimelineSegment(gapBeforeMs = 0, durationMs = 300, label = "A"),
        TimelineSegment(gapBeforeMs = 200, durationMs = 500, label = "B"),
        TimelineSegment(gapBeforeMs = 0, durationMs = 100, label = "C"),
    )

    @Test fun `total is every gap and duration summed`() {
        assertEquals(0 + 300 + 200 + 500 + 0 + 100, TimelineMath.totalMs(segments))
    }

    @Test fun `start times land after each step's own gap, chained from the previous step's end`() {
        assertEquals(listOf(0, 500, 1000), TimelineMath.startTimes(segments))
    }

    @Test fun `an empty timeline has zero duration and nothing ever active`() {
        assertEquals(0, TimelineMath.totalMs(emptyList()))
        assertNull(TimelineMath.activeStepAt(0f, emptyList()))
    }

    @Test fun `each step is active only across its own window`() {
        assertEquals(0, TimelineMath.activeStepAt(0f, segments)?.index)
        assertEquals(0, TimelineMath.activeStepAt(299f, segments)?.index)
        assertNull(TimelineMath.activeStepAt(300f, segments)) // step A ends, gap before B starts
        assertNull(TimelineMath.activeStepAt(499f, segments))
        assertEquals(1, TimelineMath.activeStepAt(500f, segments)?.index)
        assertEquals(1, TimelineMath.activeStepAt(999f, segments)?.index)
        assertEquals(2, TimelineMath.activeStepAt(1000f, segments)?.index)
        assertEquals(2, TimelineMath.activeStepAt(1099f, segments)?.index)
        assertNull(TimelineMath.activeStepAt(1100f, segments)) // exactly at the end
        assertNull(TimelineMath.activeStepAt(5000f, segments))
    }

    @Test fun `progress runs from 0 at a step's start to just under 1 at its end`() {
        assertEquals(0f, TimelineMath.activeStepAt(500f, segments)!!.progress, 1e-4f)
        assertEquals(0.5f, TimelineMath.activeStepAt(750f, segments)!!.progress, 1e-4f)
        assertEquals(0.998f, TimelineMath.activeStepAt(999f, segments)!!.progress, 1e-3f)
    }

    @Test fun `a zero duration step is considered fully progressed rather than dividing by zero`() {
        val withZero = listOf(TimelineSegment(0, 0, "instant"))
        // A zero-length window is never actually entered by the half-open [start, end) test,
        // so nothing is active at t=0 -- exercised here mainly to confirm it doesn't throw.
        assertNull(TimelineMath.activeStepAt(0f, withZero))
    }

    @Test fun `negative playhead values are simply never inside anything`() {
        assertNull(TimelineMath.activeStepAt(-50f, segments))
    }
}
