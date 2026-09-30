package com.klynstudios.hapture.core.spec

import com.klynstudios.hapture.export.SpringMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A judgement about a motion: something a designer would want to know before shipping it.
 * [rule] says which check raised it; [fixLabel] is set when [MotionLint.fix] can clear it in one tap.
 */
data class LintIssue(val level: Level, val message: String, val rule: Rule? = null, val fixLabel: String? = null) {
    enum class Level { WARN, INFO }
    enum class Rule { SLUGGISH, INSTANT, WOBBLE, OVERSHOOT, JITTER, STAGGER, NO_HAPTICS }
}

/** A one-tap change that clears an issue. */
sealed interface LintFix {
    /** Swap the main spring for this one. */
    data class Spring(val stiffness: Float, val dampingRatio: Float) : LintFix

    /** Turn haptics on, with this preset (a [com.klynstudios.hapture.core.haptics.HapticPreset] name, lowercase). */
    data class Haptic(val preset: String) : LintFix
}

object MotionLint {

    /** The slider range every editor works in (see ParameterMapping), so a fix always lands on a reachable spring. */
    private const val MIN_K = 50f
    private const val MAX_K = 3000f

    fun check(spec: MotionSpec): List<LintIssue> {
        val issues = ArrayList<LintIssue>()
        val s = spec.spring
        val settle = s.settleMs
        val trigger = spec.trigger != "release"

        if (settle > 700) issues += LintIssue(LintIssue.Level.WARN, "Settles in $settle ms; over ~700 ms reads as sluggish for a response to a tap.", LintIssue.Rule.SLUGGISH, "Speed it up")
        if (settle < 90) issues += LintIssue(LintIssue.Level.INFO, "Settles in $settle ms; that is close to instant, so the spring will barely be seen.", LintIssue.Rule.INSTANT, "Slow it a touch")
        if (s.dampingRatio < 0.25f) issues += LintIssue(LintIssue.Level.WARN, "Damping ratio ${fmt(s.dampingRatio)} rings for a long time; expect visible wobble.", LintIssue.Rule.WOBBLE, "Calm the wobble")
        if (trigger && s.overshootPercent > 30f) issues += LintIssue(LintIssue.Level.WARN, "${s.overshootPercent.roundToInt()}% overshoot is large for a control; small UI usually stays under ~20%.", LintIssue.Rule.OVERSHOOT, "Cut to 15%")
        if (s.stiffness > 2500f && s.dampingRatio < 0.5f) issues += LintIssue(LintIssue.Level.WARN, "Very stiff and under-damped: may look like jitter on 60 Hz screens.", LintIssue.Rule.JITTER, "Damp the jitter")
        if (spec.staggerTotalMs > 900) issues += LintIssue(LintIssue.Level.WARN, "Stagger delays add up to ${spec.staggerTotalMs} ms before the last item starts; people will wait for it.", LintIssue.Rule.STAGGER)
        if (spec.haptic == "off" && trigger) issues += LintIssue(LintIssue.Level.INFO, "Haptics are off; a tap-triggered motion usually feels better with a light tick.", LintIssue.Rule.NO_HAPTICS, "Use Crisp")
        return issues
    }

    /**
     * The change that clears [rule] for [spec], or null if there isn't one in reach. Computed on demand, not in
     * [check]: finding a stiffness for a settle time is a search, too slow to run on every slider frame.
     */
    fun fix(spec: MotionSpec, rule: LintIssue.Rule): LintFix? {
        val s = spec.spring
        return when (rule) {
            LintIssue.Rule.SLUGGISH -> sluggishFix(s)
            LintIssue.Rule.INSTANT -> stiffnessFor(s.dampingRatio, 180)?.let { LintFix.Spring(it, s.dampingRatio) }
            LintIssue.Rule.WOBBLE -> LintFix.Spring(s.stiffness, WOBBLE_FIX)
            LintIssue.Rule.OVERSHOOT -> LintFix.Spring(s.stiffness, dampingForOvershoot(0.15f))
            LintIssue.Rule.JITTER -> LintFix.Spring(s.stiffness, JITTER_FIX)
            LintIssue.Rule.NO_HAPTICS -> LintFix.Haptic("crisp")
            LintIssue.Rule.STAGGER -> null
        }
    }

    /**
     * A slow settle has two causes: a soft spring, or so little damping that it rings. Ringing is fixed by
     * damping (a stiffer spring would only ring faster), softness by stiffness at the same damping.
     */
    private fun sluggishFix(s: SpringSpec): LintFix.Spring? {
        if (s.dampingRatio < 0.5f) {
            val z = 0.7f
            val k = if (SpringMath.settleMs(s.stiffness, z) <= TARGET_SETTLE_MS) s.stiffness else stiffnessFor(z, TARGET_SETTLE_MS) ?: return null
            return LintFix.Spring(k, z)
        }
        val z = s.dampingRatio.coerceAtMost(1f)
        val k = stiffnessFor(z, TARGET_SETTLE_MS) ?: return null
        return LintFix.Spring(k, z)
    }

    /**
     * The stiffness, within the slider range, whose settle time at [dampingRatio] is closest to [targetMs].
     * Settle time falls as stiffness rises, so a bisection on log stiffness finds it. Null if even the ends
     * of the range can't get near it.
     */
    internal fun stiffnessFor(dampingRatio: Float, targetMs: Int): Float? {
        var lo = ln(MIN_K.toDouble())
        var hi = ln(MAX_K.toDouble())
        repeat(20) {
            val mid = (lo + hi) / 2
            if (SpringMath.settleMs(exp(mid).toFloat(), dampingRatio) > targetMs) lo = mid else hi = mid
        }
        val k = exp(hi).toFloat().coerceIn(MIN_K, MAX_K)
        val got = SpringMath.settleMs(k, dampingRatio)
        return if (abs(got - targetMs) <= targetMs * 0.35f) k else null
    }

    /** The damping ratio whose peak overshoot is [fraction] of the travel (inverse of [SpringMath.overshoot]). */
    internal fun dampingForOvershoot(fraction: Float): Float {
        val l = -ln(fraction.toDouble())
        return (l / sqrt(PI * PI + l * l)).toFloat()
    }

    private const val TARGET_SETTLE_MS = 450
    private const val WOBBLE_FIX = 0.4f
    private const val JITTER_FIX = 0.65f

    private fun fmt(v: Float) = "%.2f".format(v)
}
