package com.klynstudios.hapture.core

import com.klynstudios.hapture.core.physics.CardExpandSolver
import com.klynstudios.hapture.core.physics.CardExpandSolver.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardExpandSolverTest {

    @Test fun progressZeroIsTheCardAndOneIsThePage() {
        val card = Box(10f, 20f, 110f, 120f)
        val page = Box(0f, 0f, 400f, 300f)
        assertEquals(card, CardExpandSolver.lerp(card, page, 0f))
        assertEquals(page, CardExpandSolver.lerp(card, page, 1f))
        val half = CardExpandSolver.lerp(card, page, 0.5f)
        assertEquals(5f, half.left, 1e-4f)
        assertEquals(255f, half.right, 1e-4f)
    }

    @Test fun gridCellsTileTheWidthWithEqualGaps() {
        val a = CardExpandSolver.cell(0, 2, 400f, 16f, 12f, 100f)
        val b = CardExpandSolver.cell(1, 2, 400f, 16f, 12f, 100f)
        val c = CardExpandSolver.cell(2, 2, 400f, 16f, 12f, 100f)
        assertEquals(16f, a.left, 1e-4f)
        assertEquals(384f, b.right, 1e-4f)
        assertEquals(12f, b.left - a.right, 1e-4f)
        assertEquals(a.width, b.width, 1e-4f)
        assertEquals(a.bottom + 12f, c.top, 1e-4f)
    }

    @Test fun cornersStraightenAndNeverGoNegative() {
        assertEquals(24f, CardExpandSolver.cornerPx(24f, 0f), 0f)
        assertEquals(0f, CardExpandSolver.cornerPx(24f, 1f), 0f)
        assertEquals(0f, CardExpandSolver.cornerPx(24f, 1.2f), 0f)
    }

    @Test fun theTwoFacesFadeThroughWithoutOverlapping() {
        for (f in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val card = CardExpandSolver.cardContentAlpha(f)
            val page = CardExpandSolver.pageContentAlpha(f)
            assertTrue("both visible at $f", card == 0f || page == 0f)
        }
        assertEquals(1f, CardExpandSolver.cardContentAlpha(0f), 0f)
        assertEquals(1f, CardExpandSolver.pageContentAlpha(1f), 0f)
    }
}
