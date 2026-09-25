package com.motionlab.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ExperimentEntity::class, TimelineEntity::class, TimelineStepEntity::class, TimelineHapticEntity::class, DoodleEntity::class, MotionTokenEntity::class,
        TokenSetEntity::class, TokenSetItemEntity::class, BackdropEntity::class,
    ],
    version = 13,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MotionLabDatabase : RoomDatabase() {
    abstract fun experimentDao(): ExperimentDao
    abstract fun timelineDao(): TimelineDao
    abstract fun doodleDao(): DoodleDao
    abstract fun motionTokenDao(): MotionTokenDao
    abstract fun tokenSetDao(): TokenSetDao
    abstract fun backdropDao(): BackdropDao
}

/** v2: the dragged object's shape, size and fill. Existing rows keep the look they had. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE experiments ADD COLUMN objectShape TEXT NOT NULL DEFAULT 'ROUNDED'")
        db.execSQL("ALTER TABLE experiments ADD COLUMN objectSizeT REAL NOT NULL DEFAULT 0.43")
        db.execSQL("ALTER TABLE experiments ADD COLUMN objectFill TEXT NOT NULL DEFAULT 'INK'")
    }
}

/** v3: swipe/fling parameters (null for other types) and the haptic preset. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE experiments ADD COLUMN flingDistanceT REAL")
        db.execSQL("ALTER TABLE experiments ADD COLUMN flingSensitivityT REAL")
        db.execSQL("ALTER TABLE experiments ADD COLUMN flingFrictionT REAL")
        db.execSQL("ALTER TABLE experiments ADD COLUMN flingTiltT REAL")
        db.execSQL("ALTER TABLE experiments ADD COLUMN hapticPreset TEXT NOT NULL DEFAULT 'CRISP'")
    }
}

/** v4: bottom-sheet parameters (null for other types). */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE experiments ADD COLUMN sheetPeekT REAL")
        db.execSQL("ALTER TABLE experiments ADD COLUMN sheetMidT REAL")
        db.execSQL("ALTER TABLE experiments ADD COLUMN sheetMomentumT REAL")
        db.execSQL("ALTER TABLE experiments ADD COLUMN sheetDismissT REAL")
    }
}

/** v5: a hand-drawn object outline, empty (no custom shape) for every existing row. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE experiments ADD COLUMN customShapePath TEXT NOT NULL DEFAULT ''")
    }
}

/** v6: timelines and their steps -- brand new tables, nothing on `experiments` changes. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS timelines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS timeline_steps (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timelineId INTEGER NOT NULL, experimentId INTEGER NOT NULL,
                position INTEGER NOT NULL, gapBeforeMs INTEGER NOT NULL,
                FOREIGN KEY(timelineId) REFERENCES timelines(id) ON DELETE CASCADE,
                FOREIGN KEY(experimentId) REFERENCES experiments(id) ON DELETE CASCADE
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_timeline_steps_timelineId ON timeline_steps(timelineId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_timeline_steps_experimentId ON timeline_steps(experimentId)")
    }
}

/** v7: pin a favorite experiment to the top of Home regardless of recency. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE experiments ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
    }
}

/** v8: pull-to-refresh, pinch-to-zoom and drag-to-reorder parameters (null for other types). */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE experiments ADD COLUMN pullTriggerT REAL")
        db.execSQL("ALTER TABLE experiments ADD COLUMN pullHoldT REAL")
        db.execSQL("ALTER TABLE experiments ADD COLUMN zoomMinT REAL")
        db.execSQL("ALTER TABLE experiments ADD COLUMN zoomMaxT REAL")
        db.execSQL("ALTER TABLE experiments ADD COLUMN reorderThresholdT REAL")
    }
}

/** v9: haptic markers on a timeline -- a brand new table. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS timeline_haptics (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timelineId INTEGER NOT NULL, timeMs INTEGER NOT NULL, effect TEXT NOT NULL,
                FOREIGN KEY(timelineId) REFERENCES timelines(id) ON DELETE CASCADE
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_timeline_haptics_timelineId ON timeline_haptics(timelineId)")
    }
}

/** v10: saved doodles -- a brand new table. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS doodles (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                background TEXT NOT NULL, strokes TEXT NOT NULL
            )"""
        )
    }
}

/** v11: trigger-interaction parameters (toggle, button, tabs, stagger, like) -- null for every other type. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE experiments ADD COLUMN triggerAmountT REAL")
        db.execSQL("ALTER TABLE experiments ADD COLUMN triggerStaggerT REAL")
    }
}

/** v12: named motion tokens -- a brand new table. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS motion_tokens (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL, stiffness REAL NOT NULL, dampingRatio REAL NOT NULL, createdAt INTEGER NOT NULL
            )"""
        )
    }
}

/**
 * v13: feel sets (two new tables), screenshot backdrops (one new table), and the optional
 * chained-follower settings on trigger experiments (nullable columns, so every existing row is unchanged).
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS token_sets (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL, archetype TEXT NOT NULL, createdAt INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS token_set_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                setId INTEGER NOT NULL, name TEXT NOT NULL, stiffness REAL NOT NULL, dampingRatio REAL NOT NULL,
                FOREIGN KEY(setId) REFERENCES token_sets(id) ON DELETE CASCADE
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_token_set_items_setId ON token_set_items(setId)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS experiment_backdrops (
                experimentId INTEGER PRIMARY KEY NOT NULL, path TEXT NOT NULL, focusY REAL NOT NULL,
                FOREIGN KEY(experimentId) REFERENCES experiments(id) ON DELETE CASCADE
            )"""
        )
        db.execSQL("ALTER TABLE experiments ADD COLUMN chainProperty TEXT")
        db.execSQL("ALTER TABLE experiments ADD COLUMN chainAtT REAL")
        db.execSQL("ALTER TABLE experiments ADD COLUMN chainLagT REAL")
        db.execSQL("ALTER TABLE experiments ADD COLUMN chainStiffnessT REAL")
        db.execSQL("ALTER TABLE experiments ADD COLUMN chainDampingT REAL")
    }
}
