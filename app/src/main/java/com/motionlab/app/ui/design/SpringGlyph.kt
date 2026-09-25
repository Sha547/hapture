package com.motionlab.app.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.motionlab.app.core.spec.SpringFingerprint
import com.motionlab.app.core.spec.SpringSpec

/** The spring's signature: its curve on a dashed rest line, with a bar underneath that grows as the spring gets slower. */
@Composable
fun SpringGlyph(spring: SpringSpec, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val print = remember(spring.stiffness, spring.dampingRatio) { SpringFingerprint.of(spring) }
    val shape = RoundedCornerShape(8.dp)
    Canvas(modifier.size(40.dp).background(t.surface, shape).border(t.hairline, t.line, shape)) {
        val pad = 6.dp.toPx()
        val w = size.width - 2 * pad
        val top = pad
        val bottom = size.height - pad - 5.dp.toPx()
        val ceiling = maxOf(1.05f, print.shape.max())
        fun y(v: Float) = bottom - (bottom - top) * (v / ceiling)
        drawLine(t.line, Offset(pad, y(1f)), Offset(pad + w, y(1f)), 1f)
        val path = Path()
        print.shape.forEachIndexed { i, v ->
            val x = pad + w * i / (print.shape.size - 1)
            if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
        }
        drawPath(path, t.ink, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val barY = size.height - pad + 1.dp.toPx()
        drawLine(t.inkFaint, Offset(pad, barY), Offset(pad + w * (0.15f + 0.85f * print.speed), barY), 1.5.dp.toPx(), StrokeCap.Round)
    }
}
