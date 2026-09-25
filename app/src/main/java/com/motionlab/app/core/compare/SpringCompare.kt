package com.motionlab.app.core.compare

import com.motionlab.app.core.model.MotionPreset
import com.motionlab.app.core.physics.ParameterMapping
import com.motionlab.app.core.spec.SpringSpec
import com.motionlab.app.export.SpringMath
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/** Pure logic behind the Compare screen: blending, curves on a shared clock, settle ticks and the blind game. */
object SpringCompare {

    /**
     * The spring [t] of the way from [a] to [b] (0 = a, 1 = b). Stiffness is
     * blended on a log scale (a 100 -> 3000 range would otherwise feel like
     * it changes all at once near b), damping ratio linearly.
     */
    fun blend(a: SpringSpec, b: SpringSpec, t: Float): SpringSpec {
        val f = t.coerceIn(0f, 1f)
        val k = exp(ln(a.stiffness) + (ln(b.stiffness) - ln(a.stiffness)) * f)
        val z = a.dampingRatio + (b.dampingRatio - a.dampingRatio) * f
        return SpringSpec(k, z, a.mass + (b.mass - a.mass) * f)
    }

    fun fromPreset(p: MotionPreset) =
        SpringSpec(ParameterMapping.stiffness(p.stiffnessT), ParameterMapping.dampingRatio(p.dampingT))

    /** Position of each spring in [springs] at [tSec], all released together. */
    fun positions(springs: List<SpringSpec>, tSec: Float): List<Float> =
        springs.map { SpringMath.stepResponse(tSec.toDouble(), it.stiffness.toDouble(), it.dampingRatio.toDouble(), it.mass.toDouble()).toFloat() }

    /** Time axis long enough for the slowest spring, with a little tail. */
    fun spanMs(springs: List<SpringSpec>): Int = ((springs.maxOfOrNull { it.settleMs } ?: 0) * 1.1f).toInt().coerceAtLeast(200)

    /** [count] samples of every spring's step response on one shared clock, so curves overlay honestly. */
    fun curves(springs: List<SpringSpec>, count: Int = 96): List<List<Float>> {
        val span = spanMs(springs) / 1000f
        return springs.map { s ->
            List(count) { i -> positions(listOf(s), span * i / (count - 1)).first() }
        }
    }

    /**
     * When to buzz for "this one has settled". Two springs that settle within
     * [mergeMs] of each other would feel like one muddy tick, so they share one.
     */
    fun settleTicks(settleMs: List<Int>, mergeMs: Int = 40): List<Int> {
        val out = ArrayList<Int>()
        settleMs.sorted().forEach { ms -> if (out.isEmpty() || ms - out.last() > mergeMs) out += ms }
        return out
    }

    /** One blind round: which of two presets is behind the label "A". */
    data class BlindRound(val a: MotionPreset, val b: MotionPreset) {
        fun springA() = fromPreset(a)
        fun springB() = fromPreset(b)
        /** [guess] is the preset the player says A is. */
        fun isCorrect(guess: MotionPreset) = guess == a
    }

    /** Two presets far enough apart that telling them by feel is fair. */
    fun distinct(x: MotionPreset, y: MotionPreset) =
        x != y && abs(x.stiffnessT - y.stiffnessT) + abs(x.dampingT - y.dampingT) >= 0.2f

    fun newRound(random: Random = Random.Default): BlindRound {
        while (true) {
            val x = MotionPreset.entries.random(random)
            val y = MotionPreset.entries.random(random)
            if (distinct(x, y)) return BlindRound(x, y)
        }
    }

    /** Streak after a guess: grows on a right answer, resets on a wrong one. */
    fun nextStreak(streak: Int, correct: Boolean) = if (correct) streak + 1 else 0
}
