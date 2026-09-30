package com.klynstudios.hapture.core.doodle

/** A point in canvas-relative units (0f..1f on both axes), so a doodle renders identically at any size. */
data class Pt(val x: Float, val y: Float)

enum class Brush(val label: String) {
    PEN("Pen"),
    MARKER("Marker"),
    PAINT("Paint"),
    SPRAY("Spray"),
    ERASER("Eraser"),
}

enum class DoodleBackground(val label: String, val argb: Int) {
    PAPER("Paper", 0xFFFFFFFF.toInt()),
    CREAM("Cream", 0xFFF4EEE1.toInt()),
    STONE("Stone", 0xFFD9D6CE.toInt()),
    INK("Ink", 0xFF171716.toInt()),
}

/** [width] is a fraction of the canvas width; [color] is ARGB. */
data class DoodleStroke(
    val brush: Brush,
    val color: Int,
    val width: Float,
    val points: List<Pt>,
)

/** The palette people paint from. Content colour, so it's allowed to be colourful. */
val DOODLE_PALETTE: List<Int> = listOf(
    0xFF171716, 0xFF6F6D67, 0xFFFFFFFF, 0xFFD64545, 0xFFE8873A, 0xFFEBC443,
    0xFF6BB56B, 0xFF2F8F83, 0xFF3F7FD6, 0xFF6C5CC9, 0xFFC45FA8, 0xFF8A5A3C,
).map { it.toInt() }
