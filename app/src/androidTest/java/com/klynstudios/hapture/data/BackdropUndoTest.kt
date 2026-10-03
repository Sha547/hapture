package com.klynstudios.hapture.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Deleting an experiment and pressing Undo brings its screenshot back too; letting the Undo lapse deletes the file (what MainActivity does with these calls). */
@RunWith(AndroidJUnit4::class)
class BackdropUndoTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, HaptureDatabase::class.java).build()
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
        db.backdropDao().put(BackdropEntity(e.id, file.name, 0.7f))

        val snap = backdrops.snapshot(e.id)
        experiments.delete(e)
        assertNull(db.backdropDao().get(e.id))

        experiments.restore(e)
        backdrops.restore(snap!!)
        val back = db.backdropDao().get(e.id)
        assertNotNull(back)
        assertEquals(0.7f, back!!.focusY, 1e-4f)
        assertTrue(file.exists())
    }

    @Test
    fun onceTheUndoIsGoneTheImageFileIsDeleted() = runBlocking {
        val experiments = ExperimentRepository(db.experimentDao())
        val backdrops = BackdropRepository(context, db.backdropDao())
        val e = experiments.createDefault(ExperimentType.SPRING_DRAG)
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        db.backdropDao().put(BackdropEntity(e.id, file.name, 0.7f))

        val snap = backdrops.snapshot(e.id)!!
        experiments.delete(e)
        // The cascade takes the row but leaves the file, so an Undo could still bring it back...
        assertTrue(file.exists())
        // ...until the delete settles.
        backdrops.discard(snap)
        assertFalse(file.exists())
    }

    @Test
    fun aMissingImageFileIsNotRestored() = runBlocking {
        val experiments = ExperimentRepository(db.experimentDao())
        val backdrops = BackdropRepository(context, db.backdropDao())
        val e = experiments.createDefault(ExperimentType.SPRING_DRAG)
        val snap = BackdropEntity(e.id, "gone.jpg", 0.5f)
        backdrops.restore(snap)
        assertNull(db.backdropDao().get(e.id))
    }
}
