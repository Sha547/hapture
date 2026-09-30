package com.klynstudios.hapture.data

import com.klynstudios.hapture.core.haptics.HapticEffect
import kotlinx.coroutines.flow.Flow

/** Longest gap you can put before a step, so a fat-fingered stepper can't produce an absurd wait. */
const val MAX_GAP_MS = 3000

/**
 * Thin wrapper over [TimelineDao] -- screens depend on this, not Room directly.
 *
 * Every method that changes an *existing* row uses [TimelineDao.update] /
 * [TimelineDao.updateStep], never [TimelineDao.upsert] / [upsertStep] --
 * see those methods' doc comments for why `INSERT OR REPLACE` is unsafe here.
 */
class TimelineRepository(private val dao: TimelineDao) {

    fun observeAll(): Flow<List<TimelineEntity>> = dao.observeAll()

    suspend fun get(id: Long): TimelineEntity? = dao.getById(id)

    fun observeSteps(timelineId: Long): Flow<List<TimelineStepEntity>> = dao.observeSteps(timelineId)

    suspend fun createDefault(): TimelineEntity {
        val count = dao.count()
        val name = if (count == 0) "Timeline" else "Timeline ${count + 1}"
        val now = System.currentTimeMillis()
        val id = dao.upsert(TimelineEntity(name = name, createdAt = now, updatedAt = now))
        return TimelineEntity(id = id, name = name, createdAt = now, updatedAt = now)
    }

    suspend fun rename(entity: TimelineEntity, newName: String) {
        dao.update(entity.copy(name = newName, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(entity: TimelineEntity) = dao.delete(entity)

    /** Appends [experimentId] as the new last step. */
    suspend fun addStep(timelineId: Long, experimentId: Long): TimelineStepEntity {
        val position = dao.getSteps(timelineId).size
        val step = TimelineStepEntity(timelineId = timelineId, experimentId = experimentId, position = position)
        val id = dao.upsertStep(step)
        touch(timelineId)
        return step.copy(id = id)
    }

    /** Removes [step] and closes the gap it leaves in the ordering. */
    suspend fun removeStep(step: TimelineStepEntity) {
        dao.deleteStep(step)
        val remaining = dao.getSteps(step.timelineId)
        TimelineOrdering.repositioned(remaining).forEach { dao.updateStep(it) }
        touch(step.timelineId)
    }

    /** Moves [step] by [delta] positions (-1 = up/earlier, +1 = down/later); clamps at either end. */
    suspend fun moveStep(step: TimelineStepEntity, delta: Int) {
        val steps = dao.getSteps(step.timelineId)
        val from = steps.indexOfFirst { it.id == step.id }
        if (from < 0) return
        TimelineOrdering.moved(steps, from, from + delta).forEach { dao.updateStep(it) }
        touch(step.timelineId)
    }

    suspend fun setGap(step: TimelineStepEntity, gapMs: Int) {
        dao.updateStep(step.copy(gapBeforeMs = gapMs.coerceIn(0, MAX_GAP_MS)))
        touch(step.timelineId)
    }

    /** Changes [step]'s gap by [deltaMs] relative to what's stored now, so rapid taps never cancel each other out. */
    suspend fun nudgeGap(step: TimelineStepEntity, deltaMs: Int) {
        dao.nudgeGap(step.id, deltaMs, MAX_GAP_MS)
        touch(step.timelineId)
    }

    fun observeHaptics(timelineId: Long): Flow<List<TimelineHapticEntity>> = dao.observeHaptics(timelineId)

    suspend fun addHaptic(timelineId: Long, timeMs: Int, effect: HapticEffect): TimelineHapticEntity {
        val marker = TimelineHapticEntity(timelineId = timelineId, timeMs = timeMs.coerceAtLeast(0), effect = effect)
        val id = dao.upsertHaptic(marker)
        touch(timelineId)
        return marker.copy(id = id)
    }

    suspend fun setHapticTime(marker: TimelineHapticEntity, timeMs: Int) {
        dao.updateHaptic(marker.copy(timeMs = timeMs.coerceAtLeast(0)))
        touch(marker.timelineId)
    }

    suspend fun removeHaptic(marker: TimelineHapticEntity) {
        dao.deleteHaptic(marker)
        touch(marker.timelineId)
    }

    private suspend fun touch(timelineId: Long) {
        dao.getById(timelineId)?.let { dao.update(it.copy(updatedAt = System.currentTimeMillis())) }
    }
}
