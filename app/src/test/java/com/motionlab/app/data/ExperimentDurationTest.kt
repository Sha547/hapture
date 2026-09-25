package com.motionlab.app.data

import com.motionlab.app.export.SpringMath
import com.motionlab.app.core.physics.ParameterMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentDurationTest {
    private fun entity(type: ExperimentType, stiffnessT: Float, dampingT: Float) = ExperimentEntity(
        type = type, name = "n", createdAt = 0, updatedAt = 0, stiffnessT = stiffnessT, dampingT = dampingT,
    )

    @Test fun `matches SpringMath's own settle time for the same stiffness and damping`() {
        val e = entity(ExperimentType.SPRING_DRAG, 0.6f, 0.4f)
        val expected = SpringMath.settleMs(ParameterMapping.stiffness(0.6f), ParameterMapping.dampingRatio(0.4f))
        assertEquals(expected, e.nominalDurationMs)
    }

    @Test fun `applies the same way regardless of interaction type`() {
        // Every type carries its own stiffness/damping (main motion or return spring);
        // the duration formula doesn't special-case any of them.
        for (type in ExperimentType.entries) {
            val e = entity(type, 0.5f, 0.5f)
            assertEquals(SpringMath.settleMs(ParameterMapping.stiffness(0.5f), ParameterMapping.dampingRatio(0.5f)), e.nominalDurationMs)
        }
    }

    @Test fun `stiffer settles faster, more damping settles faster`() {
        val soft = entity(ExperimentType.MAGNETIC_SNAP, 0.1f, 0.5f)
        val stiff = entity(ExperimentType.MAGNETIC_SNAP, 0.9f, 0.5f)
        assertTrue(stiff.nominalDurationMs < soft.nominalDurationMs)

        val bouncy = entity(ExperimentType.MAGNETIC_SNAP, 0.5f, 0.1f)
        val damped = entity(ExperimentType.MAGNETIC_SNAP, 0.5f, 0.9f)
        assertTrue(damped.nominalDurationMs < bouncy.nominalDurationMs)
    }

    @Test fun `is always a positive, finite number of milliseconds`() {
        for (s in listOf(0f, 0.5f, 1f)) for (d in listOf(0f, 0.5f, 1f)) {
            assertTrue(entity(ExperimentType.SWIPE_FLING, s, d).nominalDurationMs > 0)
        }
    }
}
