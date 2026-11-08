package com.assismohanty.awaken.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.BlockState
import com.assismohanty.awaken.core.designsystem.components.EmptyState
import com.assismohanty.awaken.core.designsystem.components.HabitRowActions
import com.assismohanty.awaken.core.designsystem.components.HealthTile
import com.assismohanty.awaken.core.designsystem.components.HealthTileModel
import com.assismohanty.awaken.core.designsystem.components.isLargeText
import com.assismohanty.awaken.core.designsystem.components.isSmallPhone
import com.assismohanty.awaken.core.designsystem.components.HunterHeaderModel
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.ListRow
import com.assismohanty.awaken.core.designsystem.components.QuestRow
import com.assismohanty.awaken.core.designsystem.components.QuestRowModel
import com.assismohanty.awaken.core.designsystem.components.QuestRowState
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.ReelsTile
import com.assismohanty.awaken.core.designsystem.components.ScreenTimeTile
import com.assismohanty.awaken.core.designsystem.components.ScreenTimeTileModel
import com.assismohanty.awaken.core.designsystem.components.SectionHeader
import com.assismohanty.awaken.core.designsystem.components.SkeletonCard
import com.assismohanty.awaken.core.designsystem.components.TimeBlockHeader
import com.assismohanty.awaken.core.designsystem.components.TodayHeaderRow
import com.assismohanty.awaken.core.designsystem.components.staggeredEntrance
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.BedtimeToday
import com.assismohanty.awaken.engine.MissLine
import com.assismohanty.awaken.engine.TimeBlocks
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.TimeOfDay
import com.assismohanty.awaken.engine.model.VerificationSource
import com.assismohanty.awaken.feature.health.openHealthConnectSettings
import com.assismohanty.awaken.feature.program.ProgramBreakHost
import com.assismohanty.awaken.feature.screentime.openUsageAccessSettings
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayRoute(
    onOpenQuest: (questInstanceId: String) -> Unit,
    onOpenTrial: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProgress: () -> Unit = {},
    onOpenScreenTime: () -> Unit = {},
    onOpenReelStats: () -> Unit = {},
    onOpenGates: () -> Unit = {},
    onOpenQuit: () -> Unit = {},
    onOpenReview: () -> Unit = {},
    onOpenHealth: () -> Unit = {},
    onOpenWindDown: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    viewModel: TodayViewModel = hiltViewModel(),
    habitsViewModel: HabitsViewModel = hiltViewModel(),
    dayPlanViewModel: DayPlanViewModel = hiltViewModel(),
    summaryViewModel: TodaySummaryViewModel = hiltViewModel(),
    bedtimeViewModel: BedtimeCardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val summary by summaryViewModel.uiState.collectAsStateWithLifecycle()
    val bedtime by bedtimeViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Usage and Health Connect change outside the app (and after granting access in Settings): re-read on every return.
    LifecycleResumeEffect(summaryViewModel) {
        summaryViewModel.refresh()
        onPauseOrDispose { }
    }
    val habits by habitsViewModel.uiState.collectAsStateWithLifecycle()
    val dayPlan by dayPlanViewModel.uiState.collectAsStateWithLifecycle()
    // A Timed habit's session started: open the Focus screen where it runs (GD §16.1).
    LaunchedEffect(viewModel) { viewModel.gateEntered.collect { onOpenGates() } }
    TodayScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenQuest = onOpenQuest,
        onOpenTrial = onOpenTrial,
        onHabitsEvent = habitsViewModel::onEvent,
        onDayPlanEvent = dayPlanViewModel::onEvent,
        modifier = modifier,
        onOpenProgress = onOpenProgress,
        onOpenScreenTime = onOpenScreenTime,
        onOpenReelStats = onOpenReelStats,
        onOpenReview = onOpenReview,
        onOpenQuit = onOpenQuit,
        summary = summary,
        bedtime = bedtime,
        onOpenWindDown = onOpenWindDown,
        onOpenHealth = onOpenHealth,
        onGrantUsageAccess = { openUsageAccessSettings(context) },
        onSetUpHealth = { if (summary.healthNeedsConnect) onOpenSettings() else openHealthConnectSettings(context) },
    )
    HabitSheets(state = habits, onEvent = habitsViewModel::onEvent)
    // The question after a broken program day (GD §16.15), asked on the next open.
    ProgramBreakHost()
    DayPlanSheet(state = dayPlan, onEvent = dayPlanViewModel::onEvent)
    HabitUndoSnackbar(habits.undo, snackbarHostState, habitsViewModel::onEvent)
}

/** "Habit deleted · Undo" after an archive or delete, through the app-wide snackbar host. */
@Composable
private fun HabitUndoSnackbar(undo: HabitUndo?, host: SnackbarHostState?, onEvent: (HabitsEvent) -> Unit) {
    if (undo == null || host == null) return
    val message = lexString(
        if (undo.action == HabitRemoveAction.DELETE) R.string.habit_deleted_snack else R.string.habit_archived_snack,
        undo.title,
    )
    val action = lexString(R.string.action_undo)
    LaunchedEffect(undo.token) {
        val result = host.showSnackbar(message = message, actionLabel = action, duration = SnackbarDuration.Long)
        onEvent(if (result == SnackbarResult.ActionPerformed) HabitsEvent.Undo(undo.token) else HabitsEvent.UndoHandled(undo.token))
    }
}

/**
 * Today (DECLUTTER_PLAN §4): the date as the title, then the live metrics (screen time, steps and health, reels) the
 * moment the tab opens, then Header (level, XP, streak; opens Progress) → Today's plan (comeback, rank-up test,
 * Morning / Day / Evening / Anytime, weekly review) → Quit counters (only when one exists). Focus has its own tab.
 */
@Composable
fun TodayScreen(
    state: TodayUiState,
    onEvent: (TodayEvent) -> Unit,
    onOpenQuest: (String) -> Unit,
    onOpenTrial: () -> Unit,
    onHabitsEvent: (HabitsEvent) -> Unit,
    onDayPlanEvent: (DayPlanEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenProgress: () -> Unit = {},
    onOpenScreenTime: () -> Unit = {},
    onOpenReelStats: () -> Unit = {},
    onOpenQuit: () -> Unit = {},
    onOpenReview: () -> Unit = {},
    summary: TodaySummaryUiState = TodaySummaryUiState(),
    bedtime: BedtimeToday? = null,
    onOpenWindDown: () -> Unit = {},
    onOpenHealth: () -> Unit = {},
    onGrantUsageAccess: () -> Unit = {},
    onSetUpHealth: () -> Unit = {},
) {
    val header = state.header
    LargeTitleScaffold(
        title = state.gameDate?.let(::formatDay) ?: lexString(R.string.nav_today),
        modifier = modifier,
    ) {
        // Steps, screen time and reels lead the tab: they don't wait for the plan to load.
        TodayMetrics(
            summary = summary,
            onOpenScreenTime = onOpenScreenTime,
            onOpenHealth = onOpenHealth,
            onOpenReelStats = onOpenReelStats,
            onGrantUsageAccess = onGrantUsageAccess,
            onSetUpHealth = onSetUpHealth,
        )
        if (state.loading || header == null) {
            SkeletonCard()
            SkeletonCard(lines = 4)
            return@LargeTitleScaffold
        }
        TodayHeaderRow(
            level = header.level,
            rank = header.rank,
            xpIntoLevel = header.xpIntoLevel,
            xpForLevel = header.xpForLevel,
            streak = header.streak,
            onClick = onOpenProgress,
            modifier = Modifier.staggeredEntrance(0),
        )
        TodayPlan(
            state = state,
            bedtime = bedtime,
            onEvent = onEvent,
            onOpenQuest = onOpenQuest,
            onOpenTrial = onOpenTrial,
            onHabitsEvent = onHabitsEvent,
            onDayPlanEvent = onDayPlanEvent,
            onOpenReview = onOpenReview,
            onOpenScreenTime = onOpenScreenTime,
            onOpenWindDown = onOpenWindDown,
            onGrantUsageAccess = onGrantUsageAccess,
        )


        if (summary.quit.isNotEmpty()) {
            SectionHeader(title = lexString(R.string.today_section_quit))
            AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
                summary.quit.forEach { line ->
                    ListRow(title = quitLine(line), onClick = onOpenQuit, trailing = { Chevron() })
                }
            }
        }
    }
}

/** Screen time and health side by side (equal heights: the taller tile sets both), and the reel count under them when it is on. */
@Composable
private fun TodayMetrics(
    summary: TodaySummaryUiState,
    onOpenScreenTime: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenReelStats: () -> Unit,
    onGrantUsageAccess: () -> Unit,
    onSetUpHealth: () -> Unit,
) {
    if (isLargeText() || isSmallPhone()) {
        // Big text or a small phone: one full-width tile under the other, so nothing truncates.
        ScreenTimeTile(model = summary.screenTime, onClick = onOpenScreenTime, onGrantAccess = onGrantUsageAccess, modifier = Modifier.fillMaxWidth())
        HealthTile(model = summary.health, onClick = onOpenHealth, onSetUp = onSetUpHealth, modifier = Modifier.fillMaxWidth())
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            ScreenTimeTile(
                model = summary.screenTime,
                onClick = onOpenScreenTime,
                onGrantAccess = onGrantUsageAccess,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            HealthTile(
                model = summary.health,
                onClick = onOpenHealth,
                onSetUp = onSetUpHealth,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
    summary.reelsToday?.let { ReelsTile(total = it, onClick = onOpenReelStats, modifier = Modifier.fillMaxWidth()) }
}

/**
 * Today's plan: the section header with its two actions, a comeback row after a missed day, the rank-up test while one is
 * active, then the quests as blocks (GD §14.5) Morning → Day → Evening → Anytime with empty blocks hidden. The block "now"
 * is in is marked; earlier blocks start collapsed and expand on tap, and their open quests stay completable. The phone-down
 * row sits in Evening (tonight) or Morning (last night); the app-limits row in Anytime. The weekly review closes the plan.
 */
@Composable
private fun TodayPlan(
    state: TodayUiState,
    bedtime: BedtimeToday?,
    onEvent: (TodayEvent) -> Unit,
    onOpenQuest: (String) -> Unit,
    onOpenTrial: () -> Unit,
    onHabitsEvent: (HabitsEvent) -> Unit,
    onDayPlanEvent: (DayPlanEvent) -> Unit,
    onOpenReview: () -> Unit,
    onOpenScreenTime: () -> Unit,
    onOpenWindDown: () -> Unit,
    onGrantUsageAccess: () -> Unit,
) {
    SectionHeader(
        title = if (state.total > 0) lexString(R.string.today_plan_title, state.completed, state.total) else lexString(R.string.today_plan_title_empty),
    ) {
        QuietButton(text = lexString(R.string.action_plan_day), onClick = { onDayPlanEvent(DayPlanEvent.Open) })
        QuietButton(text = lexString(R.string.today_habits), onClick = { onHabitsEvent(HabitsEvent.OpenList) })
    }

    ComebackRow(state, onEvent, onOpenQuest)

    state.trial?.let { trial ->
        TrialWindow(
            trial = trial,
            stages = state.trialStages,
            onBegin = { onEvent(TodayEvent.BeginTrial) },
            onOpenTrial = onOpenTrial,
            onComplete = { onEvent(TodayEvent.Complete(it)) },
            onOpenQuest = onOpenQuest,
        )
    }

    Timeline(state, bedtime, onEvent, onOpenQuest, onHabitsEvent, onOpenScreenTime, onOpenWindDown, onGrantUsageAccess)

    if (state.habits.isEmpty()) {
        AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
            EmptyState(
                line = lexString(R.string.today_no_habits),
                actionLabel = lexString(R.string.action_add_habit),
                onAction = { onHabitsEvent(HabitsEvent.Add) },
            )
        }
    }
    if (state.reviewOpen) ReviewRow(onOpenReview)
}

/** After a missed day: the comeback quest with the calm "never miss twice" line, as one card. Nothing when neither exists. */
@Composable
private fun ComebackRow(state: TodayUiState, onEvent: (TodayEvent) -> Unit, onOpenQuest: (String) -> Unit) {
    val missLine = state.missLine
    val comeback = state.penalty?.copy(timeOfDay = state.currentBlock, comeback = true)
    if (missLine == null && comeback == null) return
    AwakenCard(
        reveal = false,
        title = lexString(R.string.today_comeback_title),
        modifier = Modifier.fillMaxWidth(),
    ) {
        missLine?.let { Text(lexString(it.stringRes()), style = AwakenTheme.type.body, color = AwakenTheme.colors.textSecondary) }
        comeback?.let {
            QuestRow(
                model = it,
                onComplete = { onEvent(TodayEvent.Complete(it.id)) },
                onOpen = { onOpenQuest(it.id) },
            )
        }
    }
}

@Composable
private fun Timeline(
    state: TodayUiState,
    bedtime: BedtimeToday?,
    onEvent: (TodayEvent) -> Unit,
    onOpenQuest: (String) -> Unit,
    onHabitsEvent: (HabitsEvent) -> Unit,
    onOpenScreenTime: () -> Unit,
    onOpenWindDown: () -> Unit,
    onGrantUsageAccess: () -> Unit,
) {
    val quests = listOfNotNull(state.main) + state.paths + state.sides + state.habits
    val bedtimeAt = bedtime?.let { bedtimeBlock(it, state.currentBlock) }
    val showBedtime = bedtime != null && bedtimeAt != null
    val showLimits = state.discipline != null
    val grouped = quests.groupBy { it.timeOfDay }.toMutableMap<TimeOfDay?, List<QuestRowModel>>()
    if (showBedtime) grouped.putIfAbsent(bedtimeAt, emptyList())
    if (showLimits) grouped.putIfAbsent(null, emptyList())
    val byBlock = grouped.toSortedMap(compareBy(TimeBlocks::order))
    val expanded = rememberSaveable(saver = expandedSaver) { mutableStateMapOf() }
    byBlock.forEach { (block, rows) ->
        val blockState = blockState(block, state.currentBlock)
        val key = block?.name ?: ANYTIME_KEY
        val isExpanded = expanded[key] ?: (blockState != BlockState.PAST)
        Column {
            TimeBlockHeader(
                block = block.toTimeBlock(),
                done = rows.count { it.state == QuestRowState.DONE },
                total = rows.size,
                state = blockState,
                expanded = isExpanded,
                onToggle = { expanded[key] = !isExpanded },
            )
            if (isExpanded) {
                AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero) {
                    Column {
                        rows.forEachIndexed { index, row ->
                            val habitId = state.habitIdByQuest[row.id]
                            QuestRow(
                                model = row,
                                onComplete = { onEvent(TodayEvent.Complete(row.id)) },
                                onOpen = { onOpenQuest(row.id) },
                                showDivider = index > 0,
                                onLongPress = habitId?.let { { onHabitsEvent(HabitsEvent.Options(it)) } },
                                longPressLabel = habitId?.let { lexString(R.string.action_habit_options) },
                                habitActions = HabitRowActions(
                                    onPlus = { onEvent(TodayEvent.HabitPlus(row.id)) },
                                    onMinus = { onEvent(TodayEvent.HabitMinus(row.id)) },
                                    onStartTimed = { onEvent(TodayEvent.HabitStartTimed(row.id)) },
                                    onKept = { onEvent(TodayEvent.HabitKept(row.id)) },
                                    onSlip = { onEvent(TodayEvent.HabitSlip(row.id)) },
                                    onUndoSlip = { onEvent(TodayEvent.HabitUndoSlip(row.id)) },
                                    onUndoSkip = { habitId?.let { onEvent(TodayEvent.HabitUndoSkip(it)) } },
                                ),
                            )
                        }
                        if (showBedtime && bedtimeAt == block && bedtime != null) {
                            BedtimeRow(bedtime, state.currentBlock, onWindDown = onOpenWindDown, onGrantAccess = onGrantUsageAccess)
                        }
                        val limits = state.discipline
                        if (block == null && limits != null) LimitsRow(limits, onOpenScreenTime)
                    }
                }
            }
        }
    }
}

/** The weekly review is open (GD §14.4): one quiet row, nothing to earn. */
@Composable
private fun ReviewRow(onOpenReview: () -> Unit) {
    AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
        ListRow(
            title = lexString(R.string.today_review_open),
            icon = AwakenGlyphs.Calendar,
            iconTint = AwakenTheme.colors.accent,
            secondary = lexString(R.string.today_review_open_body),
            onClick = onOpenReview,
            trailing = { Chevron() },
        )
    }
}

/** Screen discipline as an Anytime row: whether today's app limits are holding (GD §6.7). */
@Composable
private fun LimitsRow(card: DisciplineCard, onOpenScreenTime: () -> Unit) {
    val colors = AwakenTheme.colors
    ListRow(
        title = lexString(R.string.today_limits_title),
        icon = AwakenGlyphs.Phone,
        iconTint = if (card.held) colors.positive else colors.critical,
        secondary = if (card.held) {
            lexString(R.string.today_discipline_on_track, card.xpAtStake)
        } else {
            lexString(R.string.today_discipline_forfeited)
        },
        onClick = onOpenScreenTime,
        trailing = { Chevron() },
    )
}

@Composable
private fun quitLine(line: QuitSummaryLine): String =
    if (line.days <= 0) {
        lexString(R.string.today_quit_started_today, line.name)
    } else {
        lexPlural(R.plurals.today_quit_days, line.days, line.name, line.days)
    }

@Composable
private fun Chevron() {
    Icon(AwakenGlyphs.ChevronRight, contentDescription = null, tint = AwakenTheme.colors.textSecondary, modifier = Modifier.size(AwakenTheme.dimens.iconSmall))
}

/** Anytime is never "past": it has no clock time. */
private fun blockState(block: TimeOfDay?, current: TimeOfDay): BlockState = when {
    block == null -> BlockState.UPCOMING
    block == current -> BlockState.CURRENT
    block.ordinal < current.ordinal -> BlockState.PAST
    else -> BlockState.UPCOMING
}

private val expandedSaver = androidx.compose.runtime.saveable.Saver<androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>, List<Any>>(
    save = { map -> map.flatMap { (key, value) -> listOf(key, value) } },
    restore = { saved -> mutableStateMapOf<String, Boolean>().apply { saved.chunked(2).forEach { (k, v) -> put(k as String, v as Boolean) } } },
)

/** "Tuesday 3 November": the title (DECLUTTER_PLAN §4). */
private fun formatDay(date: LocalDate): String = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()).format(date)

private const val ANYTIME_KEY = "ANYTIME"

// region previews

private fun previewRow(
    id: String,
    title: String,
    stat: Stat,
    target: Int,
    unit: String,
    block: TimeOfDay?,
    isMain: Boolean = false,
    done: Boolean = false,
    verification: VerificationSource = VerificationSource.SELF,
) = QuestRowModel(
    id = id,
    title = title,
    stat = stat,
    target = target,
    unit = unit,
    difficulty = if (isMain) Difficulty.HARD else Difficulty.NORMAL,
    verification = verification,
    isMain = isMain,
    state = if (done) QuestRowState.DONE else QuestRowState.OPEN,
    timeOfDay = block,
)

internal val previewTodayState = TodayUiState(
    loading = false,
    header = HunterHeaderModel(
        name = "Assis",
        level = 24,
        rank = Rank.C,
        xpIntoLevel = 1_240,
        xpForLevel = 1_434,
        streak = 12,
        gold = 1_920,
    ),
    rank = Rank.C,
    main = previewRow("m", "Do push-ups", Stat.STR, 80, "reps", block = null, isMain = true),
    sides = listOf(
        previewRow("s0", "Get outside in daylight", Stat.VIT, 15, "min", block = TimeOfDay.MORNING, done = true),
        previewRow("s1", "Do deep work", Stat.INT, 25, "min", block = null, done = true, verification = VerificationSource.TIMER),
        previewRow("s2", "Walk your steps", Stat.VIT, 7_000, "steps", block = null, verification = VerificationSource.HEALTH),
        previewRow("s3", "Put screens away before bed", Stat.WIL, 30, "min", block = TimeOfDay.EVENING),
    ),
    habits = listOf(previewRow("h1", "Call your family", Stat.CHA, 1, "call", block = TimeOfDay.DAY)),
    completed = 2,
    total = 6,
    currentBlock = TimeOfDay.DAY,
    gameDate = LocalDate.of(2026, 10, 27),
    discipline = DisciplineCard(held = true, xpAtStake = 42),
)

internal val previewSummary = TodaySummaryUiState(
    screenTime = ScreenTimeTileModel.Tracked(distracting = 54, productive = 92, neutral = 31),
    health = HealthTileModel.Ready(steps = 8_412, distanceKm = 6.2, activeKcal = 412, sleepMinutes = 452),
)


@PhonePreviews
@Composable
private fun TodayScreenPreview() {
    AwakenTheme {
        TodayScreen(previewTodayState, onEvent = {}, onOpenQuest = {}, onOpenTrial = {}, onHabitsEvent = {}, onDayPlanEvent = {}, summary = previewSummary)
    }
}

/** No usage access and no health source: each tile offers its fix, never a 0. */
@PhonePreviews
@Composable
private fun TodayScreenNoAccessPreview() {
    AwakenTheme {
        TodayScreen(
            previewTodayState,
            onEvent = {},
            onOpenQuest = {},
            onOpenTrial = {},
            onHabitsEvent = {},
            onDayPlanEvent = {},
            summary = TodaySummaryUiState(screenTime = ScreenTimeTileModel.NoAccess, health = HealthTileModel.NoData),
        )
    }
}

/** Evening with a quit counter, a comeback after a missed day and no habits. */
@PhonePreviews
@Composable
private fun TodayScreenEveningPreview() {
    AwakenTheme {
        TodayScreen(
            previewTodayState.copy(
                cleared = true,
                completed = 6,
                penalty = previewRow("p", "Take a short walk", Stat.VIT, 20, "min", block = null),
                missLine = MissLine.MISSED_YESTERDAY,
                habits = emptyList(),
                currentBlock = TimeOfDay.EVENING,
            ),
            onEvent = {},
            onOpenQuest = {},
            onOpenTrial = {},
            onHabitsEvent = {},
            onDayPlanEvent = {},
            summary = previewSummary.copy(quit = listOf(QuitSummaryLine("q1", "Smoking", 12))),
        )
    }
}

private fun MissLine.stringRes(): Int = when (this) {
    MissLine.MISSED_YESTERDAY -> R.string.miss_line_yesterday
    MissLine.FRESH_START -> R.string.miss_line_fresh_start
}
