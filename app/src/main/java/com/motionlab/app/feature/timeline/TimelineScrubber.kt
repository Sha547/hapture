package com.motionlab.app.feature.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.motionlab.app.core.model.TimelineSegment
import com.motionlab.app.ui.design.LocalTokens

/**
 * A horizontal strip of the whole sequence: each step's own share of the bar,
 * alternating shade so consecutive steps read as distinct, gaps left as plain
 * track. Built on Compose's real [Slider] (same track/thumb customization
 * [com.motionlab.app.ui.design.ValueSlider] already uses) rather than
 * hand-rolled pointer input, so drag, tap-to-seek and accessibility semantics
 * all come for free.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScrubber(
    segments: List<TimelineSegment>,
    totalMs: Int,
    playheadMs: Float,
    markersMs: List<Int> = emptyList(),
    onScrub: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val safeTotalMs = totalMs.coerceAtLeast(1).toFloat()

    Slider(
        modifier = modifier.fillMaxWidth().testTag("timelineScrubber"),
        value = playheadMs.coerceIn(0f, safeTotalMs),
        onValueChange = onScrub,
        valueRange = 0f..safeTotalMs,
        thumb = { Box(Modifier.width(3.dp).height(44.dp).background(t.ink)) },
        track = {
            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                drawLine(t.line, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 2f)
                var cursor = 0
                segments.forEachIndexed { i, seg ->
                    cursor += seg.gapBeforeMs
                    val x0 = size.width * cursor / safeTotalMs
                    cursor += seg.durationMs
                    val x1 = size.width * cursor / safeTotalMs
                    val alpha = if (i % 2 == 0) 0.85f else 0.45f
                    drawRect(
                        color = t.ink.copy(alpha = alpha),
                        topLeft = Offset(x0, size.height * 0.2f),
                        size = Size((x1 - x0).coerceAtLeast(1f), size.height * 0.6f),
                    )
                }
                markersMs.forEach { ms ->
                    val x = size.width * ms / safeTotalMs
                    drawLine(t.ink, Offset(x, 0f), Offset(x, size.height * 0.16f), 3f)
                }
            }
        },
    )
}
