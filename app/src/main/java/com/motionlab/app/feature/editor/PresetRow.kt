package com.motionlab.app.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.motionlab.app.core.model.MotionPreset
import com.motionlab.app.ui.design.Chip

/**
 * Quick-start row (spec §24). Tapping a chip sets Stiffness/Damping to that
 * preset's values -- it's a starting point, not a locked-in animation, so
 * the sliders remain freely tunable afterward.
 */
@Composable
internal fun PresetRow(selected: MotionPreset?, onSelect: (MotionPreset) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MotionPreset.entries) { preset ->
            Chip(label = preset.displayName, selected = preset == selected, onClick = { onSelect(preset) })
        }
    }
}
