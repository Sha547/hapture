package com.klynstudios.hapture.core.physics

import kotlin.math.abs

/**
 * Rubber-band overscroll (spec §13): d' = d / (1 + k|d|).
 *
 * This is the *only* custom solver in the drag path. Spring physics itself
 * is intentionally NOT reimplemented here -- the editor drives the real
 * Compose `spring()` AnimationSpec directly (see EditorScreen), so whatever
 * feels right in the preview is byte-for-byte what the exported code runs.
 * Rubber-band and (later) magnetic/snap solvers don't have a Compose-native
 * equivalent, so they're implemented once here and the exact same formula
 * is what CodeGenerator emits into exported Kotlin -- one source of truth,
 * copy-pasted into two call sites, never two implementations that can drift.
 */
object RubberBand {

    /**
     * @param raw the unclamped drag delta from the rest position
     * @param bound the distance the object may travel with no resistance at all
     * @param k resistance constant, see [ParameterMapping.resistance]
     */
    fun apply(raw: Float, bound: Float, k: Float): Float {
        val clamped = raw.coerceIn(-bound, bound)
        val excess = raw - clamped
        val resisted = excess / (1 + k * abs(excess))
        return clamped + resisted
    }
}
