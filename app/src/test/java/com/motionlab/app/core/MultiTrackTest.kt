package com.motionlab.app.core

import com.motionlab.app.core.capture.MultiTrack
import com.motionlab.app.core.capture.ObjectTracker.Sample
import com.motionlab.app.core.spec.TriggerSpecs
import com.motionlab.app.export.SpringMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiTrackTest {
    private fun samples(k: Double, z: Double, onsetSec: Double, fps: Int = 30, seconds: Double = 2.4) =
        (0..(seconds * fps).toInt()).map { i ->
            val t = i / fps.toDouble()
            Sample(t.toFloat(), (0.2 + 0.5 * SpringMath.stepResponse(t - onsetSec, k, z)).toFloat(), 0.5f, 0.01f)
        }

    @Test fun `two objects get their own springs and the gap between their starts`() {
        val out = MultiTrack.analyse(samples(500.0, 0.6, 0.10), samples(300.0, 0.8, 0.25)) as MultiTrack.Outcome.Ok
        val a = out.analysis
        assertEquals(0.6f, a.first.fit.dampingRatio, 0.12f)
        assertEquals(0.8f, a.second.fit.dampingRatio, 0.15f)
        assertEquals("delay ${a.delayMs}", 150f, a.delayMs.toFloat(), 45f)
    }

    @Test fun `an object that starts first gives a negative delay`() {
        val out = MultiTrack.analyse(samples(400.0, 0.7, 0.30), samples(400.0, 0.7, 0.10)) as MultiTrack.Outcome.Ok
        assertTrue(out.analysis.delayMs < -100)
    }

    @Test fun `a still object is reported by name`() {
        val still = (0..70).map { Sample(it / 30f, 0.5f, 0.5f, 0.01f) }
        val out = MultiTrack.analyse(samples(400.0, 0.7, 0.1), still)
        assertTrue((out as MultiTrack.Outcome.Failed).reason.startsWith("Object 2"))
    }

    @Test fun `near-identical colours cannot be told apart`() {
        assertTrue(!MultiTrack.distinguishable(0xFFD64545.toInt(), 0xFFD04A48.toInt()))
        assertTrue(MultiTrack.distinguishable(0xFFD64545.toInt(), 0xFF3F7FD6.toInt()))
    }

    @Test fun `a measured delay maps onto the stagger slider and back`() {
        assertEquals(60f, TriggerSpecs.staggerMs(TriggerSpecs.staggerT(60f)), 0.01f)
        assertEquals(0f, TriggerSpecs.staggerT(5f), 0f)
        assertEquals(1f, TriggerSpecs.staggerT(900f), 0f)
    }
}
