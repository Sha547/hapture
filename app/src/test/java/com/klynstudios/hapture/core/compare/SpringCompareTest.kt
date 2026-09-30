package com.klynstudios.hapture.core.compare

import com.klynstudios.hapture.core.model.MotionPreset
import com.klynstudios.hapture.core.spec.SpringSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SpringCompareTest {
    private val a = SpringSpec(100f, 0.2f)
    private val b = SpringSpec(2500f, 1.0f)

    @Test fun `blend hits both ends`() {
        assertEquals(a.stiffness, SpringCompare.blend(a, b, 0f).stiffness, 0.01f)
        assertEquals(b.stiffness, SpringCompare.blend(a, b, 1f).stiffness, 0.5f)
        assertEquals(b.dampingRatio, SpringCompare.blend(a, b, 1f).dampingRatio, 1e-4f)
    }

    @Test fun `blend is geometric in stiffness and linear in damping`() {
        val m = SpringCompare.blend(a, b, 0.5f)
        assertEquals(Math.sqrt(100.0 * 2500.0).toFloat(), m.stiffness, 0.5f)
        assertEquals(0.6f, m.dampingRatio, 1e-4f)
    }

    @Test fun `blend clamps out-of-range t`() {
        assertEquals(SpringCompare.blend(a, b, 0f), SpringCompare.blend(a, b, -3f))
        assertEquals(SpringCompare.blend(a, b, 1f), SpringCompare.blend(a, b, 9f))
    }

    @Test fun `curves share a clock and end at rest`() {
        val c = SpringCompare.curves(listOf(a, b))
        assertEquals(2, c.size)
        assertEquals(0f, c[0].first(), 1e-4f)
        // the stiff, damped spring is already at 1 long before the slow one is
        assertEquals(1f, c[1].last(), 0.01f)
        assertTrue(c[1][c[1].size / 2] > 0.99f)
        assertTrue(c[0][2] < c[1][2])
    }

    @Test fun `ticks merge near-simultaneous settles`() {
        assertEquals(listOf(300, 700), SpringCompare.settleTicks(listOf(700, 300)))
        assertEquals(listOf(300), SpringCompare.settleTicks(listOf(300, 330)))
        assertEquals(listOf(300, 400), SpringCompare.settleTicks(listOf(300, 400, 410)))
    }

    @Test fun `blind rounds are always fair pairs`() {
        val r = Random(7)
        repeat(200) {
            val round = SpringCompare.newRound(r)
            assertNotEquals(round.a, round.b)
            assertTrue(SpringCompare.distinct(round.a, round.b))
        }
    }

    @Test fun `guessing scores and streaks`() {
        val round = SpringCompare.BlindRound(MotionPreset.BOUNCY, MotionPreset.HEAVY)
        assertTrue(round.isCorrect(MotionPreset.BOUNCY))
        assertTrue(!round.isCorrect(MotionPreset.HEAVY))
        assertEquals(3, SpringCompare.nextStreak(2, true))
        assertEquals(0, SpringCompare.nextStreak(5, false))
    }
}
