package com.klynstudios.hapture.core.model

import com.klynstudios.hapture.export.SpringMath
import kotlin.math.ceil

/**
 * What a rendered video of a timeline shows at each frame. It uses exactly the maths of the
 * in-app preview ([TimelineMath.activeStepAt] and the step's own spring step-response), so the
 * video and the screen can't disagree, and it has no Android dependency so it is unit-testable.
 */
object TimelineFrames {
    const val FPS = 30
    /** Held on the finished state so the last frame isn't cut off. */
    const val TAIL_MS = 500
    /** How long a haptic tick stays lit after it fires. */
    const val PULSE_MS = 240

    class StepSpring(val stiffness: Float, val dampingRatio: Float)

    /**
     * [stepIndex] is null in a gap. [offset] runs -1..1 across the step (0 at rest, as in the app preview).
     * [firing] are the indices of haptic markers lit right now and [pulse] is the freshest one's strength, 1 just fired down to 0.
     */
    data class Frame(val timeMs: Int, val stepIndex: Int?, val offset: Float, val firing: List<Int>, val pulse: Float)

    fun totalMs(segments: List<TimelineSegment>, markerTimesMs: List<Int>): Int =
        HapticMarkers.totalMs(TimelineMath.totalMs(segments), markerTimesMs) + TAIL_MS

    fun frameCount(totalMs: Int): Int = ceil(totalMs * FPS / 1000.0).toInt() + 1

    fun timeMs(frame: Int): Int = (frame * 1000L / FPS).toInt()

    fun frameAt(
        timeMs: Int,
        segments: List<TimelineSegment>,
        springs: List<StepSpring>,
        markerTimesMs: List<Int>,
    ): Frame {
        val active = TimelineMath.activeStepAt(timeMs.toFloat(), segments)
        var offset = 0f
        if (active != null) {
            val seg = segments[active.index]
            val s = springs.getOrNull(active.index)
            if (s != null) {
                val x = SpringMath.stepResponse(active.progress * seg.durationMs / 1000.0, s.stiffness.toDouble(), s.dampingRatio.toDouble()).toFloat()
                offset = x * 2f - 1f
            }
        }
        val firing = markerTimesMs.indices.filter { timeMs >= markerTimesMs[it] && timeMs - markerTimesMs[it] < PULSE_MS }
        val pulse = firing.maxOfOrNull { 1f - (timeMs - markerTimesMs[it]) / PULSE_MS.toFloat() } ?: 0f
        return Frame(timeMs, active?.index, offset, firing, pulse)
    }
}
