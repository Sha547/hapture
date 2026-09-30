package com.klynstudios.hapture.core.model

import com.klynstudios.hapture.core.physics.ParameterMapping
import com.klynstudios.hapture.core.spec.SpringSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialMotionTest {

    @Test fun everyMaterialSpringIsReachableBySliders() {
        MaterialSpring.entries.forEach { m ->
            val k = ParameterMapping.stiffness(ParameterMapping.stiffnessT(m.stiffness))
            val z = ParameterMapping.dampingRatio(ParameterMapping.dampingT(m.dampingRatio))
            assertEquals(m.name, m.stiffness, k, 0.5f)
            assertEquals(m.name, m.dampingRatio, z, 0.001f)
        }
    }

    @Test fun aMaterialSpringMatchesItselfExactly() {
        MaterialSpring.entries.forEach { m ->
            val match = MaterialMotion.nearest(m.spring)
            assertEquals(m, match.nearest)
            assertEquals(MaterialMatch.Closeness.EXACT, match.closeness)
        }
    }

    @Test fun aSmallNudgeIsCloseAndAFarSpringIsFar() {
        val nudged = SpringSpec(760f, 0.92f)
        val m = MaterialMotion.nearest(nudged)
        assertEquals(MaterialSpring.STANDARD_DEFAULT, m.nearest)
        assertEquals(MaterialMatch.Closeness.CLOSE, m.closeness)

        val wobbly = SpringSpec(3000f, 0.15f)
        assertEquals(MaterialMatch.Closeness.FAR, MaterialMotion.nearest(wobbly).closeness)
    }

    @Test fun distanceIsSymmetricAndZeroForTheSameSpring() {
        val a = SpringSpec(400f, 0.7f)
        val b = SpringSpec(900f, 0.5f)
        assertEquals(0f, MaterialMotion.distance(a, a), 1e-6f)
        assertEquals(MaterialMotion.distance(a, b), MaterialMotion.distance(b, a), 1e-6f)
        assertTrue(MaterialMotion.distance(a, b) > 0f)
    }
}
