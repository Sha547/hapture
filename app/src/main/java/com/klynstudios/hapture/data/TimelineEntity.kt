package com.klynstudios.hapture.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.klynstudios.hapture.core.haptics.HapticEffect

/**
 * A saved sequence of existing experiments played one after another (spec's
 * "Timeline" -- see the README's Phase 7 note). Deliberately thin: a name and
 * timestamps, no motion parameters of its own. Everything about *how* a step
 * moves belongs to the experiment it references; a timeline only decides
 * order and pacing (see [TimelineStepEntity]).
 */
@Entity(tableName = "timelines")
data class TimelineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One step: play [experimentId]'s own motion, after waiting [gapBeforeMs].
 * [position] is the step's order within its timeline, 0-based and contiguous
 * -- [com.klynstudios.hapture.data.TimelineRepository] keeps it that way after every
 * add, remove or reorder, so rendering never has to sort-and-hope.
 *
 * Both foreign keys cascade: delete the timeline and its steps go with it;
 * delete an experiment and any step referencing it goes too, rather than
 * leaving a step that points at nothing.
 */
@Entity(
    tableName = "timeline_steps",
    foreignKeys = [
        ForeignKey(entity = TimelineEntity::class, parentColumns = ["id"], childColumns = ["timelineId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExperimentEntity::class, parentColumns = ["id"], childColumns = ["experimentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("timelineId"), Index("experimentId")],
)
data class TimelineStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timelineId: Long,
    val experimentId: Long,
    val position: Int,
    val gapBeforeMs: Int = 0,
)

/**
 * A haptic pulse at an absolute time on the timeline, independent of any
 * motion step. Cascades with its timeline.
 */
@Entity(
    tableName = "timeline_haptics",
    foreignKeys = [
        ForeignKey(entity = TimelineEntity::class, parentColumns = ["id"], childColumns = ["timelineId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("timelineId")],
)
data class TimelineHapticEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timelineId: Long,
    val timeMs: Int,
    val effect: HapticEffect,
)
