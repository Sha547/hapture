package com.motionlab.app.feature.timeline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.motionlab.app.ui.design.AppIcon
import com.motionlab.app.ui.design.Hairline
import com.motionlab.app.ui.design.IconKind
import com.motionlab.app.ui.design.LocalTokens
import com.motionlab.app.ui.theme.MonoSmall

/**
 * One step in the list: what it plays, how long it takes, the gap before it,
 * and its place in the order. Reorder is up/down buttons rather than a drag
 * gesture -- deterministic, keyboard/accessibility-friendly, and doesn't
 * fight the page's own scroll the way a free drag would.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TimelineStepRow(
    icon: IconKind,
    title: String,
    durationMs: Int,
    gapMs: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    active: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onGapDecrease: () -> Unit,
    onGapIncrease: () -> Unit,
    onLongClick: () -> Unit,
) {
    val t = LocalTokens.current
    val titleColor = t.ink

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = onLongClick)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tile = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .size(40.dp)
                    .background(if (active) t.ink else t.surface, tile)
                    .border(t.hairline, if (active) t.ink else t.line, tile),
                contentAlignment = Alignment.Center,
            ) { AppIcon(icon, tint = if (active) t.canvas else titleColor, size = 20.dp) }
            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (gapMs > 0) "${durationMs} ms, ${gapMs} ms gap before" else "$durationMs ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.inkSoft,
                )
            }

            ReorderButton(IconKind.UP, enabled = canMoveUp, onClick = onMoveUp, testTag = "moveUp_$title")
            Spacer(Modifier.width(4.dp))
            ReorderButton(IconKind.DOWN, enabled = canMoveDown, onClick = onMoveDown, testTag = "moveDown_$title")
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Gap", style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
            Spacer(Modifier.width(10.dp))
            GapStepper(onClick = onGapDecrease, symbol = "−", testTag = "gapDecrease_$title")
            Spacer(Modifier.width(10.dp))
            Text("${gapMs} ms", style = MonoSmall, color = t.ink, modifier = Modifier.testTag("gapValue_$title"))
            Spacer(Modifier.width(10.dp))
            GapStepper(onClick = onGapIncrease, symbol = "+", testTag = "gapIncrease_$title")
        }
        Hairline()
    }
}

@Composable
private fun ReorderButton(icon: IconKind, enabled: Boolean, onClick: () -> Unit, testTag: String) {
    val t = LocalTokens.current
    Box(
        Modifier
            .testTag(testTag)
            .size(32.dp)
            .clip(CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, tint = if (enabled) t.ink else t.inkFaint, size = 16.dp)
    }
}

@Composable
private fun GapStepper(onClick: () -> Unit, symbol: String, testTag: String) {
    val t = LocalTokens.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .testTag(testTag)
            .size(28.dp)
            .clip(shape)
            .background(if (pressed) t.line else Color.Transparent)
            .border(t.hairline, t.line, shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = MaterialTheme.typography.labelLarge, color = t.ink) }
}
