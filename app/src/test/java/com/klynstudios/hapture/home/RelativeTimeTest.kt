package com.klynstudios.hapture.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val now = 1_000_000_000_000L
    private fun ago(minutes: Long) = relativeTime(now, now - minutes * 60_000L)

    @Test fun `buckets read naturally`() {
        assertEquals("Just now", ago(0))
        assertEquals("5 min ago", ago(5))
        assertEquals("59 min ago", ago(59))
        assertEquals("1 h ago", ago(60))
        assertEquals("23 h ago", ago(23 * 60))
        assertEquals("Yesterday", ago(24 * 60))
        assertEquals("3 d ago", ago(3 * 24 * 60))
    }

    @Test fun `a timestamp in the future never goes negative`() {
        assertEquals("Just now", relativeTime(now, now + 5 * 60_000L))
    }
}
