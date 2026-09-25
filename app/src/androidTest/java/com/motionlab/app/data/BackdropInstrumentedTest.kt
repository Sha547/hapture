package com.motionlab.app.data

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Screenshot backdrops against real SQLite and real files: copied in, downscaled, focus-adjustable, gone with the experiment. */
@RunWith(AndroidJUnit4::class)
class BackdropInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: MotionLabDatabase
    private lateinit var repo: BackdropRepository
    private lateinit var experiments: ExperimentRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, MotionLabDatabase::class.java).allowMainThreadQueries().build()
        repo = BackdropRepository(context, db.backdropDao())
        experiments = ExperimentRepository(db.experimentDao())
    }

    @After
    fun tearDown() { db.close(); File(context.filesDir, "backdrops").deleteRecursively() }

    private fun screenshot(w: Int, h: Int, color: Int): Uri {
        val f = File(context.cacheDir, "shot-$w-$h.png")
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }.compress(Bitmap.CompressFormat.PNG, 100, f.outputStream())
        return Uri.fromFile(f)
    }

    @Test fun aPickedScreenshotIsCopiedDownscaledAndCanBeRefocusedAndCleared() = runBlocking {
        val e = experiments.createDefault(ExperimentType.SPRING_DRAG)
        assertTrue(repo.set(e.id, screenshot(1200, 3200, Color.RED)))
        val row = repo.observe(e.id).first()!!
        assertTrue(File(row.path).exists())
        val loaded = repo.load(row.path)!!
        assertTrue("long edge ${maxOf(loaded.width, loaded.height)}", maxOf(loaded.width, loaded.height) <= BackdropRepository.MAX_EDGE)
        // Stored as JPEG, so allow for its rounding: still clearly red.
        val px = loaded.getPixel(loaded.width / 2, loaded.height / 2)
        assertTrue(Color.red(px) > 240 && Color.green(px) < 15 && Color.blue(px) < 15)

        repo.setFocus(e.id, 9f) // out of range is clamped
        assertEquals(1f, repo.observe(e.id).first()!!.focusY, 0f)
        // Picking another one keeps the position you had.
        assertTrue(repo.set(e.id, screenshot(800, 1600, Color.BLUE)))
        assertEquals(1f, repo.observe(e.id).first()!!.focusY, 0f)

        repo.clear(e.id)
        assertNull(repo.observe(e.id).first())
        assertFalse(File(row.path).exists())
    }

    @Test fun somethingThatIsNotAnImageIsRefusedAndLeavesNothingBehind() = runBlocking {
        val e = experiments.createDefault(ExperimentType.TOGGLE)
        val junk = File(context.cacheDir, "junk.txt").apply { writeText("not an image") }
        assertFalse(repo.set(e.id, Uri.fromFile(junk)))
        assertNull(repo.observe(e.id).first())
    }

    @Test fun deletingTheExperimentRemovesItsBackdropRow() = runBlocking {
        val e = experiments.createDefault(ExperimentType.PINCH_ZOOM)
        repo.set(e.id, screenshot(300, 600, Color.GREEN))
        assertNotNull(db.backdropDao().get(e.id))
        experiments.delete(e)
        assertNull(db.backdropDao().get(e.id))
    }
}
