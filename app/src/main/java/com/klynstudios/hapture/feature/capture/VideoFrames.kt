package com.klynstudios.hapture.feature.capture

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.klynstudios.hapture.core.capture.ObjectTracker
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Pulls frames out of a video the person picked, at a fixed rate, small enough to scan quickly. */
object VideoFrames {
    private const val SCAN_WIDTH = 96

    fun durationSec(context: Context, uri: Uri): Float = withRetriever(context, uri) { r ->
        (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000f
    }

    fun frameAt(context: Context, uri: Uri, sec: Float): Bitmap? = withRetriever(context, uri) { r ->
        r.getFrameAtTime((sec * 1_000_000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST)
    }

    /** Colour of the pixel at fraction ([fx], [fy]) of [bitmap]. */
    fun colorAt(bitmap: Bitmap, fx: Float, fy: Float): Int =
        bitmap.getPixel((fx * bitmap.width).toInt().coerceIn(0, bitmap.width - 1), (fy * bitmap.height).toInt().coerceIn(0, bitmap.height - 1))

    /** Tracks [target] from [startSec] for [lengthSec], [fps] frames a second. */
    suspend fun track(
        context: Context, uri: Uri, target: Int, startSec: Float, lengthSec: Float = 3f, fps: Int = 30,
        onProgress: (Float) -> Unit = {},
    ): List<ObjectTracker.Sample> = trackAll(context, uri, listOf(target), startSec, lengthSec, fps, onProgress).first()

    /**
     * Tracks several colours in one pass over the video, so the frames are decoded once however many objects there are.
     * Stops between frames if the calling coroutine is cancelled (the person left the screen).
     */
    suspend fun trackAll(
        context: Context, uri: Uri, targets: List<Int>, startSec: Float, lengthSec: Float = 3f, fps: Int = 30,
        onProgress: (Float) -> Unit = {},
    ): List<List<ObjectTracker.Sample>> = withRetriever(context, uri) { r ->
        val total = (lengthSec * fps).toInt()
        val out = targets.map { ArrayList<ObjectTracker.Sample>() }
        for (i in 0 until total) {
            currentCoroutineContext().ensureActive()
            val t = startSec + i / fps.toFloat()
            val full = r.getFrameAtTime((t * 1_000_000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST) ?: break
            val h = (SCAN_WIDTH * full.height.toFloat() / full.width).toInt().coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(full, SCAN_WIDTH, h, true)
            val px = IntArray(SCAN_WIDTH * h)
            small.getPixels(px, 0, SCAN_WIDTH, 0, 0, SCAN_WIDTH, h)
            // A full video frame is megabytes; don't leave one per frame for the GC. (createScaledBitmap hands back
            // the same bitmap when no scaling was needed, so that one is only recycled once.)
            if (small !== full) small.recycle()
            full.recycle()
            targets.forEachIndexed { n, target ->
                ObjectTracker.locate(px, SCAN_WIDTH, h, target)?.let { (cx, cy, area) ->
                    out[n] += ObjectTracker.Sample(i / fps.toFloat(), cx, cy, area)
                }
            }
            onProgress((i + 1) / total.toFloat())
        }
        out
    }

    private inline fun <T> withRetriever(context: Context, uri: Uri, block: (MediaMetadataRetriever) -> T): T {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            return block(r)
        } finally {
            r.release()
        }
    }
}
