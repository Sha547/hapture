package com.klynstudios.hapture.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.klynstudios.hapture.core.haptics.HapticEffect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineHapticInstrumentedTest {
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), HaptureDatabase::class.java,
    ).build()
    private val repo = TimelineRepository(db.timelineDao())

    @After fun tearDown() = db.close()

    @Test fun markersComeBackOrderedByTimeAndEditingKeepsThem() = runBlocking {
        val tl = repo.createDefault()
        val late = repo.addHaptic(tl.id, 900, HapticEffect.HEAVY)
        repo.addHaptic(tl.id, 100, HapticEffect.TICK)
        repo.setHapticTime(late, 50)
        val got = repo.observeHaptics(tl.id).first()
        assertEquals(listOf(50, 100), got.map { it.timeMs })
        assertEquals(HapticEffect.HEAVY, got.first().effect)
    }

    @Test fun deletingTheTimelineCascadesItsMarkers() = runBlocking {
        val tl = repo.createDefault()
        repo.addHaptic(tl.id, 100, HapticEffect.CLICK)
        repo.delete(tl)
        assertEquals(0, repo.observeHaptics(tl.id).first().size)
    }

    @Test fun updatingATimelineDoesNotCascadeDeleteItsMarkers() = runBlocking {
        val tl = repo.createDefault()
        repo.addHaptic(tl.id, 100, HapticEffect.CLICK)
        repo.rename(tl, "Renamed")
        assertEquals(1, repo.observeHaptics(tl.id).first().size)
    }
}
