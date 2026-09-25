package com.motionlab.app.core

import com.motionlab.app.core.spec.MotionGrade
import com.motionlab.app.core.spec.MotionLint
import com.motionlab.app.core.spec.SpringSpec
import com.motionlab.app.data.ExperimentEntity
import com.motionlab.app.data.ExperimentType
import com.motionlab.app.data.toMotionSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionGradeTest {
    @Test fun `a calm, quick spring earns an A with nothing to explain`() {
        val g = MotionGrade.ofSpring("calm", SpringSpec(500f, 0.9f))
        assertEquals("A", g.letter)
        assertTrue(g.reasons.isEmpty())
    }

    @Test fun `a wobbly slow control is graded down and says why`() {
        val g = MotionGrade.ofSpring("wobble", SpringSpec(60f, 0.1f))
        assertTrue(g.letter, g.letter in listOf("D", "F"))
        assertTrue(g.reasons.any { it.contains("wobble") || it.contains("rings") })
    }

    @Test fun `the grade is exactly what the lint finds`() {
        val s = SpringSpec(3000f, 0.2f)
        val spec = com.motionlab.app.core.spec.MotionSpec("x", "token", "tap", s, emptyList())
        assertEquals(MotionLint.check(spec).map { it.message }, MotionGrade.of(spec).reasons)
    }

    @Test fun `letters follow the score bands`() {
        assertEquals("A", MotionGrade.letterFor(100))
        assertEquals("B", MotionGrade.letterFor(85))
        assertEquals("C", MotionGrade.letterFor(70))
        assertEquals("D", MotionGrade.letterFor(55))
        assertEquals("F", MotionGrade.letterFor(10))
    }

    @Test fun `a saved experiment can be graded without its editor`() {
        fun e(type: ExperimentType) = ExperimentEntity(type = type, name = "n", createdAt = 0, updatedAt = 0, stiffnessT = 0.5f, dampingT = 0.5f)
        ExperimentType.entries.forEach { type ->
            val spec = e(type).toMotionSpec()
            assertTrue("$type has a spring", spec.spring.stiffness > 0f)
            assertTrue("$type grades", MotionGrade.of(spec).letter.isNotEmpty())
        }
    }
}
