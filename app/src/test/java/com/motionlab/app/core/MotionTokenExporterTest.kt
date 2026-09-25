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
