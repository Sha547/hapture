package com.klynstudios.hapture.core.capture

import com.klynstudios.hapture.export.SpringMath
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Finds the spring (mass 1) whose unit step response best matches a measured
 * curve, by least squares: a coarse grid over stiffness and damping ratio,
 * then a shrinking pattern search around the best cell. Times are seconds
 * from the start of the motion; values run from 0 (start) to 1 (rest).
 */
object SpringFit {

    data class Result(val stiffness: Float, val dampingRatio: Float, val rms: Float)

    private const val K_MIN = 30.0
    private const val K_MAX = 6000.0
    private const val Z_MIN = 0.05
    private const val Z_MAX = 2.0

    fun fit(samples: List<Pair<Float, Float>>): Result? {
        if (samples.size < 4) return null
        val ts = DoubleArray(samples.size) { samples[it].first.toDouble() }
        val vs = DoubleArray(samples.size) { samples[it].second.toDouble() }

        fun rms(logK: Double, z: Double): Double {
            val k = exp(logK)
            var sum = 0.0
            for (i in ts.indices) {
                val d = SpringMath.stepResponse(ts[i], k, z) - vs[i]
                sum += d * d
            }
            return sqrt(sum / ts.size)
        }

        var bestK = 0.0
        var bestZ = 0.0
        var best = Double.MAX_VALUE
        val steps = 44
        for (i in 0..steps) {
            val logK = ln(K_MIN) + (ln(K_MAX) - ln(K_MIN)) * i / steps
            for (j in 0..steps) {
                val z = Z_MIN + (Z_MAX - Z_MIN) * j / steps
                val e = rms(logK, z)
                if (e < best) { best = e; bestK = logK; bestZ = z }
            }
        }

        var dk = (ln(K_MAX) - ln(K_MIN)) / steps
        var dz = (Z_MAX - Z_MIN) / steps
        repeat(60) {
            var improved = false
            for ((a, b) in listOf(dk to 0.0, -dk to 0.0, 0.0 to dz, 0.0 to -dz, dk to dz, -dk to -dz, dk to -dz, -dk to dz)) {
                val k2 = (bestK + a).coerceIn(ln(K_MIN), ln(K_MAX))
                val z2 = (bestZ + b).coerceIn(Z_MIN, Z_MAX)
                val e = rms(k2, z2)
                if (e < best) { best = e; bestK = k2; bestZ = z2; improved = true }
            }
            if (!improved) { dk *= 0.6; dz *= 0.6 }
        }
        return Result(exp(bestK).toFloat(), bestZ.toFloat(), best.toFloat())
    }
}
