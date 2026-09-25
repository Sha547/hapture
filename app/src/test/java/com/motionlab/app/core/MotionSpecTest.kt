package com.motionlab.app.core

import com.motionlab.app.core.spec.LintIssue
import com.motionlab.app.core.spec.MotionLint
import com.motionlab.app.core.spec.MotionSpec
import com.motionlab.app.core.spec.MotionTransition
import com.motionlab.app.core.spec.SpringSpec
import com.motionlab.app.export.platform.ComposeGenericGenerator
import com.motionlab.app.export.platform.FlutterGenerator
import com.motionlab.app.export.platform.LottieGenerator
import com.motionlab.app.export.platform.ReactNativeGenerator
import com.motionlab.app.export.platform.SwiftUiGenerator
import com.motionlab.app.export.platform.WebGenerator
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionSpecTest {
    private val spring = SpringSpec(stiffness = 400f, dampingRatio = 0.6f)
    private val spec = MotionSpec(
        "Toggle Switch", "toggle", "tap", spring,
        listOf(
            MotionTransition("offsetX", 0f, 22f, 0, spring),
            MotionTransition("scale", 1f, 1.1f, 60, spring),
        ),
        mapOf("thumbStretch" to 0.3f), "crisp",
    )

    @Test fun `json round trips`() {
        val back = MotionSpec.fromJson(spec.toJson())!!
        assertEquals(spec.name, back.name)
        assertEquals(400f, back.spring.stiffness, 1e-3f)
        assertEquals(2, back.transitions.size)
        assertEquals(60, back.transitions[1].delayMs)
        assertEquals(0.3f, back.parameters["thumbStretch"]!!, 1e-3f)
    }

    @Test fun `json carries reduced motion and curve`() {
        val o = JSONObject(spec.toJson())
        assertEquals(1.0, o.getJSONObject("reducedMotion").getJSONObject("spring").getDouble("dampingRatio"), 1e-6)
        assertEquals(48, o.getJSONObject("curve").getJSONArray("samples").length())
    }

    @Test fun `garbage is not a spec`() {
        assertNull(MotionSpec.fromJson("nope"))
        assertNull(MotionSpec.fromJson("{}"))
    }

    @Test fun `design json converts to a neutral spec`() {
        val design = """{"name":"My Drag","interaction":"pinch_zoom","spring":{"stiffness":300,"dampingRatio":0.5},
            "parameters":{"minScale":0.5},"haptics":{"preset":"firm"}}"""
        val s = MotionSpec.fromDesignJson(design)!!
        assertEquals("scale", s.transitions.single().property)
        assertEquals(300f, s.spring.stiffness, 1e-3f)
        assertEquals("firm", s.haptic)
        assertEquals(0.5f, s.parameters["minScale"]!!, 1e-3f)
    }

    @Test fun `reduced spring never overshoots`() {
        assertEquals(0f, spring.reduced().overshootPercent, 0f)
        assertTrue(spring.overshootPercent > 5f)
    }

    @Test fun `lint flags wobbly sluggish and stagger`() {
        val wobbly = spec.copy(spring = SpringSpec(300f, 0.2f))
        val msgs = MotionLint.check(wobbly).map { it.message }
        assertTrue(msgs.any { it.contains("rings") })
        assertTrue(msgs.any { it.contains("overshoot") })
        val slow = spec.copy(spring = SpringSpec(40f, 1.0f))
        assertTrue(MotionLint.check(slow).any { it.message.contains("sluggish") })
        val staggered = spec.copy(transitions = listOf(MotionTransition("opacity", 0f, 1f, 1200, spring)))
        assertTrue(MotionLint.check(staggered).any { it.message.contains("Stagger") })
    }

    @Test fun `a well tuned toggle has no warnings`() {
        val ok = spec.copy(spring = SpringSpec(600f, 0.85f))
        assertTrue(MotionLint.check(ok).none { it.level == LintIssue.Level.WARN })
    }

    @Test fun `every generator carries the same spring numbers`() {
        val damping = "%.2f".format(spring.damping).trimEnd('0').trimEnd('.')
        for ((name, code) in listOf(
            "swift" to SwiftUiGenerator.generate(spec),
            "flutter" to FlutterGenerator.generate(spec),
            "rn" to ReactNativeGenerator.generate(spec),
            "web" to WebGenerator.generate(spec),
        )) {
            assertTrue("$name stiffness", code.contains("400"))
            assertTrue("$name damping $damping", code.contains(damping))
            assertTrue("$name reduced motion", code.lowercase().contains("reduce"))
        }
        val compose = ComposeGenericGenerator.generate(spec)
        assertTrue(compose.contains("stiffness = 400f"))
        assertTrue(compose.contains("delay(60L)"))
    }

    @Test fun `swift uses the property specific modifiers and delay`() {
        val code = SwiftUiGenerator.generate(spec)
        assertTrue(code.contains(".offset(x: active ? 22 : 0)"))
        assertTrue(code.contains(".scaleEffect(active ? 1.1 : 1)"))
        assertTrue(code.contains(".delay(0.06)"))
    }

    @Test fun `lottie is a well formed file whose curve ends at the target`() {
        val o = JSONObject(LottieGenerator.generate(spec))
        assertEquals(60, o.getInt("fr"))
        val layer = o.getJSONArray("layers").getJSONObject(0)
        val kfs = layer.getJSONObject("ks").getJSONObject("p").getJSONArray("k")
        assertEquals(0.0, kfs.getJSONObject(0).getJSONArray("s").getDouble(0) - 200.0, 1e-6)
        val last = kfs.getJSONObject(kfs.length() - 1).getJSONArray("s").getDouble(0)
        assertEquals(222.0, last, 0.2)
        assertNotNull(layer.getJSONArray("shapes"))
    }
}
