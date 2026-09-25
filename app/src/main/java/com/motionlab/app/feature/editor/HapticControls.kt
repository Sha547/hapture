package com.motionlab.app.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.motionlab.app.core.haptics.HapticPreset
import com.motionlab.app.ui.design.Chip
import com.motionlab.app.ui.design.LocalTokens

/**
 * How the interaction feels in the hand. Picking a preset plays it once, so
 * you can compare Soft and Firm by touch. Saved with the experiment and
 * included in the design spec.
 */
@Composable
internal fun HapticControls(preset: HapticPreset, onSelect: (HapticPreset) -> Unit) {
    val t = LocalTokens.current
    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HapticPreset.entries) { p ->
                Chip(p.label, selected = p == preset, onClick = { onSelect(p) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(preset.description, style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
    }
}
