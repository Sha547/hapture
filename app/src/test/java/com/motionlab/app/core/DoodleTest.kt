package com.motionlab.app.core

import com.motionlab.app.core.doodle.Brush
import com.motionlab.app.core.doodle.DoodleBackground
import com.motionlab.app.core.doodle.DoodleCodec
import com.motionlab.app.core.doodle.DoodleGeometry
import com.motionlab.app.core.doodle.DoodleStroke
import com.motionlab.app.core.doodle.DoodleSvg
import com.motionlab.app.core.doodle.Pt
import com.motionlab.app.core.doodle.doodleWidthFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoodleTest {
    private val stroke = DoodleStroke(Brush.MARKER, 0xFF3F7FD6.toInt(), 0.02f, listOf(Pt(0.1f, 0.2f), Pt(0.5f, 0.6f), Pt(0.9f, 0.3f)))

    @Test fun `codec round trips strokes`() {
        val back = DoodleCodec.decode(DoodleCodec.encode(listOf(stroke)))
        assertEquals(1, back.size)
        assertEquals(Brush.MARKER, back[0].brush)
        assertEquals(stroke.color, back[0].color)
        assertEquals(0.5f, back[0].points[1].x, 1e-4f)
    }

    @Test fun `decode never throws on garbage`() {
        assertEquals(0, DoodleCodec.decode("").size)
        assertEquals(0, DoodleCodec.decode("not json").size)
        assertEquals(0, DoodleCodec.decode("""{"strokes":[{"b":"NOPE","p":[0,0]},{"b":"PEN","p":[]}]}""").size)
    }

    @Test fun `unknown strokes are dropped but good ones survive`() {
        val text = """{"strokes":[{"b":"NOPE","c":1,"w":0.01,"p":[0,0]},{"b":"PEN","c":1,"w":0.01,"p":[0.1,0.1,0.2,0.2]}]}"""
        assertEquals(1, DoodleCodec.decode(text).size)
    }

    @Test fun `a single point is a dot, not a path`() {
        assertNull(DoodleGeometry.smooth(listOf(Pt(0.5f, 0.5f))))
        assertNotNull(DoodleGeometry.smooth(stroke.points))
    }

    @Test fun `smooth path ends on the last point`() {
        val (_, quads) = DoodleGeometry.smooth(stroke.points)!!
        assertEquals(0.9f, quads.last().ex, 1e-6f)
    }

    @Test fun `spray is deterministic`() {
        assertEquals(DoodleGeometry.sprayDots(stroke.points, 0.03f), DoodleGeometry.sprayDots(stroke.points, 0.03f))
    }

    @Test fun `thinning keeps the endpoints`() {
        val dense = List(50) { Pt(0.5f + it * 0.00001f, 0.5f) }
        val out = DoodleGeometry.thinned(dense)
        assertEquals(dense.first(), out.first())
        assertEquals(dense.last(), out.last())
        assertTrue(out.size < dense.size)
    }

    @Test fun `width slider maps into a sane range`() {
        assertTrue(doodleWidthFor(0f) < doodleWidthFor(1f))
        assertEquals(doodleWidthFor(0f), doodleWidthFor(-5f), 0f)
    }

    @Test fun `svg has a background and one shape per stroke, eraser uses the background colour`() {
        val eraser = stroke.copy(brush = Brush.ERASER)
        val svg = DoodleSvg.of(listOf(stroke, eraser), DoodleBackground.INK, 100)
        assertTrue(svg.contains("""fill="#171716""""))
        assertEquals(2, Regex("<path").findAll(svg).count())
        assertTrue(svg.contains("""stroke="#171716""""))
        assertTrue(svg.contains("stroke-opacity"))
    }
}
