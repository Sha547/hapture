package com.klynstudios.hapture.core

import com.klynstudios.hapture.core.physics.ParameterMapping
import com.klynstudios.hapture.core.physics.PredictiveBackSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictiveBackSolverTest {

    @Test fun restingPageIsUntouched() {
        assertEquals(1f, PredictiveBackSolver.scale(0f, 0.9f), 0f)
        assertEquals(0f, PredictiveBackSolver.shiftPx(0f, 0f, 400f, 0.1f, true), 0f)
        assertEquals(0f, PredictiveBackSolver.cornerDp(0f), 0f)
    }

    @Test fun fullSwipeReachesTheTunedScaleAndLean() {
        assertEquals(0.9f, PredictiveBackSolver.scale(1f, 0.9f), 1e-6f)
        assertEquals(40f, PredictiveBackSolver.shiftPx(1f, 0f, 400f, 0.1f, true), 1e-4f)
        assertEquals(-40f, PredictiveBackSolver.shiftPx(1f, 0f, 400f, 0.1f, false), 1e-4f)
        assertEquals(PredictiveBackSolver.MAX_CORNER_DP, PredictiveBackSolver.cornerDp(1f), 0f)
    }

    @Test fun exitCarriesThePageFullyOff() {
        assertTrue(PredictiveBackSolver.shiftPx(1f, 1f, 400f, 0.1f, true) >= 400f)
    }

    @Test fun commitsPastAThirdOrOnAFlick() {
        assertFalse(PredictiveBackSolver.commits(0.2f, 0f))
        assertTrue(PredictiveBackSolver.commits(0.4f, 0f))
        assertTrue(PredictiveBackSolver.commits(0.1f, 2f))
    }

    @Test fun materialDefaultScaleSitsInsideTheSliderRange() {
        assertTrue(0.9f in ParameterMapping.backMinScale(1f)..ParameterMapping.backMinScale(0f))
    }
}
