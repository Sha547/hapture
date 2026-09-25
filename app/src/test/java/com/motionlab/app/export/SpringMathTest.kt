package com.motionlab.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class SpringMathTest {

    @Test fun `step starts at zero and ends at one for every damping regime`() {
        for (zeta in listOf(0.15, 0.6, 1.0, 1.5)) {
            assertEquals(0.0, SpringMath.stepResponse(0.0, 800.0, zeta), 1e-9)
            assertEquals(1.0, SpringMath.stepResponse(20.0, 800.0, zeta), 1e-6)
        }
    }

    @Test fun `regimes join continuously around critical damping`() {
        val under = SpringMath.stepResponse(0.1, 800.0, 0.9999)
        val critical = SpringMath.stepResponse(0.1, 800.0, 1.0)
        val over = SpringMath.stepResponse(0.1, 800.0, 1.0001)
        assertEquals(critical, under, 1e-3)
        assertEquals(critical, over, 1e-3)
    }

    @Test fun `underdamped overshoot matches the closed form and the sampled peak`() {
        val zeta = 0.3f
        val expected = SpringMath.overshoot(zeta)
        val peak = (0..2000).maxOf { SpringMath.stepResponse(it / 1000.0, 500.0, zeta.toDouble()) } - 1.0
        assertEquals(expected.toDouble(), peak, 2e-3)
    }

    @Test fun `no overshoot at or above critical damping`() {
        assertEquals(0f, SpringMath.overshoot(1f), 0f)
        assertEquals(0f, SpringMath.overshoot(1.4f), 0f)
        val peak = (0..3000).maxOf { SpringMath.stepResponse(it / 1000.0, 500.0, 1.2) }
        assertTrue(peak <= 1.0 + 1e-9)
    }

    @Test fun `damping coefficient is 2 zeta sqrt k for unit mass`() {
        assertEquals(2f * 0.5f * sqrt(400f), SpringMath.damping(400f, 0.5f), 1e-4f)
    }

    @Test fun `stiffer springs settle faster and softer damping settles slower`() {
        assertTrue(SpringMath.settleMs(2000f, 0.6f) < SpringMath.settleMs(200f, 0.6f))
        assertTrue(SpringMath.settleMs(800f, 0.2f) > SpringMath.settleMs(800f, 0.9f))
    }

    @Test fun `settled means it stays within tolerance afterwards`() {
        val k = 700f; val z = 0.35f
        val settle = SpringMath.settleMs(k, z)
        for (ms in settle..(settle + 2000) step 7) {
            assertTrue(abs(1.0 - SpringMath.stepResponse(ms / 1000.0, k.toDouble(), z.toDouble())) <= 0.001 + 1e-9)
        }
    }

    @Test fun `samples run from exactly zero to exactly one`() {
        val s = SpringMath.samples(600f, 0.5f, count = 48)
        assertEquals(48, s.size)
        assertEquals(0f, s.first(), 0f)
        assertEquals(1f, s.last(), 0f)
    }
}
