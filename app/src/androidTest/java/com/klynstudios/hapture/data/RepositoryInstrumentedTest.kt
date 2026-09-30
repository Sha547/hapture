package com.klynstudios.hapture.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.klynstudios.hapture.core.haptics.HapticPreset
import com.klynstudios.hapture.core.model.CustomPoint
import com.klynstudios.hapture.core.model.CustomShapeCodec
import com.klynstudios.hapture.core.model.ObjectFill
import com.klynstudios.hapture.core.model.ObjectShape
import com.klynstudios.hapture.core.model.ObjectStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [ExperimentRepository] against a real in-memory SQLite database, not a fake
 * DAO. This is the layer that would catch a Room annotation mistake (wrong
 * column, a TypeConverter that compiles but isn't actually registered) that a
 * pure-Kotlin unit test can't see, because there is no SQLite underneath it.
 */
@RunWith(AndroidJUnit4::class)
class RepositoryInstrumentedTest {

    private lateinit var db: HaptureDatabase
    private lateinit var repository: ExperimentRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, HaptureDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ExperimentRepository(db.experimentDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun everyFieldRoundTripsThroughRealSqlite() = runBlocking {
        val created = repository.createDefault(ExperimentType.BOTTOM_SHEET)
        val styled = created.copy(
            objectShape = ObjectShape.CIRCLE,
            objectFill = ObjectFill.PAPER,
            hapticPreset = HapticPreset.FIRM,
            sheetPeekT = 0.33f,
        )
        repository.save(styled)

        val fetched = repository.get(created.id)
        assertEquals(ObjectShape.CIRCLE, fetched?.objectShape)
        assertEquals(ObjectFill.PAPER, fetched?.objectFill)
        assertEquals(HapticPreset.FIRM, fetched?.hapticPreset)
        assertEquals(0.33f, fetched?.sheetPeekT!!, 1e-4f)
    }

    @Test
    fun creatingEachTypeGivesItsOwnSensibleDefaults() = runBlocking {
        val spring = repository.createDefault(ExperimentType.SPRING_DRAG)
        assertEquals("Spring Drag", spring.name)
        assertNotEquals(null, spring.resistanceT)
        assertNull(spring.magneticStrengthT)

        val magnet = repository.createDefault(ExperimentType.MAGNETIC_SNAP)
        assertNotEquals(null, magnet.magneticStrengthT)

        val secondSpring = repository.createDefault(ExperimentType.SPRING_DRAG)
        assertEquals("Spring Drag 2", secondSpring.name)
    }

    @Test
    fun renameAndDeleteActuallyChangeTheStoredRow() = runBlocking {
        val entity = repository.createDefault(ExperimentType.SPRING_DRAG)

        repository.rename(entity, "My Tuned Spring")
        assertEquals("My Tuned Spring", repository.get(entity.id)?.name)

        repository.delete(repository.get(entity.id)!!)
        assertNull(repository.get(entity.id))
    }

    @Test
    fun importedEntitiesGetAFreshIdRatherThanReplacingWhateverHeldTheirOldOne() = runBlocking {
        val existing = repository.createDefault(ExperimentType.MAGNETIC_SNAP)
        val imported = repository.importEntity(existing.copy(id = existing.id, name = "From a file"))

        assertNotEquals(existing.id, imported.id)
        assertEquals("From a file", repository.get(imported.id)?.name)
        assertEquals(existing.id, repository.get(existing.id)?.id) // the original row is untouched
    }

    @Test
    fun observeAllReflectsWritesAndOrdersByMostRecentlyUpdated() = runBlocking {
        val first = repository.createDefault(ExperimentType.SPRING_DRAG)
        Thread.sleep(5)
        val second = repository.createDefault(ExperimentType.MAGNETIC_SNAP)

        val ids = db.experimentDao().observeAll().first().map { it.id }
        assertEquals(second.id, ids.first())
        assertTrue(ids.contains(first.id))
    }

    @Test
    fun aHandDrawnShapeRoundTripsThroughWithObjectStyleAndRealSqlite() = runBlocking {
        val drawn = listOf(CustomPoint(0.12f, 0.34f), CustomPoint(0.8f, 0.2f), CustomPoint(0.5f, 0.9f))
        val entity = repository.createDefault(ExperimentType.SPRING_DRAG)
        repository.save(entity.withObjectStyle(ObjectStyle(ObjectShape.CUSTOM, customPath = drawn)))

        val fetched = repository.get(entity.id)!!
        assertEquals(ObjectShape.CUSTOM, fetched.objectShape)
        val decoded = CustomShapeCodec.decode(fetched.customShapePath)
        assertEquals(drawn.size, decoded.size)
        for (i in drawn.indices) {
            assertEquals(drawn[i].x, decoded[i].x, 1e-3f)
            assertEquals(drawn[i].y, decoded[i].y, 1e-3f)
        }
        // objectStyle rebuilds the same shape from the stored row, not just the raw column.
        assertEquals(decoded, fetched.objectStyle.customPath)
    }

    @Test
    fun aRowWrittenWithAnUnknownEnumValueFallsBackWhenRoomReadsItBackViaTheRealConverters() = runBlocking {
        val entity = repository.createDefault(ExperimentType.SPRING_DRAG)
        // Bypass the typed API and write a value that no longer exists in the enum, the way an
        // experiment saved by an older or newer build might: Room must still be able to open it.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE experiments SET objectShape = 'STAR', hapticPreset = 'LOUD' WHERE id = ${entity.id}"
        )

        val reread = repository.get(entity.id)
        assertEquals(ObjectShape.ROUNDED, reread?.objectShape)
        assertEquals(HapticPreset.CRISP, reread?.hapticPreset)
    }
}
