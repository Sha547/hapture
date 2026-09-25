package com.motionlab.app.data

import com.motionlab.app.core.physics.ParameterMapping
import com.motionlab.app.export.SpringMath

/**
 * How long this experiment's own motion takes to settle, in ms -- every
 * experiment type has a stiffness/damping pair driving *some* spring (its
 * main motion for Spring Drag and Magnetic Snap, a return-to-rest spring for
 * everything else: Swipe and Fling, Bottom Sheet, Pull to Refresh, Pinch to
 * Zoom and Drag to Reorder), so one formula covers every type without
 * type-specific cases. This is what a [TimelineStepEntity] is sized by.
 */
val ExperimentEntity.nominalDurationMs: Int
    get() {
        val k = ParameterMapping.stiffness(stiffnessT)
        val z = ParameterMapping.dampingRatio(dampingT)
        return SpringMath.settleMs(k, z)
    }
