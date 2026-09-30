package com.klynstudios.hapture.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.klynstudios.hapture.core.model.CustomPoint
import com.klynstudios.hapture.core.model.ObjectShape
import com.klynstudios.hapture.core.model.ObjectStyle
import com.klynstudios.hapture.core.model.closedSmoothSegments

/** A closed smooth outline through [points] (each 0f..1f), scaled to fill whatever size it's drawn at. */
class DrawnShape(private val points: List<CustomPoint>) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val segments = closedSmoothSegments(points)
        if (segments.isEmpty()) return Outline.Rectangle(androidx.compose.ui.geometry.Rect(Offset.Zero, size))

        val path = Path()
        val start = points[0]
        path.moveTo(start.x * size.width, start.y * size.height)
        for (seg in segments) {
            path.cubicTo(
                seg.c1.x * size.width, seg.c1.y * size.height,
                seg.c2.x * size.width, seg.c2.y * size.height,
                seg.end.x * size.width, seg.end.y * size.height,
            )
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** The shape to draw an object in, from its style. CUSTOM with nothing drawn yet falls back to ROUNDED. */
fun ObjectStyle.toComposeShape(): Shape = when {
    shape == ObjectShape.CUSTOM && customPath.size >= 3 -> DrawnShape(customPath)
    shape == ObjectShape.SQUARE -> RoundedCornerShape(6.dp)
    shape == ObjectShape.CIRCLE || shape == ObjectShape.PILL -> RoundedCornerShape(50)
    else -> RoundedCornerShape((sizeDp * 0.28f).dp) // ROUNDED, and CUSTOM before anything's drawn
}
