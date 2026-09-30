package com.klynstudios.hapture.data

import androidx.room.TypeConverter
import com.klynstudios.hapture.core.haptics.HapticEffect
import com.klynstudios.hapture.core.haptics.HapticPreset
import com.klynstudios.hapture.core.model.ObjectFill
import com.klynstudios.hapture.core.model.ObjectShape

/** Explicit converters rather than relying on Room's version-dependent native enum support. */
class Converters {
    @TypeConverter
    fun fromExperimentType(type: ExperimentType): String = type.name

    @TypeConverter
    fun toExperimentType(value: String): ExperimentType = ExperimentType.valueOf(value)

    @TypeConverter
    fun fromObjectShape(shape: ObjectShape): String = shape.name

    @TypeConverter
    fun toObjectShape(value: String): ObjectShape =
        // Older builds also offered blob and star; those rows open as rounded rather than crash.
        ObjectShape.entries.firstOrNull { it.name == value } ?: ObjectShape.ROUNDED

    @TypeConverter
    fun fromObjectFill(fill: ObjectFill): String = fill.name

    @TypeConverter
    fun toObjectFill(value: String): ObjectFill =
        ObjectFill.entries.firstOrNull { it.name == value } ?: ObjectFill.INK

    @TypeConverter
    fun fromHapticPreset(preset: HapticPreset): String = preset.name

    @TypeConverter
    fun toHapticPreset(value: String): HapticPreset =
        HapticPreset.entries.firstOrNull { it.name == value } ?: HapticPreset.CRISP

    @TypeConverter
    fun fromHapticEffect(effect: HapticEffect): String = effect.name

    @TypeConverter
    fun toHapticEffect(value: String): HapticEffect =
        HapticEffect.entries.firstOrNull { it.name == value } ?: HapticEffect.TICK
}
