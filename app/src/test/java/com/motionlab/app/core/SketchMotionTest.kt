package com.motionlab.app.core

import com.motionlab.app.core.capture.SketchMotion
import com.motionlab.app.core.capture.SketchMotion.TimedPoint
import com.motionlab.app.core.capture.SpringFit
import com.motionlab.app.export.SpringMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SketchMotionTest {

    /** A finger following a known spring diagonally across the screen, with uneven touch timing and a hold before it moves. */
    private fun stroke(k: Double, z: Double, holdMs: Int = 150, seed: Int = 1): List<TimedPoint> {
        val rnd = Random(seed)
        val out = ArrayList<TimedPoint>()
        var t = 0
        while (t <= 1400) {
            val s = SpringMath.stepResponse(((t - holdMs).coerceAtLeast(0)) / 1000.0, k, z).toFloat()
            out += TimedPoint(0.2f + 0.5f * s, 0.8f - 0.4f * s, t)
            t += 6 + rnd.nextInt(6)
        }
        return out
    }

    @Test fun `a sketched critically damped move fits back to about the same spring`() {
        val fit = SpringFit.fit(SketchMotion.toCurve(stroke(500.0, 1.0))!!)!!
        assertEquals(1.0f, fit.dampingRatio, 0.2f)
        assertEquals(500f, fit.stiffness, 500f * 0.25f)
    }

    @Test fun `going past the target and back reads as a bouncy spring`() {
        val fit = SpringFit.fit(SketchMotion.toCurve(stroke(400.0, 0.3))!!)!!
        assertTrue("z=${fit.dampingRatio}", fit.dampingRatio < 0.5f)
        assertEquals(400f, fit.stiffness, 400f * 0.25f)
    }

    @Test fun `the start hold does not count as part of the motion`() {
        val c = SketchMotion.toCurve(stroke(500.0, 1.0, holdMs = 400))!!
        assertTrue("starts near zero at t=0: ${c.first()}", c.first().second < 0.05f && c.first().first == 0f)
    }

    @Test fun `too short, too small or too few points are refused`() {
        assertNull(SketchMotion.toCurve(emptyList()))
        assertNull(SketchMotion.toCurve((0..10).map { TimedPoint(0.5f, 0.5f, it * 30) }))            // never moved
        assertNull(SketchMotion.toCurve((0..10).map { TimedPoint(0.1f + it * 0.05f, 0.5f, it * 5) })) // over in 50 ms
        assertNotNull(SketchMotion.toCurve(stroke(500.0, 1.0)))
    }

    @Test fun `direction does not matter`() {
        val flipped = stroke(500.0, 0.6).map { it.copy(x = 1f - it.x, y = 1f - it.y) }
        val a = SpringFit.fit(SketchMotion.toCurve(stroke(500.0, 0.6))!!)!!
        val b = SpringFit.fit(SketchMotion.toCurve(flipped)!!)!!
        assertEquals(a.stiffness, b.stiffness, a.stiffness * 0.05f)
        assertEquals(a.dampingRatio, b.dampingRatio, 0.05f)
    }
}
