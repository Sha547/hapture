package com.klynstudios.hapture.core

import com.klynstudios.hapture.core.capture.ObjectTracker
import com.klynstudios.hapture.core.capture.SpringFit
import com.klynstudios.hapture.export.SpringMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureTest {

    private fun curve(k: Double, z: Double, seconds: Double = 1.2, fps: Int = 60) =
        (0..(seconds * fps).toInt()).map { i ->
            val t = i / fps.toDouble()
            t.toFloat() to SpringMath.stepResponse(t, k, z).toFloat()
        }

    @Test fun `fit recovers a bouncy spring`() {
        val r = SpringFit.fit(curve(400.0, 0.3))!!
        assertEquals(400f, r.stiffness, 400f * 0.08f)
        assertEquals(0.3f, r.dampingRatio, 0.04f)
        assertTrue(r.rms < 0.01f)
    }

    @Test fun `fit recovers a well damped spring`() {
        val r = SpringFit.fit(curve(900.0, 0.9))!!
        assertEquals(900f, r.stiffness, 900f * 0.1f)
        assertEquals(0.9f, r.dampingRatio, 0.08f)
    }

    @Test fun `fit tolerates noise`() {
        val rnd = java.util.Random(7)
        val noisy = curve(600.0, 0.5).map { it.first to it.second + (rnd.nextGaussian() * 0.02).toFloat() }
        val r = SpringFit.fit(noisy)!!
        assertEquals(0.5f, r.dampingRatio, 0.12f)
        assertEquals(600f, r.stiffness, 600f * 0.25f)
    }

    @Test fun `too few samples cannot be fitted`() {
        assertNull(SpringFit.fit(listOf(0f to 0f, 1f to 1f)))
    }

    @Test fun `locate finds a coloured square`() {
        val w = 40
        val h = 20
        val px = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        for (y in 5 until 10) for (x in 10 until 20) px[y * w + x] = 0xFFD64545.toInt()
        val (cx, cy, area) = ObjectTracker.locate(px, w, h, 0xFFD64545.toInt())!!
        assertEquals((14.5f) / w, cx, 1e-3f)
        assertEquals(7f / h, cy, 1e-3f)
        assertEquals(50f / (w * h), area, 1e-6f)
        assertNull(ObjectTracker.locate(IntArray(w * h) { 0xFFFFFFFF.toInt() }, w, h, 0xFFD64545.toInt()))
    }

    @Test fun `tracked x motion becomes a curve that fits back to the spring`() {
        val truth = curve(500.0, 0.45, 1.5, 30)
        val samples = truth.map { (t, v) -> ObjectTracker.Sample(t + 0.2f, 0.2f + 0.5f * v, 0.5f, 0.02f) }
        val padded = listOf(ObjectTracker.Sample(0f, 0.2f, 0.5f, 0.02f), ObjectTracker.Sample(0.1f, 0.2f, 0.5f, 0.02f)) + samples
        val c = ObjectTracker.toCurve(padded)!!
        val r = SpringFit.fit(c)!!
        assertEquals(0.45f, r.dampingRatio, 0.1f)
        assertEquals(500f, r.stiffness, 500f * 0.25f)
    }

    @Test fun `scale animation is picked when nothing translates`() {
        val truth = curve(400.0, 0.5, 1.2, 30)
        val samples = truth.map { (t, v) -> ObjectTracker.Sample(t, 0.5f, 0.5f, (0.02f * (1f + 0.8f * v)).let { it * it / 0.02f }) }
        assertNotNull(ObjectTracker.toCurve(samples))
    }

    @Test fun `a static object gives no curve`() {
        val samples = List(20) { ObjectTracker.Sample(it / 30f, 0.5f, 0.5f, 0.02f) }
        assertNull(ObjectTracker.toCurve(samples))
    }
}
