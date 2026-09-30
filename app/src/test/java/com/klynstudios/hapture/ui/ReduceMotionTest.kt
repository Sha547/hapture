package com.klynstudios.hapture.ui

import com.klynstudios.hapture.ui.design.isMotionReduced
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReduceMotionTest {
    @Test fun `motion is reduced only when the system animator scale is zero`() {
        assertTrue(isMotionReduced(0f))
        assertFalse(isMotionReduced(1f))
        assertFalse(isMotionReduced(0.5f))   // "animation scale 0.5x" is faster, not off
        assertFalse(isMotionReduced(10f))
    }
}
