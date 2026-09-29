package com.motionlab.app.data

import com.motionlab.app.core.model.MaterialSpring
import com.motionlab.app.core.model.ObjectFill
import com.motionlab.app.core.physics.ParameterMapping
import com.motionlab.app.core.model.ObjectShape
import kotlinx.coroutines.flow.Flow

/** Thin wrapper over the DAO -- exists so screens depend on this, not Room directly. */
class ExperimentRepository(private val dao: ExperimentDao) {

    fun observeAll(): Flow<List<ExperimentEntity>> = dao.observeAll()

    suspend fun get(id: Long): ExperimentEntity? = dao.getById(id)

    /** Updates an existing experiment in place. Always [ExperimentDao.update] -- see its doc for why. */
    suspend fun save(entity: ExperimentEntity) = dao.update(entity)

    suspend fun rename(entity: ExperimentEntity, newName: String) {
        dao.update(entity.copy(name = newName, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(entity: ExperimentEntity) = dao.delete(entity)

    /**
     * Undoes a delete: the exact row, back under its own id. Any timeline step
     * that referenced it was already cascade-deleted along with it and stays
     * gone -- undo restores the experiment, not what pointed at it.
     */
    suspend fun restore(entity: ExperimentEntity) = dao.restore(entity)

    suspend fun setPinned(entity: ExperimentEntity, pinned: Boolean) {
        dao.update(entity.copy(pinned = pinned, updatedAt = System.currentTimeMillis()))
    }

    /** Adds an experiment that came from a file as a brand-new row. */
    suspend fun importEntity(entity: ExperimentEntity): ExperimentEntity {
        val now = System.currentTimeMillis()
        val fresh = entity.copy(id = 0, createdAt = now, updatedAt = now)
        return fresh.copy(id = dao.upsert(fresh))
    }

    /** Creates a new row with sensible defaults, named e.g. "Spring Drag 2". */
    suspend fun createDefault(type: ExperimentType): ExperimentEntity {
        val count = dao.countByType(type)
        val baseName = when (type) {
            ExperimentType.SPRING_DRAG -> "Spring Drag"
            ExperimentType.MAGNETIC_SNAP -> "Magnetic Snap"
            ExperimentType.SWIPE_FLING -> "Swipe and Fling"
            ExperimentType.BOTTOM_SHEET -> "Bottom Sheet"
            ExperimentType.PULL_REFRESH -> "Pull to Refresh"
            ExperimentType.PINCH_ZOOM -> "Pinch to Zoom"
            ExperimentType.DRAG_REORDER -> "Drag to Reorder"
            ExperimentType.TOGGLE -> "Toggle"
            ExperimentType.BUTTON_PRESS -> "Button Press"
            ExperimentType.TAB_INDICATOR -> "Tab Indicator"
            ExperimentType.STAGGER_LIST -> "Staggered List"
            ExperimentType.LIKE_BURST -> "Like Burst"
            ExperimentType.PREDICTIVE_BACK -> "Predictive Back"
        }
        val name = if (count == 0) baseName else "$baseName ${count + 1}"
        val now = System.currentTimeMillis()
        val entity = when (type) {
            ExperimentType.SPRING_DRAG -> ExperimentEntity(
                type = type,
                name = name,
                createdAt = now,
                updatedAt = now,
                stiffnessT = 0.4f,
                dampingT = 0.55f,
                resistanceT = 0.5f,
            )
            ExperimentType.MAGNETIC_SNAP -> ExperimentEntity(
                type = type,
                name = name,
                createdAt = now,
                updatedAt = now,
                stiffnessT = 0.4f,
                dampingT = 0.6f,
                magneticStrengthT = 0.6f,
                magneticThresholdT = 0.6f,
            )
            ExperimentType.SWIPE_FLING -> ExperimentEntity(
                type = type,
                name = name,
                createdAt = now,
                updatedAt = now,
                stiffnessT = 0.5f,
                dampingT = 0.6f,
                flingDistanceT = 0.4f,
                flingSensitivityT = 0.5f,
                flingFrictionT = 0.35f,
                flingTiltT = 0.4f,
                // A swipe card reads better larger than the drag handle.
                objectSizeT = 0.8f,
            )
            ExperimentType.BOTTOM_SHEET -> ExperimentEntity(
                type = type,
                name = name,
                createdAt = now,
                updatedAt = now,
                stiffnessT = 0.45f,
                dampingT = 0.65f,
                resistanceT = 0.5f,
                sheetPeekT = 0.4f,
                sheetMidT = 0.5f,
                sheetMomentumT = 0.5f,
                sheetDismissT = 0.5f,
                // A tinted, rounded sheet reads better than a solid slab.
                objectShape = ObjectShape.ROUNDED,
                objectFill = ObjectFill.MARKER,
            )
            ExperimentType.PULL_REFRESH -> ExperimentEntity(
                type = type,
                name = name,
                createdAt = now,
                updatedAt = now,
                stiffnessT = 0.5f,
                dampingT = 0.6f,
                resistanceT = 0.5f,
                pullTriggerT = 0.4f,
                pullHoldT = 0.3f,
                // A round indicator reads more like a real refresh spinner than a square.
                objectShape = ObjectShape.CIRCLE,
                objectFill = ObjectFill.MARKER,
            )
            ExperimentType.PINCH_ZOOM -> ExperimentEntity(
                type = type,
                name = name,
                createdAt = now,
                updatedAt = now,
                stiffnessT = 0.4f,
                dampingT = 0.6f,
                resistanceT = 0.5f,
                zoomMinT = 0.5f,
                zoomMaxT = 0.5f,
                // Larger, so the scale change actually reads clearly at rest.
                objectSizeT = 0.7f,
            )
            ExperimentType.PREDICTIVE_BACK -> ExperimentEntity(
                type = type,
                name = name,
                createdAt = now,
                updatedAt = now,
                // Material 3's standard default spatial spring (700, 0.9): what Android's own back uses.
                stiffnessT = ParameterMapping.stiffnessT(MaterialSpring.STANDARD_DEFAULT.stiffness),
                dampingT = ParameterMapping.dampingT(MaterialSpring.STANDARD_DEFAULT.dampingRatio),
                backShrinkT = 0.5f,
                backShiftT = 0.5f,
            )
            ExperimentType.DRAG_REORDER -> ExperimentEntity(
                type = type,
                name = name,
                createdAt = now,
                updatedAt = now,
                stiffnessT = 0.55f,
                dampingT = 0.7f,
                reorderThresholdT = 0.5f,
            )
            ExperimentType.TOGGLE, ExperimentType.BUTTON_PRESS, ExperimentType.TAB_INDICATOR,
            ExperimentType.STAGGER_LIST, ExperimentType.LIKE_BURST -> ExperimentEntity(
                type = type,
                name = name,
                createdAt = now,
                updatedAt = now,
                stiffnessT = 0.6f,
                dampingT = 0.55f,
                triggerAmountT = 0.5f,
                triggerStaggerT = 0.4f,
            )
        }
        val id = dao.upsert(entity)
        return entity.copy(id = id)
    }
}
