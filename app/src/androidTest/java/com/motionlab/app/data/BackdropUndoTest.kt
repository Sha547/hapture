package com.motionlab.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Deleting an experiment and pressing Undo brings its screenshot back too (what MainActivity does with these calls). */
@RunWith(AndroidJUnit4::class)
class BackdropUndoTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, MotionLabDatabase::class.java).build()
    private val file = File(context.filesDir, "backdrops/undo-test.jpg")

    @After
    fun tearDown() {
        db.close()
        file.delete()
    }

    @Test
    fun undoRestoresTheScreenshotRow() = runBlocking {
        val experiments = ExperimentRepository(db.experimentDao())
        val backdrops = BackdropRepository(context, db.backdropDao())
        val e = experiments.createDefault(ExperimentType.SPRING_DRAG)
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        db.backdropDao().put(BackdropEntity(e.id, file.absolutePath, 0.7f))

        val snap = backdrops.snapshot(e.id)
        experiments.delete(e)
        assertNull(db.backdropDao().get(e.id))

        experiments.restore(e)
        backdrops.restore(snap!!)
        val back = db.backdropDao().get(e.id)
        assertNotNull(back)
        assertEquals(0.7f, back!!.focusY, 1e-4f)
    }

    @Test
    fun aMissingImageFileIsNotRestored() = runBlocking {
        val experiments = ExperimentRepository(db.experimentDao())
        val backdrops = BackdropRepository(context, db.backdropDao())
        val e = experiments.createDefault(ExperimentType.SPRING_DRAG)
        val snap = BackdropEntity(e.id, File(context.filesDir, "backdrops/gone.jpg").absolutePath, 0.5f)
        backdrops.restore(snap)
        assertNull(db.backdropDao().get(e.id))
    }
}
