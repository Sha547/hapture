package com.klynstudios.hapture.core.haptics

/**
 * Hand-made vibrations for phones whose motor can't play Android's named effects. Each step up from
 * SOFT to HEAVY is longer and, where the motor allows, stronger, so the presets stay tellable apart.
 * Nothing is shorter than 10 ms: below that, basic motors often don't spin up enough to be felt.
 *
 * [timingsMs] alternates off/on durations starting with an off; [amplitudes] (1..255, 0 = off) line up
 * with it and are null when the motor can't vary its strength.
 */
data class HapticPulse(val timingsMs: List<Long>, val amplitudes: List<Int>?) {
    /** Rough felt strength: on-time weighted by amplitude (full strength when the motor can't vary it). */
    val energy: Long
        get() = timingsMs.indices.sumOf { i -> timingsMs[i] * (amplitudes?.get(i) ?: if (i % 2 == 1) 255 else 0) }
}

object HapticPulses {

    fun of(effect: HapticEffect, amplitudeControl: Boolean): HapticPulse = if (amplitudeControl) when (effect) {
        HapticEffect.SOFT -> one(12, 70)
        HapticEffect.TICK -> one(14, 130)
        HapticEffect.CLICK -> one(20, 185)
        HapticEffect.IMPACT -> one(30, 230)
        HapticEffect.HEAVY -> one(45, 255)
        HapticEffect.SUCCESS -> HapticPulse(listOf(0, 20, 70, 32), listOf(0, 170, 0, 255))
    } else when (effect) {
        HapticEffect.SOFT -> one(10)
        HapticEffect.TICK -> one(16)
        HapticEffect.CLICK -> one(24)
        HapticEffect.IMPACT -> one(36)
        HapticEffect.HEAVY -> one(55)
        HapticEffect.SUCCESS -> HapticPulse(listOf(0, 20, 70, 34), null)
    }

    private fun one(ms: Long, amplitude: Int? = null) =
        HapticPulse(listOf(0, ms), amplitude?.let { listOf(0, it) })
}
