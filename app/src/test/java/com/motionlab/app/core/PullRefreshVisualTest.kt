package com.motionlab.app.core

import com.motionlab.app.core.physics.ParameterMapping
import com.motionlab.app.core.physics.PullRefreshSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRefreshVisualTest {
    /** The bug: plain d/(1+kd) tops out at 1/k px, far below a 40-120 dp trigger, so a pull could never complete. */
    @Test fun `every resistance and trigger setting can be pulled past the line on any screen density`() {
        for (density in listOf(1f, 2f, 2.625f, 3.5f)) for (rt in listOf(0f, 0.5f, 1f)) for (tt in listOf(0f, 0.5f, 1f)) {
            val k = ParameterMapping.resistance(rt)
            val trigger = ParameterMapping.pullTriggerDp(tt) * density
            val visual = PullRefreshSolver.visual(20f * trigger, k, trigger) // a long pull
            assertTrue("density $density k=$k trigger=$trigger visual=$visual", PullRefreshSolver.triggered(visual, trigger))
        }
    }

    @Test fun `more resistance means you pull further for the same result`() {
        val trigger = 210f
        val soft = PullRefreshSolver.visual(300f, ParameterMapping.resistance(0f), trigger)
        val stiff = PullRefreshSolver.visual(300f, ParameterMapping.resistance(1f), trigger)
        assertTrue("$soft > $stiff", soft > stiff)
    }

    @Test fun `it starts at zero, follows the finger closely at first and never overtakes it`() {
        val k = ParameterMapping.resistance(0.5f)
        assertEquals(0f, PullRefreshSolver.visual(0f, k, 210f), 0f)
        assertEquals(10f, PullRefreshSolver.visual(10f, k, 210f), 0.5f)
        for (raw in listOf(50f, 200f, 800f)) assertTrue(PullRefreshSolver.visual(raw, k, 210f) < raw)
    }
}
