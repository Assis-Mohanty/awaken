package com.assismohanty.awaken.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shapes and fixed component dimensions (DESIGN_SYSTEM §2.3, §3). Screens never hard-code dp. */
@Immutable
data class AwakenShapes(
    /** Two opposite chamfers — the asymmetric signature of the System Window. */
    val window: Shape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp),
    val button: Shape = CutCornerShape(topEnd = 8.dp),
    val pill: Shape = CircleShape,
    val xpBar: Shape = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
)

@Immutable
data class AwakenDimens(
    val edgeWidth: Dp = 1.dp,
    val glowWidth: Dp = 8.dp,
    val hairline: Dp = 1.dp,
    val touchTarget: Dp = 48.dp,
    val xpBarHeight: Dp = 10.dp,
    val xpBarLeadGlow: Dp = 24.dp,
    val iconSmall: Dp = 16.dp,
    val icon: Dp = 24.dp,
    val sigil: Dp = 28.dp,
    val crestSmall: Dp = 44.dp,
    val crestLarge: Dp = 72.dp,
    val radarSize: Dp = 260.dp,
    val radarStroke: Dp = 1.5.dp,
    val heatmapCell: Dp = 12.dp,
    val heatmapGap: Dp = 3.dp,
    val gateDialSize: Dp = 280.dp,
    val gateDialRing: Dp = 14.dp,
    val gateDialInnerRing: Dp = 3.dp,
    val ceremonyRing: Dp = 2.dp,
    val questRowPadding: Dp = 16.dp,
    val chipHeight: Dp = 24.dp,
    val toastMaxWidth: Dp = 420.dp,
    val shareCardWidth: Dp = 360.dp,
    val shareCardHeight: Dp = 450.dp,
)

val LocalAwakenShapes = staticCompositionLocalOf { AwakenShapes() }
val LocalAwakenDimens = staticCompositionLocalOf { AwakenDimens() }
