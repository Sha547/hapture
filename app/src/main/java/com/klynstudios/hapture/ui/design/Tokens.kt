package com.klynstudios.hapture.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ThemeId(val label: String) {
    PAPER("Paper"),
    STONE("Stone"),
    GRAPHITE("Graphite"),
    INK("Ink"),
}

/**
 * The single source of truth for how the app looks. Screens read these through
 * [LocalTokens] (or the Material scheme built from them in Theme.kt); nothing
 * hardcodes a colour, radius or line weight. They are also what "Copy design
 * tokens" exports, so the app and a design file cannot drift apart.
 *
 * Warm monochrome, flat, separated by hairlines rather than shadows. Colour is
 * scarce: one muted [accent], used for the tinted object fill and nothing else.
 */
@Immutable
data class DesignTokens(
    val id: ThemeId,
    val dark: Boolean,
    /** Page background. */
    val canvas: Color,
    /** Cards and the stage. */
    val surface: Color,
    /** Hairlines, borders, dividers. */
    val line: Color,
    /** Primary text and solid fills. */
    val ink: Color,
    /** Secondary text. */
    val inkSoft: Color,
    /** Placeholder / disabled text. */
    val inkFaint: Color,
    val accent: Color,
    val radiusCard: Dp = 12.dp,
    val radiusControl: Dp = 8.dp,
    val hairline: Dp = 1.dp,
)

object DesignThemes {
    val paper = DesignTokens(
        id = ThemeId.PAPER, dark = false,
        canvas = Color(0xFFF7F6F3), surface = Color(0xFFFFFFFF), line = Color(0xFFE7E5E0),
        ink = Color(0xFF141413), inkSoft = Color(0xFF6F6D67), inkFaint = Color(0xFFA9A7A1),
        accent = Color(0xFFCFDFEB),
    )
    val stone = DesignTokens(
        id = ThemeId.STONE, dark = false,
        canvas = Color(0xFFECEAE5), surface = Color(0xFFF6F5F1), line = Color(0xFFD9D6CF),
        ink = Color(0xFF1B1A18), inkSoft = Color(0xFF65625B), inkFaint = Color(0xFF9E9B93),
        accent = Color(0xFFD8DFCF),
    )
    val graphite = DesignTokens(
        id = ThemeId.GRAPHITE, dark = true,
        canvas = Color(0xFF131312), surface = Color(0xFF1B1B1A), line = Color(0xFF2C2C2A),
        ink = Color(0xFFEEECE7), inkSoft = Color(0xFF9A978F), inkFaint = Color(0xFF66645F),
        accent = Color(0xFF3B4A57),
    )
    val ink = DesignTokens(
        id = ThemeId.INK, dark = true,
        canvas = Color(0xFF000000), surface = Color(0xFF0D0D0C), line = Color(0xFF242423),
        ink = Color(0xFFF2F0EB), inkSoft = Color(0xFF9A978F), inkFaint = Color(0xFF5E5C57),
        accent = Color(0xFF34424E),
    )

    val all = listOf(paper, stone, graphite, ink)

    fun of(id: ThemeId): DesignTokens = all.first { it.id == id }
}

val LocalTokens = staticCompositionLocalOf { DesignThemes.paper }
