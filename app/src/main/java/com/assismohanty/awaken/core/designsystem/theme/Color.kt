package com.assismohanty.awaken.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat

/*
 * Raw palettes: the Plates look (design/DESIGN.md, design/tokens.json). Only this file holds hex values. Feature code
 * reads the semantic AwakenColors (DESIGN_SYSTEM §8.4); AwakenPalette is for core/designsystem (and the widget, which
 * can't read the theme).
 *
 * Chalk and steel for the chrome, ink for the one primary action, and the five competition-plate colours reserved for
 * the five stats. Colour lives on data, never on buttons or headers.
 */

/** Raw colour values for one look. Field names are historical; DESIGN_SYSTEM §2.1 lists each role. */
@Immutable
data class AwakenPalette(
    /** The page (chalk in light, rubber floor in dark). */
    val void: Color,
    /** Recessed wells, tracks, chips and secondary buttons; same as [panelRaised]. */
    val depth: Color,
    /** Cards and rows. */
    val panel: Color,
    /** Pressed cards, the selected nav pill, wells inside cards. */
    val panelRaised: Color,
    /** Hairlines. */
    val edge: Color,
    /** Primary ink. */
    val bone: Color,
    /** Secondary ink (AA on every surface). */
    val mist: Color,
    /** Tertiary ink. Plates has no third step, so this equals [mist]. */
    val fog: Color,
    /** The primary-button fill: the primary ink, so actions read by weight, not hue. */
    val system: Color,
    /** Text and icons on a [system] fill. */
    val onAccent: Color,
    /** Critical: errors, destructive actions, things that need attention. Always shown with words. */
    val ember: Color,
    /** Reward (gold, XP): plain ink, since colour is reserved for stats. */
    val crit: Color,
    /** Verified and healthy: plain ink. */
    val verified: Color,
    /** Stat plates: small round markers, chart series, loaded bars. */
    val statStr: Color,
    val statInt: Color,
    val statWil: Color,
    val statVit: Color,
    /** Charisma is the white plate: it needs [statChaEdge] to show on chalk. */
    val statCha: Color,
    val statChaEdge: Color,
    /** Deep stat colours for full-screen sessions. */
    val fieldStr: Color,
    val fieldInt: Color,
    val fieldWil: Color,
    val fieldVit: Color,
    val fieldCha: Color,
    /** Dim behind sheets. */
    val scrim: Color,
) {
    val isDark: Boolean get() = void.luminance() < HALF

    /** Ranks carry no colour in Plates: the badge is ink with the letter. */
    @Suppress("UNUSED_PARAMETER")
    fun rank(rank: Rank): Color = bone

    /** Sessions are coloured by their stat, not by a rank. */
    @Suppress("UNUSED_PARAMETER")
    fun gate(gate: GateRank): Color = bone

    fun stat(stat: Stat): Color = when (stat) {
        Stat.STR -> statStr
        Stat.INT -> statInt
        Stat.WIL -> statWil
        Stat.VIT -> statVit
        Stat.CHA -> statCha
    }

    /** The stat colour as a stroke: Charisma gets its edge so the white plate shows on chalk. */
    fun statEdge(stat: Stat): Color = if (stat == Stat.CHA) statChaEdge else stat(stat)

    /** The full-screen session colour for a stat. */
    fun field(stat: Stat): Color = when (stat) {
        Stat.STR -> fieldStr
        Stat.INT -> fieldInt
        Stat.WIL -> fieldWil
        Stat.VIT -> fieldVit
        Stat.CHA -> fieldCha
    }

    /** The 15% wash behind a stat icon in a list row or chip. */
    fun statTint(stat: Stat): Color = stat(stat).copy(alpha = AwakenAlpha.TINT)

    /** Rings, bars and chart lines: the plate colour itself. */
    fun statFill(stat: Stat): Color = stat(stat)

    companion object {
        /** Text and the track ring on a session field. */
        val OnField = Color(0xFFFFFFFF)
        val OnFieldMuted = Color(0xFFE9ECF4)
        val FieldTrack = Color(0x29FFFFFF)

        /** The inverse primary button on a session field. */
        val ButtonOnField = Color(0xFFF8F8F5)
        val OnButtonOnField = Color(0xFF131417)

        private val Scrim = Color(0x8F0A0A0C)

        val Light = AwakenPalette(
            void = Color(0xFFF2F2ED),
            depth = Color(0xFFE6E7E1),
            panel = Color(0xFFFDFDFB),
            panelRaised = Color(0xFFE6E7E1),
            edge = Color(0xFFDCDDD7),
            bone = Color(0xFF16171B),
            mist = Color(0xFF50545B),
            fog = Color(0xFF50545B),
            system = Color(0xFF16171B),
            onAccent = Color(0xFFFDFDFB),
            ember = Color(0xFFB81B21),
            crit = Color(0xFF16171B),
            verified = Color(0xFF16171B),
            statStr = Color(0xFFC4382F),
            statInt = Color(0xFF2C63C2),
            statWil = Color(0xFFDDA92C),
            statVit = Color(0xFF2A8E66),
            statCha = Color(0xFFFFFFFF),
            statChaEdge = Color(0xFFC9CBC4),
            fieldStr = Color(0xFF8E2A25),
            fieldInt = Color(0xFF1B3F86),
            // Willpower and Charisma have no deep colour in the mocks; these are derived from their plates.
            fieldWil = Color(0xFF6B4E0C),
            fieldVit = Color(0xFF1D5F47),
            fieldCha = Color(0xFF3B3E46),
            scrim = Scrim,
        )

        val Dark = AwakenPalette(
            void = Color(0xFF131417),
            depth = Color(0xFF2B2C31),
            panel = Color(0xFF1E1F23),
            panelRaised = Color(0xFF2B2C31),
            edge = Color(0xFF2E2F35),
            bone = Color(0xFFF2F2EC),
            mist = Color(0xFFB6B8BD),
            fog = Color(0xFFB6B8BD),
            system = Color(0xFFF2F2EC),
            onAccent = Color(0xFF16171B),
            ember = Color(0xFFE5635C),
            crit = Color(0xFFF2F2EC),
            verified = Color(0xFFF2F2EC),
            statStr = Color(0xFFE5635C),
            statInt = Color(0xFF6E9CEE),
            statWil = Color(0xFFE9C25C),
            statVit = Color(0xFF57BF90),
            statCha = Color(0xFFE9E8E0),
            statChaEdge = Color(0xFFE9E8E0),
            fieldStr = Color(0xFF5E1C19),
            fieldInt = Color(0xFF142D5F),
            fieldWil = Color(0xFF4A3608),
            fieldVit = Color(0xFF123F2F),
            fieldCha = Color(0xFF2A2C32),
            scrim = Scrim,
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
    /** Recessed wells, tracks, chips and secondary buttons. */
    val surfaceSunken: Color get() = palette.depth
    /** Cards and rows. */
    val surfaceElevated: Color get() = palette.panel
    /** Pressed cards, the selected nav pill, wells inside cards. */
    val surfaceOverlay: Color get() = palette.panelRaised
    /** The selected segment of a segmented control. */
    val surfaceSelected: Color get() = palette.panel
    val textPrimary: Color get() = palette.bone
    val textSecondary: Color get() = palette.mist
    /** Plates has no third ink step: this equals [textSecondary]. */
    val textMuted: Color get() = palette.fog
    /** Primary button fill and checks: the primary ink. */
    val accent: Color get() = palette.system
    /** The primary button: fill and the text on it. */
    val buttonPrimary: Color get() = palette.system
    val onButtonPrimary: Color get() = palette.onAccent
    /** Empty tracks and quiet outlines. */
    val accentMuted: Color get() = palette.edge
    /** Text on an [accent] fill. */
    val onAccent: Color get() = palette.onAccent
    val divider: Color get() = palette.edge
    /** Verified, held, healthy, a rise (graphics only): plain ink, since colour means a stat. */
    val positive: Color get() = palette.verified
    /** Caution that is not an error (graphics only): the critical colour, always beside words. */
    val warning: Color get() = palette.ember
    /** Errors, destructive actions, things that need attention (graphics only; text stays ink). */
    val critical: Color get() = palette.ember
    /** Gold, XP: plain ink. */
    val reward: Color get() = palette.crit
    /** Chart series in stat order (STR, INT, WIL, VIT, CHA, which draws with its edge), then a neutral. */
    val chartSeries1: Color get() = palette.statStr
    val chartSeries2: Color get() = palette.statInt
    val chartSeries3: Color get() = palette.statWil
    val chartSeries4: Color get() = palette.statVit
    val chartSeries5: Color get() = palette.statChaEdge
    val chartSeries6: Color get() = palette.mist
    val chartGrid: Color get() = palette.edge
    /** Dim behind sheets. */
    val scrim: Color get() = palette.scrim

    /** A session field's text, secondary text, dial track and inverse button. */
    val onField: Color get() = AwakenPalette.OnField
    val onFieldMuted: Color get() = AwakenPalette.OnFieldMuted
    val fieldTrack: Color get() = AwakenPalette.FieldTrack
    val buttonOnField: Color get() = AwakenPalette.ButtonOnField
    val onButtonOnField: Color get() = AwakenPalette.OnButtonOnField

    /** Ranks and sessions carry no colour of their own: ink. Kept until the last call site is restyled. */
    fun rank(rank: Rank): Color = palette.rank(rank)
    /** A stat's plate colour: markers, bars, chart series (never body text). */
    fun stat(stat: Stat): Color = palette.stat(stat)
    /** A stat's colour as a stroke; Charisma gets its edge. */
    fun statEdge(stat: Stat): Color = palette.statEdge(stat)
    /** A stat's deep colour for a full-screen session. */
    fun field(stat: Stat): Color = palette.field(stat)
    /** A stat's 15% wash behind its icon. */
    fun statTint(stat: Stat): Color = palette.statTint(stat)
    /** A stat's fill for rings, bars and charts. */
    fun statFill(stat: Stat): Color = palette.statFill(stat)
    fun gate(gate: GateRank): Color = palette.gate(gate)

    /**
     * The colour of a real plate on a barbell, by kilograms (what is physically on the bar): 25 red, 20 blue, 15 yellow,
     * 10 green, 5 white. Smaller plates are plain steel, since competition colours stop at 5 kg.
     */
    fun plate(kg: Double): Color = when {
        kg >= PLATE_25 -> palette.statStr
        kg >= PLATE_20 -> palette.statInt
        kg >= PLATE_15 -> palette.statWil
        kg >= PLATE_10 -> palette.statVit
        kg >= PLATE_5 -> palette.statChaEdge
        else -> palette.mist
    }

    override fun equals(other: Any?): Boolean = other is AwakenColors && other.palette == palette
    override fun hashCode(): Int = palette.hashCode()

    companion object {
        val Light = AwakenColors(AwakenPalette.Light)
        val Dark = AwakenColors(AwakenPalette.Dark)
        private const val PLATE_25 = 25.0
        private const val PLATE_20 = 20.0
        private const val PLATE_15 = 15.0
        private const val PLATE_10 = 10.0
        private const val PLATE_5 = 5.0
    }
}

private const val HALF = 0.5f

/** Alpha steps used repeatedly across components (DESIGN_SYSTEM §2.3, §3). */
object AwakenAlpha {
    /** Pill borders. */
    const val EDGE = 0.70f
    /** The wash behind a stat icon, chip or crest. */
    const val TINT = 0.15f
    const val RADAR_FILL = 0.08f
    const val DIM_SCRIM = 0.90f
    /** Five fill steps: heatmap cells and the body map's trained muscles. */
    val HEATMAP_STEPS = listOf(0.20f, 0.38f, 0.56f, 0.78f, 1.0f)
}

val LocalAwakenPalette = staticCompositionLocalOf { AwakenPalette.Light }
val LocalAwakenColors = staticCompositionLocalOf { AwakenColors.Light }
