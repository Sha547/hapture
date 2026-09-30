package com.klynstudios.hapture.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [TimelineRepository] against real Room, not a fake DAO -- in particular the
 * cascade-delete foreign keys declared on [TimelineStepEntity], which a
 * pure-Kotlin test can't exercise (there's no SQLite underneath one to
 * actually enforce `ON DELETE CASCADE`).
 */
@RunWith(AndroidJUnit4::class)
class TimelineRepositoryInstrumentedTest {

    private lateinit var db: HaptureDatabase
    private lateinit var timelines: TimelineRepository
    private lateinit var experiments: ExperimentRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, HaptureDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        timelines = TimelineRepository(db.timelineDao())
        experiments = ExperimentRepository(db.experimentDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun addingStepsAppendsThemInOrderWithContiguousPositions() = runBlocking {
        val timeline = timelines.createDefault()
        val a = experiments.createDefault(ExperimentType.SPRING_DRAG)
        val b = experiments.createDefault(ExperimentType.MAGNETIC_SNAP)
        val c = experiments.createDefault(ExperimentType.SWIPE_FLING)

        timelines.addStep(timeline.id, a.id)
        timelines.addStep(timeline.id, b.id)
        timelines.addStep(timeline.id, c.id)

        val steps = timelines.observeSteps(timeline.id).first()
        assertEquals(listOf(a.id, b.id, c.id), steps.map { it.experimentId })
        assertEquals(listOf(0, 1, 2), steps.map { it.position })
    }

    @Test
    fun removingAStepClosesTheGapInPositions() = runBlocking {
        val timeline = timelines.createDefault()
        val a = experiments.createDefault(ExperimentType.SPRING_DRAG)
        val b = experiments.createDefault(ExperimentType.MAGNETIC_SNAP)
        val c = experiments.createDefault(ExperimentType.SWIPE_FLING)
        timelines.addStep(timeline.id, a.id)
        val middle = timelines.addStep(timeline.id, b.id)
        timelines.addStep(timeline.id, c.id)

        timelines.removeStep(middle)

        val steps = timelines.observeSteps(timeline.id).first()
        assertEquals(listOf(a.id, c.id), steps.map { it.experimentId })
        assertEquals(listOf(0, 1), steps.map { it.position })
    }

    @Test
    fun movingAStepChangesPlaybackOrder() = runBlocking {
        val timeline = timelines.createDefault()
        val a = experiments.createDefault(ExperimentType.SPRING_DRAG)
        val b = experiments.createDefault(ExperimentType.MAGNETIC_SNAP)
        timelines.addStep(timeline.id, a.id)
        val second = timelines.addStep(timeline.id, b.id)

        timelines.moveStep(second, -1)

        val steps = timelines.observeSteps(timeline.id).first()
        assertEquals(listOf(b.id, a.id), steps.map { it.experimentId })
    }

    @Test
    fun gapIsClampedIntoRange() = runBlocking {
        val timeline = timelines.createDefault()
        val a = experiments.createDefault(ExperimentType.SPRING_DRAG)
        val step = timelines.addStep(timeline.id, a.id)

        timelines.setGap(step, -500)
        assertEquals(0, timelines.observeSteps(timeline.id).first().single().gapBeforeMs)

        timelines.setGap(step, 999_999)
        assertEquals(MAX_GAP_MS, timelines.observeSteps(timeline.id).first().single().gapBeforeMs)
    }

    @Test
    fun quickGapTapsFromAStaleRowAllCount() = runBlocking {
        val timeline = timelines.createDefault()
        val a = experiments.createDefault(ExperimentType.SPRING_DRAG)
        val stale = timelines.addStep(timeline.id, a.id)

        // Three taps before the screen re-reads the row: each still adds its 100 ms.
        repeat(3) { timelines.nudgeGap(stale, 100) }
        assertEquals(300, timelines.observeSteps(timeline.id).first().single().gapBeforeMs)

        timelines.nudgeGap(stale, -1_000)
        assertEquals(0, timelines.observeSteps(timeline.id).first().single().gapBeforeMs)
        timelines.nudgeGap(stale, 999_999)
        assertEquals(MAX_GAP_MS, timelines.observeSteps(timeline.id).first().single().gapBeforeMs)
    }

    @Test
    fun deletingATimelineCascadesToItsSteps() = runBlocking {
        val timeline = timelines.createDefault()
        val a = experiments.createDefault(ExperimentType.SPRING_DRAG)
        timelines.addStep(timeline.id, a.id)
        assertEquals(1, timelines.observeSteps(timeline.id).first().size)

        timelines.delete(timeline)

        // The experiment itself is untouched; only the timeline's own step row goes.
        assertEquals(0, timelines.observeSteps(timeline.id).first().size)
        assertTrue(experiments.get(a.id) != null)
    }

    @Test
    fun deletingAnExperimentRemovesItFromAnyTimelineItWasStepIn() = runBlocking {
        val timeline = timelines.createDefault()
        val a = experiments.createDefault(ExperimentType.SPRING_DRAG)
        val b = experiments.createDefault(ExperimentType.MAGNETIC_SNAP)
        timelines.addStep(timeline.id, a.id)
        timelines.addStep(timeline.id, b.id)

        experiments.delete(experiments.get(a.id)!!)

        val steps = timelines.observeSteps(timeline.id).first()
        assertEquals(listOf(b.id), steps.map { it.experimentId })
        // The timeline itself survives losing one of its steps.
        assertTrue(timelines.get(timeline.id) != null)
    }

    @Test
    fun renamingUpdatesTheStoredRow() = runBlocking {
        val timeline = timelines.createDefault()
        timelines.rename(timeline, "Card intro")
        assertEquals("Card intro", timelines.get(timeline.id)?.name)
    }

    @Test
    fun creatingSeveralTimelinesGivesEachItsOwnSensibleName() = runBlocking {
        assertEquals("Timeline", timelines.createDefault().name)
        assertEquals("Timeline 2", timelines.createDefault().name)
    }
}
