package com.klynstudios.hapture.export

import com.klynstudios.hapture.core.physics.SwipeSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeCodeGeneratorTest {
    private fun code() = SwipeCodeGenerator.generate(
        stiffness = 812.4f, dampingRatio = 0.62f,
        distanceDp = 85.2f, velocityDpPerSec = 825f, friction = 1.4f, tiltDegrees = 8f,
    )

    @Test fun `output is deterministic`() = assertEquals(code(), code())

    @Test fun `numbers appear exactly as tuned, in dp so density cannot change the feel`() {
        val c = code()
        assertTrue(c.contains("85.dp.toPx()"))
        assertTrue(c.contains("825.dp.toPx()"))
        assertTrue(c.contains("frictionMultiplier = friction"))
        assertTrue(c.contains("val friction = 1.4f"))
        assertTrue(c.contains("val maxTilt = 8f"))
        assertTrue(c.contains("dampingRatio = 0.62f, stiffness = 812.4f"))
    }

    @Test fun `it carries the same decision and tilt formulas as the solver`() {
        val c = code()
        // The three rules of SwipeSolver.dismissDirection, verbatim.
        assertTrue(c.contains("kotlin.math.abs(velocity) >= velocityThreshold) return if (velocity > 0f) 1 else -1"))
        assertTrue(c.contains("kotlin.math.abs(offset) >= distance) return if (offset > 0f) 1 else -1"))
        assertTrue(c.contains("maxTilt * (offset / (distance * 3f)).coerceIn(-1f, 1f)"))
        // ...and the solver still behaves the way those lines say.
        assertEquals(1, SwipeSolver.dismissDirection(0f, 5f, 1f, 5f))
        assertEquals(1, SwipeSolver.dismissDirection(2f, 0f, 2f, 5f))
    }

    @Test fun `no unexpanded template placeholders leak into the code`() {
        assertFalse(code().contains("\${"))
        assertFalse(code().contains("$"))
    }
}
