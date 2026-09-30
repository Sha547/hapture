package com.klynstudios.hapture.export

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticExportTest {
    private fun h(t: Int, e: String) = TimelineSpec.Haptic(t, e)

    @Test fun `android waveform alternates gaps and pulses`() {
        val out = HapticExport.android("Demo", listOf(h(100, "tick"), h(300, "heavy")))
        // gap 100, tick 8, gap 300-108=192, heavy 32
        assertTrue(out, out.contains("longArrayOf(100, 8, 192, 32)"))
        assertTrue(out, out.contains("intArrayOf(0, 90, 0, 255)"))
    }

    @Test fun `overlapping markers never produce a negative gap`() {
        val out = HapticExport.android("Demo", listOf(h(0, "heavy"), h(10, "tick")))
        assertTrue(out, out.contains("longArrayOf(0, 32, 0, 8)"))
    }

    @Test fun `markers are sorted and success is a double tap`() {
        val out = HapticExport.android("Demo", listOf(h(500, "success"), h(0, "soft")))
        assertTrue(out, out.contains("intArrayOf(0, 40, 0, 150, 0, 150)"))
        // soft ends at 8, first success pulse at 500, second at 560 (after the first ends at 516)
        assertTrue(out, out.contains("longArrayOf(0, 8, 492, 16, 44, 16)"))
    }

    @Test fun `core haptics carries time and strength`() {
        val out = HapticExport.coreHaptics("Demo", listOf(h(250, "click")))
        assertTrue(out.contains("relativeTime: 0.25"))
        assertTrue(out.contains("value: 0.6"))
        assertTrue(out.contains("import CoreHaptics"))
    }

    @Test fun `ahap is valid json with one event per pulse`() {
        val o = JSONObject(HapticExport.ahap("Demo", listOf(h(0, "tick"), h(100, "success"))))
        assertEquals(1.0, o.getDouble("Version"), 0.0)
        assertEquals(3, o.getJSONArray("Pattern").length())
        assertEquals("HapticTransient", o.getJSONArray("Pattern").getJSONObject(0).getJSONObject("Event").getString("EventType"))
    }

    @Test fun `empty timelines say so instead of emitting an empty pattern`() {
        assertTrue(HapticExport.android("Demo", emptyList()).contains("no haptic markers"))
        assertTrue(HapticExport.coreHaptics("Demo", emptyList()).contains("no haptic markers"))
    }
}
