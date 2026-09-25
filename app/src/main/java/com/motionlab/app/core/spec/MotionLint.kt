package com.motionlab.app.core.spec

/** A judgement about a motion: something a designer would want to know before shipping it. */
data class LintIssue(val level: Level, val message: String) {
    enum class Level { WARN, INFO }
}

object MotionLint {

    fun check(spec: MotionSpec): List<LintIssue> {
        val issues = ArrayList<LintIssue>()
        val s = spec.spring
        val settle = s.settleMs
        val trigger = spec.trigger != "release"

        if (settle > 700) issues += LintIssue(LintIssue.Level.WARN, "Settles in $settle ms; over ~700 ms reads as sluggish for a response to a tap.")
        if (settle < 90) issues += LintIssue(LintIssue.Level.INFO, "Settles in $settle ms; that is close to instant, so the spring will barely be seen.")
        if (s.dampingRatio < 0.25f) issues += LintIssue(LintIssue.Level.WARN, "Damping ratio ${fmt(s.dampingRatio)} rings for a long time; expect visible wobble.")
        if (trigger && s.overshootPercent > 30f) issues += LintIssue(LintIssue.Level.WARN, "${s.overshootPercent.toInt()}% overshoot is large for a control; small UI usually stays under ~20%.")
        if (s.stiffness > 2500f && s.dampingRatio < 0.5f) issues += LintIssue(LintIssue.Level.WARN, "Very stiff and under-damped: may look like jitter on 60 Hz screens.")
        if (spec.staggerTotalMs > 900) issues += LintIssue(LintIssue.Level.WARN, "Stagger delays add up to ${spec.staggerTotalMs} ms before the last item starts; people will wait for it.")
        if (spec.haptic == "off" && trigger) issues += LintIssue(LintIssue.Level.INFO, "Haptics are off; a tap-triggered motion usually feels better with a light tick.")
        return issues
    }

    private fun fmt(v: Float) = "%.2f".format(v)
}
