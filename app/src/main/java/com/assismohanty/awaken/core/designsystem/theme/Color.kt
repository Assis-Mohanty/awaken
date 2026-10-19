package com.assismohanty.awaken.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat

// Raw palette — DESIGN_SYSTEM.md §2.1 (premium black). Only this file holds hex values; everything else reads AwakenColors.
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

@Immutable
data class AwakenColors(
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

val LocalAwakenColors = staticCompositionLocalOf { AwakenColors() }
