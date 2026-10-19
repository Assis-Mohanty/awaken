package com.assismohanty.awaken.feature.today

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.EmptyState
import com.assismohanty.awaken.core.designsystem.components.HunterHeader
import com.assismohanty.awaken.core.designsystem.components.HunterHeaderModel
import com.assismohanty.awaken.core.designsystem.components.QuestRow
import com.assismohanty.awaken.core.designsystem.components.QuestRowModel
import com.assismohanty.awaken.core.designsystem.components.QuestRowState
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.titleNameRes
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource

@Composable
fun TodayRoute(
    onOpenQuest: (questInstanceId: String) -> Unit,
    onOpenTrial: () -> Unit,
    modifier: Modifier = Modifier,
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
    )
    HabitSheets(state = habits, onEvent = habitsViewModel::onEvent)
    DayPlanSheet(state = dayPlan, onEvent = dayPlanViewModel::onEvent)
}

/**
 * Today (DESIGN_SYSTEM §4.2): header, penalty window, Rank Trial slot, the directive, habits, and one System line.
 * While a penalty quest is open the screen carries an ember tint (GD §7.3: visual pressure only, nothing locked).
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
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (state.penaltyOpen) Modifier.background(colors.ember.copy(alpha = AwakenAlpha.HAZE)) else Modifier)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val header = state.header
            if (state.loading || header == null) {
                Text(stringResource(R.string.today_loading), style = type.body, color = colors.mist)
                return@Column
            }
            val title = state.equippedTitleId?.let(::titleNameRes)?.let { stringResource(it) }
            HunterHeader(header.copy(title = title))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                QuietButton(text = stringResource(R.string.action_day_plan), onClick = { onDayPlanEvent(DayPlanEvent.Open) })
            }

            state.penalty?.let { penalty ->
                SystemWindow(title = stringResource(R.string.quest_kind_penalty), accent = colors.ember) {
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

            SystemWindow(
                title = stringResource(if (state.cleared) R.string.system_title_directive_cleared else R.string.system_title_daily_directive),
                accent = clearedPulseAccent(state.cleared, base = colors.rank(state.rank)),
                headerTrailing = {
                    Text(
                        text = stringResource(R.string.today_progress, state.completed, state.total),
                        style = type.numericSmall,
                        color = colors.mist,
                    )
                },
            ) {
                val directive = listOfNotNull(state.main) + state.sides
                directive.forEachIndexed { index, row ->
                    QuestRow(
                        model = row,
                        onComplete = { onEvent(TodayEvent.Complete(row.id)) },
                        onOpen = { onOpenQuest(row.id) },
                        showDivider = index > 0,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.today_habits),
                        style = type.windowTitle,
                        color = colors.fog,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    QuietButton(text = stringResource(R.string.action_manage_habits), onClick = { onHabitsEvent(HabitsEvent.OpenList) })
                }
                if (state.habits.isEmpty()) {
                    EmptyState(
                        line = stringResource(R.string.today_no_habits),
                        actionLabel = stringResource(R.string.action_add_habit),
                        onAction = { onHabitsEvent(HabitsEvent.Add) },
                    )
                } else {
                    state.habits.forEach { row ->
                        QuestRow(
                            model = row,
                            onComplete = { onEvent(TodayEvent.Complete(row.id)) },
                            onOpen = { onOpenQuest(row.id) },
                        )
                    }
                }
            }

            state.systemLine?.let { line ->
                Text(text = line, style = type.body, color = colors.mist, modifier = Modifier.padding(horizontal = Spacing.xxs))
            }
        }
    }
}

private fun previewRow(id: String, title: String, stat: Stat, target: Int, unit: String, isMain: Boolean = false, done: Boolean = false) =
    QuestRowModel(
        id = id,
        title = title,
        stat = stat,
        target = target,
        unit = unit,
        difficulty = if (isMain) Difficulty.HARD else Difficulty.NORMAL,
        verification = VerificationSource.SELF,
        isMain = isMain,
        state = if (done) QuestRowState.DONE else QuestRowState.OPEN,
    )

private val previewState = TodayUiState(
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
    main = previewRow("m", "Iron Arms", Stat.STR, 80, "reps", isMain = true),
    sides = listOf(
        previewRow("s1", "Deep Dive", Stat.INT, 25, "min", done = true),
        previewRow("s2", "Ground Walk", Stat.VIT, 7_000, "steps"),
        previewRow("s3", "Still Mind", Stat.WIL, 10, "min"),
    ),
    habits = listOf(previewRow("h1", "Call home", Stat.CHA, 1, "call")),
    completed = 1,
    total = 5,
    systemLine = "INT is falling behind.",
)

@PhonePreviews
@Composable
private fun TodayScreenPreview() {
    AwakenTheme { TodayScreen(previewState, onEvent = {}, onOpenQuest = {}, onOpenTrial = {}, onHabitsEvent = {}, onDayPlanEvent = {}) }
}

@PhonePreviews
@Composable
private fun TodayScreenClearedPreview() {
    AwakenTheme {
        TodayScreen(
            previewState.copy(
                cleared = true,
                penalty = previewRow("p", "Redemption Run", Stat.VIT, 20, "min"),
                habits = emptyList(),
            ),
            onEvent = {},
            onOpenQuest = {},
            onOpenTrial = {},
            onHabitsEvent = {},
            onDayPlanEvent = {},
        )
    }
}
