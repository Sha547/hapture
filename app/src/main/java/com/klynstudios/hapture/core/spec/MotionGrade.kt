package com.klynstudios.hapture.core.spec

/**
 * A single letter for "is this motion safe to ship", built from [MotionLint] so the
 * grade can never disagree with the warnings next to it. Something a non-technical
 * stakeholder can point at; the [reasons] say why it isn't an A.
 *
 * Reduce Motion is not a deduction: every export path is generated with a critically
 * damped, no-overshoot alternative, so coverage is complete by construction.
 */
data class MotionGrade(val letter: String, val score: Int, val reasons: List<String>) {

    companion object {
        private const val WARN_COST = 15
        private const val INFO_COST = 5

        fun of(spec: MotionSpec): MotionGrade {
            val issues = MotionLint.check(spec)
            val score = (100 - issues.sumOf { if (it.level == LintIssue.Level.WARN) WARN_COST else INFO_COST }).coerceAtLeast(0)
            return MotionGrade(letterFor(score), score, issues.map { it.message })
        }

        /** A bare spring (a token) judged as a tap-triggered control, the strictest common use. */
        fun ofSpring(name: String, spring: SpringSpec): MotionGrade =
            of(MotionSpec(name, "token", "tap", spring, emptyList(), emptyMap(), "crisp"))

        fun letterFor(score: Int): String = when {
            score >= 90 -> "A"
            score >= 80 -> "B"
            score >= 65 -> "C"
            score >= 50 -> "D"
            else -> "F"
        }
    }
}
