package com.klynstudios.hapture.core

import com.klynstudios.hapture.core.haptics.HapticEffect
import com.klynstudios.hapture.core.haptics.HapticPulses
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fallback pulses have to stay tellable apart on a basic motor, or the presets all feel the same again. */
class HapticPulsesTest {
    private val ladder = listOf(HapticEffect.SOFT, HapticEffect.TICK, HapticEffect.CLICK, HapticEffect.IMPACT, HapticEffect.HEAVY)

    @Test fun eachStepIsClearlyStrongerThanTheLast() {
        for (amp in listOf(true, false)) {
            val energies = ladder.map { HapticPulses.of(it, amp).energy }
            energies.zipWithNext().forEach { (a, b) -> assertTrue("amp=$amp $energies", b >= a * 1.3) }
        }
    }

    @Test fun nothingIsTooShortToFeel() {
        for (amp in listOf(true, false)) HapticEffect.entries.forEach { e ->
            val on = HapticPulses.of(e, amp).timingsMs.filterIndexed { i, _ -> i % 2 == 1 }
            assertTrue("$e amp=$amp $on", on.all { it >= 10 })
        }
    }

    @Test fun amplitudesLineUpWithTimingsAndStayInRange() {
        HapticEffect.entries.forEach { e ->
            val p = HapticPulses.of(e, amplitudeControl = true)
            val amps = p.amplitudes!!
            assertTrue(amps.size == p.timingsMs.size)
            assertTrue(amps.all { it in 0..255 })
        }
        HapticEffect.entries.forEach { e -> assertTrue(HapticPulses.of(e, amplitudeControl = false).amplitudes == null) }
    }

    @Test fun successIsTwoBeats() {
        for (amp in listOf(true, false)) {
            val on = HapticPulses.of(HapticEffect.SUCCESS, amp).timingsMs.filterIndexed { i, _ -> i % 2 == 1 }
            assertTrue(on.size == 2)
        }
    }
}
