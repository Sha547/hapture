package com.klynstudios.hapture.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.klynstudios.hapture.core.model.ObjectFill
import com.klynstudios.hapture.core.model.ObjectShape
import com.klynstudios.hapture.core.model.ObjectStyle
import com.klynstudios.hapture.ui.design.Chip
import com.klynstudios.hapture.ui.design.IconKind
import com.klynstudios.hapture.ui.design.TextLink

/**
 * Preview the motion on the thing you're actually designing: a pill feels
 * different from a circle, a solid square different from a light outline --
 * and CUSTOM is whatever you draw. Changes apply live to the stage and are saved with the
 * experiment.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ObjectControls(
    style: ObjectStyle,
    onChange: (ObjectStyle) -> Unit,
    onCommit: () -> Unit,
) {
    var drawing by remember { mutableStateOf(false) }

    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ObjectShape.entries.forEach { shape ->
                Chip(shape.label, selected = shape == style.shape, onClick = {
                    if (shape == ObjectShape.CUSTOM && style.customPath.size < 3) {
                        drawing = true
                    } else {
                        onChange(style.copy(shape = shape))
                        onCommit()
                    }
                })
            }
        }
        if (style.shape == ObjectShape.CUSTOM) {
            Spacer(Modifier.height(4.dp))
            TextLink(label = "Redraw", icon = IconKind.FORWARD, onClick = { drawing = true })
        }
        Spacer(Modifier.height(14.dp))
        LabeledSlider(
            "Size", style.sizeT,
            onValueChange = { onChange(style.copy(sizeT = it)) },
            onValueChangeFinished = onCommit,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ObjectFill.entries.forEach { fill ->
                Chip(fill.label, selected = fill == style.fill, onClick = {
                    onChange(style.copy(fill = fill))
                    onCommit()
                })
            }
        }
    }

    if (drawing) {
        ShapeDrawer(
            onUse = { points ->
                onChange(style.copy(shape = ObjectShape.CUSTOM, customPath = points))
                onCommit()
                drawing = false
            },
            onDismiss = { drawing = false },
        )
    }
}
