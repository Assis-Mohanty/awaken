package com.assismohanty.awaken.feature.screentime

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.GateBackground
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
import com.assismohanty.awaken.core.domain.DisciplineStatus
import com.assismohanty.awaken.core.domain.ScreenTimeDay
import com.assismohanty.awaken.core.domain.ScreenTimeOverview
import com.assismohanty.awaken.engine.AppCategory
import com.assismohanty.awaken.engine.ScreenTimeRules
import com.assismohanty.awaken.engine.model.Rank
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun ScreenTimeRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ScreenTimeViewModel = hiltViewModel()) {
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
        onOpenUsageAccess = {
            try {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                Timber.w(e, "No usage access settings screen")
            }
        },
        modifier = modifier,
    )
}

/** Screen Time (DESIGN_SYSTEM §4.9): today's usage by app and category, the discipline stake and per-app rules. */
@Composable
fun ScreenTimeScreen(
    state: ScreenTimeUiState,
    onEvent: (ScreenTimeEvent) -> Unit,
    onBack: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    GateBackground(rank = Rank.E, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = Spacing.contentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.screentime_title),
                        style = AwakenTheme.type.metricL,
                        color = colors.textPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    QuietButton(text = stringResource(R.string.action_back), onClick = onBack)
                }
                val overview = state.overview
                when {
                    overview == null && state.failed -> MessageWindow(stringResource(R.string.screentime_failed))
                    overview == null -> MessageWindow(stringResource(R.string.screentime_loading))
                    !overview.hasAccess -> {
                        AccessWindow(onOpenUsageAccess, Modifier.staggeredEntrance(0))
                        DisciplineWindow(overview, onEvent, Modifier.staggeredEntrance(1))
                    }
                    else -> {
                        UsageTodayWindow(overview, Modifier.staggeredEntrance(0))
                        DisciplineWindow(overview, onEvent, Modifier.staggeredEntrance(1))
                        AppsWindow(state, onEvent, Modifier.staggeredEntrance(2))
                        TrendWindow(overview.trend, Modifier.staggeredEntrance(3))
                        Text(stringResource(R.string.screentime_privacy_note), style = AwakenTheme.type.caption, color = colors.textSecondary)
                    }
                }
            }
        }
    }
    state.editor?.let { RuleEditorSheet(it, onEvent) }
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
    SystemWindow(title = stringResource(R.string.screentime_window_access), accent = colors.accent, modifier = modifier, reveal = false) {
        Text(stringResource(R.string.screentime_access_body), style = AwakenTheme.type.body, color = colors.textPrimary)
        Text(stringResource(R.string.screentime_access_steps), style = AwakenTheme.type.caption, color = colors.textSecondary)
        PrimaryButton(text = stringResource(R.string.screentime_access_action), onClick = onOpenUsageAccess, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun UsageTodayWindow(overview: ScreenTimeOverview, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = stringResource(R.string.screentime_window_today), accent = colors.accent, modifier = modifier, reveal = false) {
        Text(formatMinutes(overview.totalMinutes), style = type.metricXL, color = colors.textPrimary)
        SplitBar(overview.minutesByCategory, overview.totalMinutes)
        AppCategory.entries.forEach { category ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Box(
                    modifier = Modifier
                        .size(Spacing.xs)
                        .background(categoryColor(category)),
                )
                Text(stringResource(category.labelRes), style = type.label, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text(formatMinutes(overview.minutesByCategory[category] ?: 0), style = type.numericSmall, color = colors.textSecondary)
            }
        }
    }
}

/** Distracting / productive / neutral as one segmented hairline bar; empty when nothing was used. */
@Composable
private fun SplitBar(byCategory: Map<AppCategory, Int>, total: Int) {
    val colors = AwakenTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AwakenTheme.dimens.xpBarHeight)
            .clip(AwakenTheme.shapes.xpBar)
            .background(colors.divider)
            .clearAndSetSemantics { },
    ) {
        if (total > 0) {
            AppCategory.entries.forEach { category ->
                val minutes = byCategory[category] ?: 0
                if (minutes > 0) {
                    Box(
                        modifier = Modifier
                            .weight(minutes.toFloat())
                            .fillMaxHeight()
                            .background(categoryColor(category)),
                    )
                }
            }
        }
    }
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
        title = stringResource(R.string.quest_kind_discipline),
        accent = accent,
        modifier = modifier,
        reveal = false,
        headerTrailing = {
            if (overview.status != DisciplineStatus.OFF) {
                Text(stringResource(R.string.screentime_xp_at_stake, overview.xpAtStake), style = type.numericSmall, color = colors.reward)
            }
        },
    ) {
        val line = when (overview.status) {
            DisciplineStatus.OFF -> stringResource(R.string.screentime_discipline_off, overview.xpAtStake)
            DisciplineStatus.STARTS_LATER -> stringResource(R.string.screentime_discipline_starts)
            DisciplineStatus.NO_LIMITS -> stringResource(R.string.screentime_discipline_no_limits)
            DisciplineStatus.ON_TRACK -> stringResource(R.string.screentime_discipline_on_track, overview.xpAtStake)
            DisciplineStatus.FORFEITED -> stringResource(R.string.screentime_discipline_forfeited)
        }
        Text(line, style = type.body, color = colors.textPrimary)
        overview.apps.filter { it.category == AppCategory.DISTRACTING && it.limitMinutes != null && it.usedMinutes > 0 }
            .take(LIMIT_PREVIEW_COUNT)
            .forEach { LimitBar(it) }
        if (overview.status == DisciplineStatus.OFF) {
            PrimaryButton(
                text = stringResource(R.string.screentime_discipline_turn_on),
                onClick = { onEvent(ScreenTimeEvent.SetDiscipline(true)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.screentime_discipline_rules), style = type.caption, color = colors.textSecondary)
        } else {
            QuietButton(
                text = stringResource(R.string.screentime_discipline_turn_off),
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
    val description = stringResource(R.string.screentime_limit_progress, row.label, row.usedMinutes, limit)
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Row {
            Text(row.label, style = AwakenTheme.type.label, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.screentime_minutes_of_limit, row.usedMinutes, limit),
                style = AwakenTheme.type.numericSmall,
                color = if (row.breached) colors.critical else colors.textSecondary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AwakenTheme.dimens.xpBarHeight)
                .clip(AwakenTheme.shapes.xpBar)
                .background(colors.divider),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(if (row.breached || fraction >= WARN_FRACTION) colors.critical else colors.accent),
            )
        }
    }
}

@Composable
private fun AppsWindow(state: ScreenTimeUiState, onEvent: (ScreenTimeEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    SystemWindow(title = stringResource(R.string.screentime_window_apps), accent = colors.accent, modifier = modifier, reveal = false) {
        Text(stringResource(R.string.screentime_apps_note), style = AwakenTheme.type.caption, color = colors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ChoiceChip(text = stringResource(R.string.screentime_filter_all), selected = state.filter == null, onClick = { onEvent(ScreenTimeEvent.Filter(null)) })
            AppCategory.entries.forEach { category ->
                ChoiceChip(
                    text = stringResource(category.labelRes),
                    selected = state.filter == category,
                    onClick = { onEvent(ScreenTimeEvent.Filter(category)) },
                    color = categoryColor(category),
                )
            }
        }
        val apps = state.visibleApps
        if (apps.isEmpty()) {
            Text(stringResource(R.string.screentime_apps_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
        }
        apps.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(thickness = AwakenTheme.dimens.hairline, color = colors.divider)
            AppRow(row, onClick = { onEvent(ScreenTimeEvent.OpenApp(row.packageName)) })
        }
        if (state.hiddenCount > 0 || state.showUnused) {
            QuietButton(
                text = if (state.showUnused) {
                    stringResource(R.string.screentime_hide_unused)
                } else {
                    pluralStringResource(R.plurals.screentime_show_unused, state.hiddenCount, state.hiddenCount)
                },
                onClick = { onEvent(ScreenTimeEvent.ToggleUnused) },
            )
        }
    }
}

@Composable
private fun AppRow(row: AppUsageRow, onClick: () -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val editLabel = stringResource(R.string.screentime_edit_app, row.label)
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
                add(stringResource(row.category.labelRes))
                row.limitMinutes?.let { add(stringResource(R.string.screentime_limit_short, it)) }
            }.joinToString(" · ")
            Text(detail, style = type.label, color = colors.textSecondary)
            if (row.hasPendingChange) {
                val next = row.nextCategory ?: row.category
                val nextText = row.nextLimitMinutes?.let { stringResource(R.string.screentime_pending_limit, stringResource(next.labelRes), it) }
                    ?: stringResource(R.string.screentime_pending, stringResource(next.labelRes))
                Text(nextText, style = type.caption, color = colors.reward)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMinutes(row.usedMinutes), style = type.numericSmall, color = if (row.breached) colors.critical else colors.textPrimary)
            Pill(stringResource(row.category.shortRes), categoryColor(row.category))
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
        Box(
            modifier = Modifier
                .size(size)
                .clip(AwakenTheme.shapes.button)
                .background(AwakenTheme.colors.surfaceOverlay),
        )
    }
}

@Composable
private fun TrendWindow(trend: List<ScreenTimeDay>, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val max = (trend.maxOfOrNull { it.totalMinutes } ?: 0).coerceAtLeast(1)
    SystemWindow(title = stringResource(R.string.screentime_window_trend), accent = colors.accent, modifier = modifier, reveal = false) {
        Text(stringResource(R.string.screentime_trend_note), style = AwakenTheme.type.caption, color = colors.textSecondary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AwakenTheme.dimens.trendBarHeight),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.Bottom,
        ) {
            trend.forEach { day ->
                val description = stringResource(R.string.screentime_trend_day, dayName(day.gameDate), formatMinutes(day.totalMinutes), formatMinutes(day.distractingMinutes))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics(mergeDescendants = true) { contentDescription = description },
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val total = day.totalMinutes.toFloat() / max
                    val distracting = day.distractingMinutes.toFloat() / max
                    Box(modifier = Modifier.fillMaxWidth().weight((total - distracting).coerceAtLeast(MIN_BAR), fill = true).background(colors.divider))
                    if (distracting > 0f) {
                        Box(modifier = Modifier.fillMaxWidth().weight(distracting, fill = true).background(colors.critical))
                    }
                    Spacer(modifier = Modifier.weight((1f - total).coerceAtLeast(MIN_BAR)))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
            trend.forEach { day ->
                Text(dayName(day.gameDate), style = AwakenTheme.type.caption, color = colors.textSecondary, modifier = Modifier.weight(1f))
            }
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
        Text(stringResource(R.string.screentime_editor_category), style = type.label, color = colors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            AppCategory.entries.forEach { category ->
                ChoiceChip(
                    text = stringResource(category.labelRes),
                    selected = editor.category == category,
                    onClick = { onEvent(ScreenTimeEvent.EditCategory(category)) },
                    color = categoryColor(category),
                )
            }
        }
        if (editor.category == AppCategory.DISTRACTING) {
            Text(stringResource(R.string.screentime_editor_limit), style = type.label, color = colors.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val limit = editor.limitMinutes
                SecondaryButton(
                    text = stringResource(R.string.screentime_limit_minus),
                    onClick = { onEvent(ScreenTimeEvent.StepLimit(-ScreenTimeRules.LIMIT_STEP_MINUTES)) },
                    enabled = limit != null && limit > ScreenTimeRules.MIN_LIMIT_MINUTES,
                )
                Text(
                    text = limit?.let { formatMinutes(it) } ?: stringResource(R.string.screentime_no_limit),
                    style = type.numeric,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = stringResource(R.string.screentime_limit_plus),
                    onClick = { onEvent(ScreenTimeEvent.StepLimit(ScreenTimeRules.LIMIT_STEP_MINUTES)) },
                    enabled = limit == null || limit < ScreenTimeRules.MAX_LIMIT_MINUTES,
                )
            }
            QuietButton(
                text = stringResource(if (editor.limitMinutes == null) R.string.screentime_set_limit else R.string.screentime_remove_limit),
                onClick = { onEvent(ScreenTimeEvent.ToggleNoLimit) },
            )
        }
        Text(stringResource(R.string.screentime_editor_tomorrow), style = type.caption, color = colors.reward)
        PrimaryButton(text = stringResource(R.string.action_save), onClick = { onEvent(ScreenTimeEvent.SaveEditor) }, modifier = Modifier.fillMaxWidth())
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

@Composable
internal fun formatMinutes(minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0 -> stringResource(R.string.screentime_duration_minutes, formatCount(rest))
        rest == 0 -> stringResource(R.string.screentime_duration_hours, formatCount(hours))
        else -> stringResource(R.string.screentime_duration_hours_minutes, formatCount(hours), formatCount(rest))
    }
}

private fun dayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())

private const val MINUTES_PER_HOUR = 60
private const val LIMIT_PREVIEW_COUNT = 4
private const val WARN_FRACTION = 0.84f
private const val MIN_BAR = 0.001f

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
