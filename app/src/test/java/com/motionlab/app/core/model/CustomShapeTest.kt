package com.motionlab.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ClosedSmoothSegmentsTest {
    private val triangle = listOf(CustomPoint(0.5f, 0f), CustomPoint(1f, 1f), CustomPoint(0f, 1f))

    @Test fun `fewer than three points cannot close into a shape`() {
        assertTrue(closedSmoothSegments(emptyList()).isEmpty())
        assertTrue(closedSmoothSegments(listOf(CustomPoint(0f, 0f))).isEmpty())
        assertTrue(closedSmoothSegments(listOf(CustomPoint(0f, 0f), CustomPoint(1f, 1f))).isEmpty())
    }

    @Test fun `one segment per input point, and it closes back to the first`() {
        val segments = closedSmoothSegments(triangle)
        assertEquals(triangle.size, segments.size)
        assertEquals(triangle[1], segments[0].end)
        assertEquals(triangle[2], segments[1].end)
        assertEquals(triangle[0], segments.last().end)
    }

    @Test fun `every segment lands exactly on its own input point`() {
        // The curve passes through every drawn point -- it's smoothed, not simplified away.
        val segments = closedSmoothSegments(triangle)
        for (i in triangle.indices) assertEquals(triangle[(i + 1) % triangle.size], segments[i].end)
    }

    @Test fun `a regular shape is symmetric`() {
        val square = listOf(CustomPoint(0f, 0f), CustomPoint(1f, 0f), CustomPoint(1f, 1f), CustomPoint(0f, 1f))
        val segments = closedSmoothSegments(square)
        // Each control point should sit the same fractional distance from its segment's own endpoints.
        val d1 = hypot((segments[0].c1.x - square[0].x).toDouble(), (segments[0].c1.y - square[0].y).toDouble())
        val d2 = hypot((segments[1].c1.x - square[1].x).toDouble(), (segments[1].c1.y - square[1].y).toDouble())
        assertEquals(d1, d2, 1e-6)
    }
}

class ResampleClosedTest {
    private val square = listOf(CustomPoint(0f, 0f), CustomPoint(1f, 0f), CustomPoint(1f, 1f), CustomPoint(0f, 1f))

    @Test fun `resamples to exactly the requested count`() {
        assertEquals(20, resampleClosed(square, 20).size)
        assertEquals(3, resampleClosed(square, 3).size)
    }

    @Test fun `points stay roughly evenly spaced by arc length`() {
        val result = resampleClosed(square, 16)
        val perimeter = 4.0
        val expectedStep = perimeter / 16
        for (i in result.indices) {
            val a = result[i]
            val b = result[(i + 1) % result.size]
            val d = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
            assertEquals(expectedStep, d, 0.05)
        }
    }

    @Test fun `too few points to resample are returned as is`() {
        val two = listOf(CustomPoint(0f, 0f), CustomPoint(1f, 1f))
        assertEquals(two, resampleClosed(two, 20))
    }

    @Test fun `resampled points stay within the original bounding box`() {
        val blob = listOf(CustomPoint(0.1f, 0.2f), CustomPoint(0.9f, 0.1f), CustomPoint(0.8f, 0.9f), CustomPoint(0.2f, 0.8f))
        val result = resampleClosed(blob, 24)
        for (p in result) {
            assertTrue(p.x in 0.0f..1.0f)
            assertTrue(p.y in 0.0f..1.0f)
        }
    }
}

class CustomShapeCodecTest {
    private val shape = listOf(CustomPoint(0.1234f, 0.5678f), CustomPoint(0.9f, 0.05f), CustomPoint(0.5f, 0.95f))

    @Test fun `round trips through the compact string format`() {
        val decoded = CustomShapeCodec.decode(CustomShapeCodec.encode(shape))
        assertEquals(shape.size, decoded.size)
        for (i in shape.indices) {
            assertEquals(shape[i].x, decoded[i].x, 1e-3f)
            assertEquals(shape[i].y, decoded[i].y, 1e-3f)
        }
    }

    @Test fun `blank or null decodes to an empty shape rather than throwing`() {
        assertTrue(CustomShapeCodec.decode(null).isEmpty())
        assertTrue(CustomShapeCodec.decode("").isEmpty())
        assertTrue(CustomShapeCodec.decode("   ").isEmpty())
    }

    @Test fun `malformed segments are dropped, not the whole shape`() {
        val decoded = CustomShapeCodec.decode("0.1,0.2;garbage;0.5,;;0.4,0.6")
        assertEquals(listOf(CustomPoint(0.1f, 0.2f), CustomPoint(0.4f, 0.6f)), decoded)
    }

    @Test fun `out of range coordinates are clamped, not rejected`() {
        val decoded = CustomShapeCodec.decode("-0.5,1.5")
        assertEquals(CustomPoint(0f, 1f), decoded.single())
    }

    @Test fun `absurdly long input is capped rather than stored unbounded`() {
        val huge = (0 until 500).joinToString(";") { "0.1,0.2" }
        assertTrue(CustomShapeCodec.decode(huge).size <= 64)
    }
}
