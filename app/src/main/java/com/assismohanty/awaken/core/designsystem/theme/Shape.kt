package com.assismohanty.awaken.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shapes (DESIGN_SYSTEM §2.3): soft rounded cards, fully rounded pills and buttons. Screens never hard-code dp. */
@Immutable
data class AwakenShapes(
    val cardRadius: Dp = 20.dp,
    /** Cards (the System Window became a card in v1.1). */
    val window: Shape = RoundedCornerShape(cardRadius),
    /** Wells and tiles inside a card. */
    val inner: Shape = RoundedCornerShape(14.dp),
    val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val button: Shape = CircleShape,
    val pill: Shape = CircleShape,
    /** Small squares: swatches, icon wells, calendar cells. */
    val tile: Shape = RoundedCornerShape(8.dp),
    val xpBar: Shape = CircleShape,
)

@Immutable
data class AwakenDimens(
    val edgeWidth: Dp = 1.dp,
    /** Selected outlines and the System-mode accent edge. */
    val accentWidth: Dp = 1.5.dp,
    val hairline: Dp = 1.dp,
    val touchTarget: Dp = 48.dp,
    val buttonHeight: Dp = 52.dp,
    val xpBarHeight: Dp = 8.dp,
    val xpBarLeadGlow: Dp = 24.dp,
    val iconSmall: Dp = 16.dp,
    val icon: Dp = 24.dp,
    /** Icon well behind a stat glyph or a header action. */
    val iconWell: Dp = 40.dp,
    val sigil: Dp = 28.dp,
    val avatar: Dp = 44.dp,
    val crestSmall: Dp = 40.dp,
    val crestLarge: Dp = 64.dp,
    /** The hero ring: one big number per screen (DESIGN_SYSTEM §3). */
    val heroRing: Dp = 216.dp,
    val heroRingStroke: Dp = 14.dp,
    val heroDot: Dp = 7.dp,
    /** Today's vertical timeline. */
    val timelineRail: Dp = 2.dp,
    val timelineNode: Dp = 26.dp,
    val radarSize: Dp = 260.dp,
    val radarStroke: Dp = 2.dp,
    val heatmapCell: Dp = 12.dp,
    val heatmapGap: Dp = 3.dp,
    val gateDialSize: Dp = 280.dp,
    val gateDialRing: Dp = 14.dp,
    val gateDialInnerRing: Dp = 3.dp,
    val gateTileHeight: Dp = 128.dp,
    val ceremonyRing: Dp = 3.dp,
    val questRowPadding: Dp = 14.dp,
    val chipHeight: Dp = 28.dp,
    val toastMaxWidth: Dp = 420.dp,
    val shareCardWidth: Dp = 360.dp,
    val shareCardHeight: Dp = 450.dp,
    /** How far a staggered item rises while it fades in (DS §2.5). */
    val staggerRise: Dp = 8.dp,
    /** Bottom bar: the selected item's soft pill behind its icon. */
    val navIndicatorWidth: Dp = 56.dp,
    val navIndicatorHeight: Dp = 30.dp,
    /** Screen Time 7-day bars (DS §4.9). */
    val trendBarHeight: Dp = 96.dp,
    /** Health 7-day columns: the area the tallest bar fills. */
    val columnChartHeight: Dp = 72.dp,
    /** Line charts in trend cards. */
    val lineChartHeight: Dp = 140.dp,
    /** Sleep-stage timeline bar. */
    val stageBarHeight: Dp = 18.dp,
    /** Gym body map: front + back figures side by side (DS §3). */
    val bodyMapHeight: Dp = 260.dp,
    /** Diet calorie ring and its stroke. */
    val calorieRing: Dp = 148.dp,
    val calorieRingStroke: Dp = 12.dp,
    /** Number fields in set rows and nutrient forms. */
    val numberField: Dp = 64.dp,
    /** System-mode glow radius around cards. */
    val glow: Dp = 18.dp,
)

val LocalAwakenShapes = staticCompositionLocalOf { AwakenShapes() }
val LocalAwakenDimens = staticCompositionLocalOf { AwakenDimens() }
