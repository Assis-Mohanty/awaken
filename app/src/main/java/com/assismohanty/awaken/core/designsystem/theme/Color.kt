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
 * Light is the default: a warm off-white page, white cards with a warm hairline, near-black ink, one restrained indigo
 * accent, and a soft pastel per stat (tint for card washes, fill for rings and charts, ink for text on either). Dark is
 * a soft charcoal with the same structure. System is the dark layout on near-black with an electric accent and glow.
 */

/** Raw colour values for one look. Field names are historical; DESIGN_SYSTEM §2.1 lists each role. */
@Immutable
data class AwakenPalette(
    /** The page. */
    val void: Color,
    /** Below the page: bottom bar, recessed wells. */
    val depth: Color,
    /** Cards. */
    val panel: Color,
    /** Sheets, pressed states, wells inside cards. */
    val panelRaised: Color,
    /** Bottom stop of a card's fill; equal to [panel] when cards are flat. */
    val panelShade: Color,
    /** A 1dp lit top edge on cards; transparent when unused. */
    val panelSheen: Color,
    /** Hairlines and empty tracks. */
    val edge: Color,
    /** Primary ink. */
    val bone: Color,
    /** Secondary ink (≥ 5:1 on cards). */
    val mist: Color,
    /** Disabled / tertiary; never for text people need to read. */
    val fog: Color,
    /** The accent: primary buttons, fills, checks. */
    val system: Color,
    /** The bright end of an accent gradient. */
    val aether: Color,
    /** Text and icons on an [system] fill. */
    val onAccent: Color,
    val ember: Color,
    val crit: Color,
    val verified: Color,
    /** Two soft colours washed behind a screen's hero area. */
    val heroWashA: Color,
    val heroWashB: Color,
    /** System mode: cards and the page carry an accent glow. */
    val glow: Boolean,
    val rankE: Color,
    val rankD: Color,
    val rankC: Color,
    val rankB: Color,
    val rankA: Color,
    val rankS: Color,
    /** Stat ink: text and icons in a stat's colour (≥ 4.5:1 on cards and on the stat's tint). */
    val statStr: Color,
    val statInt: Color,
    val statWil: Color,
    val statVit: Color,
    val statCha: Color,
    /** Stat tint: soft card and chip backgrounds. */
    val tintStr: Color,
    val tintInt: Color,
    val tintWil: Color,
    val tintVit: Color,
    val tintCha: Color,
    /** Stat fill: rings, bars, chart lines and dots. */
    val fillStr: Color,
    val fillInt: Color,
    val fillWil: Color,
    val fillVit: Color,
    val fillCha: Color,
    // Vault themes (GD §9.2): re-tint accents, never the rank crest.
    val themeCrimson: Color,
    val themeViolet: Color,
    val themeGold: Color,
    // Title frame outlines (GD §9.2), cheapest to most prestigious.
    val frameIron: Color,
    val frameEmber: Color,
    val frameUmbral: Color,
    val frameRegal: Color,
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

    /** Accent override for a Vault theme id; null for the default theme. */
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

    fun statTint(stat: Stat): Color = when (stat) {
        Stat.STR -> tintStr
        Stat.INT -> tintInt
        Stat.WIL -> tintWil
        Stat.VIT -> tintVit
        Stat.CHA -> tintCha
    }

    fun statFill(stat: Stat): Color = when (stat) {
        Stat.STR -> fillStr
        Stat.INT -> fillInt
        Stat.WIL -> fillWil
        Stat.VIT -> fillVit
        Stat.CHA -> fillCha
    }

    companion object {
        /** The default look (DESIGN_SYSTEM §2.1): warm, light, calm. */
        val Light = AwakenPalette(
            void = Color(0xFFF6F4EF),
            depth = Color(0xFFEFECE6),
            panel = Color(0xFFFFFFFF),
            panelRaised = Color(0xFFF4F1EC),
            panelShade = Color(0xFFFFFFFF),
            panelSheen = Color(0x00FFFFFF),
            edge = Color(0xFFE8E3DA),
            bone = Color(0xFF1B1A18),
            mist = Color(0xFF67635C),
            fog = Color(0xFFABA69D),
            system = Color(0xFF3446C9),
            aether = Color(0xFF7182F2),
            onAccent = Color(0xFFFFFFFF),
            ember = Color(0xFFC63D36),
            crit = Color(0xFF8F6200),
            verified = Color(0xFF1D7C51),
            heroWashA = Color(0xFFEDE6FF),
            heroWashB = Color(0xFFFFEFD9),
            glow = false,
            rankE = Color(0xFF5F6B7C),
            rankD = Color(0xFF237D4D),
            rankC = Color(0xFF0E7380),
            rankB = Color(0xFF3558C4),
            rankA = Color(0xFF6C4BCB),
            rankS = Color(0xFF8F6200),
            statStr = Color(0xFF7F5600),
            statInt = Color(0xFF5640BA),
            statWil = Color(0xFF2B5DB5),
            statVit = Color(0xFF1A6F4D),
            statCha = Color(0xFFA6385D),
            tintStr = Color(0xFFFFF3D1),
            tintInt = Color(0xFFEEE9FF),
            tintWil = Color(0xFFE5EEFF),
            tintVit = Color(0xFFDFF4E9),
            tintCha = Color(0xFFFFE6EE),
            fillStr = Color(0xFFF2BC45),
            fillInt = Color(0xFF9F8BF5),
            fillWil = Color(0xFF75A1F1),
            fillVit = Color(0xFF5CC598),
            fillCha = Color(0xFFEF8BAE),
            themeCrimson = Color(0xFFB8323F),
            themeViolet = Color(0xFF6B45D8),
            themeGold = Color(0xFF8F6200),
            frameIron = Color(0xFF6B7380),
            frameEmber = Color(0xFFC0532F),
            frameUmbral = Color(0xFF6B45D8),
            frameRegal = Color(0xFF8F6200),
        )

        /** Equally polished dark: soft charcoal, never pure black. */
        val Dark = AwakenPalette(
            void = Color(0xFF131416),
            depth = Color(0xFF0F1012),
            panel = Color(0xFF1C1D20),
            panelRaised = Color(0xFF25272B),
            panelShade = Color(0xFF1C1D20),
            panelSheen = Color(0x00FFFFFF),
            edge = Color(0xFF2E3034),
            bone = Color(0xFFF1EFEA),
            mist = Color(0xFFA7A39C),
            fog = Color(0xFF6F6C66),
            system = Color(0xFF9DAAFF),
            aether = Color(0xFFC7CEFF),
            onAccent = Color(0xFF141625),
            ember = Color(0xFFFF7D73),
            crit = Color(0xFFF2C35C),
            verified = Color(0xFF62D4A2),
            heroWashA = Color(0xFF262238),
            heroWashB = Color(0xFF2B2519),
            glow = false,
            rankE = Color(0xFFA9B3C1),
            rankD = Color(0xFF6FD49D),
            rankC = Color(0xFF62D2DE),
            rankB = Color(0xFF8CA7FF),
            rankA = Color(0xFFBBA1FF),
            rankS = Color(0xFFF2C35C),
            statStr = Color(0xFFF4CB68),
            statInt = Color(0xFFBBACFF),
            statWil = Color(0xFF96B7FF),
            statVit = Color(0xFF77DBAF),
            statCha = Color(0xFFFFA6C4),
            tintStr = Color(0xFF2F2918),
            tintInt = Color(0xFF26233A),
            tintWil = Color(0xFF1E2739),
            tintVit = Color(0xFF1A2D25),
            tintCha = Color(0xFF34222B),
            fillStr = Color(0xFFE9B547),
            fillInt = Color(0xFF9F8CF5),
            fillWil = Color(0xFF7EA6F5),
            fillVit = Color(0xFF58C795),
            fillCha = Color(0xFFEE8BAE),
            themeCrimson = Color(0xFFFF6B77),
            themeViolet = Color(0xFFA98BFF),
            themeGold = Color(0xFFF2C35C),
            frameIron = Color(0xFFB4BCC8),
            frameEmber = Color(0xFFFF8A65),
            frameUmbral = Color(0xFFB79CFF),
            frameRegal = Color(0xFFF2C35C),
        )

        /** System mode (GD §14.9): the same layout on near-black with an electric accent and glow. */
        val System = AwakenPalette(
            void = Color(0xFF09090B),
            depth = Color(0xFF050506),
            panel = Color(0xFF111115),
            panelRaised = Color(0xFF1A1A20),
            panelShade = Color(0xFF0C0C0F),
            panelSheen = Color(0x14FFFFFF),
            edge = Color(0xFF26262E),
            bone = Color(0xFFEDEDED),
            mist = Color(0xFF9A9AA3),
            fog = Color(0xFF5A5A63),
            system = Color(0xFF7FD8FF),
            aether = Color(0xFFD2F4FF),
            onAccent = Color(0xFF041219),
            ember = Color(0xFFFF5468),
            crit = Color(0xFFFFC94D),
            verified = Color(0xFF5BE3B0),
            heroWashA = Color(0xFF0E2230),
            heroWashB = Color(0xFF1B1430),
            glow = true,
            rankE = Color(0xFF8A96A8),
            rankD = Color(0xFF5BD08A),
            rankC = Color(0xFF4FD6E8),
            rankB = Color(0xFF6E99FF),
            rankA = Color(0xFFA97BFF),
            rankS = Color(0xFFFFC94D),
            statStr = Color(0xFFFF8A6A),
            statInt = Color(0xFF6CC8FF),
            statWil = Color(0xFFB28CFF),
            statVit = Color(0xFF5BE3B0),
            statCha = Color(0xFFFFB3D1),
            tintStr = Color(0xFF2A1812),
            tintInt = Color(0xFF0F2230),
            tintWil = Color(0xFF1F1830),
            tintVit = Color(0xFF0F2A21),
            tintCha = Color(0xFF2E1A24),
            fillStr = Color(0xFFFF7A59),
            fillInt = Color(0xFF5CC2FF),
            fillWil = Color(0xFFA97BFF),
            fillVit = Color(0xFF5BE3B0),
            fillCha = Color(0xFFFFB3D1),
            themeCrimson = Color(0xFFE0364B),
            themeViolet = Color(0xFF8E5CFF),
            themeGold = Color(0xFFD9AE3C),
            frameIron = Color(0xFFB4BCC8),
            frameEmber = Color(0xFFFF7A59),
            frameUmbral = Color(0xFFA97BFF),
            frameRegal = Color(0xFFFFC94D),
        )

        /** The v1.0 premium-black look, kept for the share image and the widget until their own redesign (BACKLOG). */
        val Legacy = System.copy(
            void = Color(0xFF000000),
            depth = Color(0xFF0A0A0A),
            panel = Color(0xFF0E0E0E),
            panelRaised = Color(0xFF161616),
            panelShade = Color(0xFF090909),
            panelSheen = Color(0x12FFFFFF),
            edge = Color(0xFF262626),
            bone = Color(0xFFEDEDED),
            mist = Color(0xFF8A8A8A),
            fog = Color(0xFF525252),
            system = Color(0xFFEDEDED),
            aether = Color(0xFFFFFFFF),
            onAccent = Color(0xFF000000),
            glow = false,
            rankB = Color(0xFF5C8DFF),
            statStr = Color(0xFFFF7A59),
            statInt = Color(0xFF5CC2FF),
            statWil = Color(0xFFA97BFF),
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
    /** Text on an [accent] fill. */
    val onAccent: Color get() = palette.onAccent
    val divider: Color get() = palette.edge
    /** Verified, held, healthy, a rise. */
    val positive: Color get() = palette.verified
    /** Caution that is not an error. */
    val warning: Color get() = palette.crit
    /** Errors, destructive actions, penalties, breaches, a fall. */
    val critical: Color get() = palette.ember
    /** Gold, crits, level-ups. */
    val reward: Color get() = palette.crit
    /** Chart series in stat order (STR, INT, WIL, VIT, CHA), then a neutral. */
    val chartSeries1: Color get() = palette.fillStr
    val chartSeries2: Color get() = palette.fillInt
    val chartSeries3: Color get() = palette.fillWil
    val chartSeries4: Color get() = palette.fillVit
    val chartSeries5: Color get() = palette.fillCha
    val chartSeries6: Color get() = palette.mist
    val chartGrid: Color get() = palette.edge

    fun rank(rank: Rank): Color = palette.rank(rank)
    /** A stat's ink: text and icons. */
    fun stat(stat: Stat): Color = palette.stat(stat)
    /** A stat's soft background tint. */
    fun statTint(stat: Stat): Color = palette.statTint(stat)
    /** A stat's fill for rings, bars and charts. */
    fun statFill(stat: Stat): Color = palette.statFill(stat)
    fun gate(gate: GateRank): Color = palette.gate(gate)
    fun theme(themeId: String): Color? = palette.theme(themeId)
    fun frame(frameId: String): Color = palette.frame(frameId)

    override fun equals(other: Any?): Boolean = other is AwakenColors && other.palette == palette
    override fun hashCode(): Int = palette.hashCode()

    companion object {
        val Light = AwakenColors(AwakenPalette.Light)
        val Dark = AwakenColors(AwakenPalette.Dark)
        val System = AwakenColors(AwakenPalette.System)
    }
}

private const val HALF = 0.5f

/** Alpha steps used repeatedly across components (DESIGN_SYSTEM §2.3, §3). */
object AwakenAlpha {
    /** Pill borders. */
    const val EDGE = 0.70f
    /** Faint tint behind crests and swipe reveals. */
    const val TINT = 0.10f
    /** Background haze. */
    const val HAZE = 0.05f
    const val RADAR_FILL = 0.18f
    const val DIM_SCRIM = 0.90f
    /** System-mode glow around cards and the hero. */
    const val GLOW = 0.35f
    val HEATMAP_STEPS = listOf(0.08f, 0.30f, 0.50f, 0.72f, 1.0f)
}

val LocalAwakenPalette = staticCompositionLocalOf { AwakenPalette.Light }
val LocalAwakenColors = staticCompositionLocalOf { AwakenColors.Light }
