package com.assismohanty.awaken.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.assismohanty.awaken.core.designsystem.components.HealthTile
import com.assismohanty.awaken.core.designsystem.components.HealthTileModel
import com.assismohanty.awaken.core.designsystem.components.ReelsTile
import com.assismohanty.awaken.core.designsystem.components.ScreenTimeTile
import com.assismohanty.awaken.core.designsystem.components.ScreenTimeTileModel
import com.assismohanty.awaken.feature.health.openHealthConnectSettings
import com.assismohanty.awaken.feature.screentime.openUsageAccessSettings
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.BlockState
import com.assismohanty.awaken.core.designsystem.components.EmptyState
import com.assismohanty.awaken.core.designsystem.components.HabitRowActions
import com.assismohanty.awaken.core.designsystem.components.HeaderIconButton
import com.assismohanty.awaken.core.designsystem.components.HunterHeaderModel
import com.assismohanty.awaken.core.designsystem.components.InsightCard
import com.assismohanty.awaken.core.designsystem.components.NeverMissTwiceLine
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.LevelModule
import com.assismohanty.awaken.core.designsystem.components.ListRow
import com.assismohanty.awaken.core.designsystem.components.QuestRow
import com.assismohanty.awaken.core.designsystem.components.QuestRowModel
import com.assismohanty.awaken.core.designsystem.components.QuestRowState
import com.assismohanty.awaken.core.designsystem.components.RingCard
import com.assismohanty.awaken.core.designsystem.components.RingSpec
import com.assismohanty.awaken.core.designsystem.components.SkeletonCard
import com.assismohanty.awaken.core.designsystem.components.StreakModule
import com.assismohanty.awaken.core.designsystem.components.TimeBlockHeader
import com.assismohanty.awaken.core.designsystem.components.formatCount
import com.assismohanty.awaken.core.designsystem.components.staggeredEntrance
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.feature.program.ProgramBreakHost
import com.assismohanty.awaken.feature.focus.FocusCard
import com.assismohanty.awaken.feature.focus.FocusCardState
import com.assismohanty.awaken.feature.focus.FocusCardViewModel
import com.assismohanty.awaken.engine.ConsistencyScore
import com.assismohanty.awaken.engine.MissLine
import com.assismohanty.awaken.engine.TimeBlocks
import com.assismohanty.awaken.engine.campaign.CampaignAct
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.TimeOfDay
import com.assismohanty.awaken.engine.model.VerificationSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TodayRoute(
    onOpenQuest: (questInstanceId: String) -> Unit,
    onOpenTrial: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenScreenTime: () -> Unit = {},
    onOpenReelStats: () -> Unit = {},
    onOpenCampaign: () -> Unit = {},
    onOpenGates: () -> Unit = {},
    onOpenFocusReport: () -> Unit = {},
    onOpenVault: () -> Unit = {},
    onOpenReview: () -> Unit = {},
    onOpenHealth: () -> Unit = {},
    onOpenWindDown: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    viewModel: TodayViewModel = hiltViewModel(),
    habitsViewModel: HabitsViewModel = hiltViewModel(),
    dayPlanViewModel: DayPlanViewModel = hiltViewModel(),
    summaryViewModel: TodaySummaryViewModel = hiltViewModel(),
    focusViewModel: FocusCardViewModel = hiltViewModel(),
) {
    val focus by focusViewModel.uiState.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val summary by summaryViewModel.uiState.collectAsStateWithLifecycle()
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
        onOpenScreenTime = onOpenScreenTime,
        onOpenReelStats = onOpenReelStats,
        onOpenCampaign = onOpenCampaign,
        onOpenReview = onOpenReview,
        onOpenGates = onOpenGates,
        onOpenFocusReport = onOpenFocusReport,
        onOpenVault = onOpenVault,
        summary = summary,
        focus = focus,
        onOpenHealth = onOpenHealth,
        onGrantUsageAccess = { openUsageAccessSettings(context) },
        bedtimeCard = {
            BedtimeCardRoute(
                currentBlock = state.currentBlock,
                onWindDown = onOpenWindDown,
                onGrantAccess = { openUsageAccessSettings(context) },
            )
        },
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
 * Today (DESIGN_SYSTEM §4.2): "Good morning, Sam" as the large title over the date; one ring card for the day ("3 of 10
 * done today"); Level and Streak as two metric cards; one insight; then the quests grouped Morning → Day → Evening →
 * Anytime (GD §14.5) as list rows with a hollow check. The penalty quest is a normal row with a Comeback chip at the top
 * of the current block (GD §7.3); planning, habits, campaign and screen limits follow as quiet rows.
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
    onOpenScreenTime: () -> Unit = {},
    onOpenReelStats: () -> Unit = {},
    onOpenCampaign: () -> Unit = {},
    onOpenGates: () -> Unit = {},
    onOpenFocusReport: () -> Unit = {},
    focus: FocusCardState = FocusCardState(),
    onOpenVault: () -> Unit = {},
    onOpenReview: () -> Unit = {},
    summary: TodaySummaryUiState = TodaySummaryUiState(),
    onOpenHealth: () -> Unit = {},
    onGrantUsageAccess: () -> Unit = {},
    onSetUpHealth: () -> Unit = {},
    bedtimeCard: @Composable () -> Unit = {},
) {
    val colors = AwakenTheme.colors
    val header = state.header
    LargeTitleScaffold(
        title = greeting(state.currentBlock, header?.name),
        subtitle = state.gameDate?.let(::formatDay),
        modifier = modifier,
        actions = {
            HeaderIconButton(AwakenGlyphs.Portal, lexString(R.string.action_open_gates), onOpenGates)
            HeaderIconButton(AwakenGlyphs.Vault, lexString(R.string.action_open_vault), onOpenVault)
        },
    ) {
        if (state.loading || header == null) {
            SkeletonCard()
            SkeletonCard(lines = 4)
            return@LargeTitleScaffold
        }
        RingCard(
            label = lexString(if (state.cleared) R.string.system_title_directive_cleared else R.string.nav_today),
            value = formatCount(state.completed),
            unit = lexString(R.string.today_ring_unit, state.total),
            secondary = lexString(R.string.today_ring_done),
            rings = listOf(RingSpec(if (state.total > 0) state.completed.toFloat() / state.total else 0f, colors.positive)),
            description = lexString(R.string.today_hero_cd, state.completed, state.total),
            modifier = Modifier.staggeredEntrance(0),
        )
        // Equal heights: the taller card (at large font sizes) sets both.
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).staggeredEntrance(1),
            horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            LevelModule(
                level = header.level,
                rank = header.rank,
                xpIntoLevel = header.xpIntoLevel,
                xpForLevel = header.xpForLevel,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StreakModule(
                streak = header.streak,
                best = state.bestStreak,
                consistency = state.consistency,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        val missLine = state.missLine
        if (missLine != null) {
            NeverMissTwiceLine(lexString(missLine.stringRes()), modifier = Modifier.staggeredEntrance(2))
        } else {
            state.systemLine?.let { line -> InsightCard(text = line, modifier = Modifier.staggeredEntrance(2)) }
        }

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

        // Phone feedback Part 2: today's screen time and body numbers, above the quests; each opens its detail screen.
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

        summary.reelsToday?.let { ReelsTile(total = it, lumi = summary.reelsLumi, onClick = onOpenReelStats, modifier = Modifier.fillMaxWidth()) }

        FocusCard(state = focus, onStart = onOpenGates, onOpenReport = onOpenFocusReport)

        Timeline(state, onEvent, onOpenQuest, onHabitsEvent)

        if (state.reviewOpen) ReviewRow(onOpenReview)
        PlanCard(state, onHabitsEvent, onDayPlanEvent)
        CampaignRow(state, onOpenCampaign)
        state.discipline?.let { card -> DisciplineRow(card, onOpenScreenTime) }
        bedtimeCard()
    }
}

/**
 * The quests as sections (GD §14.5): Morning → Day → Evening → Anytime, empty blocks hidden, each a card of list rows.
 * The block "now" is in is marked; earlier blocks start collapsed and expand on tap, and their open quests stay
 * completable. An open penalty quest leads the current block.
 */
@Composable
private fun Timeline(
    state: TodayUiState,
    onEvent: (TodayEvent) -> Unit,
    onOpenQuest: (String) -> Unit,
    onHabitsEvent: (HabitsEvent) -> Unit,
) {
    val comeback = state.penalty?.copy(timeOfDay = state.currentBlock, comeback = true)
    val quests = listOfNotNull(comeback, state.main) + state.paths + state.sides + state.habits
    val byBlock = quests.groupBy { it.timeOfDay }.toSortedMap(compareBy(TimeBlocks::order))
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
                    }
                }
            }
        }
    }
}

/** Planning and habits as two quiet rows in one card; an empty habit list offers to add one. */
@Composable
private fun PlanCard(state: TodayUiState, onHabitsEvent: (HabitsEvent) -> Unit, onDayPlanEvent: (DayPlanEvent) -> Unit) {
    val colors = AwakenTheme.colors
    AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
        ListRow(
            title = lexString(R.string.action_day_plan),
            icon = AwakenGlyphs.Calendar,
            iconTint = colors.stat(Stat.WIL),
            secondary = lexString(R.string.today_plan_secondary),
            onClick = { onDayPlanEvent(DayPlanEvent.Open) },
            trailing = { Chevron() },
        )
        ListRow(
            title = lexString(R.string.today_habits),
            icon = AwakenGlyphs.Directive,
            iconTint = colors.stat(Stat.CHA),
            secondary = if (state.habits.isEmpty()) null else lexPlural(R.plurals.today_habits_count, state.habits.size, state.habits.size),
            onClick = { onHabitsEvent(HabitsEvent.OpenList) },
            trailing = { Chevron() },
        )
        if (state.habits.isEmpty()) {
            EmptyState(
                line = lexString(R.string.today_no_habits),
                actionLabel = lexString(R.string.action_add_habit),
                onAction = { onHabitsEvent(HabitsEvent.Add) },
            )
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

/** The campaign (GD §6.8): its day and act while one runs, otherwise a quiet offer to begin one. */
@Composable
private fun CampaignRow(state: TodayUiState, onOpenCampaign: () -> Unit) {
    val card = state.campaign
    if (card == null && !state.campaignOffer) return
    AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
        ListRow(
            title = lexString(R.string.campaign_window_progress),
            icon = AwakenGlyphs.Path,
            iconTint = AwakenTheme.colors.reward,
            secondary = if (card != null) {
                lexString(R.string.today_campaign_day, card.dayIndex, lexString(card.act.todayNameRes))
            } else {
                lexString(R.string.today_campaign_begin)
            },
            onClick = onOpenCampaign,
            trailing = { Chevron() },
        )
    }
}

@Composable
private fun DisciplineRow(card: DisciplineCard, onOpenScreenTime: () -> Unit) {
    val colors = AwakenTheme.colors
    AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
        ListRow(
            title = lexString(R.string.quest_kind_discipline),
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

@Composable
private fun greeting(block: TimeOfDay, name: String?): String {
    val res = when (block) {
        TimeOfDay.MORNING -> R.string.today_greeting_morning
        TimeOfDay.DAY -> R.string.today_greeting_day
        TimeOfDay.EVENING -> R.string.today_greeting_evening
    }
    return lexString(res, name.orEmpty()).trimEnd(',', ' ')
}

private fun formatDay(date: LocalDate): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(date)

private val CampaignAct.todayNameRes: Int
    get() = when (this) {
        CampaignAct.FOUNDATION -> R.string.campaign_act_foundation
        CampaignAct.BUILD -> R.string.campaign_act_build
        CampaignAct.ASCEND -> R.string.campaign_act_ascend
    }

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
        title = null,
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
    systemLine = "INT is falling behind. One short study block puts it back on track.",
    currentBlock = TimeOfDay.DAY,
    gameDate = LocalDate.of(2026, 10, 27),
    bestStreak = 30,
    consistency = ConsistencyScore(cleared = 22, eligible = 28),
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

@PhonePreviews
@Composable
private fun TodayScreenClearedPreview() {
    AwakenTheme {
        TodayScreen(
            previewTodayState.copy(
                cleared = true,
                completed = 6,
                penalty = previewRow("p", "Take a short walk", Stat.VIT, 20, "min", block = null),
                habits = emptyList(),
                currentBlock = TimeOfDay.EVENING,
            ),
            onEvent = {},
            onOpenQuest = {},
            onOpenTrial = {},
            onHabitsEvent = {},
            onDayPlanEvent = {},
            summary = previewSummary,
        )
    }
}

@PhonePreviews
@Composable
private fun TodayMissedYesterdayPreview() {
    AwakenTheme {
        TodayScreen(
            previewTodayState.copy(missLine = MissLine.MISSED_YESTERDAY, consistency = ConsistencyScore(cleared = 21, eligible = 28)),
            onEvent = {},
            onOpenQuest = {},
            onOpenTrial = {},
            onHabitsEvent = {},
            onDayPlanEvent = {},
            summary = previewSummary,
        )
    }
}

private fun MissLine.stringRes(): Int = when (this) {
    MissLine.MISSED_YESTERDAY -> R.string.miss_line_yesterday
    MissLine.FRESH_START -> R.string.miss_line_fresh_start
}
