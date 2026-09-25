package com.motionlab.app

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.motionlab.app.core.capture.ObjectTracker
import com.motionlab.app.core.capture.SpringFit
import com.motionlab.app.feature.capture.VideoFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The whole video path on a real decoder: an encoded mp4 of a red square moving on a spring
 * (stiffness 500, damping ratio 0.45, generated offline), tracked frame by frame and fitted.
 */
@RunWith(AndroidJUnit4::class)
class CaptureInstrumentedTest {

    @Test
    fun aRecordedSpringIsRecoveredFromItsVideo() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val file = File(instr.targetContext.cacheDir, "spring_ref.mp4")
        instr.context.assets.open("spring_ref.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        val uri = Uri.fromFile(file)
        val ctx = instr.targetContext

        assertEquals(2.5f, VideoFrames.durationSec(ctx, uri), 0.2f)
        val first = VideoFrames.frameAt(ctx, uri, 0f)
        assertNotNull(first)
        val target = VideoFrames.colorAt(first!!, (40f + 20f) / 320f, 120f / 240f)

        val samples = VideoFrames.track(ctx, uri, target, startSec = 0f, lengthSec = 2.4f)
        val curve = ObjectTracker.toCurve(samples)
        assertNotNull("motion should be detected", curve)
        val fit = SpringFit.fit(curve!!)!!
        assertEquals(0.45f, fit.dampingRatio, 0.12f)
        assertEquals(500f, fit.stiffness, 500f * 0.3f)
    }

    /** Two squares in one clip: red (stiffness 500, ratio 0.5) starts at 0.20 s, blue (300, 0.8) at 0.45 s. */
    @Test
    fun twoObjectsInOneClipGetTheirOwnSpringsAndTheGapBetweenThem() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val file = File(instr.targetContext.cacheDir, "two_spring_ref.mp4")
        instr.context.assets.open("two_spring_ref.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        val uri = Uri.fromFile(file)
        val ctx = instr.targetContext

        val first = VideoFrames.frameAt(ctx, uri, 0f)!!
        val red = VideoFrames.colorAt(first, 60f / 320f, 60f / 240f)
        val blue = VideoFrames.colorAt(first, 60f / 320f, 150f / 240f)
        assertTrue(com.motionlab.app.core.capture.MultiTrack.distinguishable(red, blue))

        val both = VideoFrames.trackAll(ctx, uri, listOf(red, blue), startSec = 0f, lengthSec = 2.4f)
        val out = com.motionlab.app.core.capture.MultiTrack.analyse(both[0], both[1])
        val a = (out as com.motionlab.app.core.capture.MultiTrack.Outcome.Ok).analysis
        assertEquals(0.5f, a.first.fit.dampingRatio, 0.15f)
        assertEquals(0.8f, a.second.fit.dampingRatio, 0.2f)
        assertEquals("delay ${a.delayMs} ms", 250f, a.delayMs.toFloat(), 70f)
    }
}
