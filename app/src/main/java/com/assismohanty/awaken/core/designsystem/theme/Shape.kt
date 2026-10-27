package com.assismohanty.awaken.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shapes (DESIGN_SYSTEM §2.3): 24dp cards with no border or shadow, fully rounded pills and buttons. Screens never hard-code dp. */
@Immutable
data class AwakenShapes(
    val cardRadius: Dp = 24.dp,
    /** Cards. */
    val window: Shape = RoundedCornerShape(cardRadius),
    /** Wells and tiles inside a card. */
    val inner: Shape = RoundedCornerShape(16.dp),
    val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val button: Shape = CircleShape,
    val pill: Shape = CircleShape,
    /** Small squares: swatches, icon wells, calendar cells. */
    val tile: Shape = RoundedCornerShape(10.dp),
    val xpBar: Shape = CircleShape,
)

@Immutable
data class AwakenDimens(
    val edgeWidth: Dp = 1.dp,
    /** Selected outlines. */
    val accentWidth: Dp = 1.5.dp,
    val hairline: Dp = 1.dp,
    val touchTarget: Dp = 48.dp,
    val buttonHeight: Dp = 52.dp,
    val xpBarHeight: Dp = 8.dp,
    /** The consistency meter track (DESIGN_SYSTEM §8.1). */
    val meterHeight: Dp = 6.dp,
    /** The two-bar comparison inside an insight card (DESIGN_SYSTEM §8.1). */
    val insightChartHeight: Dp = 56.dp,
    val iconSmall: Dp = 16.dp,
    val icon: Dp = 24.dp,
    /** The filled circle behind a list-row icon (36dp, stat colour at 15%). */
    val iconWell: Dp = 36.dp,
    val sigil: Dp = 28.dp,
    val avatar: Dp = 44.dp,
    val crestSmall: Dp = 40.dp,
    val crestLarge: Dp = 64.dp,
    /** Ring cards (Apple Fitness style): thin rings, round caps, divider-coloured track (DESIGN_SYSTEM §3). */
    val heroRing: Dp = 132.dp,
    val heroRingStroke: Dp = 10.dp,
    val heroDot: Dp = 7.dp,
    /** Today's vertical timeline. */
    val timelineRail: Dp = 2.dp,
    val timelineNode: Dp = 26.dp,
    val radarSize: Dp = 260.dp,
    val radarStroke: Dp = 2.dp,
    val heatmapCell: Dp = 12.dp,
    val heatmapGap: Dp = 3.dp,
    val gateDialSize: Dp = 264.dp,
    val gateDialRing: Dp = 10.dp,
    val gateDialInnerRing: Dp = 3.dp,
    val gateTileHeight: Dp = 128.dp,
    val ceremonyRing: Dp = 3.dp,
    val questRowPadding: Dp = 12.dp,
    val chipHeight: Dp = 24.dp,
    val toastMaxWidth: Dp = 420.dp,
    val shareCardWidth: Dp = 360.dp,
    val shareCardHeight: Dp = 450.dp,
    /** How far a staggered item rises while it fades in (DS §2.5). */
    val staggerRise: Dp = 8.dp,
    /** Floating pill nav bar: its height, and the lighter inner pill of the selected item. */
    val navBarHeight: Dp = 64.dp,
    val navIndicatorHeight: Dp = 48.dp,
    /** Large title → collapsed title (DS §3, LargeTitleScaffold). */
    val topBarHeight: Dp = 56.dp,
    /** Skeleton loader line height. */
    val skeletonLine: Dp = 14.dp,
    /** Sparkline / mini-bars at the right of a metric card. */
    val sparkWidth: Dp = 96.dp,
    val sparkHeight: Dp = 40.dp,
    /** Segmented control track. */
    val segmentedHeight: Dp = 36.dp,
    /** Screen Time 7-day bars (DS §4.9). */
    val trendBarHeight: Dp = 96.dp,
    /** Health 7-day columns: the area the tallest bar fills. */
    val columnChartHeight: Dp = 72.dp,
    /** Line charts in trend cards. */
    val lineChartHeight: Dp = 140.dp,
    /** Sleep-stage timeline bar. */
    val stageBarHeight: Dp = 18.dp,
    /** Gym body map: one figure, front or back (DS §3). */
    val bodyMapHeight: Dp = 420.dp,
    /** Diet calorie ring and its stroke. */
    val calorieRing: Dp = 148.dp,
    val calorieRingStroke: Dp = 10.dp,
    /** Number fields in set rows and nutrient forms. */
    val numberField: Dp = 64.dp,
)

val LocalAwakenShapes = staticCompositionLocalOf { AwakenShapes() }
val LocalAwakenDimens = staticCompositionLocalOf { AwakenDimens() }
