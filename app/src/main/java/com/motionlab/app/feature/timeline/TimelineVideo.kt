package com.motionlab.app.feature.timeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.motionlab.app.core.model.ObjectFill
import com.motionlab.app.core.model.ObjectShape
import com.motionlab.app.core.model.ObjectStyle
import com.motionlab.app.core.model.TimelineFrames
import com.motionlab.app.core.model.TimelineSegment
import com.motionlab.app.core.model.closedSmoothSegments
import java.io.File
import kotlin.math.roundToInt

/** Theme colours as ARGB, so the video matches the theme the person is using. */
class VideoColors(val canvas: Int, val surface: Int, val line: Int, val ink: Int, val inkSoft: Int, val inkFaint: Int, val accent: Int)

class VideoStep(val label: String, val spring: TimelineFrames.StepSpring, val style: ObjectStyle)

class VideoJob(
    val name: String,
    val steps: List<VideoStep>,
    val segments: List<TimelineSegment>,
    /** Haptic markers as (time in ms, effect name), in the same order as the marker times. */
    val markers: List<Pair<Int, String>>,
    val colors: VideoColors,
    val typeface: Typeface = Typeface.SANS_SERIF,
)

/**
 * Records a timeline into a portrait MP4: the same preview object driven by the same curves as the
 * screen, the scrub bar with its haptic ticks (which light as each one fires), and the running time.
 * Frames are drawn on a plain canvas and handed to the H.264 encoder with exact timestamps, so the
 * video runs at the timeline's real speed however long rendering takes.
 */
object TimelineVideo {
    const val W = 720
    const val H = 1280
    private const val TIMEOUT_US = 10_000L

    fun render(job: VideoJob, out: File, onProgress: (Float) -> Unit = {}): Boolean {
        out.parentFile?.mkdirs()
        out.delete()
        val total = TimelineFrames.totalMs(job.segments, job.markers.map { it.first })
        val frames = TimelineFrames.frameCount(total)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, TimelineFrames.FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var ok = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var track = -1
            val info = MediaCodec.BufferInfo()

            /** Moves finished output into the file. With [untilEnd] it waits for the end-of-stream marker. */
            fun drain(untilEnd: Boolean) {
                while (true) {
                    val i = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        i == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!untilEnd) return
                        i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            track = muxer!!.addTrack(codec.outputFormat)
                            muxer!!.start(); muxerStarted = true
                        }
                        i >= 0 -> {
                            val buf = codec.getOutputBuffer(i)!!
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                            if (info.size > 0 && muxerStarted) {
                                buf.position(info.offset); buf.limit(info.offset + info.size)
                                muxer!!.writeSampleData(track, buf, info)
                            }
                            codec.releaseOutputBuffer(i, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }

            fun nextInput(): Int {
                while (true) {
                    val i = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (i >= 0) return i
                    drain(false)
                }
            }

            val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val pixels = IntArray(W * H)
            val painter = Painter(job)
            val springs = job.steps.map { it.spring }
            val markerTimes = job.markers.map { it.first }
            for (n in 0 until frames) {
                val ms = TimelineFrames.timeMs(n)
                painter.draw(canvas, TimelineFrames.frameAt(ms, job.segments, springs, markerTimes), total)
                bitmap.getPixels(pixels, 0, W, 0, 0, W, H)
                val idx = nextInput()
                fillYuv(codec.getInputImage(idx)!!, pixels)
                codec.queueInputBuffer(idx, 0, W * H * 3 / 2, ms * 1000L, 0)
                drain(false)
                onProgress((n + 1) / frames.toFloat())
            }
            val last = nextInput()
            codec.queueInputBuffer(last, 0, 0, TimelineFrames.timeMs(frames) * 1000L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
            ok = muxerStarted
        } catch (_: Exception) {
            ok = false
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { if (muxerStarted) muxer?.stop() }
            runCatching { muxer?.release() }
            if (!ok) out.delete()
        }
        return ok
    }

    /** ARGB pixels to the encoder's YUV 4:2:0 planes (BT.601, limited range), whatever layout the device chose. */
    private fun fillYuv(image: Image, px: IntArray) {
        val (yp, up, vp) = image.planes.let { Triple(it[0], it[1], it[2]) }
        val yb = yp.buffer; val ub = up.buffer; val vb = vp.buffer
        for (y in 0 until H) {
            for (x in 0 until W) {
                val c = px[y * W + x]
                val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
                yb.put(y * yp.rowStride + x * yp.pixelStride, (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte())
                if (y % 2 == 0 && x % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    ub.put((y / 2) * up.rowStride + (x / 2) * up.pixelStride, u.toByte())
                    vb.put((y / 2) * vp.rowStride + (x / 2) * vp.pixelStride, v.toByte())
                }
            }
        }
    }

    /** Draws one frame. Kept separate from encoding so it can be looked at (and tested) on its own. */
    class Painter(private val job: VideoJob) {
        private val c = job.colors
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = job.typeface }

        fun draw(canvas: Canvas, f: TimelineFrames.Frame, totalMs: Int) {
            canvas.drawColor(c.canvas)
            label(canvas, job.name, 48f, 120f, 46f, c.ink)
            label(canvas, "Motion Lab", 48f, 168f, 26f, c.inkSoft)

            val step = f.stepIndex?.let { job.steps.getOrNull(it) }
            label(canvas, step?.let { "${f.stepIndex!! + 1}/${job.steps.size}  ${it.label}" } ?: " ", 48f, 262f, 28f, c.inkSoft)

            val stage = RectF(48f, 290f, 672f, 770f)
            fill.color = c.surface; canvas.drawRoundRect(stage, 32f, 32f, fill)
            stroke.color = c.line; stroke.strokeWidth = 2f; canvas.drawRoundRect(stage, 32f, 32f, stroke)

            val style = step?.style ?: job.steps.firstOrNull()?.style ?: ObjectStyle()
            val cx = stage.centerX() + f.offset * 200f
            val cy = stage.centerY()
            if (f.pulse > 0f) {
                stroke.color = c.ink; stroke.alpha = (f.pulse * 160).roundToInt(); stroke.strokeWidth = 4f
                canvas.drawCircle(cx, cy, style.sizeDp * 1.6f + 70f * (1f - f.pulse), stroke)
                stroke.alpha = 255
            }
            drawObject(canvas, style, cx, cy)

            val firing = f.firing.lastOrNull()?.let { job.markers.getOrNull(it) }
            label(canvas, firing?.let { "haptic  ${it.second}" } ?: " ", 48f, 850f, 30f, c.ink)

            scrubBar(canvas, f, totalMs)
            label(canvas, "%.2f s / %.2f s".format(f.timeMs / 1000f, totalMs / 1000f), 48f, 1100f, 28f, c.inkSoft)
            label(canvas, "Made with Motion Lab", 48f, 1220f, 22f, c.inkFaint)
        }

        private fun scrubBar(canvas: Canvas, f: TimelineFrames.Frame, totalMs: Int) {
            val x0 = 48f; val w = 624f
            val safe = totalMs.coerceAtLeast(1).toFloat()
            stroke.color = c.line; stroke.strokeWidth = 3f
            canvas.drawLine(x0, 1000f, x0 + w, 1000f, stroke)
            var cursor = 0
            job.segments.forEachIndexed { i, seg ->
                cursor += seg.gapBeforeMs
                val a = x0 + w * cursor / safe
                cursor += seg.durationMs
                val b = x0 + w * cursor / safe
                fill.color = c.ink; fill.alpha = if (i % 2 == 0) 217 else 115
                canvas.drawRect(a, 968f, maxOf(b, a + 2f), 1032f, fill)
                fill.alpha = 255
            }
            job.markers.forEachIndexed { i, (ms, _) ->
                val x = x0 + w * ms / safe
                stroke.color = if (i in f.firing) c.ink else c.inkSoft
                stroke.strokeWidth = if (i in f.firing) 8f else 4f
                canvas.drawLine(x, 936f, x, 962f, stroke)
            }
            val px = x0 + w * f.timeMs.coerceAtMost(totalMs) / safe
            stroke.color = c.ink; stroke.strokeWidth = 5f
            canvas.drawLine(px, 940f, px, 1060f, stroke)
        }

        private fun drawObject(canvas: Canvas, s: ObjectStyle, cx: Float, cy: Float) {
            val h = s.sizeDp * 2.6f
            val aspect = if (s.shape == ObjectShape.CUSTOM) 1f else s.shape.aspect
            val w = h * aspect
            val rect = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
            val (fillColor, strokeColor, strokeW) = when (s.fill) {
                ObjectFill.INK -> Triple(c.ink, 0, 0f)
                ObjectFill.MARKER -> Triple(c.accent, c.ink, 2f)
                ObjectFill.PAPER -> Triple(c.surface, c.ink, 5f)
            }
            fun paint(shapeDraw: (Paint) -> Unit) {
                fill.color = fillColor; shapeDraw(fill)
                if (strokeW > 0f) { stroke.color = strokeColor; stroke.strokeWidth = strokeW; if (s.fill == ObjectFill.MARKER) stroke.alpha = 46; shapeDraw(stroke); stroke.alpha = 255 }
            }
            val segs = if (s.shape == ObjectShape.CUSTOM) closedSmoothSegments(s.customPath) else emptyList()
            if (segs.isNotEmpty()) {
                val path = Path().apply {
                    moveTo(rect.left + s.customPath[0].x * w, rect.top + s.customPath[0].y * h)
                    segs.forEach { cubicTo(rect.left + it.c1.x * w, rect.top + it.c1.y * h, rect.left + it.c2.x * w, rect.top + it.c2.y * h, rect.left + it.end.x * w, rect.top + it.end.y * h) }
                    close()
                }
                paint { canvas.drawPath(path, it) }
            } else {
                val r = when (s.shape) {
                    ObjectShape.SQUARE -> 12f
                    ObjectShape.CIRCLE, ObjectShape.PILL -> h / 2
                    else -> h * 0.28f
                }
                paint { canvas.drawRoundRect(rect, r, r, it) }
            }
        }

        private fun label(canvas: Canvas, s: String, x: Float, y: Float, size: Float, color: Int) {
            text.textSize = size; text.color = color
            canvas.drawText(s, x, y, text)
        }
    }
}
