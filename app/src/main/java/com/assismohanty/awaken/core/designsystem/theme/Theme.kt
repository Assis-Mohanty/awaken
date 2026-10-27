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
 * Three palettes, one set of tokens (DESIGN_SYSTEM §2.1). No dynamic colour. Clean mode is Light (default) or Dark from
 * the user's Appearance choice; System mode is always the System palette (dark, glow, tracked-out titles).
 *
 * @param darkTheme the resolved Appearance choice for Clean mode; the default follows the device.
 * @param reduceMotion the in-app toggle; the system "Remove animations" setting is OR-ed in (§2.5).
 * @param hapticsEnabled the in-app toggle (§2.6).
 * @param themeId the equipped Vault theme (GD §9.2); re-tints the background glow and rank-coloured window accents ([AwakenTheme.rankAccent]) only.
 * @param presentation Clean or System words (GD §14.9), provided as [LocalLexicon].
 */
@Composable
fun AwakenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = false,
    hapticsEnabled: Boolean = true,
    themeId: String = "default",
    presentation: PresentationMode = PresentationMode.CLEAN,
    content: @Composable () -> Unit,
) {
    val system = presentation == PresentationMode.SYSTEM
    val palette = when {
        system -> AwakenPalette.System
        darkTheme -> AwakenPalette.Dark
        else -> AwakenPalette.Light
    }
    val colors = remember(palette) { AwakenColors(palette) }
    val type = if (system) AwakenType.System else AwakenType()
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
        LocalThemeTint provides palette.theme(themeId),
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
        secondary = aether,
        onSecondary = onAccent,
        secondaryContainer = panelRaised, // nav-bar indicator: neutral, not Material tint
        onSecondaryContainer = bone,
        tertiary = crit,
        onTertiary = onAccent,
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
        onError = onAccent,
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
    /** The equipped theme accent, or null on the default theme. */
    val themeTint: Color? @Composable @ReadOnlyComposable get() = LocalThemeTint.current

    /** Accent for a rank-coloured window: the equipped theme tint (GD §9.2), else the rank colour. Never for the crest. */
    @Composable
    @ReadOnlyComposable
    fun rankAccent(rank: Rank): Color = LocalThemeTint.current ?: LocalAwakenPalette.current.rank(rank)
}

val LocalThemeTint = compositionLocalOf<Color?> { null }
