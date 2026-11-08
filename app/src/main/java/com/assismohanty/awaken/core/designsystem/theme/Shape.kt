package com.assismohanty.awaken.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shapes (DESIGN_SYSTEM §2.3, Plates): 22dp cards with no border, 20dp tiles, 28dp sheets, fully rounded pills and buttons. Screens never hard-code dp. */
@Immutable
data class AwakenShapes(
    val cardRadius: Dp = 22.dp,
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
    /** A column in a bar chart: 4dp rounded tops, flat on the baseline. */
    val columnBar: Shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
    /** A summary tile on Today and Focus. */
    val summaryTile: Shape = RoundedCornerShape(20.dp),
    /** A barbell plate on the day bar. */
    val plate: Shape = RoundedCornerShape(9.dp),
)

@Immutable
data class AwakenDimens(
    val edgeWidth: Dp = 1.dp,
    /** Selected outlines. */
    val accentWidth: Dp = 1.5.dp,
    val hairline: Dp = 1.dp,
    val touchTarget: Dp = 48.dp,
    val buttonHeight: Dp = 56.dp,
    val xpBarHeight: Dp = 8.dp,
    /** The consistency meter track (DESIGN_SYSTEM §8.1). */
    val meterHeight: Dp = 6.dp,
    /** The two-bar comparison inside an insight card (DESIGN_SYSTEM §8.1). */
    val insightChartHeight: Dp = 56.dp,
    /** A recap story page (DESIGN_SYSTEM §8.1 RecapCard). */
    val recapCardMinHeight: Dp = 360.dp,
    val iconSmall: Dp = 16.dp,
    val icon: Dp = 24.dp,
    /** The filled circle behind a list-row icon (36dp, stat colour at 15%). */
    val iconWell: Dp = 36.dp,
    val sigil: Dp = 28.dp,
    val avatar: Dp = 44.dp,
    val crestSmall: Dp = 36.dp,
    val crestLarge: Dp = 96.dp,
    /** The rank badge beside "Rank" in the level module. */
    val crestInline: Dp = 28.dp,
    /** Ring cards (Apple Fitness style): thin rings, round caps, divider-coloured track (DESIGN_SYSTEM §3). */
    val heroRing: Dp = 132.dp,
    val heroRingStroke: Dp = 10.dp,
    val heroDot: Dp = 7.dp,
    /** The level ring (Today, Progress) and its stroke. */
    val levelRing: Dp = 76.dp,
    val levelRingStroke: Dp = 7.dp,
    /** The large ring at the top of Progress. */
    val levelRingHero: Dp = 136.dp,
    val levelRingHeroStroke: Dp = 10.dp,
    /** The round stat marker beside a stat's name. */
    val statMarker: Dp = 10.dp,
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
    /** Onboarding (docs/onboarding/PLAN.md §6): progress segments, picker rows, ruler ticks, hero art. */
    val onbSegmentHeight: Dp = 4.dp,
    val onbSegmentGap: Dp = 4.dp,
    val onbWheelRow: Dp = 48.dp,
    val onbWheelRows: Int = 5,
    val onbRulerSpan: Dp = 120.dp,
    val onbRulerTick: Dp = 12.dp,
    val onbRulerMajorTick: Dp = 28.dp,
    val onbRulerTickGap: Dp = 10.dp,
    val onbRulerNeedle: Dp = 30.dp,
    val onbBmiBadge: Dp = 14.dp,
    val onbBmiBar: Dp = 10.dp,
    val onbLogo: Dp = 40.dp,
    val onbCardImage: Dp = 96.dp,
    val onbCheck: Dp = 24.dp,
    /** The body map beside the injury options, and one day chip. */
    val onbBodyMapHeight: Dp = 232.dp,
    val onbDayChip: Dp = 40.dp,
    /** The picture on a permission screen. */
    val onbPermissionImage: Dp = 180.dp,
    /** The building screen's ring. */
    val onbPlanRing: Dp = 200.dp,
    /** An image option card: the picture and two lines of label, so a row of cards stays level. */
    val onbCardMinHeight: Dp = 176.dp,
    val onbRulerLabelGap: Dp = 4.dp,
    val toastMaxWidth: Dp = 420.dp,
    val shareCardWidth: Dp = 360.dp,
    val shareCardHeight: Dp = 450.dp,
    /** How far a staggered item rises while it fades in (DS §2.5). */
    val staggerRise: Dp = 8.dp,
    /** The nav bar's selected-tab pill. */
    val navIndicatorWidth: Dp = 60.dp,
    val navIndicatorHeight: Dp = 32.dp,
    /** Large title → collapsed title (DS §3, LargeTitleScaffold). */
    val topBarHeight: Dp = 56.dp,
    /** Skeleton loader line height. */
    val skeletonLine: Dp = 14.dp,
    /** Sparkline / mini-bars at the right of a metric card. */
    val sparkWidth: Dp = 96.dp,
    val sparkHeight: Dp = 40.dp,
    /** Segmented control track. */
    val segmentedHeight: Dp = 42.dp,
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
    /** The plan plate bar on Today: overall height, the rail it sits on, the collar at its end, and a plate's corner. */
    val dayBarHeight: Dp = 64.dp,
    val dayBarRail: Dp = 8.dp,
    val dayBarCollar: Dp = 14.dp,
    val dayBarPlateRadius: Dp = 9.dp,
    val dayBarPlateGap: Dp = 4.dp,
    val dayBarPlateMin: Dp = 10.dp,
    /** Diet calorie ring and its stroke. */
    val calorieRing: Dp = 148.dp,
    val calorieRingStroke: Dp = 10.dp,
    /** The ring inside a half-width Today summary tile (screen time) and its stroke. */
    val tileRing: Dp = 104.dp,
    val tileRingStroke: Dp = 9.dp,
    /** A health-tile figure's minimum width: two side by side only when both get this much. */
    val tileFigureMinWidth: Dp = 64.dp,
    /** Number fields in set rows and nutrient forms. */
    val numberField: Dp = 64.dp,
    /** Width of the control column beside the stopwatch face in landscape. */
    val sideControls: Dp = 220.dp,
)

val LocalAwakenShapes = staticCompositionLocalOf { AwakenShapes() }
val LocalAwakenDimens = staticCompositionLocalOf { AwakenDimens() }
