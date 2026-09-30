package com.klynstudios.hapture.export

import com.klynstudios.hapture.core.physics.ParameterMapping as P
import com.klynstudios.hapture.core.spec.Chain
import com.klynstudios.hapture.core.spec.MotionSpec
import com.klynstudios.hapture.core.spec.SpringSpec
import com.klynstudios.hapture.core.spec.TriggerKind
import com.klynstudios.hapture.core.spec.TriggerSpecs
import com.klynstudios.hapture.data.MotionTokenEntity
import com.klynstudios.hapture.export.platform.ComposeGenericGenerator
import org.junit.Test
import java.io.File

/**
 * Writes every Kotlin snippet the app hands to developers, at minimum, default and maximum slider values, into
 * build/generated-check so it can be compiled against a real Compose project (see the README's compile check).
 * Not an assertion test: it only produces files.
 */
class GeneratedKotlinDump {
    private val dir = File("build/generated-check").apply { deleteRecursively(); mkdirs() }

    private fun write(name: String, code: String) {
        val pkg = "check." + name.lowercase().replace(Regex("[^a-z0-9]"), "_")
        File(dir, "$name.kt").writeText("package $pkg\n\n$code")
    }

    @Test fun dump() {
        for ((label, t) in listOf("min" to 0f, "mid" to 0.5f, "max" to 1f)) {
            val k = P.stiffness(t); val z = P.dampingRatio(t); val r = P.resistance(t)
            write("spring_$label", SpringCodeGenerator.generate(k, z, r, 300f))
            write("magnetic_$label", MagneticSnapCodeGenerator.generate(k, z, P.magneticStrength(t), P.magneticThresholdPx(t, 180f), 180f))
            write("swipe_$label", SwipeCodeGenerator.generate(k, z, P.flingDistanceDp(t), P.flingVelocityDpPerSec(t), P.flingFriction(t), P.flingTiltDegrees(t)))
            write("sheet_$label", SheetCodeGenerator.generate(k, z, P.sheetPeekFraction(t), P.sheetMidFraction(t), P.SHEET_FULL_FRACTION, P.sheetMomentumSec(t), P.sheetDismissLineFraction(t), r))
            write("pull_$label", PullRefreshCodeGenerator.generate(k, z, P.pullTriggerDp(t), P.pullHoldMs(t), r))
            write("zoom_$label", ZoomCodeGenerator.generate(k, z, P.zoomMinScale(t), P.zoomMaxScale(t), r))
            write("reorder_$label", ReorderCodeGenerator.generate(k, z, 56f, 28f))
            write("predictive_back_$label", PredictiveBackCodeGenerator.generate(k, z, P.backMinScale(t), P.backShiftFraction(t)))
            TriggerKind.entries.forEach { kind ->
                write("trigger_${kind.name}_$label", ComposeGenericGenerator.generate(TriggerSpecs.build(kind, kind.name, t, t, t, t, "crisp")))
            }
            // A chained follower on each trigger that allows one.
            write("chained_$label", ComposeGenericGenerator.generate(TriggerSpecs.build(TriggerKind.BUTTON_PRESS, "Chained", t, t, t, t, "soft", Chain("opacity", t, t, t, t))))
            listOf("spring_drag", "magnetic_snap", "swipe_fling", "bottom_sheet", "pull_refresh", "pinch_zoom", "drag_reorder").forEach { i ->
                write("release_${i}_$label", ComposeGenericGenerator.generate(MotionSpec.forRelease("Card $i", i, SpringSpec(k, z), emptyMap(), "off")))
            }
        }
        val tokens = listOf(
            MotionTokenEntity(name = "snappy", stiffness = 900f, dampingRatio = 0.8f, createdAt = 0),
            MotionTokenEntity(name = "Big Bounce 2", stiffness = 120f, dampingRatio = 0.2f, createdAt = 0),
            MotionTokenEntity(name = "3d-tap", stiffness = 3000f, dampingRatio = 1f, createdAt = 0),
        )
        write("tokens", MotionTokenExporter.export(tokens, MotionTokenExporter.Format.KOTLIN))
        val hs = listOf("soft", "tick", "click", "impact", "heavy", "success").mapIndexed { i, e -> TimelineSpec.Haptic(i * 120, e) }
        write("haptics", HapticExport.android("Checkout flow", hs))
    }
}
