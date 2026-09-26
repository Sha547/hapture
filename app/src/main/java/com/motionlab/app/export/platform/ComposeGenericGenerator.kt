package com.motionlab.app.export.platform

import com.motionlab.app.core.spec.MotionSpec

/** Compose for trigger interactions, and the spring-only Compose view of any spec. */
object ComposeGenericGenerator {
    fun generate(source: MotionSpec): String {
        val spec = source.resolved() // chained transitions become plain delays
        val p = pascal(spec.name)
        val s = spec.spring
        val states = spec.transitions.mapIndexed { i, t ->
            val gate = if (t.delayMs > 0) "on$i" else "active"
            val delayed = if (t.delayMs > 0) {
                "    var on$i by remember { mutableStateOf(false) }\n" +
                    "    LaunchedEffect(active) { delay(${t.delayMs}L); on$i = active }\n"
            } else ""
            delayed + "    val v$i by animateFloatAsState(if ($gate) ${n(t.to)}f else ${n(t.from)}f, spec, label = \"${t.property}\")"
        }.joinToString("\n")
        val layer = spec.transitions.withIndex().filter { it.value.property != "progress" }.joinToString("; ") { (i, t) ->
            when (t.property) {
                "scale" -> "scaleX = v$i; scaleY = v$i"
                "offsetY" -> "translationY = v$i.dp.toPx()"
                "opacity" -> "alpha = v$i"
                "rotation" -> "rotationZ = v$i"
                else -> "translationX = v$i.dp.toPx()"
            }
        }
        // "progress" is a 0..1 value with no single property to write to (a track fill, a colour blend): expose it, don't guess.
        val progress = spec.transitions.withIndex().filter { it.value.property == "progress" }
            .joinToString("") { (i, _) -> "    // v$i is the 0..1 progress: use it for a track colour, a fill or a custom drawing.\n" }
        return header(spec, "//") + """
            |import android.provider.Settings
            |import kotlinx.coroutines.delay
            |import androidx.compose.animation.core.animateFloatAsState
            |import androidx.compose.animation.core.spring
            |import androidx.compose.foundation.background
            |import androidx.compose.foundation.clickable
            |import androidx.compose.foundation.layout.Box
            |import androidx.compose.foundation.layout.size
            |import androidx.compose.foundation.shape.RoundedCornerShape
            |import androidx.compose.runtime.*
            |import androidx.compose.ui.Modifier
            |import androidx.compose.ui.draw.clip
            |import androidx.compose.ui.graphics.Color
            |import androidx.compose.ui.graphics.graphicsLayer
            |import androidx.compose.ui.platform.LocalContext
            |import androidx.compose.ui.unit.dp
            |
            |/** Usage skeleton: tap toggles; wire `active` to your own trigger. */
            |@Composable
            |fun ${p}Demo(modifier: Modifier = Modifier) {
            |    var active by remember { mutableStateOf(false) }
            |    val context = LocalContext.current
            |    // Reduced Motion: the system "remove animations" setting.
            |    val reduce = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            |    val spec = spring<Float>(dampingRatio = if (reduce) 1f else ${n(s.dampingRatio, 2)}f, stiffness = ${n(s.stiffness, 1)}f)
            |$states
            |$progress
            |    Box(
            |        modifier
            |            .size(96.dp)${if (layer.isEmpty()) "" else "\n            |            .graphicsLayer { $layer }"}
            |            .clip(RoundedCornerShape(24.dp))
            |            .background(Color(0xFF141413))
            |            .clickable { active = !active },
            |    )
            |}
            |
            |// Haptic: ${spec.haptic}. LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.TextHandleMove) at the trigger.
        """.trimMargin() + "\n"
    }
}
