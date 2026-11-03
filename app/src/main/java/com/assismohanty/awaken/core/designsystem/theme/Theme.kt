package com.assismohanty.awaken.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.assismohanty.awaken.core.designsystem.lexicon.Lexicon
import com.assismohanty.awaken.core.designsystem.lexicon.LocalLexicon
import com.assismohanty.awaken.core.designsystem.lexicon.PresentationMode
import com.assismohanty.awaken.engine.model.Rank

/**
 * Two palettes, one set of tokens (DESIGN_SYSTEM §2.1). No dynamic colour. Light or Dark follows the phone unless the
 * user picked one in Appearance.
 *
 * @param darkTheme the resolved Appearance choice; the default follows the device.
 * @param reduceMotion the in-app toggle; the system "Remove animations" setting is OR-ed in (§2.5).
 * @param hapticsEnabled the in-app toggle (§2.6).
 * @param presentation the words, provided as [LocalLexicon]. The app always passes Clean; tests may pin System words.
 */
@Composable
fun AwakenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = false,
    hapticsEnabled: Boolean = true,
    presentation: PresentationMode = PresentationMode.CLEAN,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) AwakenPalette.Dark else AwakenPalette.Light
    val colors = remember(palette) { AwakenColors(palette) }
    val type = remember { AwakenType() }
    val reduce = reduceMotion || systemReduceMotion()
    val motion = remember(reduce) { AwakenMotion(reduceMotion = reduce) }
    val shapes = remember { AwakenShapes() }
    val dimens = remember { AwakenDimens() }
    val colorScheme = remember(palette) { palette.toColorScheme(palette.isDark) }
    CompositionLocalProvider(
        LocalAwakenPalette provides palette,
        LocalAwakenColors provides colors,
        LocalAwakenType provides type,
        LocalAwakenMotion provides motion,
        LocalAwakenShapes provides shapes,
        LocalAwakenDimens provides dimens,
        LocalHaptics provides rememberHaptics(hapticsEnabled),
        LocalLexicon provides Lexicon.of(presentation),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = remember(type) { type.toMaterialTypography() },
            content = content,
        )
    }
}

/** Material slots mapped onto our tokens; identical mapping for both palettes so components never branch on mode. */
private fun AwakenPalette.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = system,
        onPrimary = onAccent,
        primaryContainer = panelRaised,
        onPrimaryContainer = bone,
        secondary = mist,
        onSecondary = onAccent,
        secondaryContainer = panelRaised, // nav-bar indicator: neutral, not Material tint
        onSecondaryContainer = bone,
        tertiary = crit,
        onTertiary = bone,
        background = void,
        onBackground = bone,
        surface = void,
        onSurface = bone,
        surfaceVariant = depth,
        onSurfaceVariant = mist,
        // Sheets (Low), menus and dialogs (High) sit on card white / charcoal, like every other card.
        surfaceContainer = panel,
        surfaceContainerLow = panel,
        surfaceContainerHigh = panel,
        surfaceContainerHighest = panelRaised,
        outline = edge,
        outlineVariant = fog,
        error = ember,
        onError = void,
    )
}

/** Token access: screens use `AwakenTheme.colors.textPrimary`, `AwakenTheme.type.title`, …; design-system code may read [palette]. */
object AwakenTheme {
    /** Semantic tokens (DS §8.4): the only colours feature code may use. */
    val colors: AwakenColors @Composable @ReadOnlyComposable get() = LocalAwakenColors.current
    /** Raw palette for core/designsystem components; never for feature code. */
    val palette: AwakenPalette @Composable @ReadOnlyComposable get() = LocalAwakenPalette.current
    val type: AwakenType @Composable @ReadOnlyComposable get() = LocalAwakenType.current
    val motion: AwakenMotion @Composable @ReadOnlyComposable get() = LocalAwakenMotion.current
    val shapes: AwakenShapes @Composable @ReadOnlyComposable get() = LocalAwakenShapes.current
    val dimens: AwakenDimens @Composable @ReadOnlyComposable get() = LocalAwakenDimens.current
    val haptics: Haptics @Composable @ReadOnlyComposable get() = LocalHaptics.current
    /** Accent for a rank-coloured window: the rank colour. Never for the crest. */
    @Composable
    @ReadOnlyComposable
    fun rankAccent(rank: Rank): Color = LocalAwakenPalette.current.rank(rank)
}

