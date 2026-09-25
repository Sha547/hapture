package com.motionlab.app.core

import com.motionlab.app.core.physics.ParameterMapping
import com.motionlab.app.core.physics.PullRefreshSolver
import com.motionlab.app.core.physics.ReorderSolver
import com.motionlab.app.core.physics.ZoomSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRefreshSolverTest {
    @Test fun `below the trigger distance is not triggered`() {
        assertFalse(PullRefreshSolver.triggered(50f, 80f))
        assertFalse(PullRefreshSolver.triggered(0f, 80f))
    }

    @Test fun `at or past the trigger distance is triggered`() {
        assertTrue(PullRefreshSolver.triggered(80f, 80f))
        assertTrue(PullRefreshSolver.triggered(200f, 80f))
    }

    @Test fun `a longer required hold maps to a higher slider value`() {
        assertTrue(ParameterMapping.pullHoldMs(1f) > ParameterMapping.pullHoldMs(0f))
    }
}

class ZoomSolverTest {
    private val min = 0.6f
    private val max = 2f
    private val k = 0.05f

    @Test fun `within bounds passes straight through`() {
        assertEquals(1f, ZoomSolver.visualScale(1f, min, max, k), 1e-6f)
        assertEquals(min, ZoomSolver.visualScale(min, min, max, k), 1e-6f)
        assertEquals(max, ZoomSolver.visualScale(max, min, max, k), 1e-6f)
    }

    @Test fun `past max resists rather than tracking one to one`() {
        val visual = ZoomSolver.visualScale(3f, min, max, k)
        assertTrue(visual in max..3f)
        assertTrue(visual < 3f)
    }

    @Test fun `below min resists rather than tracking one to one`() {
        val visual = ZoomSolver.visualScale(0.2f, min, max, k)
        assertTrue(visual in 0.2f..min)
        assertTrue(visual > 0.2f)
    }

    @Test fun `resistance grows the further past a bound you go`() {
        val near = ZoomSolver.visualScale(max + 0.2f, min, max, k) - max
        val far = ZoomSolver.visualScale(max + 2f, min, max, k) - max
        assertTrue(far > near) // absolute excess grows...
        assertTrue(far / 2f < near / 0.2f) // ...but ever more slowly, not one-to-one
    }

    @Test fun `never crosses to the other side of a bound`() {
        assertTrue(ZoomSolver.visualScale(100f, min, max, k) > max)
        assertTrue(ZoomSolver.visualScale(-100f, min, max, k) in 0f..min)
    }

    @Test fun `a wider zoom range maps to more extreme scale endpoints`() {
        assertTrue(ParameterMapping.zoomMinScale(1f) < ParameterMapping.zoomMinScale(0f))
        assertTrue(ParameterMapping.zoomMaxScale(1f) > ParameterMapping.zoomMaxScale(0f))
    }
}

class ReorderSolverTest {
    private val itemHeight = 100f
    private val threshold = 40f

    @Test fun `no movement until the threshold is crossed`() {
        assertEquals(0, ReorderSolver.slotsMoved(0f, itemHeight, threshold))
        assertEquals(0, ReorderSolver.slotsMoved(39f, itemHeight, threshold))
        assertEquals(0, ReorderSolver.slotsMoved(-39f, itemHeight, threshold))
    }

    @Test fun `crossing the threshold moves exactly one slot`() {
        assertEquals(1, ReorderSolver.slotsMoved(40f, itemHeight, threshold))
        assertEquals(1, ReorderSolver.slotsMoved(139f, itemHeight, threshold))
        assertEquals(-1, ReorderSolver.slotsMoved(-40f, itemHeight, threshold))
    }

    @Test fun `each further full item height moves one more slot`() {
        assertEquals(2, ReorderSolver.slotsMoved(140f, itemHeight, threshold))
        assertEquals(3, ReorderSolver.slotsMoved(240f, itemHeight, threshold))
        assertEquals(-2, ReorderSolver.slotsMoved(-140f, itemHeight, threshold))
    }

    @Test fun `target index clamps to the real list bounds`() {
        assertEquals(3, ReorderSolver.targetIndex(0, 1000f, itemHeight, threshold, count = 4))
        assertEquals(0, ReorderSolver.targetIndex(3, -1000f, itemHeight, threshold, count = 4))
        assertEquals(0, ReorderSolver.targetIndex(0, 0f, itemHeight, threshold, count = 1))
    }

    @Test fun `reordered moves the item and shifts the rest, nothing duplicated or dropped`() {
        val list = listOf("A", "B", "C", "D")
        assertEquals(listOf("B", "C", "A", "D"), ReorderSolver.reordered(list, fromIndex = 0, toIndex = 2))
        assertEquals(listOf("A", "D", "B", "C"), ReorderSolver.reordered(list, fromIndex = 3, toIndex = 1))
    }

    @Test fun `reordering to the same spot changes nothing`() {
        val list = listOf("A", "B", "C")
        assertEquals(list, ReorderSolver.reordered(list, fromIndex = 1, toIndex = 1))
    }

    @Test fun `an out of range source index is a no-op, not a crash`() {
        val list = listOf("A", "B")
        assertEquals(list, ReorderSolver.reordered(list, fromIndex = 9, toIndex = 0))
        assertEquals(emptyList<String>(), ReorderSolver.reordered(emptyList<String>(), 0, 1))
    }

    @Test fun `a lower slider value requires a bigger nudge to swap`() {
        assertTrue(ParameterMapping.reorderThresholdFraction(0f) > ParameterMapping.reorderThresholdFraction(1f))
    }
}
