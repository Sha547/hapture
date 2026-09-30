package com.klynstudios.hapture.core.capture

import kotlin.math.roundToInt

/**
 * Two objects in one clip (a card and its shadow, two staggered rows): each is fitted
 * to its own spring, and the gap between the moments they start moving is the
 * relative timing, which is what a stagger or a chained transition needs.
 */
object MultiTrack {

    class Tracked(val fit: SpringFit.Result, val curve: List<Pair<Float, Float>>, val onsetSec: Float)

    class Analysis(val first: Tracked, val second: Tracked) {
        /** How long after the first object the second starts moving; negative if it starts first. */
        val delayMs: Int get() = ((second.onsetSec - first.onsetSec) * 1000f).roundToInt()
    }

    sealed interface Outcome {
        data class Ok(val analysis: Analysis) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    /** Two colours that both match the same pixels can't be told apart. */
    fun distinguishable(a: Int, b: Int, tolerance: Int = 40): Boolean = !ObjectTracker.near(a, b, tolerance * 2)

    fun analyse(first: List<ObjectTracker.Sample>, second: List<ObjectTracker.Sample>): Outcome {
        val a = track("Object 1", first) ?: return Outcome.Failed(reasonFor("Object 1", first))
        val b = track("Object 2", second) ?: return Outcome.Failed(reasonFor("Object 2", second))
        return Outcome.Ok(Analysis(a, b))
    }

    private fun track(@Suppress("UNUSED_PARAMETER") label: String, samples: List<ObjectTracker.Sample>): Tracked? {
        val timed = ObjectTracker.toTimedCurve(samples) ?: return null
        val fit = SpringFit.fit(timed.points) ?: return null
        return Tracked(fit, timed.points, timed.onsetSec)
    }

    private fun reasonFor(label: String, samples: List<ObjectTracker.Sample>) =
        if (samples.size < 6) "Couldn't find $label's colour in enough frames."
        else "$label barely moved; pick the object that moves, or start earlier."
}
