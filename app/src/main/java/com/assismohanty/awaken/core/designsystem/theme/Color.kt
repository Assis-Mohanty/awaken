package com.assismohanty.awaken.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat

// Raw palette — DESIGN_SYSTEM.md §2.1 (premium black). Only this file holds hex values. Feature code reads the semantic
// AwakenColors (DS §8.4); AwakenPalette is for core/designsystem only.
// The chrome is greyscale; rank, stat, crit, verified and ember are the only hues.
val Void = Color(0xFF000000)
val Depth = Color(0xFF0A0A0A)
val Panel = Color(0xFF0E0E0E)
val PanelRaised = Color(0xFF161616)
val Edge = Color(0xFF262626)
val Bone = Color(0xFFEDEDED)
val Mist = Color(0xFF8A8A8A)
val Fog = Color(0xFF525252)
val SystemAccent = Bone // monochrome primary accent
val Aether = Color(0xFFFFFFFF)
private val IntBlue = Color(0xFF5CC2FF)
val Ember = Color(0xFFFF5468)
val Crit = Color(0xFFFFC94D)
val Verified = Color(0xFF5BE3B0)

/** Raw colour values for one mode. Only `core/designsystem` (and the widget, which can't read the theme) may name these. */
@Immutable
data class AwakenPalette(
    val void: Color = Void,
    val depth: Color = Depth,
    val panel: Color = Panel,
    val panelRaised: Color = PanelRaised,
    val edge: Color = Edge,
    val bone: Color = Bone,
    val mist: Color = Mist,
    val fog: Color = Fog,
    val system: Color = SystemAccent,
    val aether: Color = Aether,
    val ember: Color = Ember,
    val crit: Color = Crit,
    val verified: Color = Verified,
    /** Bottom stop of the System Window's vertical fill (panel → panelShade): depth without shadows (DS §2.3). */
    val panelShade: Color = Color(0xFF090909),
    /** The 1dp "lit edge" along a window's top: light catching the slab. Transparent in the light palette. */
    val panelSheen: Color = Color(0x12FFFFFF),
    val rankE: Color = Color(0xFF8A96A8),
    val rankD: Color = Color(0xFF5BD08A),
    val rankC: Color = Color(0xFF4FD6E8),
    val rankB: Color = Color(0xFF5C8DFF),
    val rankA: Color = Color(0xFFA97BFF),
    val rankS: Color = Crit,
    val statStr: Color = Color(0xFFFF7A59),
    val statInt: Color = IntBlue,
    val statWil: Color = Color(0xFFA97BFF),
    val statVit: Color = Verified,
    val statCha: Color = Color(0xFFFFB3D1),
    // Vault themes (GD §9.2): re-tint the background glow and window accents, never the rank crest.
    val themeCrimson: Color = Color(0xFFE0364B),
    val themeViolet: Color = Color(0xFF8E5CFF),
    val themeGold: Color = Color(0xFFD9AE3C),
    // Title frame outlines (GD §9.2), cheapest to most prestigious.
    val frameIron: Color = Color(0xFFB4BCC8),
    val frameEmber: Color = Color(0xFFFF7A59),
    val frameUmbral: Color = Color(0xFFA97BFF),
    val frameRegal: Color = Color(0xFFFFC94D),
) {
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

    /** Accent override for a Vault theme id; null for the default theme (rank colours apply). */
    fun theme(themeId: String): Color? = when (themeId) {
        "theme_crimson_gate" -> themeCrimson
        "theme_abyssal_violet" -> themeViolet
        "theme_sovereign_gold" -> themeGold
        else -> null
    }

    fun frame(frameId: String): Color = when (frameId) {
        "frame_iron" -> frameIron
        "frame_ember" -> frameEmber
        "frame_umbral" -> frameUmbral
        else -> frameRegal
    }

    fun stat(stat: Stat): Color = when (stat) {
        Stat.STR -> statStr
        Stat.INT -> statInt
        Stat.WIL -> statWil
        Stat.VIT -> statVit
        Stat.CHA -> statCha
    }

    companion object {
        /** The premium-black palette (DESIGN_SYSTEM §2.1). */
        val Dark = AwakenPalette()

        /**
         * The light palette (DESIGN_SYSTEM §2.1b): same tokens, inverted ink. `void` is still "the page" and `bone` is
         * still "primary ink". Hues are darkened so text in them reaches ≥ 4.5:1 on `panel` (white).
         */
        val Light = AwakenPalette(
            void = Color(0xFFF6F6F4),
            depth = Color(0xFFEFEFEC),
            panel = Color(0xFFFFFFFF),
            panelRaised = Color(0xFFF2F2F0),
            edge = Color(0xFFDADAD6),
            bone = Color(0xFF111111),
            mist = Color(0xFF5C5C5C),
            fog = Color(0xFFA3A3A3),
            system = Color(0xFF111111),
            aether = Color(0xFF000000),
            ember = Color(0xFFD42C42),
            crit = Color(0xFF946500),
            verified = Color(0xFF0A7F56),
            panelShade = Color(0xFFFAFAF8),
            panelSheen = Color(0x00FFFFFF),
            rankE = Color(0xFF5A6678),
            rankD = Color(0xFF1E8048),
            rankC = Color(0xFF0B7A8A),
            rankB = Color(0xFF2F5FD6),
            rankA = Color(0xFF7446D9),
            rankS = Color(0xFF946500),
            statStr = Color(0xFFC4492A),
            statInt = Color(0xFF1C72AC),
            statWil = Color(0xFF7446D9),
            statVit = Color(0xFF0A7F56),
            statCha = Color(0xFFB8467A),
            themeCrimson = Color(0xFFC42238),
            themeViolet = Color(0xFF6E3BE0),
            themeGold = Color(0xFF9A6E0E),
            frameIron = Color(0xFF6B7380),
            frameEmber = Color(0xFFC4492A),
            frameUmbral = Color(0xFF7446D9),
            frameRegal = Color(0xFF946500),
        )
    }
}

/** Alpha steps used repeatedly across components (DESIGN_SYSTEM §2.3, §3). */
object AwakenAlpha {
    /** Pill borders. */
    const val EDGE = 0.70f
    /** Faint tint behind crests and swipe reveals. */
    const val TINT = 0.10f
    /** Background rank haze (~5 %). */
    const val HAZE = 0.05f
    const val RADAR_FILL = 0.14f
    const val DIM_SCRIM = 0.90f
    val HEATMAP_STEPS = listOf(0.08f, 0.30f, 0.50f, 0.72f, 1.0f)
}

/**
 * Semantic colour tokens (DESIGN_SYSTEM §8.4): what a colour is *for*, not what it looks like. Feature code may use only
 * these, so a redesign changes the mapping here instead of every screen.
 */
@Immutable
class AwakenColors(private val palette: AwakenPalette) {
    /** The page. */
    val surface: Color get() = palette.void
    /** Below the page: bottom bar, recessed tracks. */
    val surfaceSunken: Color get() = palette.depth
    /** Cards and windows. */
    val surfaceElevated: Color get() = palette.panel
    /** Sheets, pressed states and wells inside cards. */
    val surfaceOverlay: Color get() = palette.panelRaised
    val textPrimary: Color get() = palette.bone
    val textSecondary: Color get() = palette.mist
    /** Disabled and tertiary; never for text people need to read. */
    val textMuted: Color get() = palette.fog
    /** Primary actions, fills, checks. */
    val accent: Color get() = palette.system
    /** Empty tracks and quiet outlines. */
    val accentMuted: Color get() = palette.edge
    /** The bright leading edge of a fill. */
    val accentHighlight: Color get() = palette.aether
    val divider: Color get() = palette.edge
    /** Verified, held, healthy. */
    val positive: Color get() = palette.verified
    /** Caution that is not an error. */
    val warning: Color get() = palette.crit
    /** Errors, destructive actions, penalties, breaches. */
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
    fun stat(stat: Stat): Color = palette.stat(stat)
    fun gate(gate: GateRank): Color = palette.gate(gate)
    fun theme(themeId: String): Color? = palette.theme(themeId)
    fun frame(frameId: String): Color = palette.frame(frameId)

    override fun equals(other: Any?): Boolean = other is AwakenColors && other.palette == palette
    override fun hashCode(): Int = palette.hashCode()

    companion object {
        val Dark = AwakenColors(AwakenPalette.Dark)
        val Light = AwakenColors(AwakenPalette.Light)
    }
}

val LocalAwakenPalette = staticCompositionLocalOf { AwakenPalette() }
val LocalAwakenColors = staticCompositionLocalOf { AwakenColors.Dark }
