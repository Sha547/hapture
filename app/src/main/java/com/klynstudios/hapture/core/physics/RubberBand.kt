package com.klynstudios.hapture.core.physics

import kotlin.math.abs

/**
 * Rubber-band overscroll: d' = d / (1 + k|d|).
 *
 * The springs themselves aren't reimplemented anywhere: editors animate with
 * Compose's own `spring()`. Rubber-banding has no Compose equivalent, so the
 * formula lives here and SpringCodeGenerator writes the same formula into the
 * exported code.
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
