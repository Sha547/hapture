package com.klynstudios.hapture.core

import com.klynstudios.hapture.core.physics.ParameterMapping
import com.klynstudios.hapture.core.physics.SheetSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetSolverTest {
    // A 300px container: peek 90, middle 180, full 276.
    private val detents = floatArrayOf(90f, 180f, 276f)
    private val dismissLine = 45f
    private val lookahead = 0.1f

    private fun settle(h: Float, v: Float) = SheetSolver.settleHeight(h, v, lookahead, detents, dismissLine)

    @Test fun `let go at rest and it settles at the nearest detent`() {
        assertEquals(90f, settle(100f, 0f), 0f)
        assertEquals(180f, settle(150f, 0f), 0f)
        assertEquals(276f, settle(260f, 0f), 0f)
    }

    @Test fun `a flick upward carries it to the next detent`() {
        // At the peek but moving up fast: 90 + 1200 * 0.1 = 210, nearest is the middle.
        assertEquals(180f, settle(90f, 1200f), 0f)
        assertEquals(276f, settle(180f, 1200f), 0f)
    }

    @Test fun `a flick downward steps down one detent`() {
        assertEquals(90f, settle(180f, -1200f), 0f)
        assertEquals(180f, settle(276f, -1000f), 0f)
    }

    @Test fun `pulling below the dismiss line, or flicking down from the peek, dismisses`() {
        assertEquals(0f, settle(30f, 0f), 0f)
        assertEquals(0f, settle(90f, -1500f), 0f)
    }

    @Test fun `just above the dismiss line it stays at the peek`() {
        assertEquals(90f, settle(50f, 0f), 0f)
    }

    @Test fun `it never settles between detents or above full`() {
        for (h in 0..400 step 7) for (v in -3000..3000 step 250) {
            val r = settle(h.toFloat(), v.toFloat())
            assertTrue("h=$h v=$v -> $r", r == 0f || r in detents.toList())
        }
    }

    @Test fun `dragging up is one to one until full, then resists`() {
        assertEquals(150f, SheetSolver.visualHeight(150f, 276f, 0.05f), 0f)
        assertEquals(276f, SheetSolver.visualHeight(276f, 276f, 0.05f), 0f)
        val over = SheetSolver.visualHeight(376f, 276f, 0.05f)
        assertTrue("over=$over", over > 276f && over < 376f)
        // Resistance grows: the next 100px moves it less than the first 100.
        val more = SheetSolver.visualHeight(476f, 276f, 0.05f)
        assertTrue(more - over < over - 276f)
    }

    @Test fun `it can be dragged all the way down but never below hidden`() {
        assertEquals(0f, SheetSolver.visualHeight(-40f, 276f, 0.05f), 0f)
        assertEquals(12f, SheetSolver.visualHeight(12f, 276f, 0.05f), 0f)
    }
}

class SheetMappingTest {
    private val steps = (0..20).map { it / 20f }

    @Test fun `for every slider combination peek is below middle and middle is below full`() {
        for (p in steps) for (m in steps) {
            val peek = ParameterMapping.sheetPeekFraction(p)
            val mid = ParameterMapping.sheetMidFraction(m)
            assertTrue("peek=$peek mid=$mid", peek < mid)
            assertTrue("mid=$mid", mid < ParameterMapping.SHEET_FULL_FRACTION)
        }
    }

    @Test fun `more momentum looks further ahead and easier dismiss draws the line closer`() {
        assertTrue(ParameterMapping.sheetMomentumSec(1f) > ParameterMapping.sheetMomentumSec(0f))
        assertTrue(ParameterMapping.sheetDismissLineFraction(1f) > ParameterMapping.sheetDismissLineFraction(0f))
    }

    @Test fun `the dismiss line always sits below the peek`() {
        for (t in steps) assertTrue(ParameterMapping.sheetDismissLineFraction(t) < 1f)
    }
}
