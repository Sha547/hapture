package com.klynstudios.hapture.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.klynstudios.hapture.core.model.MaterialSpring
import com.klynstudios.hapture.core.model.MotionPreset
import com.klynstudios.hapture.core.physics.ParameterMapping
import com.klynstudios.hapture.core.spec.SpringSpec
import com.klynstudios.hapture.feature.common.LocalOpenCompare
import com.klynstudios.hapture.ui.design.Chip
import com.klynstudios.hapture.ui.design.IconKind
import com.klynstudios.hapture.ui.design.LocalTokens
import com.klynstudios.hapture.ui.design.TextLink
import kotlin.math.abs

/**
 * Quick-start row. Tapping a chip sets Stiffness/Damping to that
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

/**
 * Everything that sets the spring in one tap: the house presets, Material 3's
 * spatial springs (what `MaterialTheme.motionScheme` hands out, so an Android
 * developer can start from what their app already uses), saved tokens, and a
 * way to race this spring against another.
 */
@Composable
internal fun SpringPresets(
    name: String,
    stiffnessT: Float,
    dampingT: Float,
    selected: MotionPreset?,
    onPreset: (MotionPreset) -> Unit,
    onSpring: (stiffnessT: Float, dampingT: Float) -> Unit,
) {
    val t = LocalTokens.current
    Column {
        PresetRow(selected = selected, onSelect = onPreset)
        Spacer(Modifier.height(18.dp))
        Text("MATERIAL 3", style = MaterialTheme.typography.labelSmall, color = t.inkSoft)
        Spacer(Modifier.height(10.dp))
        MaterialSpring.entries.groupBy { it.scheme }.forEach { (scheme, springs) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    scheme,
                    style = MaterialTheme.typography.bodySmall,
                    color = t.inkSoft,
                    maxLines = 1,
                    modifier = Modifier.width(84.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(springs) { m ->
                        val sT = ParameterMapping.stiffnessT(m.stiffness)
                        val dT = ParameterMapping.dampingT(m.dampingRatio)
                        val on = abs(sT - stiffnessT) < 0.002f && abs(dT - dampingT) < 0.002f
                        Chip(
                            label = m.speed.replaceFirstChar { it.uppercase() },
                            selected = on,
                            onClick = { onSpring(sT, dT) },
                            modifier = Modifier.testTag("m3_${m.name.lowercase()}"),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        TokenRow(onApply = onSpring)
        LocalOpenCompare.current?.let { open ->
            Spacer(Modifier.height(4.dp))
            TextLink(
                label = "Compare with another spring",
                icon = IconKind.SPRING,
                modifier = Modifier.testTag("compareWith"),
                onClick = {
                    open(name, SpringSpec(ParameterMapping.stiffness(stiffnessT), ParameterMapping.dampingRatio(dampingT)))
                },
            )
        }
    }
}
