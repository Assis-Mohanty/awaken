package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import kotlin.math.cos
import kotlin.math.sin

/*
 * The v1.1 modules (DESIGN_SYSTEM §3): one hero number per screen, calm cards, small uppercase labels over large values.
 */

enum class HeroStyle { RING, DOTS }

/**
 * The hero (DESIGN_SYSTEM §3): a very large number in a progress ring ([HeroStyle.RING]) or a ring of soft pastel dots
 * ([HeroStyle.DOTS], "61 out of 100"). [kicker] sits above the number (a date), [unitLine] under it ("of 5 done").
 * [colors] tint the arc or the dots; defaults to the five stat fills. One TalkBack node: [description].
 */
@Composable
fun HeroRing(
    value: String,
    progress: Float,
    description: String,
    modifier: Modifier = Modifier,
    unitLine: String? = null,
    kicker: String? = null,
    style: HeroStyle = HeroStyle.RING,
    colors: List<Color>? = null,
) {
    val palette = AwakenTheme.palette
    val dimens = AwakenTheme.dimens
    val motion = AwakenTheme.motion
    val type = AwakenTheme.type
    val fills = colors ?: Stat.entries.map(palette::statFill)
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(motion.duration(motion.revealMs), easing = motion.standardEasing), label = "hero")
    Box(
        modifier = modifier
            .size(dimens.heroRing)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (palette.glow) {
                drawCircle(Brush.radialGradient(listOf(palette.system.copy(alpha = AwakenAlpha.GLOW), Color.Transparent)))
            }
            val stroke = dimens.heroRingStroke.toPx()
            when (style) {
                HeroStyle.RING -> {
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(palette.edge, 0f, FULL_TURN, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    if (shown > 0f) {
                        rotate(START_ANGLE.toFloat()) {
                            drawArc(
                                brush = Brush.sweepGradient(fills + fills.first()),
                                startAngle = 0f,
                                sweepAngle = FULL_TURN * shown,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(stroke, cap = StrokeCap.Round),
                            )
                        }
                    }
                }
                HeroStyle.DOTS -> {
                    val dot = dimens.heroDot.toPx()
                    listOf(OUTER_DOTS to 0f, INNER_DOTS to dot * 2.6f).forEach { (count, inward) ->
                        val radius = size.minDimension / 2f - dot - inward
                        val filled = (count * shown).toInt()
                        for (i in 0 until count) {
                            val angle = Math.toRadians(START_ANGLE + i * FULL_TURN / count.toDouble())
                            val center = Offset(
                                x = size.width / 2f + radius * cos(angle).toFloat(),
                                y = size.height / 2f + radius * sin(angle).toFloat(),
                            )
                            // A gentle rhythm of sizes so the ring feels organic, not mechanical.
                            val r = dot / 2f * DOT_SIZES[i % DOT_SIZES.size] * (if (inward > 0f) INNER_SCALE else 1f)
                            drawCircle(if (i < filled) fills[i % fills.size] else palette.edge, radius = r, center = center)
                        }
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            kicker?.let { Text(it, style = type.caption, color = palette.mist) }
            Text(value, style = type.metricXL, color = palette.bone)
            unitLine?.let { Text(it, style = type.label, color = palette.mist, textAlign = TextAlign.Center) }
        }
    }
}

/** A soft two-colour wash behind a screen's hero area (DESIGN_SYSTEM §2.1). Decorative. */
@Composable
fun Modifier.heroWash(): Modifier {
    val a = AwakenTheme.palette.heroWashA
    val b = AwakenTheme.palette.heroWashB
    return heroWash(a, b)
}

private fun Modifier.heroWash(a: Color, b: Color): Modifier = drawBehind {
    drawRect(Brush.radialGradient(listOf(a, Color.Transparent), center = Offset(size.width * 0.15f, 0f), radius = size.width * 0.9f))
    drawRect(Brush.radialGradient(listOf(b, Color.Transparent), center = Offset(size.width * 0.9f, size.height * 0.2f), radius = size.width * 0.8f))
}

/** Small uppercase label over a large value ("RESULT · High"). */
@Composable
fun MetricLabel(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null, large: Boolean = false) {
    val palette = AwakenTheme.palette
    val type = AwakenTheme.type
    Column(modifier = modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(label.uppercase(LocalConfiguration.current.locales[0]), style = type.overline, color = palette.mist)
        Text(value, style = if (large) type.metricL else type.numeric, color = valueColor ?: palette.bone, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

enum class Trend { UP, DOWN, FLAT }

/** A small trend pill: "+3.6 %" with an arrow, green for up, ember for down, grey when flat. */
@Composable
fun TrendPill(text: String, trend: Trend, modifier: Modifier = Modifier) {
    val palette = AwakenTheme.palette
    val color = when (trend) {
        Trend.UP -> palette.verified
        Trend.DOWN -> palette.ember
        Trend.FLAT -> palette.mist
    }
    Pill(
        text = text,
        color = color,
        icon = when (trend) {
            Trend.UP -> AwakenGlyphs.ArrowUp
            Trend.DOWN -> AwakenGlyphs.ArrowDown
            Trend.FLAT -> null
        },
        modifier = modifier,
    )
}

/** Week / Month / Quarter / Year: a soft segmented control. Each option is a 48dp radio. */
@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val palette = AwakenTheme.palette
    val shape = AwakenTheme.shapes.pill
    Row(
        modifier = modifier
            .clip(shape)
            .background(palette.panelRaised)
            .padding(Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget - Spacing.xs)
                    .clip(shape)
                    .background(if (isSelected) palette.panel else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(option, style = AwakenTheme.type.label, color = if (isSelected) palette.bone else palette.mist)
            }
        }
    }
}

/** A stat's card (Withings-style "Following" row): its pastel, glyph well, name and a short status. */
@Composable
fun StatTintCard(stat: Stat, title: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val palette = AwakenTheme.palette
    val shape = AwakenTheme.shapes.window
    Column(
        modifier = modifier
            .clip(shape)
            .background(palette.statTint(stat))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {}
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(AwakenTheme.dimens.iconWell)
                .clip(AwakenTheme.shapes.pill)
                .background(palette.panel.copy(alpha = WELL_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AwakenGlyphs.stat(stat), contentDescription = null, tint = palette.stat(stat), modifier = Modifier.size(AwakenTheme.dimens.icon))
        }
        Text(title, style = AwakenTheme.type.title, color = palette.bone, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = AwakenTheme.type.label, color = palette.stat(stat))
    }
}

/** Screen greeting ("Good afternoon, Assis") with a quiet subtitle and room for header actions. */
@Composable
fun GreetingHeader(greeting: String, modifier: Modifier = Modifier, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val palette = AwakenTheme.palette
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(greeting, style = AwakenTheme.type.headline, color = palette.bone, modifier = Modifier.semantics { heading() })
            subtitle?.let { Text(it, style = AwakenTheme.type.body, color = palette.mist) }
        }
        actions()
    }
}

/** A round header action (Gates, Vault, settings): an icon on a soft well, 48dp target. */
@Composable
fun HeaderIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = AwakenTheme.palette
    IconButton(onClick = onClick, modifier = modifier.size(AwakenTheme.dimens.touchTarget)) {
        Box(
            modifier = Modifier
                .size(AwakenTheme.dimens.iconWell)
                .clip(AwakenTheme.shapes.pill)
                .background(palette.panel)
                .border(AwakenTheme.dimens.hairline, palette.edge, AwakenTheme.shapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = palette.bone, modifier = Modifier.size(AwakenTheme.dimens.icon * ICON_IN_WELL))
        }
    }
}

/** An insight or daily line on a soft lavender-to-peach wash, with a sparkle (DESIGN_SYSTEM §8.1). */
@Composable
fun InsightCard(title: String, body: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val palette = AwakenTheme.palette
    val shape = AwakenTheme.shapes.window
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(palette.heroWashA, palette.heroWashB)))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {}
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(AwakenTheme.dimens.iconWell)
                .clip(AwakenTheme.shapes.pill)
                .background(palette.panel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AwakenGlyphs.Sparkle, contentDescription = null, tint = palette.system, modifier = Modifier.size(AwakenTheme.dimens.icon * ICON_IN_WELL))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(title, style = AwakenTheme.type.label, color = palette.mist)
            Text(body, style = AwakenTheme.type.body, color = palette.bone)
        }
    }
}

enum class TimeBlock { MORNING, DAY, EVENING, ANYTIME }

enum class BlockState { PAST, CURRENT, UPCOMING }

/**
 * Header of a time block on Today's timeline (DESIGN_SYSTEM §8.1): glyph, name, "2 of 3 done" and a chevron. The current
 * block is marked "Now"; past blocks can be collapsed. One 48dp toggle with its state announced.
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
    val palette = AwakenTheme.palette
    val name = lexString(block.nameRes)
    val progress = lexString(R.string.time_block_progress, done, total)
    val stateText = lexString(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    val tint = if (state == BlockState.PAST) palette.mist else palette.bone
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget)
            .clip(AwakenTheme.shapes.inner)
            .clickable(onClick = onToggle)
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = stateText
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(block.glyph, contentDescription = null, tint = if (state == BlockState.CURRENT) palette.system else tint, modifier = Modifier.size(AwakenTheme.dimens.icon))
        Text(name, style = AwakenTheme.type.title, color = tint)
        if (state == BlockState.CURRENT) {
            Pill(lexString(R.string.time_block_now), palette.system)
        }
        Text(progress, style = AwakenTheme.type.label, color = palette.mist, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Icon(
            if (expanded) AwakenGlyphs.ChevronDown else AwakenGlyphs.ChevronRight,
            contentDescription = null,
            tint = palette.mist,
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

/** Level, rank and XP as one compact card: the game, kept small and elegant. */
@Composable
fun LevelModule(level: Int, rank: Rank, xpIntoLevel: Long, xpForLevel: Long, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val palette = AwakenTheme.palette
    val description = lexString(R.string.cd_level_module, level, rank.name, formatCount(xpIntoLevel), formatCount(xpForLevel))
    ModuleSurface(modifier, onClick, description) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RankCrest(rank, AwakenTheme.dimens.crestSmall)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(lexString(R.string.level_long, level), style = AwakenTheme.type.title, color = palette.bone)
                Text(lexString(R.string.xp_progress, formatCount(xpIntoLevel), formatCount(xpForLevel)), style = AwakenTheme.type.caption, color = palette.mist)
            }
        }
        XpBar(level = level, xpIntoLevel = xpIntoLevel, xpForLevel = xpForLevel, showLabel = false)
    }
}

/** The streak as a compact card: flame, the count large, the best underneath. */
@Composable
fun StreakModule(streak: Int, best: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val palette = AwakenTheme.palette
    val description = lexString(R.string.cd_streak_module, streak, best)
    ModuleSurface(modifier, onClick, description) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Box(
                modifier = Modifier
                    .size(AwakenTheme.dimens.crestSmall)
                    .clip(AwakenTheme.shapes.pill)
                    .background(palette.tintStr),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AwakenGlyphs.Flame, contentDescription = null, tint = palette.statStr, modifier = Modifier.size(AwakenTheme.dimens.icon))
            }
            Column {
                Text(formatCount(streak), style = AwakenTheme.type.numeric, color = palette.bone)
                Text(lexString(R.string.streak_label), style = AwakenTheme.type.caption, color = palette.mist)
            }
        }
        Text(lexString(R.string.best_streak_label, best), style = AwakenTheme.type.caption, color = palette.mist)
    }
}

@Composable
private fun ModuleSurface(modifier: Modifier, onClick: (() -> Unit)?, description: String, content: @Composable () -> Unit) {
    val palette = AwakenTheme.palette
    val shape = AwakenTheme.shapes.window
    Column(
        modifier = modifier
            .clip(shape)
            .background(palette.panel)
            .border(AwakenTheme.dimens.hairline, if (palette.glow) palette.system.copy(alpha = AwakenAlpha.GLOW) else palette.edge, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .clearAndSetSemantics { contentDescription = description }
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        content()
    }
}

private const val FULL_TURN = 360f
private const val START_ANGLE = -90.0
private const val OUTER_DOTS = 72
private const val INNER_DOTS = 54
private const val INNER_SCALE = 0.75f
private val DOT_SIZES = floatArrayOf(1f, 0.7f, 0.85f, 0.6f, 0.95f)
private const val WELL_ALPHA = 0.7f
private const val ICON_IN_WELL = 0.85f

@ComponentPreview
@Composable
private fun HeroRingPreview() {
    AwakenTheme {
        Row(modifier = Modifier.padding(Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            HeroRing(value = "3", unitLine = "of 5 done", kicker = "Today", progress = 0.6f, description = "3 of 5 done", modifier = Modifier.weight(1f))
        }
    }
}

@ComponentPreview
@Composable
private fun HeroDotsPreview() {
    AwakenTheme {
        Box(modifier = Modifier.padding(Spacing.gutter)) {
            HeroRing(value = "61", unitLine = "out of 100", kicker = "12–18 Feb", progress = 0.61f, description = "61 out of 100", style = HeroStyle.DOTS)
        }
    }
}

@ComponentPreview
@Composable
private fun ModulesPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            GreetingHeader(greeting = "Good afternoon, Assis", subtitle = "Tuesday, 27 October") {
                HeaderIconButton(AwakenGlyphs.Portal, "Open focus sessions", onClick = {})
            }
            InsightCard(title = "Daily focus", body = "INT is falling behind. One short study block puts it back on track.")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                LevelModule(level = 24, rank = Rank.C, xpIntoLevel = 1_240, xpForLevel = 1_434, modifier = Modifier.weight(1.4f))
                StreakModule(streak = 12, best = 30, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                MetricLabel("Result", "High")
                MetricLabel("Trend", "+3.6%")
                TrendPill("+2 points", Trend.UP)
            }
            SegmentedTabs(listOf("Week", "Month", "Quarter", "Year"), selected = 1, onSelect = {})
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTintCard(Stat.STR, "Strength", "+2 levels", modifier = Modifier.weight(1f))
                StatTintCard(Stat.INT, "Intellect", "Stable", modifier = Modifier.weight(1f))
            }
            TimeBlockHeader(TimeBlock.MORNING, done = 2, total = 3, state = BlockState.PAST, expanded = false, onToggle = {})
            TimeBlockHeader(TimeBlock.DAY, done = 0, total = 2, state = BlockState.CURRENT, expanded = true, onToggle = {})
        }
    }
}
