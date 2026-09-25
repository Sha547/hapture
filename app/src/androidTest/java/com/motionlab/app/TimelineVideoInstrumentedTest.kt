package com.motionlab.app

import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.motionlab.app.core.model.ObjectStyle
import com.motionlab.app.core.model.TimelineFrames
import com.motionlab.app.core.model.TimelineSegment
import com.motionlab.app.feature.capture.VideoFrames
import com.motionlab.app.feature.timeline.TimelineVideo
import com.motionlab.app.feature.timeline.VideoColors
import com.motionlab.app.feature.timeline.VideoJob
import com.motionlab.app.feature.timeline.VideoStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The whole video path on the real encoder: render a timeline, then decode the MP4 back. */
@RunWith(AndroidJUnit4::class)
class TimelineVideoInstrumentedTest {

    @Test
    fun aTimelineRendersToAPortraitMp4ThatMovesAndLastsAsLongAsTheTimeline() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val segments = listOf(TimelineSegment(0, 600, "Drag"), TimelineSegment(300, 700, "Toggle"))
        val job = VideoJob(
            name = "Checkout flow",
            steps = listOf(
                VideoStep("Spring drag", TimelineFrames.StepSpring(400f, 0.7f), ObjectStyle()),
                VideoStep("Toggle", TimelineFrames.StepSpring(300f, 0.9f), ObjectStyle()),
            ),
            segments = segments,
            markers = listOf(400 to "click", 1100 to "success"),
            colors = VideoColors(0xFFF7F6F3.toInt(), 0xFFFFFFFF.toInt(), 0xFFE7E5E0.toInt(), 0xFF141413.toInt(), 0xFF6F6D67.toInt(), 0xFFA9A7A1.toInt(), 0xFFCFDFEB.toInt()),
        )
        val out = File(ctx.cacheDir, "videos/test-timeline.mp4")
        var last = 0f
        assertTrue("render succeeded", TimelineVideo.render(job, out) { last = it })
        assertEquals(1f, last, 0.001f)
        assertTrue("file has content", out.length() > 5_000)

        val expected = TimelineFrames.totalMs(segments, listOf(400, 1100)) / 1000f
        val uri = Uri.fromFile(out)
        assertEquals(expected, VideoFrames.durationSec(ctx, uri), 0.25f)

        val start = VideoFrames.frameAt(ctx, uri, 0.05f)!!
        assertEquals(TimelineVideo.W, start.width)
        assertEquals(TimelineVideo.H, start.height)
        val mid = VideoFrames.frameAt(ctx, uri, 0.45f)!! // the object has moved and the click tick is lit
        File(ctx.getExternalFilesDir(null), "video-frame-0.png").outputStream().use { mid.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        File(ctx.getExternalFilesDir(null), "video-frame-start.png").outputStream().use { start.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }

        // The stage centre row differs between frames: something moved.
        fun row(b: android.graphics.Bitmap) = (48 until 672 step 8).map { b.getPixel(it, 530) }
        assertNotEquals(row(start), row(mid))
        // The canvas colour survived encoding (a corner far from anything drawn).
        val corner = start.getPixel(10, 700)
        assertTrue(Color.red(corner) in 235..255 && Color.green(corner) in 235..255)
    }
}
