package com.motionlab.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.motionlab.app.ui.design.DesignThemes
import com.motionlab.app.ui.design.DesignTokens
import com.motionlab.app.ui.design.LocalTokens
import com.motionlab.app.ui.design.ThemeId

/**
 * The theme you get before you have ever picked one: graphite after dark,
 * paper otherwise. Once picked, the choice is stored (see ThemeStore).
 */
@Composable
fun defaultThemeId(): ThemeId = if (isSystemInDarkTheme()) ThemeId.GRAPHITE else ThemeId.PAPER

/**
 * Every Material role is set explicitly from the tokens. Leaving any unset
 * falls back to Material's purple baseline (e.g. Slider's inactive track pulls
 * from secondaryContainer), which is exactly the bright accent this palette
 * avoids.
 */
private fun DesignTokens.toColorScheme(): ColorScheme {
    val well = line.copy(alpha = 0.6f).compositeOver(canvas)
    val error = Color(0xFFC4554D)
    return if (dark) darkColorScheme(
        primary = ink, onPrimary = canvas,
        primaryContainer = surface, onPrimaryContainer = ink,
        secondary = inkSoft, onSecondary = canvas,
        secondaryContainer = line, onSecondaryContainer = ink,
        tertiary = accent, onTertiary = canvas,
        tertiaryContainer = surface, onTertiaryContainer = ink,
        error = error, onError = canvas,
        background = canvas, onBackground = ink,
        surface = surface, onSurface = ink,
        surfaceVariant = well, onSurfaceVariant = inkSoft,
        outline = line, outlineVariant = line,
        inverseSurface = ink, inverseOnSurface = canvas, inversePrimary = surface,
        scrim = Color.Black,
        surfaceBright = surface, surfaceDim = canvas,
        surfaceContainer = surface, surfaceContainerLow = canvas,
        surfaceContainerLowest = canvas, surfaceContainerHigh = surface, surfaceContainerHighest = surface,
    ) else lightColorScheme(
        primary = ink, onPrimary = canvas,
        primaryContainer = surface, onPrimaryContainer = ink,
        secondary = inkSoft, onSecondary = canvas,
        secondaryContainer = line, onSecondaryContainer = ink,
        tertiary = accent, onTertiary = canvas,
        tertiaryContainer = surface, onTertiaryContainer = ink,
        error = error, onError = canvas,
        background = canvas, onBackground = ink,
        surface = surface, onSurface = ink,
        surfaceVariant = well, onSurfaceVariant = inkSoft,
        outline = line, outlineVariant = line,
        inverseSurface = ink, inverseOnSurface = canvas, inversePrimary = surface,
        scrim = Color.Black,
        surfaceBright = surface, surfaceDim = canvas,
        surfaceContainer = surface, surfaceContainerLow = canvas,
        surfaceContainerLowest = canvas, surfaceContainerHigh = surface, surfaceContainerHighest = surface,
    )
}

@Composable
fun MotionLabTheme(
    themeId: ThemeId,
    content: @Composable () -> Unit,
) {
    val tokens = DesignThemes.of(themeId)
    CompositionLocalProvider(LocalTokens provides tokens) {
        MaterialTheme(
            colorScheme = tokens.toColorScheme(),
            typography = AppTypography,
            content = content,
        )
    }
}
