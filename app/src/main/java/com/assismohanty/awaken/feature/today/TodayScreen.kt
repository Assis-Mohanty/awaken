package com.assismohanty.awaken.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.BlockState
import com.assismohanty.awaken.core.designsystem.components.EmptyState
import com.assismohanty.awaken.core.designsystem.components.GreetingHeader
import com.assismohanty.awaken.core.designsystem.components.HeaderIconButton
import com.assismohanty.awaken.core.designsystem.components.HeroRing
import com.assismohanty.awaken.core.designsystem.components.HunterHeaderModel
import com.assismohanty.awaken.core.designsystem.components.InsightCard
import com.assismohanty.awaken.core.designsystem.components.LevelModule
import com.assismohanty.awaken.core.designsystem.components.QuestRow
import com.assismohanty.awaken.core.designsystem.components.QuestRowModel
import com.assismohanty.awaken.core.designsystem.components.QuestRowState
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.StreakModule
import com.assismohanty.awaken.core.designsystem.components.TimeBlockHeader
import com.assismohanty.awaken.core.designsystem.components.TimelineQuestRow
import com.assismohanty.awaken.core.designsystem.components.heroWash
import com.assismohanty.awaken.core.designsystem.components.penaltyTint
import com.assismohanty.awaken.core.designsystem.components.staggeredEntrance
import com.assismohanty.awaken.core.designsystem.lexicon.Term
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.lexicon.termString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
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
    onOpenCampaign: () -> Unit = {},
    onOpenGates: () -> Unit = {},
    onOpenVault: () -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
    habitsViewModel: HabitsViewModel = hiltViewModel(),
    dayPlanViewModel: DayPlanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val habits by habitsViewModel.uiState.collectAsStateWithLifecycle()
    val dayPlan by dayPlanViewModel.uiState.collectAsStateWithLifecycle()
    TodayScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenQuest = onOpenQuest,
        onOpenTrial = onOpenTrial,
        onHabitsEvent = habitsViewModel::onEvent,
        onDayPlanEvent = dayPlanViewModel::onEvent,
        modifier = modifier,
        onOpenScreenTime = onOpenScreenTime,
        onOpenCampaign = onOpenCampaign,
        onOpenGates = onOpenGates,
        onOpenVault = onOpenVault,
    )
    HabitSheets(state = habits, onEvent = habitsViewModel::onEvent)
    DayPlanSheet(state = dayPlan, onEvent = dayPlanViewModel::onEvent)
}

/**
 * Today (DESIGN_SYSTEM §4.2): a greeting over a soft wash, one hero ring for the day's progress, level and streak as
 * compact modules, the daily line as an insight, then the plan as a timeline grouped Morning → Day → Evening → Anytime
 * (GD §14.5). A penalty quest stays pinned above it and the screen carries a faint ember tint (GD §7.3).
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
    onOpenCampaign: () -> Unit = {},
    onOpenGates: () -> Unit = {},
    onOpenVault: () -> Unit = {},
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    Column(
        modifier = modifier
            .fillMaxSize()
            .penaltyTint(state.penaltyOpen)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val header = state.header
        // The hero area: greeting, the day's ring and the game modules, over a soft wash.
        Box(modifier = Modifier.fillMaxWidth().heroWash(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = Spacing.contentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                GreetingHeader(
                    greeting = greeting(state.currentBlock, header?.name),
                    subtitle = state.gameDate?.let(::formatDay),
                    modifier = Modifier.staggeredEntrance(0),
                ) {
                    HeaderIconButton(AwakenGlyphs.Portal, lexString(R.string.action_open_gates), onOpenGates)
                    HeaderIconButton(AwakenGlyphs.Vault, lexString(R.string.action_open_vault), onOpenVault)
                }
                if (state.loading || header == null) {
                    Text(lexString(R.string.today_loading), style = type.body, color = colors.textSecondary)
                    return@Column
                }
                HeroRing(
                    value = state.completed.toString(),
                    unitLine = lexString(R.string.today_hero_unit, state.total),
                    kicker = lexString(if (state.cleared) R.string.system_title_directive_cleared else R.string.nav_today),
                    progress = if (state.total > 0) state.completed.toFloat() / state.total else 0f,
                    description = lexString(R.string.today_hero_cd, state.completed, state.total),
                    modifier = Modifier.staggeredEntrance(1),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().staggeredEntrance(2),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    LevelModule(
                        level = header.level,
                        rank = header.rank,
                        xpIntoLevel = header.xpIntoLevel,
                        xpForLevel = header.xpForLevel,
                        modifier = Modifier.weight(LEVEL_WEIGHT),
                    )
                    StreakModule(streak = header.streak, best = state.bestStreak, modifier = Modifier.weight(1f))
                }
            }
        }
        if (state.loading || header == null) return@Column

        Column(
            modifier = Modifier
                .widthIn(max = Spacing.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            state.systemLine?.let { line ->
                InsightCard(title = termString(Term.SYSTEM_MESSAGE), body = line, modifier = Modifier.staggeredEntrance(3))
            }

            state.penalty?.let { penalty ->
                AwakenCard(title = lexString(R.string.quest_kind_penalty), accent = colors.critical, modifier = Modifier.staggeredEntrance(3)) {
                    QuestRow(
                        model = penalty,
                        onComplete = { onEvent(TodayEvent.Complete(penalty.id)) },
                        onOpen = { onOpenQuest(penalty.id) },
                        showDivider = false,
                    )
                }
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

            Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = lexString(R.string.system_title_daily_directive),
                    style = type.section,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                QuietButton(text = lexString(R.string.action_day_plan), onClick = { onDayPlanEvent(DayPlanEvent.Open) })
            }

            Timeline(state, onEvent, onOpenQuest)

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = lexString(R.string.today_habits),
                    style = type.title,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                QuietButton(text = lexString(R.string.action_manage_habits), onClick = { onHabitsEvent(HabitsEvent.OpenList) })
            }
            if (state.habits.isEmpty()) {
                EmptyState(
                    line = lexString(R.string.today_no_habits),
                    actionLabel = lexString(R.string.action_add_habit),
                    onAction = { onHabitsEvent(HabitsEvent.Add) },
                )
            }

            CampaignCardWindow(state, onOpenCampaign)

            state.discipline?.let { card ->
                DisciplineCardWindow(card, onOpenScreenTime)
            }
        }
    }
}

/**
 * The plan as a vertical timeline (GD §14.5): Morning → Day → Evening → Anytime, empty blocks hidden. The block "now"
 * is in is marked; earlier blocks start collapsed and expand on tap, and their open quests stay completable.
 */
@Composable
private fun Timeline(state: TodayUiState, onEvent: (TodayEvent) -> Unit, onOpenQuest: (String) -> Unit) {
    val quests = listOfNotNull(state.main) + state.paths + state.sides + state.habits
    val byBlock = quests.groupBy { it.timeOfDay }.toSortedMap(compareBy(TimeBlocks::order))
    val expanded = rememberSaveable(saver = expandedSaver) { mutableStateMapOf() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        byBlock.forEach { (block, rows) ->
            val blockState = blockState(block, state.currentBlock)
            val key = block?.name ?: ANYTIME_KEY
            val isExpanded = expanded[key] ?: (blockState != BlockState.PAST)
            TimeBlockHeader(
                block = block.toTimeBlock(),
                done = rows.count { it.state == QuestRowState.DONE },
                total = rows.size,
                state = blockState,
                expanded = isExpanded,
                onToggle = { expanded[key] = !isExpanded },
            )
            if (isExpanded) {
                rows.forEachIndexed { index, row ->
                    TimelineQuestRow(
                        model = row,
                        onComplete = { onEvent(TodayEvent.Complete(row.id)) },
                        onOpen = { onOpenQuest(row.id) },
                        first = index == 0,
                        last = index == rows.lastIndex,
                    )
                }
            }
        }
    }
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

@Composable
private fun DisciplineCardWindow(card: DisciplineCard, onOpenScreenTime: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    AwakenCard(
        title = lexString(R.string.quest_kind_discipline),
        accent = if (card.held) colors.positive else colors.critical,
        modifier = modifier,
        reveal = false,
        headerTrailing = { QuietButton(text = lexString(R.string.action_open_screen_time), onClick = onOpenScreenTime) },
    ) {
        Text(
            text = if (card.held) {
                lexString(R.string.today_discipline_on_track, card.xpAtStake)
            } else {
                lexString(R.string.today_discipline_forfeited)
            },
            style = AwakenTheme.type.body,
            color = if (card.held) colors.textPrimary else colors.textSecondary,
        )
    }
}

/** The campaign on Today (GD §6.8): its day and act while one runs, otherwise a quiet offer to begin one. */
@Composable
private fun CampaignCardWindow(state: TodayUiState, onOpenCampaign: () -> Unit) {
    val colors = AwakenTheme.colors
    val card = state.campaign
    if (card == null && !state.campaignOffer) return
    AwakenCard(
        title = lexString(R.string.campaign_window_progress),
        accent = if (card != null) colors.reward else colors.accent,
        reveal = false,
        headerTrailing = { QuietButton(text = lexString(R.string.action_open_campaign), onClick = onOpenCampaign) },
    ) {
        Text(
            text = if (card != null) {
                lexString(R.string.today_campaign_day, card.dayIndex, lexString(card.act.todayNameRes))
            } else {
                lexString(R.string.today_campaign_begin)
            },
            style = if (card != null) AwakenTheme.type.numeric else AwakenTheme.type.body,
            color = if (card != null) colors.textPrimary else colors.textSecondary,
        )
    }
}

private val CampaignAct.todayNameRes: Int
    get() = when (this) {
        CampaignAct.FOUNDATION -> R.string.campaign_act_foundation
        CampaignAct.BUILD -> R.string.campaign_act_build
        CampaignAct.ASCEND -> R.string.campaign_act_ascend
    }

private const val LEVEL_WEIGHT = 1.4f
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
)

@PhonePreviews
@Composable
private fun TodayScreenPreview() {
    AwakenTheme { TodayScreen(previewTodayState, onEvent = {}, onOpenQuest = {}, onOpenTrial = {}, onHabitsEvent = {}, onDayPlanEvent = {}) }
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
        )
    }
}
