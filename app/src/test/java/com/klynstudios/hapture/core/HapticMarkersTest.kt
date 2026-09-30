package com.klynstudios.hapture.core

import com.klynstudios.hapture.core.model.HapticMarkers
import com.klynstudios.hapture.export.TimelineSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticMarkersTest {
    private val times = listOf(100, 500, 500, 900)

    @Test fun `fires only markers passed since the last frame`() {
        assertEquals(listOf(1, 2), HapticMarkers.crossed(times, 100f, 500f))
    }

    @Test fun `a marker exactly at the start of the window is not replayed`() {
        assertEquals(emptyList<Int>(), HapticMarkers.crossed(times, 900f, 1000f))
    }

    @Test fun `scrubbing backwards fires nothing`() {
        assertEquals(emptyList<Int>(), HapticMarkers.crossed(times, 900f, 100f))
    }

    @Test fun `timeline is as long as its last marker`() {
        assertEquals(900, HapticMarkers.totalMs(400, times))
        assertEquals(400, HapticMarkers.totalMs(400, emptyList()))
    }

    @Test fun `spec lists haptics and extends totalMs`() {
        val json = TimelineSpec.json("T", emptyList(), listOf(TimelineSpec.Haptic(750, "click")))
        assertTrue(json.contains("\"timeMs\": 750"))
        assertTrue(json.contains("\"effect\": \"click\""))
        assertTrue(json.contains("\"totalMs\": 750"))
    }
}
