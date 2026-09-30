package com.klynstudios.hapture.data

import com.klynstudios.hapture.core.physics.ParameterMapping
import com.klynstudios.hapture.core.spec.MotionSpec
import com.klynstudios.hapture.core.spec.SpringSpec
import com.klynstudios.hapture.core.spec.TriggerKind
import com.klynstudios.hapture.core.spec.TriggerSpecs

/** The spring this experiment is tuned to, in physical terms. */
fun ExperimentEntity.spring(): SpringSpec =
    SpringSpec(ParameterMapping.stiffness(stiffnessT), ParameterMapping.dampingRatio(dampingT))

/**
 * The neutral spec for a saved experiment without opening its editor, so Home can
 * lint and grade it. Triggers use the same builder as their editor; a gesture is
 * its release spring (its other parameters don't affect any lint check).
 */
fun ExperimentEntity.toMotionSpec(): MotionSpec {
    val haptic = hapticPreset.name.lowercase()
    val kind = when (type) {
        ExperimentType.TOGGLE -> TriggerKind.TOGGLE
        ExperimentType.BUTTON_PRESS -> TriggerKind.BUTTON_PRESS
        ExperimentType.TAB_INDICATOR -> TriggerKind.TAB_INDICATOR
        ExperimentType.STAGGER_LIST -> TriggerKind.STAGGER_LIST
        ExperimentType.LIKE_BURST -> TriggerKind.LIKE_BURST
        else -> null
    }
    return if (kind != null) {
        TriggerSpecs.build(kind, name, stiffnessT, dampingT, triggerAmountT ?: 0.5f, triggerStaggerT ?: 0.5f, haptic)
    } else {
        MotionSpec.forRelease(name, type.name.lowercase(), spring(), emptyMap(), haptic)
    }
}
