package com.motionlab.app.export

import com.motionlab.app.core.model.CustomPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgPathTest {
    private val triangle = listOf(CustomPoint(0.5f, 0f), CustomPoint(1f, 1f), CustomPoint(0f, 1f))

    @Test fun `too few points produce no path`() {
        assertNull(SvgPath.of(emptyList()))
        assertNull(SvgPath.of(listOf(CustomPoint(0f, 0f), CustomPoint(1f, 1f))))
    }

    @Test fun `starts with a move to the first point, scaled to the view box`() {
        val d = SvgPath.of(triangle, viewBox = 100f)!!
        assertTrue(d.startsWith("M 50 0"))
    }

    @Test fun `one cubic command per point and it closes`() {
        val d = SvgPath.of(triangle)!!
        assertEquals(triangle.size, Regex("C ").findAll(d).count())
        assertTrue(d.trim().endsWith("Z"))
    }

    @Test fun `output is deterministic`() {
        assertEquals(SvgPath.of(triangle), SvgPath.of(triangle))
    }

    @Test fun `a different view box scales every coordinate`() {
        val small = SvgPath.of(triangle, viewBox = 1f)!!
        assertTrue(small.startsWith("M 0.5 0"))
    }
}
