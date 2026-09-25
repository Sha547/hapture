package com.motionlab.app.export

import com.motionlab.app.core.model.CustomPoint
import com.motionlab.app.core.model.ObjectFill
import com.motionlab.app.core.model.ObjectShape
import com.motionlab.app.core.model.ObjectStyle
import com.motionlab.app.ui.design.DesignThemes
import com.motionlab.app.core.haptics.HapticPreset
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSpecTest {

    private fun spec(name: String = "Spring Drag 2") = DesignSpec.json(
        name = name,
        interaction = "spring_drag",
        stiffness = 812.4f,
        dampingRatio = 0.62f,
        extras = listOf(DesignSpec.Extra("resistanceK", 0.0455f), DesignSpec.Extra("boundDp", 56f)),
        style = ObjectStyle(ObjectShape.PILL, 0.5f, ObjectFill.MARKER),
        tokens = DesignThemes.paper,
    )

    @Test fun `spec is valid json with the documented shape`() {
        val j = JSONObject(spec())
        assertEquals("spring_drag", j.getString("interaction"))
        assertEquals(812.4, j.getJSONObject("spring").getDouble("stiffness"), 1e-6)
        assertEquals("pill", j.getJSONObject("object").getString("shape"))
        assertEquals("#CFDFEB", j.getJSONObject("object").getString("fillColor"))
        assertEquals(0.0455, j.getJSONObject("parameters").getDouble("resistanceK"), 1e-6)
    }

    @Test fun `figma custom spring uses absolute damping not the ratio`() {
        val figma = JSONObject(spec()).getJSONObject("figma")
        assertEquals(1, figma.getInt("mass"))
        assertEquals(SpringMath.damping(812.4f, 0.62f).toDouble(), figma.getDouble("damping"), 1e-3)
    }

    @Test fun `names with quotes and backslashes cannot break the json`() {
        val j = JSONObject(spec("""My "spring" \ 2"""))
        assertEquals("""My "spring" \ 2""", j.getString("name"))
    }

    @Test fun `output is deterministic`() {
        assertEquals(spec(), spec())
    }

    @Test fun `spec without extras is still valid`() {
        val s = DesignSpec.json("n", "x", 300f, 1f, emptyList(), ObjectStyle(), DesignThemes.graphite)
        assertEquals(0, JSONObject(s).getJSONObject("parameters").length())
    }

    @Test fun `css easing starts at zero, ends at one and names a duration`() {
        val css = DesignSpec.css("Spring Drag 2", 800f, 0.5f)
        assertTrue(css.contains("--spring-drag-2-duration: ${SpringMath.settleMs(800f, 0.5f)}ms"))
        assertTrue(css.contains("linear(0, "))
        assertTrue(css.contains(", 1)"))
    }

    @Test fun `tokens export is valid dtcg json for every theme`() {
        for (t in DesignThemes.all) {
            val j = JSONObject(DesignTokenExporter.json(t)).getJSONObject("motionLab")
            val ink = j.getJSONObject("color").getJSONObject("ink")
            assertEquals("color", ink.getString("\$type"))
            assertEquals(DesignSpec.hex(t.ink), ink.getString("\$value"))
            assertEquals(t.id.name.lowercase(), j.getJSONObject("theme").getString("\$value"))
        }
    }

    @Test fun `spec carries the haptic preset and its full event table`() {
        val s = DesignSpec.json("n", "x", 300f, 1f, emptyList(), ObjectStyle(), DesignThemes.paper, haptics = HapticPreset.FIRM)
        val h = JSONObject(s).getJSONObject("haptics")
        assertEquals("firm", h.getString("preset"))
        assertEquals("heavy", h.getString("impact"))
        assertEquals("click", h.getString("press"))
    }

    @Test fun `a silent preset reads none for every event`() {
        val s = DesignSpec.json("n", "x", 300f, 1f, emptyList(), ObjectStyle(), DesignThemes.paper, haptics = HapticPreset.OFF)
        val h = JSONObject(s).getJSONObject("haptics")
        assertEquals("none", h.getString("snap"))
    }

    @Test fun `a custom shape carries its svg path, an ordinary shape carries none`() {
        val drawn = listOf(CustomPoint(0.1f, 0.1f), CustomPoint(0.9f, 0.1f), CustomPoint(0.5f, 0.9f))
        val custom = DesignSpec.json(
            "n", "x", 300f, 1f, emptyList(),
            ObjectStyle(ObjectShape.CUSTOM, customPath = drawn), DesignThemes.paper,
        )
        val obj = JSONObject(custom).getJSONObject("object")
        assertEquals("custom", obj.getString("shape"))
        assertTrue(obj.getString("customPathSvg").startsWith("M "))
        assertEquals("0 0 100 100", obj.getString("customPathViewBox"))

        val plain = JSONObject(spec()).getJSONObject("object")
        assertTrue(!plain.has("customPathSvg"))
    }
}
