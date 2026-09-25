package com.motionlab.app.core

import com.motionlab.app.core.haptics.HapticEffect
import com.motionlab.app.core.haptics.HapticEvent
import com.motionlab.app.core.haptics.HapticPreset
import com.motionlab.app.core.physics.ParameterMapping
import com.motionlab.app.core.physics.SwipeSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeSolverTest {
    private val distance = 100f
    private val threshold = 1000f

    @Test fun `a short slow release springs back`() {
        assertEquals(0, SwipeSolver.dismissDirection(40f, 200f, distance, threshold))
        assertEquals(0, SwipeSolver.dismissDirection(-99.9f, -999f, distance, threshold))
        assertEquals(0, SwipeSolver.dismissDirection(0f, 0f, distance, threshold))
    }

    @Test fun `dragging past the distance dismisses in that direction`() {
        assertEquals(1, SwipeSolver.dismissDirection(100f, 0f, distance, threshold))
        assertEquals(-1, SwipeSolver.dismissDirection(-150f, 0f, distance, threshold))
    }

    @Test fun `a fast flick dismisses even from a tiny drag`() {
        assertEquals(1, SwipeSolver.dismissDirection(5f, 1000f, distance, threshold))
        assertEquals(-1, SwipeSolver.dismissDirection(5f, -2500f, distance, threshold))
    }

    @Test fun `a flick against the drag wins, because velocity is intent`() {
        assertEquals(-1, SwipeSolver.dismissDirection(150f, -1500f, distance, threshold))
    }

    @Test fun `tilt is proportional, signed and capped`() {
        assertEquals(0f, SwipeSolver.tiltDegrees(0f, 100f, 12f), 1e-6f)
        assertEquals(6f, SwipeSolver.tiltDegrees(150f, 100f, 12f), 1e-4f)
        assertEquals(12f, SwipeSolver.tiltDegrees(300f, 100f, 12f), 1e-4f)
        assertEquals(12f, SwipeSolver.tiltDegrees(9000f, 100f, 12f), 1e-4f)
        assertEquals(-12f, SwipeSolver.tiltDegrees(-9000f, 100f, 12f), 1e-4f)
        assertEquals(0f, SwipeSolver.tiltDegrees(500f, 100f, 0f), 1e-6f)
    }
}

class FlingMappingTest {
    @Test fun `ranges match the documented endpoints`() {
        assertEquals(48f, ParameterMapping.flingDistanceDp(0f), 1e-4f)
        assertEquals(140f, ParameterMapping.flingDistanceDp(1f), 1e-4f)
        assertEquals(0f, ParameterMapping.flingTiltDegrees(0f), 1e-4f)
        assertEquals(20f, ParameterMapping.flingTiltDegrees(1f), 1e-4f)
    }

    @Test fun `higher sensitivity needs a lighter flick`() {
        assertTrue(ParameterMapping.flingVelocityDpPerSec(1f) < ParameterMapping.flingVelocityDpPerSec(0f))
    }

    @Test fun `higher friction stops sooner`() {
        assertTrue(ParameterMapping.flingFriction(1f) > ParameterMapping.flingFriction(0f))
    }

    @Test fun `out of range input is clamped`() {
        assertEquals(ParameterMapping.flingDistanceDp(1f), ParameterMapping.flingDistanceDp(9f), 0f)
        assertEquals(ParameterMapping.flingFriction(0f), ParameterMapping.flingFriction(-3f), 0f)
    }
}

class HapticPresetTest {
    private val events = HapticEvent.all.map { it.second }

    @Test fun `off is silent for every event`() {
        for (e in events) assertNull(HapticPreset.OFF.effectFor(e))
    }

    @Test fun `every other preset answers every event`() {
        for (p in HapticPreset.entries.filter { it != HapticPreset.OFF }) {
            for (e in events) assertNotNull("$p $e", p.effectFor(e))
        }
    }

    @Test fun `crisp keeps the behaviour the app has always had`() {
        assertEquals(HapticEffect.TICK, HapticPreset.CRISP.effectFor(HapticEvent.Press))
        assertEquals(HapticEffect.TICK, HapticPreset.CRISP.effectFor(HapticEvent.Release))
        assertEquals(HapticEffect.CLICK, HapticPreset.CRISP.effectFor(HapticEvent.Snap))
        assertEquals(HapticEffect.IMPACT, HapticPreset.CRISP.effectFor(HapticEvent.Impact))
        assertEquals(HapticEffect.SUCCESS, HapticPreset.CRISP.effectFor(HapticEvent.Success))
    }

    @Test fun `firm is never lighter than soft for the same event`() {
        for (e in events) {
            val soft = HapticPreset.SOFT.effectFor(e)!!.ordinal
            val firm = HapticPreset.FIRM.effectFor(e)!!.ordinal
            assertTrue("$e soft=$soft firm=$firm", firm >= soft)
        }
    }

    @Test fun `table lists every event once and marks silence as none`() {
        assertEquals(events.size, HapticPreset.CRISP.table().size)
        assertTrue(HapticPreset.OFF.table().all { it.second == "none" })
        assertEquals(HapticEvent.all.map { it.first }, HapticPreset.SOFT.table().map { it.first })
    }
}
