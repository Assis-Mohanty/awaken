package com.assismohanty.awaken.feature.status

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.InsightCard
import com.assismohanty.awaken.core.designsystem.components.SectionTitle
import com.assismohanty.awaken.core.designsystem.components.currentLocale
import com.assismohanty.awaken.core.designsystem.components.formatOneDecimal
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.InsightsModel
import com.assismohanty.awaken.engine.insight.BlockInsight
import com.assismohanty.awaken.engine.insight.BucketInsight
import com.assismohanty.awaken.engine.insight.FocusHourInsight
import com.assismohanty.awaken.engine.insight.Insight
import com.assismohanty.awaken.engine.insight.InsightRule
import com.assismohanty.awaken.engine.insight.WeekdayInsight
import com.assismohanty.awaken.engine.model.TimeOfDay
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** INSIGHTS on Status (DESIGN_SYSTEM §8.2): up to 3 cards, or the empty / no-pattern line. Grants nothing. */
@Composable
internal fun InsightsSection(model: InsightsModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        SectionTitle(lexString(R.string.insights_title))
        when {
            model.daysNeeded > 0 -> InsightCard(text = lexPlural(R.plurals.insights_days_needed, model.daysNeeded, model.daysNeeded))
            model.insights.isEmpty() -> InsightCard(text = lexString(R.string.insights_none))
            else -> model.insights.forEach { insight ->
                InsightCard(
                    text = insightText(insight),
                    icon = insight.glyph(),
                    chart = insightChart(insight),
                    caption = lexString(R.string.insights_caption),
                )
            }
        }
    }
}

/** The first insight's sentence, for the Weekly Review (GD §14.4), or the empty-state line. */
@Composable
internal fun insightLine(model: InsightsModel): String = when {
    model.daysNeeded > 0 -> lexPlural(R.plurals.insights_days_needed, model.daysNeeded, model.daysNeeded)
    model.insights.isEmpty() -> lexString(R.string.insights_none)
    else -> insightText(model.insights.first())
}

@Composable
private fun insightText(insight: Insight): String = when (insight) {
    is BucketInsight -> {
        val copy = insight.copyRes()
        val percent = insight.percent
        if (percent != null) {
            lexString(copy.relative, percent)
        } else {
            lexString(copy.absolute, formatOneDecimal(insight.higherMean), formatOneDecimal(insight.lowerMean))
        }
    }
    is WeekdayInsight -> {
        val best = insight.best.full()
        val worst = insight.worst.full()
        val percent = insight.percent
        if (percent != null) {
            lexString(R.string.insight_weekday, percent, best, worst)
        } else {
            lexString(R.string.insight_weekday_abs, formatOneDecimal(insight.bestMean), best, formatOneDecimal(insight.worstMean), worst)
        }
    }
    is BlockInsight -> {
        val best = lexString(insight.best.wordRes())
        val worst = lexString(insight.worst.wordRes())
        val percent = insight.percent
        if (percent != null) {
            lexString(R.string.insight_block, percent, best, worst)
        } else {
            lexString(R.string.insight_block_abs, (insight.bestRate * PERCENT).roundToInt(), best, (insight.worstRate * PERCENT).roundToInt(), worst)
        }
    }
    is FocusHourInsight -> lexString(
        R.string.insight_focus_hour,
        LocalTime.of(insight.hour, 0).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale())),
    )
}

/** Two bars, the higher first, labelled; the focus-hour insight has no chart. */
@Composable
private fun insightChart(insight: Insight): ChartSpec? {
    val (labels, values) = when (insight) {
        is BucketInsight -> {
            val (a, b) = insight.bucketLabels()
            val pair = if (insight.aIsHigher) listOf(a, b) else listOf(b, a)
            pair to listOf(insight.higherMean, insight.lowerMean)
        }
        is WeekdayInsight -> listOf(insight.best.short(), insight.worst.short()) to listOf(insight.bestMean, insight.worstMean)
        is BlockInsight -> listOf(lexString(insight.best.nameRes()), lexString(insight.worst.nameRes())) to
            listOf(insight.bestRate, insight.worstRate)
        is FocusHourInsight -> return null
    }
    val top = values.max().takeIf { it > 0 } ?: return null
    val shown = if (insight is BlockInsight) values.map { "${(it * PERCENT).roundToInt()}%" } else values.map { formatOneDecimal(it) }
    return ChartSpec.Columns(
        fractions = values.map { (it / top).toFloat() },
        labels = labels,
        color = AwakenTheme.colors.chartSeries3,
        description = lexString(R.string.insight_chart_cd, labels[0], shown[0], labels[1], shown[1]),
        areaHeight = AwakenTheme.dimens.insightChartHeight,
    )
}

private data class BucketCopy(@StringRes val relative: Int, @StringRes val absolute: Int)

private fun BucketInsight.copyRes(): BucketCopy = when (rule) {
    InsightRule.SLEEP -> if (aIsHigher) {
        BucketCopy(R.string.insight_sleep_more, R.string.insight_sleep_more_abs)
    } else {
        BucketCopy(R.string.insight_sleep_less, R.string.insight_sleep_less_abs)
    }
    InsightRule.STEPS -> if (aIsHigher) {
        BucketCopy(R.string.insight_steps_more, R.string.insight_steps_more_abs)
    } else {
        BucketCopy(R.string.insight_steps_less, R.string.insight_steps_less_abs)
    }
    else -> if (aIsHigher) {
        BucketCopy(R.string.insight_momentum_more, R.string.insight_momentum_more_abs)
    } else {
        BucketCopy(R.string.insight_momentum_less, R.string.insight_momentum_less_abs)
    }
}

@Composable
private fun BucketInsight.bucketLabels(): Pair<String, String> = when (rule) {
    InsightRule.SLEEP -> lexString(R.string.insight_bucket_sleep_a) to lexString(R.string.insight_bucket_sleep_b)
    InsightRule.STEPS -> lexString(R.string.insight_bucket_steps_a) to lexString(R.string.insight_bucket_steps_b)
    else -> lexString(R.string.insight_bucket_momentum_a) to lexString(R.string.insight_bucket_momentum_b)
}

private fun Insight.glyph() = when (rule) {
    InsightRule.SLEEP -> AwakenGlyphs.Moon
    InsightRule.WEEKDAY -> AwakenGlyphs.Calendar
    InsightRule.TIME_OF_DAY -> AwakenGlyphs.Sun
    InsightRule.FOCUS_HOUR -> AwakenGlyphs.Clock
    InsightRule.STEPS -> AwakenGlyphs.Sparkle
    InsightRule.MOMENTUM -> AwakenGlyphs.Trophy
}

/** "Tuesday": the full weekday name in the user's locale. */
@Composable
private fun DayOfWeek.full(): String = getDisplayName(TextStyle.FULL, currentLocale())

@Composable
private fun DayOfWeek.short(): String = getDisplayName(TextStyle.SHORT, currentLocale())

@StringRes
private fun TimeOfDay.wordRes(): Int = when (this) {
    TimeOfDay.MORNING -> R.string.insight_block_morning
    TimeOfDay.DAY -> R.string.insight_block_day
    TimeOfDay.EVENING -> R.string.insight_block_evening
}

@StringRes
private fun TimeOfDay.nameRes(): Int = when (this) {
    TimeOfDay.MORNING -> R.string.time_block_morning
    TimeOfDay.DAY -> R.string.time_block_day
    TimeOfDay.EVENING -> R.string.time_block_evening
}

private const val PERCENT = 100
