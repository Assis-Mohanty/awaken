package com.assismohanty.awaken.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat

// Raw palette — DESIGN_SYSTEM.md §2.1. Only this file holds hex values; everything else reads AwakenColors.
val Void = Color(0xFF05080F)
val Depth = Color(0xFF0A1220)
val Panel = Color(0xDB0F1A2B) // #0F1A2B @ 86 %
val PanelRaised = Color(0xFF15233A)
val Edge = Color(0xFF27405F)
val Bone = Color(0xFFE7EEF6)
val Mist = Color(0xFF93A4BA)
val Fog = Color(0xFF5B6B80)
val SystemBlue = Color(0xFF5CC2FF)
val Aether = Color(0xFFB9E4FF)
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
    val system: Color = SystemBlue,
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
    val statInt: Color = SystemBlue,
    val statWil: Color = Color(0xFFA97BFF),
    val statVit: Color = Verified,
    val statCha: Color = Color(0xFFFFB3D1),
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
    const val EDGE = 0.70f
    const val GLOW = 0.18f
    const val RADAR_FILL = 0.25f
    const val DIM_SCRIM = 0.90f
    val HEATMAP_STEPS = listOf(0.08f, 0.30f, 0.50f, 0.72f, 1.0f)
}

val LocalAwakenColors = staticCompositionLocalOf { AwakenColors() }
