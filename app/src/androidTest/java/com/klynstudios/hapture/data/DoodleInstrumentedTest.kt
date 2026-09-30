package com.klynstudios.hapture.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DoodleInstrumentedTest {
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), HaptureDatabase::class.java,
    ).build()
    private val repo = DoodleRepository(db.doodleDao())

    @After fun tearDown() = db.close()

    @Test fun saveRoundTripsStrokesAndBackground() = runBlocking {
        val d = repo.createDefault()
        repo.save(d.copy(strokes = "{\"v\":1,\"strokes\":[]}", background = "INK"))
        val got = repo.get(d.id)!!
        assertEquals("INK", got.background)
        assertEquals("Doodle", got.name)
    }

    @Test fun deleteThenRestoreBringsTheSameRowBack() = runBlocking {
        val d = repo.createDefault()
        val saved = d.copy(strokes = "abc")
        repo.delete(saved)
        assertEquals(0, repo.observeAll().first().size)
        repo.restore(saved)
        assertEquals(d.id, repo.observeAll().first().single().id)
        assertEquals("abc", repo.get(d.id)!!.strokes)
    }
}
