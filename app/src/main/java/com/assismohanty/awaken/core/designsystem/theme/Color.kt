package com.assismohanty.awaken.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat

/*
 * Raw palettes — DESIGN_SYSTEM.md §2.1. Only this file holds hex values. Feature code reads the semantic AwakenColors
 * (DS §8.4); AwakenPalette is for core/designsystem (and the widget, which can't read the theme).
 *
 * Two equal looks that follow the phone's light/dark setting: a warm neutral page, flat cards separated by colour alone
 * (no borders, no shadows), ink in three steps, and one small set of accents (the five stats plus positive/negative)
 * used only on icons, chips, chart lines and progress fills, never as text or card backgrounds.
 */

/** Raw colour values for one look. Field names are historical; DESIGN_SYSTEM §2.1 lists each role. */
@Immutable
data class AwakenPalette(
    /** The page. */
    val void: Color,
    /** Recessed wells and tracks; same as [panelRaised]. */
    val depth: Color,
    /** Cards. */
    val panel: Color,
    /** Pressed cards, the selected nav pill, wells inside cards. */
    val panelRaised: Color,
    /** Hairlines and empty ring tracks. */
    val edge: Color,
    /** Primary ink. */
    val bone: Color,
    /** Secondary ink (≥ 5:1 on cards). */
    val mist: Color,
    /** Tertiary ink: captions people may skip, disabled. */
    val fog: Color,
    /** The primary-button fill: the primary ink, so actions read by weight, not hue. */
    val system: Color,
    /** Text and icons on a [system] fill: the page colour. */
    val onAccent: Color,
    /** Negative: penalties, errors, a fall. */
    val ember: Color,
    /** Reward gold (the STR accent). */
    val crit: Color,
    /** Positive: verified, a rise. */
    val verified: Color,
    val rankE: Color,
    val rankD: Color,
    val rankC: Color,
    val rankB: Color,
    val rankA: Color,
    val rankS: Color,
    /** Stat accents: small icons, chips, chart lines, progress fills. */
    val statStr: Color,
    val statInt: Color,
    val statWil: Color,
    val statVit: Color,
    val statCha: Color,
) {
    val isDark: Boolean get() = void.luminance() < HALF

    fun rank(rank: Rank): Color = when (rank) {
        Rank.E -> rankE
        Rank.D -> rankD
        Rank.C -> rankC
        Rank.B -> rankB
        Rank.A -> rankA
        Rank.S -> rankS
    }

    /** Gates share the colour of the rank with the same letter. */
    fun gate(gate: GateRank): Color = rank(Rank.valueOf(gate.name))

    fun stat(stat: Stat): Color = when (stat) {
        Stat.STR -> statStr
        Stat.INT -> statInt
        Stat.WIL -> statWil
        Stat.VIT -> statVit
        Stat.CHA -> statCha
    }

    /** The 15% wash behind a stat icon in a list row or chip. */
    fun statTint(stat: Stat): Color = stat(stat).copy(alpha = AwakenAlpha.TINT)

    /** Rings, bars and chart lines: the accent itself. */
    fun statFill(stat: Stat): Color = stat(stat)

    companion object {
        private val AccentStr = Color(0xFFE8B931)
        private val AccentInt = Color(0xFFA99BF0)
        private val AccentVit = Color(0xFF5FBF8F)
        private val AccentWil = Color(0xFF6FA8F5)
        private val AccentCha = Color(0xFFF08A7A)
        private val Positive = Color(0xFF4CC38A)
        private val Negative = Color(0xFFE5675A)

        @Suppress("LongParameterList")
        private fun look(page: Color, card: Color, pressed: Color, divider: Color, ink: Color, ink2: Color, ink3: Color) =
            AwakenPalette(
                void = page,
                depth = pressed,
                panel = card,
                panelRaised = pressed,
                edge = divider,
                bone = ink,
                mist = ink2,
                fog = ink3,
                system = ink,
                onAccent = page,
                ember = Negative,
                crit = AccentStr,
                verified = Positive,
                rankE = ink2,
                rankD = AccentVit,
                rankC = AccentWil,
                rankB = AccentInt,
                rankA = AccentCha,
                rankS = AccentStr,
                statStr = AccentStr,
                statInt = AccentInt,
                statWil = AccentWil,
                statVit = AccentVit,
                statCha = AccentCha,
            )

        val Light = look(
            page = Color(0xFFF5F4F1),
            card = Color(0xFFFFFFFF),
            pressed = Color(0xFFEFEDE8),
            divider = Color(0xFFE6E4DF),
            ink = Color(0xFF111111),
            ink2 = Color(0xFF6B6965),
            ink3 = Color(0xFF9A978F),
        )

        val Dark = look(
            page = Color(0xFF0F0F0F),
            card = Color(0xFF1F1E1C),
            pressed = Color(0xFF2A2927),
            divider = Color(0xFF343330),
            ink = Color(0xFFF5F4F1),
            ink2 = Color(0xFFA8A6A1),
            ink3 = Color(0xFF75736E),
        )
    }
}

/**
 * Semantic colour tokens (DESIGN_SYSTEM §8.4): what a colour is *for*, not what it looks like. Feature code may use only
 * these, so a redesign changes the palettes, not every screen.
 */
@Immutable
class AwakenColors(private val palette: AwakenPalette) {
    /** The page. */
    val surface: Color get() = palette.void
    /** Recessed wells and tracks. */
    val surfaceSunken: Color get() = palette.depth
    /** Cards. */
    val surfaceElevated: Color get() = palette.panel
    /** Pressed cards, the selected nav pill, wells inside cards. */
    val surfaceOverlay: Color get() = palette.panelRaised
    /** The selected segment of a segmented control: lighter than its track in both looks (card white / a raised grey). */
    val surfaceSelected: Color get() = if (palette.isDark) palette.edge else palette.panel
    val textPrimary: Color get() = palette.bone
    val textSecondary: Color get() = palette.mist
    /** Tertiary: captions people may skip, disabled. */
    val textMuted: Color get() = palette.fog
    /** Primary button fill and checks: the primary ink. */
    val accent: Color get() = palette.system
    /** The dark primary button: fill and the text on it. Today the same ink as [accent]; the redesign may split them. */
    val buttonPrimary: Color get() = palette.system
    val onButtonPrimary: Color get() = palette.onAccent
    /** Empty tracks and quiet outlines. */
    val accentMuted: Color get() = palette.edge
    /** Text on an [accent] fill. */
    val onAccent: Color get() = palette.onAccent
    val divider: Color get() = palette.edge
    /** Verified, held, healthy, a rise (graphics only; text stays ink). */
    val positive: Color get() = palette.verified
    /** Caution that is not an error (graphics only). */
    val warning: Color get() = palette.crit
    /** Errors, destructive actions, penalties, a fall (graphics only; text stays ink). */
    val critical: Color get() = palette.ember
    /** Gold, crits, level-ups. */
    val reward: Color get() = palette.crit
    /** Chart series in stat order (STR, INT, WIL, VIT, CHA), then a neutral. */
    val chartSeries1: Color get() = palette.statStr
    val chartSeries2: Color get() = palette.statInt
    val chartSeries3: Color get() = palette.statWil
    val chartSeries4: Color get() = palette.statVit
    val chartSeries5: Color get() = palette.statCha
    val chartSeries6: Color get() = palette.mist
    val chartGrid: Color get() = palette.edge

    fun rank(rank: Rank): Color = palette.rank(rank)
    /** A stat's accent: icons, chips, lines (never body text). */
    fun stat(stat: Stat): Color = palette.stat(stat)
    /** A stat's 15% wash behind its icon. */
    fun statTint(stat: Stat): Color = palette.statTint(stat)
    /** A stat's fill for rings, bars and charts. */
    fun statFill(stat: Stat): Color = palette.statFill(stat)
    fun gate(gate: GateRank): Color = palette.gate(gate)

    override fun equals(other: Any?): Boolean = other is AwakenColors && other.palette == palette
    override fun hashCode(): Int = palette.hashCode()

    companion object {
        val Light = AwakenColors(AwakenPalette.Light)
        val Dark = AwakenColors(AwakenPalette.Dark)
    }
}

private const val HALF = 0.5f

/** Alpha steps used repeatedly across components (DESIGN_SYSTEM §2.3, §3). */
object AwakenAlpha {
    /** Pill borders. */
    const val EDGE = 0.70f
    /** The wash behind a stat icon, chip or crest. */
    const val TINT = 0.15f
    const val RADAR_FILL = 0.18f
    const val DIM_SCRIM = 0.90f
    /** Five fill steps: heatmap cells and the body map's trained muscles. */
    val HEATMAP_STEPS = listOf(0.20f, 0.38f, 0.56f, 0.78f, 1.0f)
}

val LocalAwakenPalette = staticCompositionLocalOf { AwakenPalette.Light }
val LocalAwakenColors = staticCompositionLocalOf { AwakenColors.Light }
