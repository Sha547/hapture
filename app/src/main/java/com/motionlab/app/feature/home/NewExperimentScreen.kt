package com.motionlab.app.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.motionlab.app.data.ExperimentType
import com.motionlab.app.ui.design.AppIcon
import com.motionlab.app.ui.design.AppScreen
import com.motionlab.app.ui.design.Hairline
import com.motionlab.app.ui.design.IconKind
import com.motionlab.app.ui.design.ListRow
import com.motionlab.app.ui.design.LocalTokens
import com.motionlab.app.ui.design.TopBar
import com.motionlab.app.ui.design.reveal

/** Interaction type picker (spec §39's "New Experiment" list). */
@Composable
fun NewExperimentScreen(
    onBack: () -> Unit,
    onPick: (ExperimentType) -> Unit,
) {
    val t = LocalTokens.current
    AppScreen(scrollable = true) {
        TopBar(title = "New experiment", onBack = onBack)
        Spacer(Modifier.height(24.dp))

        Column(Modifier.reveal(0)) {
            Text("Choose an interaction", style = MaterialTheme.typography.titleLarge, color = t.ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "Each one starts from sensible defaults and saves as you tune it.",
                style = MaterialTheme.typography.bodyMedium,
                color = t.inkSoft,
            )
        }
        Spacer(Modifier.height(32.dp))

        Column(Modifier.reveal(1)) {
            Hairline()
            ListRow(
                title = "Spring drag", subtitle = "Rubber-band resistance, spring release",
                leading = IconKind.SPRING,
                trailing = { AppIcon(IconKind.FORWARD, tint = t.inkSoft, size = 18.dp) },
                onClick = { onPick(ExperimentType.SPRING_DRAG) },
            )
            ListRow(
                title = "Magnetic snap", subtitle = "Pulls toward the nearest point",
                leading = IconKind.MAGNET,
                trailing = { AppIcon(IconKind.FORWARD, tint = t.inkSoft, size = 18.dp) },
                onClick = { onPick(ExperimentType.MAGNETIC_SNAP) },
            )
            ListRow(
                title = "Swipe and fling", subtitle = "Velocity-driven dismissal",
                leading = IconKind.SWIPE,
                trailing = { AppIcon(IconKind.FORWARD, tint = t.inkSoft, size = 18.dp) },
                onClick = { onPick(ExperimentType.SWIPE_FLING) },
            )
            ListRow(
                title = "Bottom sheet", subtitle = "Detents and drag-to-dismiss",
                leading = IconKind.SHEET,
                trailing = { AppIcon(IconKind.FORWARD, tint = t.inkSoft, size = 18.dp) },
                onClick = { onPick(ExperimentType.BOTTOM_SHEET) },
            )
            ListRow(
                title = "Pull to refresh", subtitle = "Elastic pull, hold, release",
                leading = IconKind.REFRESH,
                trailing = { AppIcon(IconKind.FORWARD, tint = t.inkSoft, size = 18.dp) },
                onClick = { onPick(ExperimentType.PULL_REFRESH) },
            )
            ListRow(
                title = "Pinch to zoom", subtitle = "Two-finger scale, bounded",
                leading = IconKind.ZOOM,
                trailing = { AppIcon(IconKind.FORWARD, tint = t.inkSoft, size = 18.dp) },
                onClick = { onPick(ExperimentType.PINCH_ZOOM) },
            )
            ListRow(
                title = "Drag to reorder", subtitle = "Swap threshold, spring settle",
                leading = IconKind.REORDER,
                trailing = { AppIcon(IconKind.FORWARD, tint = t.inkSoft, size = 18.dp) },
                onClick = { onPick(ExperimentType.DRAG_REORDER) },
            )
            ListRow(
                title = "Toggle", subtitle = "Switch thumb with a spring",
                leading = IconKind.TOGGLE,
                trailing = { AppIcon(IconKind.FORWARD, tint = t.inkSoft, size = 18.dp) },
                onClick = { onPick(ExperimentType.TOGGLE) },
            )
            ListRow(
                title = "Button press", subtitle = "Press depth and release",
                leading = IconKind.TAP,
                trailing = { AppIcon(IconKind.FORWARD, tint = t.inkSoft, size = 18.dp) },
                onClick = { onPick(ExperimentType.BUTTON_PRESS) },
            )
            ListRow(
                title = "Tab indicator", subtitle = "Sliding underline",
                leading = IconKind.TABS,
                trailing = { AppIcon(IconKind.FORWARD, tint = t.inkSoft, size = 18.dp) },
                onClick = { onPick(ExperimentType.TAB_INDICATOR) },
            )
            ListRow(
                title = "Staggered list", subtitle = "Items arriving in turn",
                leading = IconKind.STAGGER,
                trailing = { AppIcon(IconKind.FORWARD, tint = t.inkSoft, size = 18.dp) },
                onClick = { onPick(ExperimentType.STAGGER_LIST) },
            )
            ListRow(
                title = "Like burst", subtitle = "Pop and particles",
                leading = IconKind.HEART,
                trailing = { AppIcon(IconKind.FORWARD, tint = t.inkSoft, size = 18.dp) },
                onClick = { onPick(ExperimentType.LIKE_BURST) },
            )
        }
    }
}
