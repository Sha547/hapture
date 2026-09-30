package com.klynstudios.hapture.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineOrderingTest {
    private fun step(id: Long, position: Int) = TimelineStepEntity(id = id, timelineId = 1, experimentId = id, position = position)

    @Test fun `repositioned closes gaps and starts from zero`() {
        val withGap = listOf(step(1, 0), step(2, 3), step(3, 7))
        val fixed = TimelineOrdering.repositioned(withGap)
        assertEquals(listOf(0, 1, 2), fixed.map { it.position })
        assertEquals(listOf(1L, 2L, 3L), fixed.map { it.id }) // order preserved, only numbers renumbered
    }

    @Test fun `repositioned is a no-op when already contiguous`() {
        val tidy = listOf(step(1, 0), step(2, 1), step(3, 2))
        assertEquals(tidy, TimelineOrdering.repositioned(tidy))
    }

    @Test fun `moving a step earlier shifts the ones between it and its new spot`() {
        val steps = listOf(step(1, 0), step(2, 1), step(3, 2), step(4, 3))
        val moved = TimelineOrdering.moved(steps, fromIndex = 3, toIndex = 1)
        assertEquals(listOf(1L, 4L, 2L, 3L), moved.sortedBy { it.position }.map { it.id })
        assertEquals(listOf(0, 1, 2, 3), moved.map { it.position })
    }

    @Test fun `moving a step later shifts the ones between it and its new spot`() {
        val steps = listOf(step(1, 0), step(2, 1), step(3, 2), step(4, 3))
        val moved = TimelineOrdering.moved(steps, fromIndex = 0, toIndex = 2)
        assertEquals(listOf(2L, 3L, 1L, 4L), moved.sortedBy { it.position }.map { it.id })
    }

    @Test fun `moving the first step up, or the last step down, is a no-op`() {
        val steps = listOf(step(1, 0), step(2, 1), step(3, 2))
        assertEquals(steps.map { it.id }, TimelineOrdering.moved(steps, 0, -1).sortedBy { it.position }.map { it.id })
        assertEquals(steps.map { it.id }, TimelineOrdering.moved(steps, 2, 5).sortedBy { it.position }.map { it.id })
    }

    @Test fun `moving still renumbers even a list that arrived with gaps`() {
        val messy = listOf(step(1, 0), step(2, 5), step(3, 9))
        val moved = TimelineOrdering.moved(messy, fromIndex = 0, toIndex = 1)
        assertEquals(listOf(2L, 1L, 3L), moved.sortedBy { it.position }.map { it.id })
        assertEquals(listOf(0, 1, 2), moved.map { it.position })
    }

    @Test fun `an empty list is left alone`() {
        assertEquals(emptyList<TimelineStepEntity>(), TimelineOrdering.moved(emptyList(), 0, 1))
        assertEquals(emptyList<TimelineStepEntity>(), TimelineOrdering.repositioned(emptyList()))
    }
}
