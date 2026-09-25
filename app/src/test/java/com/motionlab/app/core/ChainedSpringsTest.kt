package com.motionlab.app.core

import com.motionlab.app.core.spec.Chain
import com.motionlab.app.core.spec.Follow
import com.motionlab.app.core.spec.MotionSpec
import com.motionlab.app.core.spec.MotionTransition
import com.motionlab.app.core.spec.SpringSpec
import com.motionlab.app.core.spec.TriggerKind
import com.motionlab.app.core.spec.TriggerSpecs
import com.motionlab.app.export.SpringMath
import com.motionlab.app.export.platform.ComposeGenericGenerator
import com.motionlab.app.export.platform.FlutterGenerator
import com.motionlab.app.export.platform.LottieGenerator
import com.motionlab.app.export.platform.ReactNativeGenerator
import com.motionlab.app.export.platform.SwiftUiGenerator
import com.motionlab.app.export.platform.WebGenerator
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainedSpringsTest {
    private val slow = SpringSpec(150f, 0.9f)
    private val fast = SpringSpec(900f, 0.9f)

    private fun spec(vararg t: MotionTransition) = MotionSpec("Press", "button_press", "press", slow, t.toList())
    private fun tr(prop: String, s: SpringSpec, follows: Follow? = null, delay: Int = 0) = MotionTransition(prop, 1f, 0.9f, delay, s, follows)

    @Test fun `time to reach a fraction comes from the spring, so slower springs wait longer`() {
        val a = SpringMath.timeToReachMs(150f, 0.9f, 0.5f)
        val b = SpringMath.timeToReachMs(900f, 0.9f, 0.5f)
        assertTrue("$a > $b", a > b)
        assertTrue(SpringMath.stepResponse(a / 1000.0, 150.0, 0.9) >= 0.5)
        assertTrue(SpringMath.stepResponse((a - 1) / 1000.0, 150.0, 0.9) < 0.5)
    }

    @Test fun `a follower starts when its source reaches the chosen progress, plus its lag`() {
        val s = spec(tr("scale", slow), tr("opacity", fast, Follow(0, 0.5f, 40))).resolved()
        assertEquals(0, s.transitions[0].delayMs)
        assertEquals(SpringMath.timeToReachMs(150f, 0.9f, 0.5f) + 40, s.transitions[1].delayMs)
    }

    @Test fun `retuning the source moves the follower with it`() {
        fun delayFor(source: SpringSpec) = spec(tr("scale", source), tr("opacity", fast, Follow(0, 0.5f, 0))).resolved().transitions[1].delayMs
        assertTrue(delayFor(SpringSpec(150f, 0.9f)) > delayFor(SpringSpec(900f, 0.9f)))
    }

    @Test fun `chains of chains add up and bad references are ignored`() {
        val s = spec(
            tr("scale", slow), tr("opacity", fast, Follow(0, 0.5f, 0)), tr("offsetY", fast, Follow(1, 0.5f, 10)),
            tr("rotation", fast, Follow(3, 0.5f, 0), delay = 77), // points at itself/later: keeps its own delay
        ).resolved()
        val d1 = s.transitions[1].delayMs
        assertEquals(d1 + SpringMath.timeToReachMs(900f, 0.9f, 0.5f) + 10, s.transitions[2].delayMs)
        assertEquals(77, s.transitions[3].delayMs)
        assertEquals(s, s.resolved())
    }

    @Test fun `specs without a chain are untouched`() {
        val s = spec(tr("scale", slow, delay = 30))
        assertTrue(s.resolved() === s)
    }

    @Test fun `json carries the effective delay and the relationship and round trips`() {
        val s = spec(tr("scale", slow), tr("opacity", fast, Follow(0, 0.5f, 40)))
        val text = s.toJson()
        val o = JSONObject(text)
        assertEquals(3, o.getInt("specVersion"))
        val second = o.getJSONArray("transitions").getJSONObject(1)
        assertEquals(s.resolved().transitions[1].delayMs, second.getInt("delayMs"))
        assertEquals(0, second.getJSONObject("follows").getInt("source"))
        val back = MotionSpec.fromJson(text)!!
        assertEquals(Follow(0, 0.5f, 40), back.transitions[1].follows)
        assertEquals(s.resolved().transitions[1].delayMs, back.resolved().transitions[1].delayMs)
        assertEquals(2, JSONObject(spec(tr("scale", slow)).toJson()).getInt("specVersion"))
    }

    @Test fun `every platform gets the resolved delay`() {
        val s = spec(tr("scale", slow), tr("opacity", fast, Follow(0, 0.5f, 40)))
        val ms = s.resolved().transitions[1].delayMs
        assertTrue(ms > 40)
        val sec = DesignSpecNum.n(ms / 1000f)
        listOf(SwiftUiGenerator.generate(s), FlutterGenerator.generate(s), ReactNativeGenerator.generate(s), WebGenerator.generate(s), ComposeGenericGenerator.generate(s), LottieGenerator.generate(s)).forEachIndexed { i, code ->
            assertTrue("platform $i mentions ${ms} ms / ${sec}s", code.contains("$ms") || code.contains(sec) || code.contains("${ms / 1000.0}"))
        }
    }

    @Test fun `lottie bakes the follower onto its own property, resting until its delay`() {
        val s = spec(tr("scale", slow), tr("opacity", fast, Follow(0, 0.5f, 40)))
        val layer = JSONObject(LottieGenerator.generate(s)).getJSONArray("layers").getJSONObject(0).getJSONObject("ks")
        val scaleKeys = layer.getJSONObject("s").getJSONArray("k")
        val opacityKeys = layer.getJSONObject("o").getJSONArray("k")
        assertEquals(0, scaleKeys.getJSONObject(0).getInt("t"))
        // Opacity holds its start value at frame 0, then starts at the resolved delay (in frames at 60 fps).
        assertEquals(0, opacityKeys.getJSONObject(0).getInt("t"))
        assertEquals(opacityKeys.getJSONObject(0).getJSONArray("s").getDouble(0), 100.0, 0.001)
        val delayFrames = (s.resolved().transitions[1].delayMs / 1000f * 60).toInt()
        assertEquals(delayFrames, opacityKeys.getJSONObject(1).getInt("t"))
        assertTrue(delayFrames > 2)
    }

    @Test fun `triggers build a follower and refuse one that would collide with the leader`() {
        val chain = Chain("opacity", 0.5f, 0.2f, 0.7f, 0.6f)
        val press = TriggerSpecs.build(TriggerKind.BUTTON_PRESS, "b", 0.5f, 0.5f, 0.5f, 0.4f, "crisp", chain)
        assertEquals(listOf("scale", "opacity"), press.transitions.map { it.property })
        assertEquals(0, press.transitions[1].follows!!.source)
        assertEquals(TriggerSpecs.chainLagMs(0.2f), press.transitions[1].follows!!.lagMs)
        val clash = TriggerSpecs.build(TriggerKind.BUTTON_PRESS, "b", 0.5f, 0.5f, 0.5f, 0.4f, "crisp", chain.copy(property = "scale"))
        assertEquals(1, clash.transitions.size)
        assertEquals(1, TriggerSpecs.build(TriggerKind.BUTTON_PRESS, "b", 0.5f, 0.5f, 0.5f, 0.4f, "crisp").transitions.size)
    }

    @Test fun `a chain counts towards the total time before the last thing starts`() {
        val s = spec(tr("scale", slow), tr("opacity", fast, Follow(0, 0.9f, 300)))
        assertTrue(s.staggerTotalMs >= s.resolved().transitions[1].delayMs)
    }
}

private object DesignSpecNum {
    fun n(v: Float) = com.motionlab.app.export.DesignSpec.num(v, 3)
}
