package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.ConsistencyScore
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat

/*
 * The v2 modules (DESIGN_SYSTEM §3): Apple-style metric cards, ring cards, insight cards, list rows, segmented
 * controls and delta pills. One primary thing per card; hierarchy from type size and weight; one accent per card.
 */

// ---- Rings ----

/** One ring of a [Rings] stack: its 0..1 [progress] and accent [color]. */
@Immutable
data class RingSpec(val progress: Float, val color: Color)

/**
 * Apple Fitness–style rings: concentric, 10dp stroke, round caps, a divider-coloured track. Each ring closes from 0 on
 * first composition (300ms ease-out); reduce-motion draws it closed. Decorative: the caller describes it.
 */
@Composable
fun Rings(rings: List<RingSpec>, modifier: Modifier = Modifier, size: Dp = AwakenTheme.dimens.heroRing, stroke: Dp = AwakenTheme.dimens.heroRingStroke) {
    val colors = AwakenTheme.colors
    val motion = AwakenTheme.motion
    val grow = remember { Animatable(if (motion.reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { if (grow.value < 1f) grow.animateTo(1f, tween(motion.countUpMs, easing = motion.standardEasing)) }
    val targets = rings.map { it.progress.coerceIn(0f, 1f) }
    Canvas(modifier = modifier.size(size)) {
        val w = stroke.toPx()
        val gap = w * RING_GAP
        rings.forEachIndexed { i, ring ->
            val inset = w / 2f + i * (w + gap)
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            if (arcSize.width <= 0f) return@forEachIndexed
            drawArc(colors.divider, 0f, FULL_TURN, false, Offset(inset, inset), arcSize, style = Stroke(w))
            val sweep = FULL_TURN * targets[i] * grow.value
            if (sweep > 0f) {
                drawArc(ring.color, START_ANGLE, sweep.coerceAtLeast(MIN_SWEEP), false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
    }
}

/**
 * The ring card (DESIGN_SYSTEM §3): a label, the big count with its unit ("3 of 10"), a secondary line, and the rings on
 * the right. One TalkBack node: [description].
 */
@Composable
fun RingCard(
    label: String,
    value: String,
    rings: List<RingSpec>,
    description: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    secondary: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = AwakenTheme.colors.textPrimary,
    onClick: (() -> Unit)? = null,
) {
    val colors = AwakenTheme.colors
    AwakenCard(modifier = modifier.clearAndSetSemantics { contentDescription = description }, reveal = false, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                CardLabel(label, icon, iconTint)
                BigValue(value, unit, large = true)
                secondary?.let { Text(it, style = AwakenTheme.type.body, color = colors.textSecondary) }
            }
            Spacer(Modifier.width(Spacing.md))
            Rings(rings)
        }
    }
}

/** The pre-v2 hero ring, now a single thin ring with the number inside. [colors] picks the arc colour (first entry). */
@Composable
fun HeroRing(
    value: String,
    progress: Float,
    description: String,
    modifier: Modifier = Modifier,
    unitLine: String? = null,
    kicker: String? = null,
    @Suppress("UNUSED_PARAMETER") style: HeroStyle = HeroStyle.RING,
    colors: List<Color>? = null,
) {
    val tokens = AwakenTheme.colors
    val type = AwakenTheme.type
    Box(modifier = modifier.clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Rings(listOf(RingSpec(progress, colors?.firstOrNull() ?: tokens.positive)), size = AwakenTheme.dimens.calorieRing)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            kicker?.let { Text(it, style = type.caption, color = tokens.textSecondary) }
            Text(value, style = type.metricL, color = tokens.textPrimary)
            unitLine?.let { Text(it, style = type.caption, color = tokens.textSecondary) }
        }
    }
}

enum class HeroStyle { RING, DOTS }

// ---- Metric card ----

/**
 * Metric card (DESIGN_SYSTEM §3, Withings "Latest measurements"): icon + label top-left, [timestamp] + chevron top-right
 * (the chevron when [onClick] is set), the big [value] with its [unit], a [secondary] line or [delta], and an optional
 * [spark] chart on the right. One TalkBack node: [description].
 */
@Composable
fun MetricCard(
    label: String,
    value: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = AwakenTheme.colors.textPrimary,
    unit: String? = null,
    timestamp: String? = null,
    secondary: String? = null,
    delta: (@Composable () -> Unit)? = null,
    spark: Spark? = null,
    large: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = AwakenTheme.colors
    AwakenCard(modifier = modifier.clearAndSetSemantics { contentDescription = description }, reveal = false, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel(label, icon, iconTint, Modifier.weight(1f))
            timestamp?.let { Text(it, style = AwakenTheme.type.caption, color = colors.textSecondary, maxLines = 1) }
            if (onClick != null) {
                Icon(AwakenGlyphs.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(AwakenTheme.dimens.iconSmall))
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                BigValue(value, unit, large = large)
                secondary?.let { Text(it, style = AwakenTheme.type.caption, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                delta?.invoke()
            }
            spark?.let { SparkChart(it, iconTint) }
        }
    }
}

/** A tiny chart at the right of a metric card: a line of [values], or mini [bars]. Values are scaled to their own max. */
@Immutable
data class Spark(val values: List<Float>, val bars: Boolean = false)

@Composable
private fun SparkChart(spark: Spark, color: Color) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    Canvas(modifier = Modifier.width(dimens.sparkWidth).height(dimens.sparkHeight)) {
        val values = spark.values
        if (values.isEmpty()) return@Canvas
        val max = values.max().coerceAtLeast(MIN_SPARK_MAX)
        if (spark.bars) {
            val slot = size.width / values.size
            val barWidth = slot * BAR_FILL
            values.forEachIndexed { i, v ->
                val h = (v / max * size.height).coerceAtLeast(barWidth)
                drawRoundRect(
                    color = if (v > 0f) color else colors.divider,
                    topLeft = Offset(i * slot + (slot - barWidth) / 2f, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
        } else {
            val step = if (values.size > 1) size.width / (values.size - 1) else 0f
            val path = Path()
            values.forEachIndexed { i, v ->
                val p = Offset(i * step, size.height - v / max * size.height)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, color, style = Stroke(SPARK_STROKE.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color, radius = SPARK_DOT.toPx(), center = Offset((values.size - 1) * step, size.height - values.last() / max * size.height))
        }
    }
}

/** Icon + label, the top-left of every card. */
@Composable
private fun CardLabel(label: String, icon: ImageVector?, tint: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        icon?.let { Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(AwakenTheme.dimens.iconSmall + Spacing.xxs)) }
        Text(label, style = AwakenTheme.type.windowTitle, color = AwakenTheme.colors.textPrimary)
    }
}

/** "82.0 kg": the number in the metric style, the unit smaller and secondary on the same baseline. */
@Composable
fun BigValue(value: String, unit: String?, modifier: Modifier = Modifier, large: Boolean = false) {
    val type = AwakenTheme.type
    val colors = AwakenTheme.colors
    val number = if (large) type.metricXL else type.metricL
    Text(
        text = buildAnnotatedString {
            withStyle(number.toSpanStyle()) { append(value) }
            if (unit != null) {
                append(" ")
                withStyle(type.metricUnit.toSpanStyle().copy(color = colors.textSecondary)) { append(unit) }
            }
        },
        style = number,
        color = colors.textPrimary,
        modifier = modifier,
    )
}

// ---- Insight card ----

/** "Highlights" (DESIGN_SYSTEM §3): one sentence, a small accent icon, a chevron when it opens something. */
@Composable
fun InsightCard(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = AwakenGlyphs.Sparkle,
    tint: Color = AwakenTheme.colors.reward,
    onClick: (() -> Unit)? = null,
    /** A ChartKit mini comparison under the sentence (DESIGN_SYSTEM §8.1); it carries its own description. */
    chart: ChartSpec? = null,
    caption: String? = null,
) {
    val colors = AwakenTheme.colors
    AwakenCard(modifier = modifier.semantics(mergeDescendants = true) {}, reveal = false, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            IconCircle(icon, tint)
            Text(text, style = AwakenTheme.type.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
            if (onClick != null) {
                Icon(AwakenGlyphs.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(AwakenTheme.dimens.iconSmall))
            }
        }
        chart?.let { Chart(it, Modifier.fillMaxWidth()) }
        caption?.let { Text(it, style = AwakenTheme.type.caption, color = colors.textSecondary) }
    }
}

/** The calm line Today shows instead of the System line the day after a missed day (DESIGN_SYSTEM §8.1): no icon, no alarm colour. */
@Composable
fun NeverMissTwiceLine(text: String, modifier: Modifier = Modifier) {
    AwakenCard(modifier = modifier.fillMaxWidth(), reveal = false) {
        Text(text, style = AwakenTheme.type.body, color = AwakenTheme.colors.textSecondary)
    }
}

/** A 36dp filled circle at 15% of [tint] with the icon in [tint]: the leading mark of list rows and insights. */
@Composable
fun IconCircle(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = AwakenTheme.dimens.iconWell) {
    Box(
        modifier = modifier
            .size(size)
            .clip(AwakenTheme.shapes.pill)
            .background(tint.copy(alpha = ICON_WASH)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * ICON_IN_CIRCLE))
    }
}

// ---- List row ----

/**
 * List row (DESIGN_SYSTEM §3): leading [IconCircle], title (17sp), secondary line, trailing control. The whole row is
 * one target when [onClick] is set. Use inside a card; rows separate by spacing, not lines.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = AwakenTheme.colors.textPrimary,
    secondary: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = AwakenTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget + Spacing.xs)
            .then(if (onClick != null) Modifier.clip(AwakenTheme.shapes.inner).clickable(onClick = onClick) else Modifier)
            .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        icon?.let { IconCircle(it, iconTint) }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs / 2)) {
            Text(title, style = AwakenTheme.type.windowTitle, color = colors.textPrimary)
            secondary?.let { Text(it, style = AwakenTheme.type.caption, color = colors.textSecondary) }
        }
        trailing?.invoke(this)
    }
}

// ---- Small pieces ----

/** Small sentence-case label over a value ("Result · High"). */
@Composable
fun MetricLabel(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null, large: Boolean = false) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    Column(modifier = modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(label, style = type.overline, color = colors.textSecondary)
        Text(value, style = if (large) type.metricL else type.numeric, color = valueColor ?: colors.textPrimary)
    }
}

enum class Trend { UP, DOWN, FLAT }

/** Delta pill (Withings "+0.0 kg"): a 15% wash of positive / negative / neutral, an arrow, ink text. */
@Composable
fun TrendPill(text: String, trend: Trend, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val color = when (trend) {
        Trend.UP -> colors.positive
        Trend.DOWN -> colors.critical
        Trend.FLAT -> colors.textSecondary
    }
    Pill(
        text = text,
        color = color,
        icon = when (trend) {
            Trend.UP -> AwakenGlyphs.ArrowUp
            Trend.DOWN -> AwakenGlyphs.ArrowDown
            Trend.FLAT -> AwakenGlyphs.ChevronRight
        },
        modifier = modifier,
    )
}

/** The v2 name for [TrendPill]. */
@Composable
fun DeltaPill(text: String, trend: Trend, modifier: Modifier = Modifier) = TrendPill(text, trend, modifier)

/**
 * Segmented control (Week / Month / Year, Front / Back): a pressed-colour track with the selected segment in the card
 * colour. Each segment is a 48dp tab.
 */
@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val shape = AwakenTheme.shapes.pill
    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.surfaceOverlay)
            .padding(Spacing.xxs / 2)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = AwakenTheme.dimens.segmentedHeight)
                    .clip(shape)
                    .background(if (isSelected) colors.surfaceSelected else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = Spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(option, style = AwakenTheme.type.label, color = if (isSelected) colors.textPrimary else colors.textSecondary, maxLines = 1)
            }
        }
    }
}

/** The v2 name for [SegmentedTabs]. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) =
    SegmentedTabs(options, selected, onSelect, modifier)

/** A stat card: its glyph in a tinted circle, the name and a short status. Card colour, never a stat wash. */
@Composable
fun StatTintCard(stat: Stat, title: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val colors = AwakenTheme.colors
    AwakenCard(modifier = modifier.semantics(mergeDescendants = true) {}, reveal = false, onClick = onClick) {
        IconCircle(AwakenGlyphs.stat(stat), colors.stat(stat))
        Text(title, style = AwakenTheme.type.windowTitle, color = colors.textPrimary)
        Text(value, style = AwakenTheme.type.caption, color = colors.textSecondary)
    }
}

/**
 * The large title ("Good morning, Sam", 34sp bold) at full width, under a row with the quiet [subtitle] (the date) on
 * the left and the header [actions] on the right, so the title never shares its line.
 */
@Composable
fun GreetingHeader(
    greeting: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = AwakenTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                BackButton(onBack)
                Spacer(Modifier.weight(1f))
            } else {
                Text(subtitle.orEmpty(), style = AwakenTheme.type.label, color = colors.textSecondary, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), content = actions)
        }
        if (onBack != null && subtitle != null) {
            Text(subtitle, style = AwakenTheme.type.label, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.xs))
        }
        Text(greeting, style = AwakenTheme.type.headline, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
    }
}

/** Back: an arrow on a card-coloured circle, top-left of every pushed screen. */
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) =
    HeaderIconButton(AwakenGlyphs.ArrowLeft, lexString(R.string.action_back), onBack, modifier)

/** A round header action (Gates, Vault, settings): an ink icon on a card-coloured circle, 48dp target. */
@Composable
fun HeaderIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    IconButton(onClick = onClick, modifier = modifier.size(AwakenTheme.dimens.touchTarget)) {
        Box(
            modifier = Modifier
                .size(AwakenTheme.dimens.touchTarget - Spacing.xxs)
                .clip(AwakenTheme.shapes.pill)
                .background(colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = colors.textPrimary, modifier = Modifier.size(AwakenTheme.dimens.icon * ICON_IN_BUTTON))
        }
    }
}

/** A section heading on a screen ("Morning", 22sp semibold) with an optional trailing caption. */
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier = modifier.fillMaxWidth().padding(top = Spacing.md, bottom = Spacing.xxs), verticalAlignment = Alignment.Bottom) {
        Text(title, style = AwakenTheme.type.section, color = AwakenTheme.colors.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.let { Text(it, style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary) }
    }
}

// ---- Time blocks (Today, GD §14.5) ----

enum class TimeBlock { MORNING, DAY, EVENING, ANYTIME }

enum class BlockState { PAST, CURRENT, UPCOMING }

/**
 * Header of a time block on Today (DESIGN_SYSTEM §8.1): the block name as a section title, "2 of 3 done", a "Now" chip
 * on the current block and a chevron. One 48dp toggle with its state announced.
 */
@Composable
fun TimeBlockHeader(
    block: TimeBlock,
    done: Int,
    total: Int,
    state: BlockState,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val name = lexString(block.nameRes)
    val progress = lexString(R.string.time_block_progress, done, total)
    val stateText = lexString(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget)
            .clickable(onClick = onToggle)
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = stateText
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(name, style = AwakenTheme.type.section, color = if (state == BlockState.PAST) colors.textSecondary else colors.textPrimary)
        if (state == BlockState.CURRENT) {
            Pill(lexString(R.string.time_block_now), colors.textPrimary, background = colors.surfaceElevated)
        }
        Spacer(Modifier.weight(1f))
        Text(progress, style = AwakenTheme.type.caption, color = colors.textSecondary)
        Icon(
            if (expanded) AwakenGlyphs.ChevronDown else AwakenGlyphs.ChevronRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(AwakenTheme.dimens.iconSmall),
        )
    }
}

val TimeBlock.nameRes: Int
    get() = when (this) {
        TimeBlock.MORNING -> R.string.time_block_morning
        TimeBlock.DAY -> R.string.time_block_day
        TimeBlock.EVENING -> R.string.time_block_evening
        TimeBlock.ANYTIME -> R.string.time_block_anytime
    }

val TimeBlock.glyph: ImageVector
    get() = when (this) {
        TimeBlock.MORNING -> AwakenGlyphs.Sunrise
        TimeBlock.DAY -> AwakenGlyphs.Sun
        TimeBlock.EVENING -> AwakenGlyphs.Moon
        TimeBlock.ANYTIME -> AwakenGlyphs.Clock
    }

// ---- Level and streak ----

/** Level as a metric card: the number large, XP toward the next level and a slim bar. */
@Composable
fun LevelModule(level: Int, rank: Rank, xpIntoLevel: Long, xpForLevel: Long, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val colors = AwakenTheme.colors
    val description = lexString(R.string.cd_level_module, level, rank.name, formatCount(xpIntoLevel), formatCount(xpForLevel))
    AwakenCard(modifier = modifier.clearAndSetSemantics { contentDescription = description }, reveal = false, onClick = onClick) {
        CardLabel(lexString(R.string.metric_level), AwakenGlyphs.Level, colors.reward)
        BigValue(formatCount(level), unit = null)
        Text(lexString(R.string.rank_line, rank.name), style = AwakenTheme.type.caption, color = colors.textSecondary)
        Spacer(Modifier.weight(1f))
        XpBar(level = level, xpIntoLevel = xpIntoLevel, xpForLevel = xpForLevel, showLabel = false)
        Text(lexString(R.string.xp_progress, formatCount(xpIntoLevel), formatCount(xpForLevel)), style = AwakenTheme.type.caption, color = colors.textSecondary)
    }
}

/** The streak as a metric card: flame, the count large with "days", the best underneath. */
@Composable
fun StreakModule(
    streak: Int,
    best: Int,
    modifier: Modifier = Modifier,
    consistency: ConsistencyScore? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = AwakenTheme.colors
    val description = lexString(R.string.cd_streak_module, streak, best)
    AwakenCard(modifier = modifier.clearAndSetSemantics { contentDescription = description }, reveal = false, onClick = onClick) {
        CardLabel(lexString(R.string.metric_streak), AwakenGlyphs.Flame, colors.stat(Stat.CHA))
        BigValue(formatCount(streak), unit = null)
        Text(lexString(R.string.unit_days_line), style = AwakenTheme.type.caption, color = colors.textSecondary)
        Spacer(Modifier.weight(1f))
        consistency?.let { ConsistencyMeter(it, Modifier.padding(bottom = Spacing.xs)) }
        Text(lexString(R.string.best_streak_label, best), style = AwakenTheme.type.caption, color = colors.textSecondary)
    }
}

/**
 * "22 of the last 28 days" over a thin track filled to the ratio (DESIGN_SYSTEM §8.1, GD §14.2). One colour whatever
 * the value: no bands, no good/bad. No eligible day reads "—" over an empty track.
 */
@Composable
fun ConsistencyMeter(score: ConsistencyScore, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val description = if (score.eligible == 0) {
        lexString(R.string.consistency_none_cd)
    } else {
        lexPlural(R.plurals.consistency_cd, score.eligible, score.cleared, score.eligible)
    }
    Column(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            text = if (score.eligible == 0) lexString(R.string.consistency_none) else lexPlural(R.plurals.consistency_line, score.eligible, score.cleared, score.eligible),
            style = AwakenTheme.type.caption,
            color = colors.textSecondary,
        )
        Chart(
            ChartSpec.Progress(
                progress = score.ratio?.toFloat() ?: 0f,
                color = colors.accent,
                track = colors.surfaceSunken,
                height = AwakenTheme.dimens.meterHeight,
            ),
        )
    }
}

// ---- Skeleton ----

/** Skeleton loader: quiet static blocks in the pressed colour where content will land. Never pulses. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier, lines: Int = 3) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    AwakenCard(modifier = modifier.clearAndSetSemantics { }, reveal = false) {
        repeat(lines) { i ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (i == 0) SKELETON_FIRST else if (i == lines - 1) SKELETON_LAST else 1f)
                    .height(if (i == 0) dimens.skeletonLine * 2 else dimens.skeletonLine)
                    .clip(AwakenTheme.shapes.pill)
                    .background(colors.surfaceOverlay),
            )
        }
    }
}

/** Fills the available space with a ring track; used while a ring card's numbers load. */
@Composable
fun SkeletonRing(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) { Rings(emptyList()) }
}

private const val FULL_TURN = 360f
private const val START_ANGLE = -90f
private const val MIN_SWEEP = 0.5f
/** Space between concentric rings, as a fraction of the stroke. */
private const val RING_GAP = 0.25f
private const val ICON_WASH = 0.15f
private const val ICON_IN_CIRCLE = 0.5f
private const val ICON_IN_BUTTON = 0.85f
private const val BAR_FILL = 0.55f
private const val MIN_SPARK_MAX = 0.0001f
private val SPARK_STROKE = Spacing.xxs / 2
private val SPARK_DOT = Spacing.xxs * 0.75f
private const val SKELETON_FIRST = 0.4f
private const val SKELETON_LAST = 0.6f

@ComponentPreview
@Composable
private fun RingCardPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            RingCard(
                label = "Today",
                value = "3",
                unit = "of 10",
                secondary = "done today",
                rings = listOf(RingSpec(0.3f, AwakenTheme.colors.positive)),
                description = "3 of 10 done today",
            )
            RingCard(
                label = "Stats",
                value = "61",
                rings = Stat.entries.take(3).mapIndexed { i, s -> RingSpec(0.4f + i * 0.2f, AwakenTheme.colors.stat(s)) },
                description = "",
            )
        }
    }
}

@ComponentPreview
@Composable
private fun ModulesPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            GreetingHeader(greeting = "Good morning, Assis", subtitle = "Tuesday, 27 October") {
                HeaderIconButton(AwakenGlyphs.Portal, "Open focus sessions", onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                LevelModule(level = 24, rank = Rank.C, xpIntoLevel = 1_240, xpForLevel = 1_434, modifier = Modifier.weight(1f))
                StreakModule(streak = 12, best = 30, consistency = ConsistencyScore(22, 28), modifier = Modifier.weight(1f))
            }
            InsightCard("INT is falling behind. One short study block puts it back on track.", onClick = {})
            NeverMissTwiceLine("Missed yesterday. Don't miss twice.")
            AwakenCard(reveal = false) { ConsistencyMeter(ConsistencyScore(0, 0)) }
            MetricCard(
                label = "Steps",
                value = "8,412",
                timestamp = "10:50",
                secondary = "84% of goal",
                icon = AwakenGlyphs.Vitality,
                iconTint = AwakenTheme.colors.stat(Stat.VIT),
                spark = Spark(listOf(2f, 5f, 3f, 6f, 4f, 7f, 8f), bars = true),
                description = "",
                onClick = {},
            )
            MetricCard(
                label = "Body weight",
                value = "82.0",
                unit = "kg",
                timestamp = "03:52",
                icon = AwakenGlyphs.Strength,
                iconTint = AwakenTheme.colors.stat(Stat.STR),
                delta = { DeltaPill("+0.0 kg", Trend.FLAT) },
                spark = Spark(listOf(82.4f, 82.1f, 82.3f, 82f, 82f)),
                description = "",
                onClick = {},
            )
            SegmentedControl(listOf("Week", "Month", "Year"), selected = 0, onSelect = {})
            AwakenCard(reveal = false) {
                ListRow("Morning run", icon = AwakenGlyphs.Vitality, iconTint = AwakenTheme.colors.stat(Stat.VIT), secondary = "30 min · +40 XP")
                ListRow("Read 20 pages", icon = AwakenGlyphs.Intelligence, iconTint = AwakenTheme.colors.stat(Stat.INT), secondary = "+25 XP")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TrendPill("+2", Trend.UP)
                TrendPill("−1", Trend.DOWN)
                TrendPill("0", Trend.FLAT)
            }
            TimeBlockHeader(TimeBlock.MORNING, done = 2, total = 3, state = BlockState.PAST, expanded = false, onToggle = {})
            TimeBlockHeader(TimeBlock.DAY, done = 0, total = 2, state = BlockState.CURRENT, expanded = true, onToggle = {})
            SkeletonCard()
        }
    }
}
