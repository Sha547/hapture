package com.klynstudios.hapture.core.model

import com.klynstudios.hapture.core.model.TimelineFrames.StepSpring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineFramesTest {
    private val segs = listOf(TimelineSegment(0, 600, "a"), TimelineSegment(300, 800, "b"))
    private val springs = listOf(StepSpring(400f, 0.8f), StepSpring(200f, 1f))

    @Test fun `total includes the last marker and a held tail, frames cover it`() {
        assertEquals(1700 + TimelineFrames.TAIL_MS, TimelineFrames.totalMs(segs, emptyList()))
        assertEquals(3000 + TimelineFrames.TAIL_MS, TimelineFrames.totalMs(segs, listOf(3000)))
        assertEquals(31, TimelineFrames.frameCount(1000))
        assertEquals(1000, TimelineFrames.timeMs(30))
    }

    @Test fun `a step starts at one side and finishes at the other, like the app preview`() {
        assertEquals(-1f, TimelineFrames.frameAt(0, segs, springs, emptyList()).offset, 1e-3f)
        val nearEnd = TimelineFrames.frameAt(590, segs, springs, emptyList())
        assertEquals(0, nearEnd.stepIndex)
        assertTrue("offset ${nearEnd.offset}", nearEnd.offset > 0.9f)
    }

    @Test fun `gaps and the end have no active step and sit at rest`() {
        val gap = TimelineFrames.frameAt(700, segs, springs, emptyList())
        assertNull(gap.stepIndex)
        assertEquals(0f, gap.offset, 0f)
        assertNull(TimelineFrames.frameAt(1700, segs, springs, emptyList()).stepIndex)
        assertEquals(1, TimelineFrames.frameAt(1000, segs, springs, emptyList()).stepIndex)
    }

    @Test fun `a haptic tick lights when it fires and fades out`() {
        val markers = listOf(500)
        assertTrue(TimelineFrames.frameAt(499, segs, springs, markers).firing.isEmpty())
        val just = TimelineFrames.frameAt(500, segs, springs, markers)
        assertEquals(listOf(0), just.firing)
        assertEquals(1f, just.pulse, 1e-3f)
        val mid = TimelineFrames.frameAt(500 + TimelineFrames.PULSE_MS / 2, segs, springs, markers)
        assertEquals(0.5f, mid.pulse, 0.02f)
        assertTrue(TimelineFrames.frameAt(500 + TimelineFrames.PULSE_MS, segs, springs, markers).firing.isEmpty())
    }

    @Test fun `no steps and no markers still gives an at-rest frame`() {
        val f = TimelineFrames.frameAt(0, emptyList(), emptyList(), emptyList())
        assertNull(f.stepIndex)
        assertEquals(0f, f.pulse, 0f)
    }
}
