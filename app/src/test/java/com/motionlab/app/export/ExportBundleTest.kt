package com.motionlab.app.export

import com.motionlab.app.core.spec.MotionSpec
import com.motionlab.app.core.spec.SpringSpec
import com.motionlab.app.core.spec.MotionTransition
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportBundleTest {
    private val spring = SpringSpec(400f, 0.6f)
    private val spec = MotionSpec("Card", "spring_drag", "release", spring, listOf(MotionTransition("offsetX", 96f, 0f, 0, spring)))

    @Test fun `bundle carries every platform with label, extension and code`() {
        val o = JSONObject(ExportBundle.json("kt code", "{}", "css code", spec))
        listOf("compose", "swift", "flutter", "rn", "web", "css", "lottie", "spec", "compose2", "neutral").forEach {
            val e = o.getJSONObject(it)
            assertTrue("$it label", e.getString("label").isNotBlank())
            assertTrue("$it code", e.getString("code").isNotBlank())
        }
        val compose = o.getJSONObject("compose").getString("code")
        assertTrue(compose.contains("kt code"))
        // Code exports carry a stamp that verifies; JSON exports are left as they are.
        assertTrue(ExportStamp.check(compose, emptyList()) is ExportStamp.Verdict.Current)
        assertTrue(o.getJSONObject("css").getString("code").startsWith("/* motionlab:v1"))
        assertFalse(o.getJSONObject("neutral").getString("code").contains("motionlab:v1"))
        assertTrue(o.getJSONObject("swift").getString("code").contains("400"))
    }

    @Test fun `without a neutral spec only the hand-built exports remain`() {
        val o = JSONObject(ExportBundle.json("kt", "{}", "css", null))
        assertFalse(o.has("swift"))
        assertTrue(o.has("compose") && o.has("css") && o.has("spec"))
    }
}
