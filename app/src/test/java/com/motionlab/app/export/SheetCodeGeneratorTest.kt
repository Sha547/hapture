package com.motionlab.app.export

import com.motionlab.app.core.physics.SheetSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetCodeGeneratorTest {
    private fun code() = SheetCodeGenerator.generate(
        stiffness = 700f, dampingRatio = 0.75f,
        peekFraction = 0.22f, midFraction = 0.57f, fullFraction = 0.92f,
        momentumSec = 0.14f, dismissLineFraction = 0.55f, resistanceK = 0.045f,
    )

    @Test fun `output is deterministic`() = assertEquals(code(), code())

    @Test fun `detents are emitted as fractions of the container so they scale anywhere`() {
        val c = code()
        assertTrue(c.contains("val peek = container * 0.22f"))
        assertTrue(c.contains("container * 0.57f"))
        assertTrue(c.contains("container * 0.92f"))
        assertTrue(c.contains("val dismissLine = peek * 0.55f"))
        assertTrue(c.contains("dampingRatio = 0.75f, stiffness = 700f"))
        assertTrue(c.contains("0.045f"))
    }

    @Test fun `it carries the same settle and resistance rules as the solver`() {
        val c = code()
        assertTrue(c.contains("val target = height + velocityUp * lookaheadSec"))
        assertTrue(c.contains("if (target < dismissLine) return 0f"))
        assertTrue(c.contains("for (d in detents) if (kotlin.math.abs(target - d) < kotlin.math.abs(target - best)) best = d"))
        assertTrue(c.contains("return full + excess / (1 + k * kotlin.math.abs(excess))"))
        // ...and the solver still behaves the way those lines say.
        assertEquals(0f, SheetSolver.settleHeight(10f, 0f, 0.1f, floatArrayOf(50f, 100f), 30f), 0f)
    }

    @Test fun `the generated drag tracks the height, not the pointer-local position`() {
        assertTrue(code().contains("tracker.addPosition(change.uptimeMillis, Offset(0f, visual))"))
        assertFalse(code().contains("change.position"))
    }

    @Test fun `no unexpanded template placeholders leak into the code`() {
        assertFalse(code().contains("\${"))
        assertFalse(code().contains("$"))
    }
}
