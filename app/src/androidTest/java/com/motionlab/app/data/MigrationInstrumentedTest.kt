package com.motionlab.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.motionlab.app.core.haptics.HapticPreset
import com.motionlab.app.core.model.CustomShapeCodec
import com.motionlab.app.core.model.ObjectFill
import com.motionlab.app.core.model.ObjectShape
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real SQLite, real migrations, no mocks. Each test hand-builds a database
 * exactly as an earlier app version would have left it on a phone (the same
 * shape verified by hand against the emulator during development -- see the
 * README's Phase 4 note), then opens it through [MotionLabDatabase] with the
 * production migration chain and checks both the data and the new columns'
 * defaults. A schema mismatch or a bad migration fails loudly here instead of
 * on someone's real, unwiped device.
 */
@RunWith(AndroidJUnit4::class)
class MigrationInstrumentedTest {

    private val dbName = "migration-test.db"
    private var db: MotionLabDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase(dbName)
    }

    private fun openMigrated(): MotionLabDatabase {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return Room.databaseBuilder(context, MotionLabDatabase::class.java, dbName)
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            )
            .build()
            .also { db = it }
    }

    private fun rawDbFile(): java.io.File =
        ApplicationProvider.getApplicationContext<android.content.Context>().getDatabasePath(dbName)

    @Test
    fun v1DatabaseMigratesForwardWithExistingRowsIntact() {
        SQLiteDatabase.openOrCreateDatabase(rawDbFile(), null).use { raw ->
            raw.execSQL(
                """CREATE TABLE experiments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, name TEXT NOT NULL,
                    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    stiffnessT REAL NOT NULL, dampingT REAL NOT NULL,
                    resistanceT REAL, magneticStrengthT REAL, magneticThresholdT REAL
                )"""
            )
            raw.execSQL(
                "INSERT INTO experiments (type,name,createdAt,updatedAt,stiffnessT,dampingT,resistanceT) " +
                    "VALUES ('SPRING_DRAG','Old Spring',1,2,0.6,0.3,0.5)"
            )
            raw.execSQL(
                "INSERT INTO experiments (type,name,createdAt,updatedAt,stiffnessT,dampingT,magneticStrengthT,magneticThresholdT) " +
                    "VALUES ('MAGNETIC_SNAP','Old Magnet',1,1,0.4,0.6,0.6,0.6)"
            )
            raw.version = 1
        }

        runBlocking {
            val rows = openMigrated().experimentDao().observeAll().first()
            assertEquals(2, rows.size)

            val spring = rows.first { it.name == "Old Spring" }
            assertEquals(0.6f, spring.stiffnessT, 1e-4f)
            assertEquals(0.5f, spring.resistanceT!!, 1e-4f)
            // Columns that didn't exist in v1 come back as the migrations' documented defaults.
            assertEquals(ObjectShape.ROUNDED, spring.objectShape)
            assertEquals(ObjectFill.INK, spring.objectFill)
            assertEquals(HapticPreset.CRISP, spring.hapticPreset)
            assertNull(spring.flingDistanceT)
            assertNull(spring.sheetPeekT)

            val magnet = rows.first { it.name == "Old Magnet" }
            assertEquals(0.6f, magnet.magneticStrengthT!!, 1e-4f)
        }
    }

    @Test
    fun v2DatabaseWithACustomLookMigratesForward() {
        SQLiteDatabase.openOrCreateDatabase(rawDbFile(), null).use { raw ->
            raw.execSQL(
                """CREATE TABLE experiments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, name TEXT NOT NULL,
                    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    stiffnessT REAL NOT NULL, dampingT REAL NOT NULL,
                    resistanceT REAL, magneticStrengthT REAL, magneticThresholdT REAL,
                    objectShape TEXT NOT NULL DEFAULT 'ROUNDED', objectSizeT REAL NOT NULL DEFAULT 0.43,
                    objectFill TEXT NOT NULL DEFAULT 'INK'
                )"""
            )
            raw.execSQL(
                "INSERT INTO experiments (type,name,createdAt,updatedAt,stiffnessT,dampingT,magneticStrengthT,magneticThresholdT,objectShape,objectSizeT,objectFill) " +
                    "VALUES ('MAGNETIC_SNAP','Custom Look',1,1,0.4,0.6,0.6,0.6,'PILL',0.7,'MARKER')"
            )
            raw.version = 2
        }

        runBlocking {
            val row = openMigrated().experimentDao().observeAll().first().single()
            // The v2 look survives, not just the defaults.
            assertEquals(ObjectShape.PILL, row.objectShape)
            assertEquals(0.7f, row.objectSizeT, 1e-4f)
            assertEquals(ObjectFill.MARKER, row.objectFill)
            assertEquals(HapticPreset.CRISP, row.hapticPreset)
        }
    }

    @Test
    fun v3DatabaseWithAFlingRowMigratesForward() {
        SQLiteDatabase.openOrCreateDatabase(rawDbFile(), null).use { raw ->
            raw.execSQL(
                """CREATE TABLE experiments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, name TEXT NOT NULL,
                    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    stiffnessT REAL NOT NULL, dampingT REAL NOT NULL,
                    resistanceT REAL, magneticStrengthT REAL, magneticThresholdT REAL,
                    objectShape TEXT NOT NULL DEFAULT 'ROUNDED', objectSizeT REAL NOT NULL DEFAULT 0.43,
                    objectFill TEXT NOT NULL DEFAULT 'INK',
                    flingDistanceT REAL, flingSensitivityT REAL, flingFrictionT REAL, flingTiltT REAL,
                    hapticPreset TEXT NOT NULL DEFAULT 'CRISP'
                )"""
            )
            raw.execSQL(
                "INSERT INTO experiments (type,name,createdAt,updatedAt,stiffnessT,dampingT," +
                    "flingDistanceT,flingSensitivityT,flingFrictionT,flingTiltT,hapticPreset) " +
                    "VALUES ('SWIPE_FLING','Old Swipe',1,1,0.5,0.6,0.2,0.9,0.1,0.8,'FIRM')"
            )
            raw.version = 3
        }

        runBlocking {
            val row = openMigrated().experimentDao().observeAll().first().single()
            assertEquals(0.9f, row.flingSensitivityT!!, 1e-4f)
            assertEquals(HapticPreset.FIRM, row.hapticPreset)
            // Bottom sheet didn't exist yet: the new columns are null, not a guessed default.
            assertNull(row.sheetPeekT)
            assertNull(row.sheetMidT)
        }
    }

    @Test
    fun v4DatabaseMigratesForwardWithNoCustomShapeYet() {
        SQLiteDatabase.openOrCreateDatabase(rawDbFile(), null).use { raw ->
            raw.execSQL(
                """CREATE TABLE experiments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, name TEXT NOT NULL,
                    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    stiffnessT REAL NOT NULL, dampingT REAL NOT NULL,
                    resistanceT REAL, magneticStrengthT REAL, magneticThresholdT REAL,
                    objectShape TEXT NOT NULL DEFAULT 'ROUNDED', objectSizeT REAL NOT NULL DEFAULT 0.43,
                    objectFill TEXT NOT NULL DEFAULT 'INK',
                    flingDistanceT REAL, flingSensitivityT REAL, flingFrictionT REAL, flingTiltT REAL,
                    hapticPreset TEXT NOT NULL DEFAULT 'CRISP',
                    sheetPeekT REAL, sheetMidT REAL, sheetMomentumT REAL, sheetDismissT REAL
                )"""
            )
            raw.execSQL(
                "INSERT INTO experiments (type,name,createdAt,updatedAt,stiffnessT,dampingT,resistanceT," +
                    "sheetPeekT,sheetMidT,sheetMomentumT,sheetDismissT) " +
                    "VALUES ('BOTTOM_SHEET','Old Sheet',1,1,0.45,0.65,0.5,0.4,0.5,0.5,0.5)"
            )
            raw.version = 4
        }

        runBlocking {
            val row = openMigrated().experimentDao().observeAll().first().single()
            assertEquals(0.4f, row.sheetPeekT!!, 1e-4f)
            // The new column exists and decodes as "nothing drawn", not as garbage or a crash.
            assertEquals("", row.customShapePath)
            assertEquals(emptyList<Any>(), CustomShapeCodec.decode(row.customShapePath))
        }
    }

    @Test
    fun v5DatabaseGetsWorkingEmptyTimelineTablesAndKeepsItsExperiments() {
        SQLiteDatabase.openOrCreateDatabase(rawDbFile(), null).use { raw ->
            raw.execSQL(
                """CREATE TABLE experiments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, name TEXT NOT NULL,
                    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    stiffnessT REAL NOT NULL, dampingT REAL NOT NULL,
                    resistanceT REAL, magneticStrengthT REAL, magneticThresholdT REAL,
                    objectShape TEXT NOT NULL DEFAULT 'ROUNDED', objectSizeT REAL NOT NULL DEFAULT 0.43,
                    objectFill TEXT NOT NULL DEFAULT 'INK',
                    flingDistanceT REAL, flingSensitivityT REAL, flingFrictionT REAL, flingTiltT REAL,
                    hapticPreset TEXT NOT NULL DEFAULT 'CRISP',
                    sheetPeekT REAL, sheetMidT REAL, sheetMomentumT REAL, sheetDismissT REAL,
                    customShapePath TEXT NOT NULL DEFAULT ''
                )"""
            )
            raw.execSQL(
                "INSERT INTO experiments (type,name,createdAt,updatedAt,stiffnessT,dampingT,resistanceT) " +
                    "VALUES ('SPRING_DRAG','Pre-existing',1,1,0.5,0.5,0.5)"
            )
            raw.version = 5
        }

        runBlocking {
            val db = openMigrated()
            // The experiment from before the migration is untouched.
            assertEquals("Pre-existing", db.experimentDao().observeAll().first().single().name)

            // The new tables aren't just present -- a real write through them round-trips.
            val timelineRepo = TimelineRepository(db.timelineDao())
            val experiment = db.experimentDao().observeAll().first().single()
            val timeline = timelineRepo.createDefault()
            val step = timelineRepo.addStep(timeline.id, experiment.id)
            assertEquals(experiment.id, timelineRepo.observeSteps(timeline.id).first().single().experimentId)
            assertEquals(0, step.position)
        }
    }

    @Test
    fun v6DatabaseGetsPinnedAndNewInteractionColumnsAndKeepsItsExperiments() {
        SQLiteDatabase.openOrCreateDatabase(rawDbFile(), null).use { raw ->
            raw.execSQL(
                """CREATE TABLE experiments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, name TEXT NOT NULL,
                    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    stiffnessT REAL NOT NULL, dampingT REAL NOT NULL,
                    resistanceT REAL, magneticStrengthT REAL, magneticThresholdT REAL,
                    objectShape TEXT NOT NULL DEFAULT 'ROUNDED', objectSizeT REAL NOT NULL DEFAULT 0.43,
                    objectFill TEXT NOT NULL DEFAULT 'INK',
                    flingDistanceT REAL, flingSensitivityT REAL, flingFrictionT REAL, flingTiltT REAL,
                    hapticPreset TEXT NOT NULL DEFAULT 'CRISP',
                    sheetPeekT REAL, sheetMidT REAL, sheetMomentumT REAL, sheetDismissT REAL,
                    customShapePath TEXT NOT NULL DEFAULT ''
                )"""
            )
            raw.execSQL(
                """CREATE TABLE timelines (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
                )"""
            )
            raw.execSQL(
                """CREATE TABLE timeline_steps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    timelineId INTEGER NOT NULL, experimentId INTEGER NOT NULL,
                    position INTEGER NOT NULL, gapBeforeMs INTEGER NOT NULL,
                    FOREIGN KEY(timelineId) REFERENCES timelines(id) ON DELETE CASCADE,
                    FOREIGN KEY(experimentId) REFERENCES experiments(id) ON DELETE CASCADE
                )"""
            )
            raw.execSQL("CREATE INDEX IF NOT EXISTS index_timeline_steps_timelineId ON timeline_steps(timelineId)")
            raw.execSQL("CREATE INDEX IF NOT EXISTS index_timeline_steps_experimentId ON timeline_steps(experimentId)")
            raw.execSQL(
                "INSERT INTO experiments (type,name,createdAt,updatedAt,stiffnessT,dampingT,resistanceT) " +
                    "VALUES ('SPRING_DRAG','Pre-existing',1,1,0.5,0.5,0.5)"
            )
            raw.version = 6
        }

        runBlocking {
            val db = openMigrated()
            val row = db.experimentDao().observeAll().first().single()
            // Existing row survives, and the new columns come back as documented defaults.
            assertEquals("Pre-existing", row.name)
            assertEquals(false, row.pinned)
            assertNull(row.pullTriggerT)
            assertNull(row.pullHoldT)
            assertNull(row.zoomMinT)
            assertNull(row.zoomMaxT)
            assertNull(row.reorderThresholdT)

            // A real write through the new column round-trips, not just the default.
            val repo = ExperimentRepository(db.experimentDao())
            repo.setPinned(row, true)
            assertEquals(true, repo.get(row.id)!!.pinned)

            val pull = repo.createDefault(ExperimentType.PULL_REFRESH)
            assertEquals(ExperimentType.PULL_REFRESH, repo.get(pull.id)?.type)
        }
    }

    @Test
    fun aFreshInstallNeedsNoMigrationAndCreatesEveryInteractionType() {
        val repository = ExperimentRepository(openMigrated().experimentDao())
        runBlocking {
            for (type in ExperimentType.entries) {
                val entity = repository.createDefault(type)
                assertEquals(type, repository.get(entity.id)?.type)
            }
        }
    }

    @Test
    fun v12DatabaseGainsFeelSetsBackdropsAndChainColumnsAndKeepsItsExperiments() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // The v12 shape, derived from what Room itself builds for v13, minus everything v13 added.
        val fresh = "migration-fresh.db"
        context.deleteDatabase(fresh)
        Room.databaseBuilder(context, MotionLabDatabase::class.java, fresh).build().apply { openHelper.writableDatabase; close() }
        val ddl = ArrayList<String>()
        SQLiteDatabase.openDatabase(context.getDatabasePath(fresh).path, null, SQLiteDatabase.OPEN_READONLY).use { f ->
            f.rawQuery("SELECT name, sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' AND name != 'room_master_table'", null).use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0)
                    if (name in listOf("token_sets", "token_set_items", "experiment_backdrops", "index_token_set_items_setId")) continue
                    var sql = c.getString(1)
                    if (name == "experiments") sql = sql.replace(Regex(",\\s*`chain\\w+` (TEXT|REAL)"), "")
                    ddl += sql
                }
            }
        }
        context.deleteDatabase(fresh)

        SQLiteDatabase.openOrCreateDatabase(rawDbFile(), null).use { raw ->
            ddl.forEach { raw.execSQL(it) }
            raw.execSQL(
                "INSERT INTO experiments (type,name,createdAt,updatedAt,stiffnessT,dampingT,triggerAmountT) VALUES ('TOGGLE','Old Toggle',1,2,0.5,0.5,0.4)"
            )
            raw.version = 12
        }

        runBlocking {
            val database = openMigrated()
            val old = database.experimentDao().observeAll().first().single()
            assertEquals("Old Toggle", old.name)
            assertEquals(0.4f, old.triggerAmountT!!, 1e-4f)
            assertNull(old.chainProperty)
            assertNull(old.chainAtT)

            // The chain columns round-trip through a real @Update.
            database.experimentDao().update(old.copy(chainProperty = "opacity", chainAtT = 0.5f, chainLagT = 0.2f))
            assertEquals("opacity", database.experimentDao().getById(old.id)!!.chainProperty)

            // Sets: items hang off their set and go with it.
            val sets = TokenSetRepository(database.tokenSetDao())
            val id = sets.create("Fintech", "fintech", listOf(Triple("tap", 700f, 1f), Triple("sheet", 400f, 0.95f)))!!
            assertEquals(2, database.tokenSetDao().itemsOf(id).size)
            database.tokenSetDao().delete(database.tokenSetDao().observeSets().first().single())
            assertEquals(0, database.tokenSetDao().itemsOf(id).size)

            // Backdrops go when their experiment does.
            database.backdropDao().put(BackdropEntity(old.id, "/x.jpg", 0.3f))
            assertEquals(0.3f, database.backdropDao().get(old.id)!!.focusY, 1e-4f)
            database.experimentDao().delete(old)
            assertNull(database.backdropDao().get(old.id))
        }
    }
}
