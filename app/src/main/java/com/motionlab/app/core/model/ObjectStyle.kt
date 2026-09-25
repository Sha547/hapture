package com.motionlab.app.core.model

/**
 * What the thing you're dragging looks like. Motion feels different on a
 * fat circle than on a thin pill, so the object is part of the experiment
 * and is saved and exported with it.
 */
enum class ObjectShape(val label: String, val aspect: Float = 1f) {
    SQUARE("Square"),
    ROUNDED("Rounded"),
    CIRCLE("Circle"),
    PILL("Pill", aspect = 1.6f),
    /** A shape drawn by hand; see [ObjectStyle.customPath]. Always square bounds -- the drawing itself carries its own proportions. */
    CUSTOM("Custom"),
}

/** Which theme token fills the object. Stored by role, so it follows theme changes. */
enum class ObjectFill(val label: String) {
    INK("Solid"),
    MARKER("Tint"),
    PAPER("Outline"),
}

data class ObjectStyle(
    val shape: ObjectShape = ObjectShape.ROUNDED,
    /** 0f..1f, mapped to [sizeDp]. */
    val sizeT: Float = DEFAULT_SIZE_T,
    val fill: ObjectFill = ObjectFill.INK,
    /**
     * A closed hand-drawn outline, each point 0f..1f within its own square
     * bounds. Only meaningful when [shape] is [ObjectShape.CUSTOM]; empty
     * until something has been drawn, in which case CUSTOM falls back to
     * looking like [ObjectShape.ROUNDED] (see [DrawnShape]).
     */
    val customPath: List<CustomPoint> = emptyList(),
) {
    val sizeDp: Float get() = sizeDp(sizeT)

    companion object {
        const val DEFAULT_SIZE_T = 0.43f
        fun sizeDp(t: Float): Float = 40f + (96f - 40f) * t.coerceIn(0f, 1f)
    }
}
