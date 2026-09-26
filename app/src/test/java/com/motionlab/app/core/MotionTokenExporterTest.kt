package com.motionlab.app.core

import com.motionlab.app.data.MotionTokenEntity
import com.motionlab.app.export.MotionTokenExporter
import com.motionlab.app.export.MotionTokenExporter.Format
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTokenExporterTest {
    private val tokens = listOf(
        MotionTokenEntity(1, "Snappy Button", 800f, 0.75f, 0),
        MotionTokenEntity(2, "gentle", 200f, 0.9f, 0),
    )

    @Test fun `names become identifiers`() {
        assertEquals("snappyButton", MotionTokenExporter.camel("Snappy Button"))
        assertEquals("snappy-button", MotionTokenExporter.slug("Snappy Button"))
    }

    @Test fun `json has a spring per token`() {
        val o = JSONObject(MotionTokenExporter.json(tokens)).getJSONObject("motion")
        assertEquals("spring", o.getJSONObject("snappy-button").getString("\$type"))
        assertEquals(800.0, o.getJSONObject("snappy-button").getJSONObject("\$value").getDouble("stiffness"), 0.01)
    }

    @Test fun `every format names every token`() {
        for (f in Format.entries) {
            val out = MotionTokenExporter.export(tokens, f)
            assertTrue("$f", out.contains("gentle"))
            if (f != Format.CSS) assertTrue("$f", out.contains("800"))
            if (f == Format.CSS) assertTrue("$f", out.contains("linear("))
        }
    }
}

class TokenIdentifierTest {
    private fun t(name: String) = com.motionlab.app.data.MotionTokenEntity(name = name, stiffness = 500f, dampingRatio = 0.8f, createdAt = 0)
    private val fmt = com.motionlab.app.export.MotionTokenExporter.Format.entries

    @org.junit.Test fun `names that are not valid identifiers are made valid`() {
        val e = com.motionlab.app.export.MotionTokenExporter
        org.junit.Assert.assertEquals("_3dTap", e.camel("3d-tap"))
        org.junit.Assert.assertEquals("objectSpring", e.camel("object"))
        org.junit.Assert.assertEquals("defaultSpring", e.camel("Default"))
        org.junit.Assert.assertEquals("bigBounce2", e.camel("Big Bounce 2"))
        org.junit.Assert.assertEquals("spring", e.camel("!!!"))
    }

    @org.junit.Test fun `two names that collapse to the same identifier both survive in every format`() {
        val tokens = listOf(t("Big Bounce"), t("big-bounce"))
        val kotlin = com.motionlab.app.export.MotionTokenExporter.export(tokens, com.motionlab.app.export.MotionTokenExporter.Format.KOTLIN)
        org.junit.Assert.assertTrue(kotlin, kotlin.contains("fun bigBounce()") && kotlin.contains("fun bigBounce2()"))
        val json = org.json.JSONObject(com.motionlab.app.export.MotionTokenExporter.json(tokens)).getJSONObject("motion")
        org.junit.Assert.assertEquals(2, json.length())
        val css = com.motionlab.app.export.MotionTokenExporter.export(tokens, com.motionlab.app.export.MotionTokenExporter.Format.CSS)
        org.junit.Assert.assertTrue(css.contains("--motion-big-bounce-duration") && css.contains("--motion-big-bounce2-duration"))
    }
}
