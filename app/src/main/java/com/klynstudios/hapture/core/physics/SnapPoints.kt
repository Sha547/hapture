package com.klynstudios.hapture.core.physics

import kotlin.math.abs

/**
 * Discrete target positions (spec §15). On release, the engine picks the
 * closest valid target; the transition itself is a normal spring, not a
 * bespoke "snap" animation, so it's covered by the same real-AnimationSpec
 * fidelity guarantee as everything else.
 */
object SnapPoints {
    fun nearestIndex(position: Float, targets: FloatArray): Int {
        var bestIndex = 0
        var bestDistance = Float.MAX_VALUE
        for (i in targets.indices) {
            val d = abs(position - targets[i])
            if (d < bestDistance) {
                bestDistance = d
                bestIndex = i
            }
        }
        return bestIndex
    }
}
