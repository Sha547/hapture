package com.klynstudios.hapture.export

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineSpecTest {
    private fun spec() = TimelineSpec.json(
        name = "Card intro",
        steps = listOf(
            TimelineSpec.Step("Drag in", "spring_drag", gapBeforeMs = 0, durationMs = 300),
            TimelineSpec.Step("Snap to grid", "magnetic_snap", gapBeforeMs = 150, durationMs = 200),
        ),
    )

    @Test fun `is valid json with the documented shape`() {
        val j = JSONObject(spec())
        assertEquals("Card intro", j.getString("name"))
        val steps = j.getJSONArray("steps")
        assertEquals(2, steps.length())
        assertEquals("Drag in", steps.getJSONObject(0).getString("name"))
        assertEquals("spring_drag", steps.getJSONObject(0).getString("type"))
    }

    @Test fun `start times are cumulative, each after its own gap`() {
        val steps = JSONObject(spec()).getJSONArray("steps")
        assertEquals(0, steps.getJSONObject(0).getInt("startMs"))
        assertEquals(300 + 150, steps.getJSONObject(1).getInt("startMs"))
    }

    @Test fun `total is every gap and duration summed, matching TimelineMath`() {
        assertEquals(300 + 150 + 200, JSONObject(spec()).getInt("totalMs"))
    }

    @Test fun `an empty timeline is still valid json with zero total`() {
        val s = TimelineSpec.json("Empty", emptyList())
        val j = JSONObject(s)
        assertEquals(0, j.getInt("totalMs"))
        assertEquals(0, j.getJSONArray("steps").length())
    }

    @Test fun `names with quotes cannot break the json`() {
        val s = TimelineSpec.json("""My "timeline"""", emptyList())
        assertEquals("""My "timeline"""", JSONObject(s).getString("name"))
    }

    @Test fun `output is deterministic`() {
        assertEquals(spec(), spec())
    }
}
