package com.motionlab.app.core.model

/** Pure playback helpers for haptic markers on a timeline. */
object HapticMarkers {

    /** Indices of markers with time in (fromMs, toMs] -- the ones a playhead moving forward just passed. */
    fun crossed(markerTimesMs: List<Int>, fromMs: Float, toMs: Float): List<Int> {
        if (toMs <= fromMs) return emptyList()
        return markerTimesMs.indices.filter { markerTimesMs[it] > fromMs && markerTimesMs[it] <= toMs }
    }

    /** The timeline is at least as long as its last marker, so a marker is never off the end of the bar. */
    fun totalMs(motionTotalMs: Int, markerTimesMs: List<Int>): Int =
        maxOf(motionTotalMs, markerTimesMs.maxOrNull() ?: 0)
}
