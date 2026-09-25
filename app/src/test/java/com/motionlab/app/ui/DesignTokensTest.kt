package com.motionlab.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignTokensTest {

    /** WCAG 2.x contrast ratio. */
    private fun contrast(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test fun `primary text is comfortably readable on every theme`() {
        for (t in DesignThemes.all) {
            assertTrue("${t.id} ink/canvas ${contrast(t.ink, t.canvas)}", contrast(t.ink, t.canvas) >= 12f)
            assertTrue("${t.id} ink/surface", contrast(t.ink, t.surface) >= 12f)
        }
    }

    @Test fun `secondary text meets AA on both canvas and surface`() {
        for (t in DesignThemes.all) {
            assertTrue("${t.id} soft/canvas ${contrast(t.inkSoft, t.canvas)}", contrast(t.inkSoft, t.canvas) >= 4.5f)
            assertTrue("${t.id} soft/surface ${contrast(t.inkSoft, t.surface)}", contrast(t.inkSoft, t.surface) >= 4.5f)
        }
    }

    @Test fun `text on the solid button is readable`() {
        for (t in DesignThemes.all) assertTrue("${t.id}", contrast(t.canvas, t.ink) >= 12f)
    }

    @Test fun `hairlines are visible but quieter than secondary text`() {
        for (t in DesignThemes.all) {
            assertTrue("${t.id} line visible", contrast(t.line, t.canvas) >= 1.08f)
            assertTrue("${t.id} line quiet", contrast(t.line, t.canvas) < contrast(t.inkSoft, t.canvas))
        }
    }

    @Test fun `theme ids are unique and resolvable`() {
        assertEquals(ThemeId.entries.size, DesignThemes.all.map { it.id }.toSet().size)
        for (id in ThemeId.entries) assertEquals(id, DesignThemes.of(id).id)
    }
}
