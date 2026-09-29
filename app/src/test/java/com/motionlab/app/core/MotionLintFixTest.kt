package com.motionlab.app.core

import com.motionlab.app.core.spec.LintFix
import com.motionlab.app.core.spec.LintIssue
import com.motionlab.app.core.spec.MotionLint
import com.motionlab.app.core.spec.MotionSpec
import com.motionlab.app.core.spec.SpringSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every one-tap fix must actually clear the issue it's offered for, and land on a spring the sliders can reach. */
class MotionLintFixTest {

    private fun tap(spring: SpringSpec, haptic: String = "crisp") =
        MotionSpec.forRelease("x", "toggle", spring, emptyMap(), haptic).copy(trigger = "tap")

    private fun fixed(spec: MotionSpec, rule: LintIssue.Rule): MotionSpec {
        val fix = MotionLint.fix(spec, rule)
        assertNotNull("no fix for $rule", fix)
        return when (fix) {
            is LintFix.Spring -> {
                assertTrue(fix.stiffness in 50f..3000f)
                assertTrue(fix.dampingRatio in 0.15f..1.5f)
                spec.copy(spring = SpringSpec(fix.stiffness, fix.dampingRatio))
            }
            is LintFix.Haptic -> spec.copy(haptic = fix.preset)
            null -> spec
        }
    }

    private fun rules(spec: MotionSpec) = MotionLint.check(spec).mapNotNull { it.rule }

    @Test fun sluggishSoftSpringSpeedsUp() {
        val spec = tap(SpringSpec(60f, 1.2f))
        assertTrue(LintIssue.Rule.SLUGGISH in rules(spec))
        val after = fixed(spec, LintIssue.Rule.SLUGGISH)
        assertTrue(LintIssue.Rule.SLUGGISH !in rules(after))
    }

    @Test fun sluggishBecauseItRingsGetsDampedNotStiffened() {
        val spec = tap(SpringSpec(300f, 0.2f))
        assertTrue(LintIssue.Rule.SLUGGISH in rules(spec))
        val after = fixed(spec, LintIssue.Rule.SLUGGISH)
        assertTrue(after.spring.dampingRatio > spec.spring.dampingRatio)
        assertTrue(LintIssue.Rule.SLUGGISH !in rules(after))
    }

    @Test fun wobbleOvershootAndJitterClear() {
        for ((spring, rule) in listOf(
            SpringSpec(600f, 0.18f) to LintIssue.Rule.WOBBLE,
            SpringSpec(800f, 0.3f) to LintIssue.Rule.OVERSHOOT,
            SpringSpec(2900f, 0.3f) to LintIssue.Rule.JITTER,
        )) {
            val spec = tap(spring)
            assertTrue("$rule not raised", rule in rules(spec))
            assertTrue("$rule still raised after its fix", rule !in rules(fixed(spec, rule)))
        }
    }

    @Test fun overshootFixLandsOnFifteenPercent() {
        val after = fixed(tap(SpringSpec(800f, 0.3f)), LintIssue.Rule.OVERSHOOT)
        assertEquals(15f, after.spring.overshootPercent, 0.5f)
    }

    @Test fun instantSpringSlowsIntoView() {
        val spec = tap(SpringSpec(3000f, 1.5f))
        if (LintIssue.Rule.INSTANT in rules(spec)) {
            assertTrue(LintIssue.Rule.INSTANT !in rules(fixed(spec, LintIssue.Rule.INSTANT)))
        }
    }

    @Test fun hapticsOffTurnsOnCrisp() {
        val spec = tap(SpringSpec(700f, 0.9f), haptic = "off")
        assertEquals(LintFix.Haptic("crisp"), MotionLint.fix(spec, LintIssue.Rule.NO_HAPTICS))
        assertTrue(LintIssue.Rule.NO_HAPTICS !in rules(fixed(spec, LintIssue.Rule.NO_HAPTICS)))
    }

    @Test fun everyIssueWithALabelHasAFixRule() {
        val spec = tap(SpringSpec(2900f, 0.16f), haptic = "off")
        MotionLint.check(spec).filter { it.fixLabel != null }.forEach { assertNotNull(it.rule) }
    }
}
