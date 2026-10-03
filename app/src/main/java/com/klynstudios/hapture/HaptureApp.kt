package com.klynstudios.hapture

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.klynstudios.hapture.data.BackdropRepository
import com.klynstudios.hapture.data.DoodleRepository
import com.klynstudios.hapture.data.ExperimentRepository
import com.klynstudios.hapture.data.HaptureDatabase
import com.klynstudios.hapture.data.MIGRATION_10_11
import com.klynstudios.hapture.data.MIGRATION_11_12
import com.klynstudios.hapture.data.MIGRATION_12_13
import com.klynstudios.hapture.data.MIGRATION_13_14
import com.klynstudios.hapture.data.MIGRATION_14_15
import com.klynstudios.hapture.data.MIGRATION_1_2
import com.klynstudios.hapture.data.MIGRATION_2_3
import com.klynstudios.hapture.data.MIGRATION_3_4
import com.klynstudios.hapture.data.MIGRATION_4_5
import com.klynstudios.hapture.data.MIGRATION_5_6
import com.klynstudios.hapture.data.MIGRATION_6_7
import com.klynstudios.hapture.data.MIGRATION_7_8
import com.klynstudios.hapture.data.MIGRATION_8_9
import com.klynstudios.hapture.data.MIGRATION_9_10
import com.klynstudios.hapture.data.MotionTokenRepository
import com.klynstudios.hapture.data.TimelineRepository
import com.klynstudios.hapture.data.TokenSetRepository

/**
 * Owns the one Room database and its repositories for the whole process. The activity is recreated on
 * config changes and after process death; building the database there opened a second instance on the
 * same file each time, so it lives here and is created on first use.
 */
class HaptureApp : Application() {
    val database: HaptureDatabase by lazy {
        Room.databaseBuilder(this, HaptureDatabase::class.java, "hapture.db")
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
            )
            .build()
    }

    val experiments by lazy { ExperimentRepository(database.experimentDao()) }
    val timelines by lazy { TimelineRepository(database.timelineDao()) }
    val doodles by lazy { DoodleRepository(database.doodleDao()) }
    val tokens by lazy { MotionTokenRepository(database.motionTokenDao()) }
    val tokenSets by lazy { TokenSetRepository(database.tokenSetDao()) }
    val backdrops by lazy { BackdropRepository(this, database.backdropDao()) }
}

val Context.haptureApp: HaptureApp get() = applicationContext as HaptureApp
