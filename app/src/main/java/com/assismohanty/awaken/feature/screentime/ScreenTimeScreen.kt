package com.assismohanty.awaken.feature.screentime

import com.assismohanty.awaken.core.designsystem.components.ValueRow
import com.assismohanty.awaken.feature.reelcounter.labelRes
import com.assismohanty.awaken.feature.reelcounter.ReelAppRowUi
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.components.formatMinutes
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.charts.BarShape
import com.assismohanty.awaken.core.designsystem.charts.Segment
import com.assismohanty.awaken.core.designsystem.charts.StackedColumn
import com.assismohanty.awaken.core.designsystem.components.LegendSwatch
import com.assismohanty.awaken.core.designsystem.components.IconPlaceholder
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.components.Pill
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.formatCount
import com.assismohanty.awaken.core.designsystem.components.staggeredEntrance
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.AppUsageRow
import com.assismohanty.awaken.core.domain.DailyAverage
import com.assismohanty.awaken.core.domain.DisciplineStatus
import com.assismohanty.awaken.core.domain.ScreenTimeDay
import com.assismohanty.awaken.core.domain.ScreenTimeOverview
import com.assismohanty.awaken.engine.AppCategory
import com.assismohanty.awaken.engine.ScreenTimeRules
import com.assismohanty.awaken.engine.model.Rank
import java.time.LocalDate
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.assismohanty.awaken.core.domain.PickupsState
import com.assismohanty.awaken.engine.PickupKind
import com.assismohanty.awaken.engine.PickupSummary
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun ScreenTimeRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenReelStats: () -> Unit = {},
    viewModel: ScreenTimeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Coming back from the usage-access settings page (or any other app) re-reads usage.
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(ScreenTimeEvent.Refresh)
        onPauseOrDispose { }
    }
    ScreenTimeScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onOpenUsageAccess = { openUsageAccessSettings(context) },
        onOpenReelStats = onOpenReelStats,
        modifier = modifier,
    )
}

/**
 * AWAKEN's own "Permit usage access" switch (a package URI deep-links to it on most phones), else the full usage-access
 * list, else AWAKEN's app-info page. Never a crash when an OEM removed the page.
 */
internal fun openUsageAccessSettings(context: Context) {
    val packageUri = Uri.fromParts("package", context.packageName, null)
    val candidates = listOf(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No settings screen for %s", intent.action)
        }
    }
}

/** Screen Time (DESIGN_SYSTEM §4.9): today's usage by app and category, the discipline stake and per-app rules. */
@Composable
fun ScreenTimeScreen(
    state: ScreenTimeUiState,
    onEvent: (ScreenTimeEvent) -> Unit,
    onBack: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenReelStats: () -> Unit = {},
) {
    val colors = AwakenTheme.colors
    ScreenBackground(modifier = modifier) {
        LargeTitleScaffold(title = lexString(R.string.screentime_title), onBack = onBack) {
            state.reels?.let { ReelsWindow(it, onOpenReelStats) }
            val overview = state.overview
            when {
                overview == null && state.failed -> MessageWindow(lexString(R.string.screentime_failed))
                overview == null -> MessageWindow(lexString(R.string.screentime_loading))
                !overview.hasAccess -> {
                    AccessWindow(onOpenUsageAccess, Modifier.staggeredEntrance(0))
                    DisciplineWindow(overview, onEvent, Modifier.staggeredEntrance(1))
                }
                else -> {
                    UsageTodayWindow(overview, Modifier.staggeredEntrance(0))
                    state.pickups?.let { PickupsWindow(it, Modifier.staggeredEntrance(1)) }
                    DisciplineWindow(overview, onEvent, Modifier.staggeredEntrance(1))
                    AppsWindow(state, onEvent, Modifier.staggeredEntrance(2))
                    TrendWindow(overview.trend, Modifier.staggeredEntrance(3))
                    Text(lexString(R.string.screentime_privacy_note), style = AwakenTheme.type.caption, color = colors.textSecondary)
                }
            }
        }
    }
    state.editor?.let { RuleEditorSheet(it, onEvent) }
}

/** Short video watched today, counted by Reel counter. Only shown while that feature is switched on. */
@Composable
private fun ReelsWindow(rows: List<ReelAppRowUi>, onOpenReelStats: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    SystemWindow(title = lexString(R.string.reel_screentime_title), accent = colors.accent, modifier = modifier, reveal = false) {
        Text(lexString(R.string.reel_screentime_note), style = AwakenTheme.type.caption, color = colors.textSecondary)
        rows.forEach { ValueRow(label = lexString(it.app.labelRes()), value = lexString(R.string.reel_stats_row, it.count, it.minutes)) }
        SecondaryButton(lexString(R.string.reel_open_stats), onOpenReelStats, Modifier.fillMaxWidth())
    }
}

@Composable
private fun MessageWindow(text: String) {
    SystemWindow(title = null, accent = AwakenTheme.colors.textSecondary, reveal = false) {
        Text(text, style = AwakenTheme.type.body, color = AwakenTheme.colors.textSecondary)
    }
}

@Composable
private fun AccessWindow(onOpenUsageAccess: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    SystemWindow(title = lexString(R.string.screentime_window_access), accent = colors.accent, modifier = modifier, reveal = false) {
        Text(lexString(R.string.screentime_access_body), style = AwakenTheme.type.body, color = colors.textPrimary)
        Text(lexString(R.string.screentime_access_steps), style = AwakenTheme.type.caption, color = colors.textSecondary)
        PrimaryButton(text = lexString(R.string.screentime_access_action), onClick = onOpenUsageAccess, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun UsageTodayWindow(overview: ScreenTimeOverview, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = lexString(R.string.screentime_window_today), accent = colors.accent, modifier = modifier, reveal = false) {
        if (!overview.tracked) {
            Text(lexString(R.string.screentime_not_tracked), style = type.metricL, color = colors.textPrimary)
            Text(lexString(R.string.screentime_not_tracked_body), style = type.body, color = colors.textSecondary)
            return@SystemWindow
        }
        Text(formatMinutes(overview.totalMinutes), style = type.metricXL, color = colors.textPrimary)
        SplitBar(overview.minutesByCategory, overview.totalMinutes)
        AppCategory.entries.forEach { category ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                LegendSwatch(categoryColor(category))
                Text(lexString(category.labelRes), style = type.label, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text(formatMinutes(overview.minutesByCategory[category] ?: 0), style = type.numericSmall, color = colors.textSecondary)
            }
        }
    }
}

/** Distracting / productive / neutral as one segmented hairline bar; empty when nothing was used. */
@Composable
private fun SplitBar(byCategory: Map<AppCategory, Int>, total: Int) {
    val segments = if (total > 0) {
        AppCategory.entries.mapNotNull { category ->
            val minutes = byCategory[category] ?: 0
            if (minutes > 0) Segment(minutes.toFloat(), categoryColor(category)) else null
        }
    } else {
        emptyList()
    }
    Chart(ChartSpec.Segmented(segments = segments, track = AwakenTheme.colors.divider))
}

@Composable
private fun DisciplineWindow(overview: ScreenTimeOverview, onEvent: (ScreenTimeEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val accent = when (overview.status) {
        DisciplineStatus.FORFEITED -> colors.critical
        DisciplineStatus.ON_TRACK -> colors.positive
        else -> colors.accent
    }
    SystemWindow(
        title = lexString(R.string.quest_kind_discipline),
        accent = accent,
        modifier = modifier,
        reveal = false,
        headerTrailing = {
            if (overview.status != DisciplineStatus.OFF && overview.status != DisciplineStatus.NOT_TRACKED) {
                Text(lexString(R.string.screentime_xp_at_stake, overview.xpAtStake), style = type.numericSmall, color = colors.reward)
            }
        },
    ) {
        val line = when (overview.status) {
            DisciplineStatus.OFF -> lexString(R.string.screentime_discipline_off, overview.xpAtStake)
            DisciplineStatus.STARTS_LATER -> lexString(R.string.screentime_discipline_starts)
            DisciplineStatus.NOT_TRACKED ->
                lexString(if (overview.hasAccess) R.string.screentime_discipline_unreadable else R.string.screentime_discipline_not_tracked)
            DisciplineStatus.NO_LIMITS -> lexString(R.string.screentime_discipline_no_limits)
            DisciplineStatus.ON_TRACK -> lexString(R.string.screentime_discipline_on_track, overview.xpAtStake)
            DisciplineStatus.FORFEITED -> lexString(R.string.screentime_discipline_forfeited)
        }
        Text(line, style = type.body, color = colors.textPrimary)
        overview.apps.filter { it.category == AppCategory.DISTRACTING && it.limitMinutes != null && it.usedMinutes > 0 }
            .take(LIMIT_PREVIEW_COUNT)
            .forEach { LimitBar(it) }
        if (overview.status == DisciplineStatus.OFF) {
            PrimaryButton(
                text = lexString(R.string.screentime_discipline_turn_on),
                onClick = { onEvent(ScreenTimeEvent.SetDiscipline(true)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(lexString(R.string.screentime_discipline_rules), style = type.caption, color = colors.textSecondary)
        } else {
            QuietButton(
                text = lexString(R.string.screentime_discipline_turn_off),
                onClick = { onEvent(ScreenTimeEvent.SetDiscipline(false)) },
            )
        }
    }
}

@Composable
private fun LimitBar(row: AppUsageRow) {
    val colors = AwakenTheme.colors
    val limit = row.limitMinutes ?: return
    val fraction = (row.usedMinutes.toFloat() / limit).coerceIn(0f, 1f)
    val description = lexString(R.string.screentime_limit_progress, row.label, row.usedMinutes, limit)
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Row {
            Text(row.label, style = AwakenTheme.type.label, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                lexString(R.string.screentime_minutes_of_limit, row.usedMinutes, limit),
                style = AwakenTheme.type.numericSmall,
                color = if (row.breached) colors.critical else colors.textSecondary,
            )
        }
        Chart(
            ChartSpec.Progress(
                progress = fraction,
                color = if (row.breached || fraction >= WARN_FRACTION) colors.critical else colors.accent,
                track = colors.divider,
                shape = BarShape.CHAMFER,
            ),
        )
    }
}

@Composable
private fun AppsWindow(state: ScreenTimeUiState, onEvent: (ScreenTimeEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    SystemWindow(title = lexString(R.string.screentime_window_apps), accent = colors.accent, modifier = modifier, reveal = false) {
        Text(lexString(R.string.screentime_apps_note), style = AwakenTheme.type.caption, color = colors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ChoiceChip(text = lexString(R.string.screentime_filter_all), selected = state.filter == null, onClick = { onEvent(ScreenTimeEvent.Filter(null)) })
            AppCategory.entries.forEach { category ->
                ChoiceChip(
                    text = lexString(category.labelRes),
                    selected = state.filter == category,
                    onClick = { onEvent(ScreenTimeEvent.Filter(category)) },
                    color = categoryColor(category),
                )
            }
        }
        val apps = state.visibleApps
        if (apps.isEmpty()) {
            Text(lexString(R.string.screentime_apps_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
        }
        apps.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(thickness = AwakenTheme.dimens.hairline, color = colors.divider)
            AppRow(row, tracked = state.overview?.tracked == true, onClick = { onEvent(ScreenTimeEvent.OpenApp(row.packageName)) })
        }
        if (state.hiddenCount > 0 || state.showUnused) {
            QuietButton(
                text = if (state.showUnused) {
                    lexString(R.string.screentime_hide_unused)
                } else {
                    lexPlural(R.plurals.screentime_show_unused, state.hiddenCount, state.hiddenCount)
                },
                onClick = { onEvent(ScreenTimeEvent.ToggleUnused) },
            )
        }
    }
}

@Composable
private fun AppRow(row: AppUsageRow, tracked: Boolean, onClick: () -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val editLabel = lexString(R.string.screentime_edit_app, row.label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AwakenTheme.dimens.touchTarget)
            .clickable(onClickLabel = editLabel, onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        AppIcon(row.packageName)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(row.label, style = type.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = buildList {
                add(lexString(row.category.labelRes))
                row.limitMinutes?.let { add(lexString(R.string.screentime_limit_short, it)) }
            }.joinToString(" · ")
            Text(detail, style = type.label, color = colors.textSecondary)
            if (row.hasPendingChange) {
                val next = row.nextCategory ?: row.category
                val nextText = row.nextLimitMinutes?.let { lexString(R.string.screentime_pending_limit, lexString(next.labelRes), it) }
                    ?: lexString(R.string.screentime_pending, lexString(next.labelRes))
                Text(nextText, style = type.caption, color = colors.reward)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (tracked) {
                Text(formatMinutes(row.usedMinutes), style = type.numericSmall, color = if (row.breached) colors.critical else colors.textPrimary)
            } else {
                Text(lexString(R.string.screentime_limit_not_tracked), style = type.label, color = colors.textSecondary)
            }
            Pill(lexString(row.category.shortRes), categoryColor(row.category))
        }
    }
}

/** The app's launcher icon; a neutral tile in previews or when it can't be loaded. Decorative: the label sits beside it. */
@Composable
private fun AppIcon(packageName: String) {
    val size = AwakenTheme.dimens.sigil
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        if (inspection) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap() }.getOrNull()
        }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        IconPlaceholder()
    }
}

@Composable
private fun TrendWindow(trend: List<ScreenTimeDay>, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val max = (trend.maxOfOrNull { it.totalMinutes } ?: 0).coerceAtLeast(1)
    SystemWindow(title = lexString(R.string.screentime_window_trend), accent = colors.accent, modifier = modifier, reveal = false) {
        Text(lexString(R.string.screentime_trend_note), style = AwakenTheme.type.caption, color = colors.textSecondary)
        val columns = trend.map { day ->
            StackedColumn(
                total = day.totalMinutes.toFloat() / max,
                highlight = day.distractingMinutes.toFloat() / max,
                description = lexString(R.string.screentime_trend_day, dayName(day.gameDate), formatMinutes(day.totalMinutes), formatMinutes(day.distractingMinutes)),
            )
        }
        Chart(ChartSpec.StackedColumns(columns = columns, base = colors.divider, highlight = colors.critical))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
            trend.forEach { day ->
                Text(dayName(day.gameDate), style = AwakenTheme.type.caption, color = colors.textSecondary, modifier = Modifier.weight(1f))
            }
        }
        DailyAverage.ofWhole(trend.map { it.totalMinutes.toLong() })?.let { average ->
            Text(lexString(R.string.trend_daily_average, formatMinutes(average.toInt())), style = AwakenTheme.type.label, color = colors.textPrimary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditorSheet(editor: AppRuleEditor, onEvent: (ScreenTimeEvent) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(ScreenTimeEvent.DismissEditor) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AwakenTheme.colors.surfaceOverlay,
        shape = AwakenTheme.shapes.window,
    ) {
        RuleEditorContent(editor, onEvent, Modifier.navigationBarsPadding())
    }
}

@Composable
internal fun RuleEditorContent(editor: AppRuleEditor, onEvent: (ScreenTimeEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(editor.label, style = type.title, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        Text(lexString(R.string.screentime_editor_category), style = type.label, color = colors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            AppCategory.entries.forEach { category ->
                ChoiceChip(
                    text = lexString(category.labelRes),
                    selected = editor.category == category,
                    onClick = { onEvent(ScreenTimeEvent.EditCategory(category)) },
                    color = categoryColor(category),
                )
            }
        }
        if (editor.category == AppCategory.DISTRACTING) {
            Text(lexString(R.string.screentime_editor_limit), style = type.label, color = colors.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val limit = editor.limitMinutes
                SecondaryButton(
                    text = lexString(R.string.screentime_limit_minus),
                    onClick = { onEvent(ScreenTimeEvent.StepLimit(-ScreenTimeRules.LIMIT_STEP_MINUTES)) },
                    enabled = limit != null && limit > ScreenTimeRules.MIN_LIMIT_MINUTES,
                )
                Text(
                    text = limit?.let { formatMinutes(it) } ?: lexString(R.string.screentime_no_limit),
                    style = type.numeric,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = lexString(R.string.screentime_limit_plus),
                    onClick = { onEvent(ScreenTimeEvent.StepLimit(ScreenTimeRules.LIMIT_STEP_MINUTES)) },
                    enabled = limit == null || limit < ScreenTimeRules.MAX_LIMIT_MINUTES,
                )
            }
            QuietButton(
                text = lexString(if (editor.limitMinutes == null) R.string.screentime_set_limit else R.string.screentime_remove_limit),
                onClick = { onEvent(ScreenTimeEvent.ToggleNoLimit) },
            )
        }
        Text(lexString(R.string.screentime_editor_tomorrow), style = type.caption, color = colors.reward)
        PrimaryButton(text = lexString(R.string.action_save), onClick = { onEvent(ScreenTimeEvent.SaveEditor) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun categoryColor(category: AppCategory): Color = when (category) {
    AppCategory.DISTRACTING -> AwakenTheme.colors.critical
    AppCategory.PRODUCTIVE -> AwakenTheme.colors.positive
    AppCategory.NEUTRAL -> AwakenTheme.colors.textSecondary
}

private val AppCategory.labelRes: Int
    get() = when (this) {
        AppCategory.DISTRACTING -> R.string.screentime_category_distracting
        AppCategory.PRODUCTIVE -> R.string.screentime_category_productive
        AppCategory.NEUTRAL -> R.string.screentime_category_neutral
    }

private val AppCategory.shortRes: Int
    get() = when (this) {
        AppCategory.DISTRACTING -> R.string.screentime_short_distracting
        AppCategory.PRODUCTIVE -> R.string.screentime_short_productive
        AppCategory.NEUTRAL -> R.string.screentime_short_neutral
    }

private fun dayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())

private const val LIMIT_PREVIEW_COUNT = 4
private const val WARN_FRACTION = 0.84f

internal fun previewScreenTimeState(): ScreenTimeUiState {
    val today = LocalDate.of(2026, 10, 25)
    val apps = listOf(
        AppUsageRow("com.instagram.android", "Instagram", 26, AppCategory.DISTRACTING, 30),
        AppUsageRow("com.google.android.apps.docs", "Docs", 74, AppCategory.PRODUCTIVE, null),
        AppUsageRow("com.google.android.youtube", "YouTube", 12, AppCategory.DISTRACTING, 45, nextLimitMinutes = 60, hasPendingChange = true),
        AppUsageRow("com.android.chrome", "Chrome", 35, AppCategory.NEUTRAL, null),
        AppUsageRow("com.example.calc", "Calculator", 0, AppCategory.NEUTRAL, null),
    )
    return ScreenTimeUiState(
        loading = false,
        overview = ScreenTimeOverview(
            gameDate = today,
            hasAccess = true,
            tracked = true,
            status = DisciplineStatus.ON_TRACK,
            startsOn = today.minusDays(3),
            xpAtStake = 78,
            totalMinutes = apps.sumOf { it.usedMinutes },
            minutesByCategory = mapOf(AppCategory.DISTRACTING to 38, AppCategory.PRODUCTIVE to 74, AppCategory.NEUTRAL to 35),
            apps = apps,
            trend = (6 downTo 0).map { back ->
                ScreenTimeDay(today.minusDays(back.toLong()), 120 + back * 17, 40 + back * 9)
            },
        ),
    )
}

@PhonePreviews
@Composable
private fun ScreenTimeScreenPreview() {
    AwakenTheme(reduceMotion = true) {
        ScreenTimeScreen(state = previewScreenTimeState(), onEvent = {}, onBack = {}, onOpenUsageAccess = {})
    }
}

@PhonePreviews
@Composable
private fun ScreenTimeNoAccessPreview() {
    val state = previewScreenTimeState()
    AwakenTheme(reduceMotion = true) {
        ScreenTimeScreen(
            state = state.copy(overview = state.overview?.copy(hasAccess = false, status = DisciplineStatus.OFF, apps = emptyList())),
            onEvent = {},
            onBack = {},
            onOpenUsageAccess = {},
        )
    }
}

/** Discipline is on but access was never granted (or was revoked): no stake is offered, the limit is "not tracked". */
@PhonePreviews
@Composable
private fun ScreenTimeNoAccessDisciplineOnPreview() {
    val state = previewScreenTimeState()
    AwakenTheme(reduceMotion = true) {
        ScreenTimeScreen(
            state = state.copy(
                overview = state.overview?.copy(hasAccess = false, tracked = false, status = DisciplineStatus.NOT_TRACKED, apps = emptyList()),
            ),
            onEvent = {},
            onBack = {},
            onOpenUsageAccess = {},
        )
    }
}

/** Access is granted but the phone reports no usage events: every limit reads "not tracked", nothing is at stake. */
@PhonePreviews
@Composable
private fun ScreenTimeUnreadablePreview() {
    val state = previewScreenTimeState()
    AwakenTheme(reduceMotion = true) {
        ScreenTimeScreen(
            state = state.copy(
                overview = state.overview?.copy(
                    tracked = false,
                    status = DisciplineStatus.NOT_TRACKED,
                    totalMinutes = 0,
                    minutesByCategory = emptyMap(),
                    apps = state.overview.apps.map { it.copy(usedMinutes = 0) },
                ),
                showUnused = true,
            ),
            onEvent = {},
            onBack = {},
            onOpenUsageAccess = {},
        )
    }
}

/** The Week view on its own (below the fold in the screen preview): 7-day bars plus the daily average. */
@PhonePreviews
@Composable
private fun ScreenTimeWeekPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.gutter)) { TrendWindow(checkNotNull(previewScreenTimeState().overview).trend) }
    }
}

/**
 * Pickups today (GD §16.10): how often the phone was unlocked, the first pickup, the last use yesterday and the longest
 * stretch without it. Display only. No access or an unreadable log shows words, never zeros.
 */
@Composable
internal fun PickupsWindow(pickups: PickupsState, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = lexString(R.string.pickups_title), accent = colors.accent, modifier = modifier, reveal = false) {
        when (pickups) {
            PickupsState.NoAccess -> Text(lexString(R.string.pickups_needs_access), style = type.body, color = colors.textSecondary)
            PickupsState.Unavailable -> Text(lexString(R.string.pickups_no_data), style = type.body, color = colors.textSecondary)
            is PickupsState.Data -> PickupsData(pickups.summary)
        }
    }
}

@Composable
private fun PickupsData(summary: PickupSummary) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val none = lexString(R.string.pickups_none_yet)
    Text(
        lexString(if (summary.kind == PickupKind.PICKUPS) R.string.pickups_label_pickups else R.string.pickups_label_screen_ons),
        style = type.label,
        color = colors.textSecondary,
    )
    Text(summary.count.toString(), style = type.metricXL, color = colors.textPrimary)
    ValueRow(lexString(R.string.pickups_first), summary.firstPickup?.let(::clockTime) ?: none)
    ValueRow(lexString(R.string.pickups_last_use), summary.lastUseYesterday?.let(::clockTime) ?: none)
    ValueRow(lexString(R.string.pickups_stretch), summary.longestStretch?.let { durationText(it) } ?: none)
    Text(lexString(R.string.pickups_note), style = type.caption, color = colors.textSecondary)
}

private fun clockTime(at: Instant): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(java.util.Locale.getDefault()).withZone(ZoneId.systemDefault()).format(at)

@Composable
private fun durationText(duration: Duration): String {
    val hours = duration.toHours().toInt()
    val minutes = (duration.toMinutes() % MINUTES_PER_HOUR).toInt()
    return if (hours > 0) lexString(R.string.pickups_duration_hm, hours, minutes) else lexString(R.string.pickups_duration_m, minutes)
}

private const val MINUTES_PER_HOUR = 60

private val previewPickups = PickupsState.Data(
    PickupSummary(
        kind = PickupKind.PICKUPS,
        count = 3,
        firstPickup = Instant.parse("2026-10-25T07:10:00Z"),
        lastUseYesterday = Instant.parse("2026-10-24T23:48:00Z"),
        longestStretch = Duration.ofHours(4).plusMinutes(25),
    ),
)

@PhonePreviews
@Composable
private fun PickupsPreview() = AwakenTheme(reduceMotion = true) {
    ScreenTimeScreen(state = previewScreenTimeState().copy(pickups = previewPickups), onEvent = {}, onBack = {}, onOpenUsageAccess = {})
}

@PhonePreviews
@Composable
private fun PickupsScreenOnsPreview() = AwakenTheme(reduceMotion = true) {
    val summary = (previewPickups as PickupsState.Data).summary.copy(kind = PickupKind.SCREEN_ONS, count = 5, longestStretch = null, lastUseYesterday = null)
    ScreenTimeScreen(state = previewScreenTimeState().copy(pickups = PickupsState.Data(summary)), onEvent = {}, onBack = {}, onOpenUsageAccess = {})
}

@PhonePreviews
@Composable
private fun PickupsNoDataPreview() = AwakenTheme(reduceMotion = true) {
    ScreenTimeScreen(state = previewScreenTimeState().copy(pickups = PickupsState.Unavailable), onEvent = {}, onBack = {}, onOpenUsageAccess = {})
}
