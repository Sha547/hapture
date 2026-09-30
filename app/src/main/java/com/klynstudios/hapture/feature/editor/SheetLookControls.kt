package com.klynstudios.hapture.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.klynstudios.hapture.core.model.ObjectFill
import com.klynstudios.hapture.core.model.ObjectShape
import com.klynstudios.hapture.core.model.ObjectStyle
import com.klynstudios.hapture.ui.design.Chip

/** Corner radius of the sheet's top edge, stored as the closest [ObjectShape] so it rides the existing columns. */
internal val SHEET_CORNERS = listOf(
    ObjectShape.SQUARE to "Sharp",
    ObjectShape.ROUNDED to "Rounded",
    ObjectShape.CIRCLE to "Soft",
)

internal fun sheetCornerDp(shape: ObjectShape): Float = when (shape) {
    ObjectShape.SQUARE -> 6f
    ObjectShape.ROUNDED -> 20f
    // The picker never offers PILL or CUSTOM here -- a drawn shape doesn't
    // mean anything for a sheet's straight top edge -- but an imported file
    // could carry either; both read as the softest corner rather than crash.
    ObjectShape.CIRCLE, ObjectShape.PILL, ObjectShape.CUSTOM -> 32f
}

/** What the sheet itself looks like: how round its top corners are, and how it is filled. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SheetLookControls(style: ObjectStyle, onChange: (ObjectStyle) -> Unit, onCommit: () -> Unit) {
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SHEET_CORNERS.forEach { (shape, label) ->
                // The picker never offers PILL or CUSTOM, but an imported file might carry either: treat them as the softest corner.
                val selected = shape == style.shape ||
                    (shape == ObjectShape.CIRCLE && style.shape in listOf(ObjectShape.PILL, ObjectShape.CUSTOM))
                Chip(label, selected = selected, onClick = {
                    onChange(style.copy(shape = shape))
                    onCommit()
                })
            }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ObjectFill.entries.forEach { fill ->
                Chip(fill.label, selected = fill == style.fill, onClick = {
                    onChange(style.copy(fill = fill))
                    onCommit()
                })
            }
        }
    }
}
