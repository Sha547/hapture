package com.klynstudios.hapture.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class IconKind {
    BACK, FORWARD, UP, DOWN, PLUS, SPRING, MAGNET, SWIPE, SHEET, COPY, SHARE, CHECK, TIMELINE, PLAY, PAUSE,
    REFRESH, ZOOM, REORDER, PIN, BRUSH, UNDO, REDO,
    TOGGLE, TAP, TABS, STAGGER, HEART,
}

// One family: 24-unit grid, one stroke weight, round caps and joins.
private fun pts(vararg v: Float): List<Offset> = List(v.size / 2) { Offset(v[it * 2], v[it * 2 + 1]) }

private fun arc(cx: Float, cy: Float, r: Float, fromDeg: Float, toDeg: Float, steps: Int = 14): List<Offset> =
    List(steps + 1) {
        val a = (fromDeg + (toDeg - fromDeg) * it / steps) * (PI.toFloat() / 180f)
        Offset(cx + r * cos(a), cy + r * sin(a))
    }

private val strokes: Map<IconKind, List<List<Offset>>> = mapOf(
    IconKind.BACK to listOf(pts(19f, 12f, 5f, 12f), pts(11f, 6f, 5f, 12f, 11f, 18f)),
    IconKind.FORWARD to listOf(pts(5f, 12f, 19f, 12f), pts(13f, 6f, 19f, 12f, 13f, 18f)),
    IconKind.UP to listOf(pts(12f, 19f, 12f, 5f), pts(6f, 11f, 12f, 5f, 18f, 11f)),
    IconKind.DOWN to listOf(pts(12f, 5f, 12f, 19f), pts(6f, 13f, 12f, 19f, 18f, 13f)),
    IconKind.PLUS to listOf(pts(12f, 5f, 12f, 19f), pts(5f, 12f, 19f, 12f)),
    IconKind.SPRING to listOf(pts(12f, 2.5f, 12f, 5f, 18f, 7.5f, 6f, 11f, 18f, 14.5f, 6f, 18f, 12f, 20.5f, 12f, 22f)),
    IconKind.MAGNET to listOf(
        pts(6f, 3.5f, 6f, 11f) + arc(12f, 11f, 6f, 180f, 0f) + pts(18f, 3.5f),
        pts(3.5f, 3.5f, 8.5f, 3.5f), pts(15.5f, 3.5f, 20.5f, 3.5f),
    ),
    IconKind.SWIPE to listOf(pts(9f, 12f, 20f, 12f), pts(15f, 7f, 20f, 12f, 15f, 17f), pts(4f, 8f, 7f, 8f), pts(4f, 16f, 7f, 16f)),
    IconKind.SHEET to listOf(pts(4f, 21f, 4f, 9f, 20f, 9f, 20f, 21f), pts(9.5f, 13f, 14.5f, 13f), pts(9f, 4f, 15f, 4f)),
    IconKind.COPY to listOf(pts(9f, 9f, 20f, 9f, 20f, 20f, 9f, 20f, 9f, 9f), pts(5f, 15f, 4f, 15f, 4f, 4f, 15f, 4f, 15f, 5f)),
    IconKind.SHARE to listOf(pts(12f, 15f, 12f, 4f), pts(8f, 8f, 12f, 4f, 16f, 8f), pts(5f, 12f, 5f, 20f, 19f, 20f, 19f, 12f)),
    IconKind.CHECK to listOf(pts(5f, 12.5f, 10f, 17.5f, 19f, 7f)),
    IconKind.TIMELINE to listOf(pts(4f, 12f, 20f, 12f), pts(8f, 8.5f, 8f, 15.5f), pts(13f, 8.5f, 13f, 15.5f), pts(18f, 8.5f, 18f, 15.5f)),
    IconKind.PLAY to listOf(pts(7f, 5f, 7f, 19f, 19f, 12f, 7f, 5f)),
    IconKind.PAUSE to listOf(pts(8f, 5f, 8f, 19f), pts(16f, 5f, 16f, 19f)),
    IconKind.REFRESH to listOf(arc(12f, 12f, 7f, -60f, 220f) + pts(19f, 5.5f, 19.5f, 10f, 15f, 9f)),
    IconKind.ZOOM to listOf(arc(11f, 11f, 6f, 0f, 360f), pts(15.5f, 15.5f, 20.5f, 20.5f)),
    IconKind.REORDER to listOf(pts(6f, 7f, 18f, 7f), pts(6f, 12f, 18f, 12f), pts(6f, 17f, 13f, 17f)),
    IconKind.BRUSH to listOf(pts(19f, 4f, 10f, 13f), pts(10f, 13f, 7f, 13f, 5.5f, 15.5f, 5f, 19f, 8.5f, 18.5f, 11f, 17f, 10f, 13f)),
    IconKind.UNDO to listOf(pts(4f, 11f, 15f, 11f) + arc(15f, 15.5f, 4.5f, -90f, 90f) + pts(8f, 20f), pts(8f, 6.5f, 4f, 11f, 8f, 15.5f)),
    IconKind.REDO to listOf(pts(20f, 11f, 9f, 11f) + arc(9f, 15.5f, 4.5f, 270f, 90f) + pts(16f, 20f), pts(16f, 6.5f, 20f, 11f, 16f, 15.5f)),
    IconKind.TOGGLE to listOf(pts(7f, 8f, 17f, 8f) + arc(17f, 12f, 4f, -90f, 90f) + pts(7f, 16f) + arc(7f, 12f, 4f, 90f, 270f), arc(16f, 12f, 1.6f, 0f, 360f)),
    IconKind.TAP to listOf(arc(12f, 12f, 3f, 0f, 360f), arc(12f, 12f, 7f, -40f, 40f), arc(12f, 12f, 7f, 140f, 220f)),
    IconKind.TABS to listOf(pts(4f, 9f, 8f, 9f), pts(10f, 9f, 14f, 9f), pts(16f, 9f, 20f, 9f), pts(4f, 16f, 10f, 16f)),
    IconKind.STAGGER to listOf(pts(4f, 6f, 20f, 6f), pts(6f, 12f, 20f, 12f), pts(9f, 18f, 20f, 18f)),
    IconKind.HEART to listOf(pts(12f, 20f) + arc(7.5f, 9f, 4.5f, 130f, 360f, 12) + pts(12f, 20f) + arc(16.5f, 9f, 4.5f, 180f, 410f, 12)),
    IconKind.PIN to listOf(arc(12f, 9f, 4f, 0f, 360f), pts(12f, 13f, 12f, 21f)),
)

@Composable
fun AppIcon(
    kind: IconKind,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 20.dp,
) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        strokes.getValue(kind).forEach { s ->
            val path = Path()
            s.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x * u, p.y * u) else path.lineTo(p.x * u, p.y * u) }
            drawPath(path, tint, style = Stroke(1.75f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
