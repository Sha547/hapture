package com.motionlab.app.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.motionlab.app.core.capture.SketchMotion
import com.motionlab.app.core.capture.SketchMotion.TimedPoint
import com.motionlab.app.core.capture.SpringFit
import com.motionlab.app.core.doodle.Brush
import com.motionlab.app.core.doodle.DoodleBackground
import com.motionlab.app.core.doodle.DoodleStroke
import com.motionlab.app.core.doodle.Pt
import com.motionlab.app.export.SpringMath
import com.motionlab.app.feature.doodle.drawDoodleStroke
import com.motionlab.app.ui.design.Chip
import com.motionlab.app.ui.design.LocalTokens
import com.motionlab.app.ui.design.PrimaryButton
import com.motionlab.app.ui.design.TextLink

/**
 * The third way to capture a feel: drag a finger across a fake phone screen to show
 * how an object should move. The trail is painted by the doodle brush engine; the
 * timing of every touch point goes to [SketchMotion] and on to the same [SpringFit]
 * the other two capture methods use. Meant for someone who would rather show than tune.
 */
@Composable
fun SketchPad(fit: SpringFit.Result?, span: Float, onSketch: (List<Pair<Float, Float>>?) -> Unit) {
    val t = LocalTokens.current
    var brush by remember { mutableStateOf(Brush.PEN) }
    var stroke by remember { mutableStateOf<DoodleStroke?>(null) }
    var from by remember { mutableStateOf<Offset?>(null) }
    var to by remember { mutableStateOf<Offset?>(null) }
    var replayKey by remember { mutableIntStateOf(0) }
    var replayT by remember { mutableFloatStateOf(-1f) }
    val background = if (t.dark) DoodleBackground.INK else DoodleBackground.PAPER

    // Replays the fitted spring along the drawn line; ends by itself so the screen is idle otherwise.
    LaunchedEffect(replayKey) {
        if (replayKey == 0) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            val sec = withFrameNanos { (it - start) / 1e9f }
            replayT = sec
            if (sec >= span + 0.3f) break
        }
    }

    Text(
        "Put your finger where the object starts and drag it to where it should land, at the speed you want. " +
            "Go past the spot and come back to add bounce.",
        style = MaterialTheme.typography.bodySmall, color = t.inkSoft,
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Brush.PEN, Brush.MARKER, Brush.PAINT).forEach { b -> Chip(b.label, selected = brush == b, onClick = { brush = b }) }
    }
    Spacer(Modifier.height(12.dp))

    val frame = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(0.62f).aspectRatio(9f / 16f)
                .clip(frame).background(t.surface).border(t.hairline, t.line, frame)
                .testTag("sketchCanvas")
                .drawBehind {
                    // A stand-in for an app screen: a few quiet blocks, so distances have something to be relative to.
                    listOf(0.06f to 0.05f, 0.16f to 0.07f, 0.9f to 0.05f).forEachIndexed { i, (y, h) ->
                        drawRoundRect(t.line, Offset(size.width * 0.08f, size.height * y), Size(size.width * (if (i == 2) 0.84f else 0.5f), size.height * h), CornerRadius(8f))
                    }
                    stroke?.let { drawDoodleStroke(it, background) }
                    from?.let { drawCircle(t.ink, 5.dp.toPx(), Offset(it.x * size.width, it.y * size.height)) }
                    to?.let { drawCircle(t.inkSoft, 5.dp.toPx(), Offset(it.x * size.width, it.y * size.height)) }
                    val a = from; val b = to
                    if (fit != null && a != null && b != null && replayT >= 0f) {
                        val s = SpringMath.stepResponse(replayT.toDouble(), fit.stiffness.toDouble(), fit.dampingRatio.toDouble()).toFloat()
                        drawCircle(t.ink, 11.dp.toPx(), Offset((a.x + (b.x - a.x) * s) * size.width, (a.y + (b.y - a.y) * s) * size.height))
                    }
                }
                .pointerInput(brush) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        replayT = -1f
                        val pts = ArrayList<TimedPoint>()
                        fun add(p: androidx.compose.ui.input.pointer.PointerInputChange) {
                            pts += TimedPoint(
                                (p.position.x / size.width).coerceIn(0f, 1f), (p.position.y / size.height).coerceIn(0f, 1f),
                                (p.uptimeMillis - down.uptimeMillis).toInt(),
                            )
                        }
                        add(down)
                        fun show() {
                            stroke = DoodleStroke(brush, t.ink.toArgb(), 0.018f, pts.map { Pt(it.x, it.y) })
                        }
                        show()
                        while (true) {
                            val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            add(ch); ch.consume(); show()
                        }
                        from = Offset(pts.first().x, pts.first().y)
                        to = Offset(pts.takeLast(3).map { it.x }.sorted()[1], pts.takeLast(3).map { it.y }.sorted()[1])
                        onSketch(SketchMotion.toCurve(pts))
                    }
                },
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (fit != null) PrimaryButton("Replay the fit", onClick = { replayKey++ })
        if (stroke != null) TextLink("Clear", onClick = { stroke = null; from = null; to = null; replayT = -1f; onSketch(null) })
    }
}
