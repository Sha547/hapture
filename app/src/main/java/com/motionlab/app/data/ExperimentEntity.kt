package com.motionlab.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.motionlab.app.core.haptics.HapticPreset
import com.motionlab.app.core.model.CustomShapeCodec
import com.motionlab.app.core.model.ObjectFill
import com.motionlab.app.core.model.ObjectShape
import com.motionlab.app.core.model.ObjectStyle

/**
 * A saved experiment (spec §26-27). Configuration is stored as discrete
 * typed columns rather than the JSON blob the spec suggests -- with only
 * two interaction types and a handful of Float params each, a shared
 * nullable-columns shape is simpler than adding a serialization dependency
 * for it. Revisit as JSON (or a real per-type table) once enough
 * interaction types exist that this table gets unwieldy.
 */
@Entity(tableName = "experiments")
data class ExperimentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ExperimentType,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,

    // Shared across every spring-based interaction (spec §11).
    val stiffnessT: Float,
    val dampingT: Float,

    // Spring Drag only.
    val resistanceT: Float? = null,

    // Magnetic Snap only.
    val magneticStrengthT: Float? = null,
    val magneticThresholdT: Float? = null,

    // Swipe / fling only (added in schema v3).
    val flingDistanceT: Float? = null,
    val flingSensitivityT: Float? = null,
    val flingFrictionT: Float? = null,
    val flingTiltT: Float? = null,

    // Bottom sheet only (added in schema v4). Resistance past full reuses resistanceT.
    val sheetPeekT: Float? = null,
    val sheetMidT: Float? = null,
    val sheetMomentumT: Float? = null,
    val sheetDismissT: Float? = null,

    // Pull to Refresh only (added in schema v8). Resistance past the trigger reuses resistanceT.
    val pullTriggerT: Float? = null,
    val pullHoldT: Float? = null,

    // Pinch to Zoom only (added in schema v8). Resistance past the bounds reuses resistanceT.
    val zoomMinT: Float? = null,
    val zoomMaxT: Float? = null,

    // Drag to Reorder only (added in schema v8).
    val reorderThresholdT: Float? = null,

    // Trigger interactions (added in schema v11): amount = press depth / thumb stretch / burst size, stagger = per-item delay.
    val triggerAmountT: Float? = null,
    val triggerStaggerT: Float? = null,

    // A chained follower on a trigger interaction (added in schema v13): a second property that starts
    // once the first has done part of its move. Null [chainProperty] = no follower.
    val chainProperty: String? = null,
    val chainAtT: Float? = null,
    val chainLagT: Float? = null,
    val chainStiffnessT: Float? = null,
    val chainDampingT: Float? = null,

    // How the interaction feels in the hand (added in schema v3).
    @ColumnInfo(defaultValue = "'CRISP'") val hapticPreset: HapticPreset = HapticPreset.CRISP,

    // What the dragged object looks like (added in schema v2). Defaults match
    // MIGRATION_1_2 so rows saved before this existed open as they always did.
    @ColumnInfo(defaultValue = "'ROUNDED'") val objectShape: ObjectShape = ObjectShape.ROUNDED,
    @ColumnInfo(defaultValue = "0.43") val objectSizeT: Float = ObjectStyle.DEFAULT_SIZE_T,
    @ColumnInfo(defaultValue = "'INK'") val objectFill: ObjectFill = ObjectFill.INK,

    // A hand-drawn object outline (added in schema v5). Empty until something's
    // been drawn; only meaningful when objectShape is CUSTOM.
    @ColumnInfo(defaultValue = "''") val customShapePath: String = "",

    // Kept at the top of Home regardless of recency (added in schema v7).
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
) {
    val objectStyle: ObjectStyle
        get() = ObjectStyle(objectShape, objectSizeT, objectFill, CustomShapeCodec.decode(customShapePath))

    fun withObjectStyle(style: ObjectStyle): ExperimentEntity =
        copy(
            objectShape = style.shape,
            objectSizeT = style.sizeT,
            objectFill = style.fill,
            customShapePath = CustomShapeCodec.encode(style.customPath),
        )
}

enum class ExperimentType {
    SPRING_DRAG,
    MAGNETIC_SNAP,
    SWIPE_FLING,
    BOTTOM_SHEET,
    PULL_REFRESH,
    PINCH_ZOOM,
    DRAG_REORDER,
    TOGGLE,
    BUTTON_PRESS,
    TAB_INDICATOR,
    STAGGER_LIST,
    LIKE_BURST,
}
