package com.assismohanty.awaken.feature.status

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.components.ShareCapture
import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.HeaderIconButton
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.identityLine
import com.assismohanty.awaken.core.designsystem.components.identityPhraseRes
import com.assismohanty.awaken.core.designsystem.components.LevelModule
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.ListRow
import com.assismohanty.awaken.core.designsystem.components.MetricCard
import com.assismohanty.awaken.core.designsystem.components.SkeletonCard
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.ConsistencyMeter
import com.assismohanty.awaken.core.designsystem.components.Heatmap
import com.assismohanty.awaken.core.designsystem.components.HeatmapDay
import com.assismohanty.awaken.core.designsystem.components.HunterHeader
import com.assismohanty.awaken.core.designsystem.components.HunterHeaderModel
import com.assismohanty.awaken.core.designsystem.components.Pill
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.ShareCardModel
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.XpBar
import com.assismohanty.awaken.core.designsystem.components.formatCount
import com.assismohanty.awaken.core.designsystem.components.label
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.HeatmapEntry
import com.assismohanty.awaken.core.domain.InsightsModel
import com.assismohanty.awaken.core.domain.StatProgress
import com.assismohanty.awaken.debug.DebugEntry
import com.assismohanty.awaken.engine.ConsistencyScore
import com.assismohanty.awaken.engine.DifficultyAdapter
import com.assismohanty.awaken.engine.insight.BucketInsight
import com.assismohanty.awaken.engine.insight.FocusHourInsight
import com.assismohanty.awaken.engine.insight.InsightRule
import com.assismohanty.awaken.engine.insight.WeekdayInsight
import com.assismohanty.awaken.engine.GameWeek
import com.assismohanty.awaken.engine.StatLevel
import com.assismohanty.awaken.engine.StatusMath
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun StatusRoute(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPaths: (() -> Unit)? = {},
    onOpenReview: () -> Unit = {},
    onOpenQuit: () -> Unit = {},
    onOpenPrograms: () -> Unit = {},
    viewModel: StatusViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooserTitle = lexString(R.string.share_report_chooser)
    LaunchedEffect(state.shareUri) {
        val uri = state.shareUri ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND)
            .setType(PNG_MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData carries the read grant through the chooser to the picked app.
        send.clipData = ClipData.newRawUri(null, uri)
        context.startActivity(Intent.createChooser(send, chooserTitle))
        viewModel.onEvent(StatusEvent.ShareHandled)
    }
    StatusScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenSettings = onOpenSettings,
        onOpenPaths = onOpenPaths,
        onOpenReview = onOpenReview,
        onOpenQuit = onOpenQuit,
        onOpenPrograms = onOpenPrograms,
        modifier = modifier,
    )
}

/** Progress (DESIGN_SYSTEM §4.5): header → radar → stats → heatmap → weekly report. */
@Composable
fun StatusScreen(
    state: StatusUiState,
    onEvent: (StatusEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenPaths: (() -> Unit)? = {},
    onOpenReview: () -> Unit = {},
    onOpenQuit: () -> Unit = {},
    onOpenPrograms: () -> Unit = {},
) {
    val header = state.header
    LargeTitleScaffold(
        title = lexString(R.string.nav_status),
        subtitle = header?.name,
        modifier = modifier,
        actions = {
            // Paths left the bottom bar in the v1.1 redesign; they open from here.
            // Null when the build has no AI Paths: the Paths entry is hidden, not just gated.
            if (onOpenPaths != null) HeaderIconButton(AwakenGlyphs.Path, lexString(R.string.cd_open_paths), onOpenPaths)
            HeaderIconButton(AwakenGlyphs.Settings, lexString(R.string.cd_open_settings), onOpenSettings)
        },
    ) {
        if (header == null) {
            SkeletonCard()
            return@LargeTitleScaffold
        }
        identityLine(state.identityIds.mapNotNull { id -> identityPhraseRes(id)?.let { lexString(it) } })?.let { line ->
            Text(line, style = AwakenTheme.type.body, color = AwakenTheme.colors.textSecondary)
        }
        // Level and gold side by side, equal heights.
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            LevelModule(
                level = header.level,
                rank = header.rank,
                xpIntoLevel = header.xpIntoLevel,
                xpForLevel = header.xpForLevel,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            MetricCard(
                label = lexString(R.string.reward_gold),
                value = formatCount(header.gold),
                secondary = lexPlural(R.plurals.cd_streak, header.streak, header.streak),
                icon = AwakenGlyphs.Coin,
                iconTint = AwakenTheme.colors.reward,
                description = lexString(R.string.cd_gold, formatCount(header.gold)),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        RadarWindow(state)
        InsightsSection(state.insights)
        StatsWindow(state.rank, state.stats)
        ActivityWindow(state, onEvent)
        WeeklyWindow(state, onEvent, onOpenReview)
        QuitRow(onOpenQuit)
        ProgramsRow(onOpenPrograms)
    }
    if (state.capturing) {
        ShareCapture(
            model = shareCardModel(state),
            onCaptured = { onEvent(StatusEvent.ShareCaptured(it)) },
            onFailed = { onEvent(StatusEvent.ShareFailed) },
        )
    }
}

@Composable
private fun RadarWindow(state: StatusUiState) {
    val colors = AwakenTheme.colors
    SystemWindow(title = lexString(R.string.status_window_radar), accent = AwakenTheme.rankAccent(state.rank), reveal = false) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Chart(ChartSpec.Radar(values = state.radarNow, maxValue = state.radarMax, previous = state.radarLastWeek))
        }
        Text(lexString(R.string.status_radar_legend), style = AwakenTheme.type.caption, color = colors.textSecondary)
    }
}

@Composable
private fun StatsWindow(rank: Rank, stats: List<StatProgress>) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = lexString(R.string.status_window_stats), accent = AwakenTheme.rankAccent(rank), reveal = false) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            stats.forEach { stat ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ListRow(
                        title = stat.stat.label(),
                        icon = AwakenGlyphs.stat(stat.stat),
                        iconTint = colors.stat(stat.stat),
                        secondary = lexString(R.string.xp_progress, formatCount(stat.xpIntoLevel), formatCount(stat.xpForLevel)),
                    ) {
                        Text(lexString(R.string.level_long, stat.level), style = type.numericSmall, color = colors.textPrimary)
                    }
                    XpBar(level = stat.level, xpIntoLevel = stat.xpIntoLevel, xpForLevel = stat.xpForLevel, showLabel = false)
                }
            }
        }
    }
}

@Composable
private fun ActivityWindow(state: StatusUiState, onEvent: (StatusEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = lexString(R.string.status_window_activity), accent = AwakenTheme.rankAccent(state.rank), reveal = false) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Chart(ChartSpec.Heatmap(days = state.heatmap, onDayClick = { onEvent(StatusEvent.SelectDay(it.date)) }))
        }
        val selected = state.selectedDay
        Text(
            text = if (selected == null) lexString(R.string.status_heatmap_hint) else daySummary(selected),
            style = type.caption,
            color = if (selected == null) colors.textSecondary else colors.textPrimary,
        )
        ConsistencyMeter(state.consistency)
    }
}

@Composable
private fun daySummary(day: HeatmapEntry): String {
    val date = formatDate(day.date)
    return if (day.restDay && day.completions == 0) {
        lexString(R.string.status_heatmap_rest, date)
    } else {
        lexPlural(R.plurals.status_heatmap_day, day.completions, date, day.completions)
    }
}

@Composable
private fun WeeklyWindow(state: StatusUiState, onEvent: (StatusEvent) -> Unit, onOpenReview: () -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val report = state.weeklyReport ?: return
    SystemWindow(title = lexString(R.string.status_window_weekly), accent = AwakenTheme.rankAccent(state.rank), reveal = false) {
        Text(
            lexString(R.string.status_weekly_range, formatDate(report.week.monday), formatDate(report.week.sunday)),
            style = type.caption,
            color = colors.textSecondary,
        )
        if (!state.weeklyOpen) {
            Text(lexString(R.string.status_weekly_available), style = type.body, color = colors.textSecondary)
        } else {
            val none = lexString(R.string.status_weekly_none)
            ReportLine(lexString(R.string.status_weekly_xp), formatCount(report.totalXp))
            ReportLine(lexString(R.string.status_weekly_quests), formatCount(report.questsCompleted))
            Stat.entries.forEach { stat -> ReportLine(stat.label(), "+${formatCount(report.statXpGained.getValue(stat))}") }
            ReportLine(lexString(R.string.status_weekly_best_day), report.bestDay?.let { formatDate(it) } ?: none)
            ReportLine(lexString(R.string.status_weekly_neglected), report.mostNeglected.label())
            Text(lexString(R.string.status_weekly_recal_title), style = type.label, color = colors.textPrimary)
            if (state.projectedRecalibration.isEmpty()) {
                Text(lexString(R.string.status_weekly_recal_none), style = type.caption, color = colors.textSecondary)
            } else {
                state.projectedRecalibration.forEach { (stat, change) ->
                    val res = if (change == DifficultyAdapter.Change.UP) R.string.status_weekly_recal_harder else R.string.status_weekly_recal_easier
                    Text(lexString(res, stat.label()), style = type.caption, color = colors.textSecondary)
                }
            }
            SecondaryButton(
                text = lexString(R.string.action_share_report),
                onClick = { onEvent(StatusEvent.Share) },
                enabled = !state.capturing,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.reviewOpen) {
            PrimaryButton(text = lexString(R.string.review_title), onClick = onOpenReview, modifier = Modifier.fillMaxWidth())
        }
        if (DebugEntry.ENABLED) {
            QuietButton(text = lexString(R.string.debug_show_weekly), onClick = { onEvent(StatusEvent.ToggleDebugWeekly) })
        }
    }
}

@Composable
private fun ReportLine(label: String, value: String) {
    val colors = AwakenTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AwakenTheme.type.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = AwakenTheme.type.numericSmall, color = colors.textPrimary)
    }
}

@Composable
private fun shareCardModel(state: StatusUiState): ShareCardModel {
    val report = state.weeklyReport
    return ShareCardModel(
        name = state.name,
        rank = state.rank,
        level = state.level,
        statLevels = state.radarNow,
        radarMax = state.radarMax,
        weekStats = listOf(
            lexString(R.string.status_weekly_xp) to formatCount(report?.totalXp ?: 0L),
            lexString(R.string.status_weekly_quests) to formatCount(report?.questsCompleted ?: 0),
            lexString(R.string.share_streak) to formatCount(state.streak),
        ),
    )
}


@Composable
private fun formatDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
}

private const val GRID_COLUMNS = 2
private const val PNG_MIME = "image/png"

internal fun previewStatusState(locked: Boolean = false): StatusUiState {
    val today = LocalDate.parse("2026-10-25")
    val week = GameWeek.of(today)
    val statXp = if (locked) emptyMap() else mapOf(Stat.STR to 5_200L, Stat.INT to 3_240L, Stat.WIL to 1_440L, Stat.VIT to 2_600L, Stat.CHA to 640L)
    val levels = Stat.entries.associateWith { StatLevel.level(statXp[it] ?: 0L) }
    val stats = Stat.entries.map { stat ->
        val xp = statXp[stat] ?: 0L
        val level = levels.getValue(stat)
        StatProgress(stat, level, xp, xp - StatLevel.xpForLevel(level), StatLevel.xpForLevel(level + 1) - StatLevel.xpForLevel(level))
    }
    val start = StatusMath.heatmapStart(today)
    val heatmap = (0 until StatusMath.HEATMAP_WEEKS * 7).map { offset ->
        val date = start.plusDays(offset.toLong())
        val count = if (locked || date > today) 0 else (offset * 7 % 6)
        HeatmapEntry(date, count, StatusMath.heatmapIntensity(count), restDay = offset % 13 == 0)
    }
    return StatusUiState(
        loading = false,
        header = HunterHeaderModel(
            name = "Assis", level = if (locked) 1 else 14, rank = Rank.D,
            xpIntoLevel = 420, xpForLevel = 712, streak = 9, gold = 1_240,
        ),
        rank = Rank.D,
        name = "Assis",
        level = 14,
        streak = 9,
        radarNow = levels.mapValues { it.value.toFloat() },
        radarLastWeek = levels.mapValues { (it.value - 1).coerceAtLeast(1).toFloat() },
        radarMax = StatusMath.radarMax(levels.values).toFloat(),
        stats = stats,
        heatmap = heatmap.map { HeatmapDay(it.date, it.intensity, it.restDay) },
        weeklyReport = StatusMath.weeklyReport(
            week,
            statXp.mapValues { it.value / 10 },
            mapOf(week.monday to 180L, week.monday.plusDays(3) to 240L),
            questsCompleted = 23,
        ),
        weeklyOpen = !locked,
        projectedRecalibration = if (locked) emptyMap() else mapOf(Stat.STR to DifficultyAdapter.Change.UP),
        consistency = if (locked) ConsistencyScore(0, 0) else ConsistencyScore(cleared = 22, eligible = 28),
        identityIds = if (locked) emptyList() else listOf("trains", "reads"),
        insights = if (locked) {
            InsightsModel(daysNeeded = 9)
        } else {
            InsightsModel(
                insights = listOf(
                    BucketInsight(InsightRule.SLEEP, aIsHigher = true, higherMean = 3.3, lowerMean = 2.4, percent = 38),
                    WeekdayInsight(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY, bestMean = 4.0, worstMean = 2.5, percent = 60),
                    FocusHourInsight(hour = 9, gates = 4, total = 10),
                ),
                daysNeeded = 0,
            )
        },
    )
}

@PhonePreviews
@Composable
private fun StatusScreenPreview() {
    AwakenTheme(reduceMotion = true) { StatusScreen(state = previewStatusState(), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun StatusScreenNewHunterPreview() {
    AwakenTheme(reduceMotion = true) { StatusScreen(state = previewStatusState(locked = true), onEvent = {}) }
}

/** The way into the quit counters (GD §16.13). */
@Composable
private fun QuitRow(onOpenQuit: () -> Unit) {
    AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
        ListRow(
            title = lexString(R.string.quit_row_title),
            secondary = lexString(R.string.quit_row_note),
            onClick = onOpenQuit,
            trailing = {
                Icon(AwakenGlyphs.ChevronRight, contentDescription = null, tint = AwakenTheme.colors.textSecondary, modifier = Modifier.size(AwakenTheme.dimens.iconSmall))
            },
        )
    }
}

/** The way into the programs (GD §16.15), next to the Campaign and the quit counters. */
@Composable
private fun ProgramsRow(onOpenPrograms: () -> Unit) {
    AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
        ListRow(
            title = lexString(R.string.programs_row_title),
            secondary = lexString(R.string.programs_row_note),
            onClick = onOpenPrograms,
            trailing = {
                Icon(AwakenGlyphs.ChevronRight, contentDescription = null, tint = AwakenTheme.colors.textSecondary, modifier = Modifier.size(AwakenTheme.dimens.iconSmall))
            },
        )
    }
}
