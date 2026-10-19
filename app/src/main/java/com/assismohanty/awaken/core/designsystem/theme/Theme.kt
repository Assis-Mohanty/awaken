package com.assismohanty.awaken.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.assismohanty.awaken.engine.model.Rank

/**
 * Dark-only in v1 (DESIGN_SYSTEM.md). No dynamic colour: the System has one look.
 *
 * @param reduceMotion the in-app toggle; the system "Remove animations" setting is OR-ed in (§2.5).
 * @param hapticsEnabled the in-app toggle (§2.6).
 * @param themeId the equipped Vault theme (GD §9.2); re-tints the background glow and rank-coloured window accents ([AwakenTheme.rankAccent]) only.
 */
@Composable
fun AwakenTheme(
    reduceMotion: Boolean = false,
    hapticsEnabled: Boolean = true,
    themeId: String = "default",
    content: @Composable () -> Unit,
) {
    val colors = remember { AwakenColors() }
    val type = remember { AwakenType() }
    val reduce = reduceMotion || systemReduceMotion()
    val motion = remember(reduce) { AwakenMotion(reduceMotion = reduce) }
    val shapes = remember { AwakenShapes() }
    val dimens = remember { AwakenDimens() }
    val colorScheme = remember(colors) {
        darkColorScheme(
            primary = colors.system,
            onPrimary = colors.void,
            primaryContainer = colors.panelRaised,
            onPrimaryContainer = colors.bone,
            secondary = colors.aether,
            onSecondary = colors.void,
            secondaryContainer = colors.panelRaised, // nav-bar indicator: neutral, not Material tint
            onSecondaryContainer = colors.bone,
            tertiary = colors.crit,
            onTertiary = colors.void,
            background = colors.void,
            onBackground = colors.bone,
            surface = colors.void,
            onSurface = colors.bone,
            surfaceVariant = colors.depth,
            onSurfaceVariant = colors.mist,
            surfaceContainer = colors.depth,
            surfaceContainerLow = colors.depth,
            surfaceContainerHigh = colors.panelRaised,
            surfaceContainerHighest = colors.panelRaised,
            outline = colors.edge,
            outlineVariant = colors.fog,
            error = colors.ember,
            onError = colors.void,
        )
    }
    CompositionLocalProvider(
        LocalAwakenColors provides colors,
        LocalAwakenType provides type,
        LocalAwakenMotion provides motion,
        LocalAwakenShapes provides shapes,
        LocalAwakenDimens provides dimens,
        LocalHaptics provides rememberHaptics(hapticsEnabled),
        LocalThemeTint provides colors.theme(themeId),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = remember(type) { type.toMaterialTypography() },
            content = content,
        )
    }
}

/** Token access for components and screens: `AwakenTheme.colors.system`, `AwakenTheme.type.title`, … */
object AwakenTheme {
    val colors: AwakenColors @Composable @ReadOnlyComposable get() = LocalAwakenColors.current
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
    fun rankAccent(rank: Rank): Color = LocalThemeTint.current ?: LocalAwakenColors.current.rank(rank)
}

val LocalThemeTint = compositionLocalOf<Color?> { null }
