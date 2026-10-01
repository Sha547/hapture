package com.klynstudios.hapture.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.klynstudios.hapture.core.model.CustomPoint
import com.klynstudios.hapture.core.model.resampleClosed
import com.klynstudios.hapture.ui.design.DrawnShape
import com.klynstudios.hapture.ui.design.Hairline
import com.klynstudios.hapture.ui.design.IconKind
import com.klynstudios.hapture.ui.design.LocalTokens
import com.klynstudios.hapture.ui.design.PrimaryButton
import com.klynstudios.hapture.ui.design.TextLink
import kotlin.math.hypot

private const val TARGET_POINTS = 28
private const val MIN_RAW_POINTS = 8

/**
 * A full-screen sheet for drawing one closed shape with a finger. Traces the
 * live stroke, then on release normalizes it (aspect-preserving, centered in
 * its own square) and resamples it to an even point count -- the same
 * [DrawnShape] that previews it here is what the object renders as everywhere
 * else, so what you see is exactly what you get.
 */
@Composable
fun ShapeDrawer(
    onUse: (List<CustomPoint>) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalTokens.current
    val density = LocalDensity.current
    var committed by remember { mutableStateOf<List<CustomPoint>>(emptyList()) }
    var liveStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var tooSmall by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(t.canvas)
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            Text("Draw a shape", style = MaterialTheme.typography.titleLarge, color = t.ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "Trace a closed outline with your finger. It's smoothed automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = t.inkSoft,
            )
            Spacer(Modifier.height(24.dp))

            val shape = RoundedCornerShape(t.radiusCard)
            Box(
                Modifier
                    .testTag("shapeDrawCanvas")
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(shape)
                    .background(t.surface)
                    .border(t.hairline, t.line, shape)
                    .pointerInput(Unit) {
                        val minStepPx = with(density) { 4.dp.toPx() }
                        var points = ArrayList<Offset>()
                        var last = Offset.Zero
                        detectDragGestures(
                            onDragStart = { start ->
                                points = arrayListOf(start)
                                last = start
                                liveStroke = points
                                tooSmall = false
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val p = change.position
                                if (hypot((p.x - last.x).toDouble(), (p.y - last.y).toDouble()) >= minStepPx) {
                                    points.add(p)
                                    last = p
                                    liveStroke = ArrayList(points)
                                }
                            },
                            onDragEnd = {
                                if (points.size < MIN_RAW_POINTS) {
                                    tooSmall = true
                                } else {
                                    val normalized = normalizeClosed(points, Size(size.width.toFloat(), size.height.toFloat()))
                                    if (normalized != null) {
                                        committed = resampleClosed(normalized, TARGET_POINTS)
                                        tooSmall = false
                                    } else {
                                        tooSmall = true
                                    }
                                }
                                liveStroke = emptyList()
                            },
                            onDragCancel = { liveStroke = emptyList() },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    liveStroke.size >= 2 -> Canvas(Modifier.fillMaxSize()) {
                        val path = Path()
                        path.moveTo(liveStroke[0].x, liveStroke[0].y)
                        for (i in 1 until liveStroke.size) path.lineTo(liveStroke[i].x, liveStroke[i].y)
                        drawPath(path, t.inkSoft, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawPoints(liveStroke, PointMode.Points, t.ink, 6f, StrokeCap.Round)
                    }
                    committed.size >= 3 -> Box(
                        Modifier
                            .fillMaxSize()
                            .padding(28.dp)
                            .background(t.accent, DrawnShape(committed))
                            .border(1.dp, t.ink.copy(alpha = 0.18f), DrawnShape(committed))
                    )
                    else -> Text(
                        if (tooSmall) "That's too small -- try a bigger loop." else "Draw here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.inkFaint,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            TextLink(label = "Clear and redraw", icon = IconKind.FORWARD, onClick = {
                committed = emptyList()
                liveStroke = emptyList()
                tooSmall = false
            })

            Spacer(Modifier.weight(1f))
            Hairline()
            Spacer(Modifier.height(20.dp))

            if (committed.size >= 3) {
                PrimaryButton("Use this shape", onClick = { onUse(committed) }, modifier = Modifier.fillMaxWidth())
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(t.radiusControl))
                        .background(t.line.copy(alpha = 0.5f))
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Draw a shape first", style = MaterialTheme.typography.labelLarge, color = t.inkFaint)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextLink(label = "Cancel", icon = IconKind.BACK, onClick = onDismiss)
            }
        }
    }
}

/**
 * Fits [raw] into its own centered unit square, preserving aspect ratio (the
 * larger dimension maps to the full 0f..1f span) rather than stretching a
 * tall or wide stroke into a distorted square.
 */
private fun normalizeClosed(raw: List<Offset>, canvasSize: Size): List<CustomPoint>? {
    if (raw.size < 3) return null
    val minX = raw.minOf { it.x }; val maxX = raw.maxOf { it.x }
    val minY = raw.minOf { it.y }; val maxY = raw.maxOf { it.y }
    val w = maxX - minX
    val h = maxY - minY
    val scale = maxOf(w, h)
    if (scale < canvasSize.minDimension * 0.06f) return null // a tap or a tiny jitter, not a shape
    val cx = (minX + maxX) / 2f
    val cy = (minY + maxY) / 2f
    return raw.map { p ->
        CustomPoint(
            x = ((p.x - cx) / scale + 0.5f).coerceIn(0.02f, 0.98f),
            y = ((p.y - cy) / scale + 0.5f).coerceIn(0.02f, 0.98f),
        )
    }
}
