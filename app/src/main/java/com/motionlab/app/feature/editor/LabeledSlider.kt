package com.motionlab.app.feature.editor

import androidx.compose.runtime.Composable
import com.motionlab.app.ui.design.ValueSlider

/**
 * Shared by every experiment screen's "MOTION" control group (spec §21).
 * [onValueChangeFinished] fires once per release, not per frame of drag --
 * that's the hook screens use to persist to Room, so dragging a slider
 * doesn't hit the database on every pixel of movement.
 */
@Composable
internal fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
) = ValueSlider(label, value, onValueChange, onValueChangeFinished, hint = SliderHints.forLabel(label))

/** Plain words for the two spring sliders every editor has, so nobody has to know what stiffness or damping mean. */
internal object SliderHints {
    const val STIFFNESS = "How fast it snaps back to rest."
    const val DAMPING = "How much it bounces. Low bounces a lot, high doesn't bounce."

    fun forLabel(label: String): String? = when (label) {
        "Stiffness" -> STIFFNESS
        "Damping" -> DAMPING
        else -> null
    }
}
