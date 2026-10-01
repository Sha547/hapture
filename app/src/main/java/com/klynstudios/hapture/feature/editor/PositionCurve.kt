package com.klynstudios.hapture.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.klynstudios.hapture.ui.design.DashedGuide
import com.klynstudios.hapture.ui.design.LocalTokens

/**
 * Live position curve. Shows the last [samples.size] readings of
 * the dragged object's x-offset so a developer can see *why* an interaction
 * feels the way it does -- overshoot, settle time, bounce count -- while
 * they're still dragging it.
 */
@Composable
fun PositionCurve(
    samples: List<Float>,
    range: Float,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.radiusCard)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(shape)
            .background(t.surface)
            .border(t.hairline, t.line, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        val midY = size.height / 2f
        // Rest position.
        drawLine(
            color = t.line,
            start = Offset(0f, midY),
            end = Offset(size.width, midY),
            strokeWidth = 1.5f,
            pathEffect = DashedGuide,
        )

        if (samples.size < 2) return@Canvas

        val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
        val scaleY = midY / range.coerceAtLeast(1f)

        val path = Path()
        samples.forEachIndexed { i, s ->
            val y = (midY - s * scaleY).coerceIn(0f, size.height)
            if (i == 0) path.moveTo(0f, y) else path.lineTo(i * stepX, y)
        }
        drawPath(path, t.ink, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
