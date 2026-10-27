package com.assismohanty.awaken.feature.today

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.components.penaltyTint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.engine.campaign.CampaignAct
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.EmptyState
import com.assismohanty.awaken.core.designsystem.components.HunterHeader
import com.assismohanty.awaken.core.designsystem.components.HunterHeaderModel
import com.assismohanty.awaken.core.designsystem.components.QuestRow
import com.assismohanty.awaken.core.designsystem.components.QuestRowModel
import com.assismohanty.awaken.core.designsystem.components.QuestRowState
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.staggeredEntrance
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
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Gates and Vault live up here since Gym and Diet took their tabs.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                HeaderAction(AwakenGlyphs.Portal, lexString(R.string.action_open_gates), onOpenGates)
                HeaderAction(AwakenGlyphs.Vault, lexString(R.string.action_open_vault), onOpenVault)
            }
            val header = state.header
            if (state.loading || header == null) {
                Text(lexString(R.string.today_loading), style = type.body, color = colors.textSecondary)
                return@Column
            }
            val title = state.equippedTitleId?.let(::titleNameRes)?.let { lexString(it) }
            HunterHeader(header.copy(title = title), modifier = Modifier.staggeredEntrance(0))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                QuietButton(text = lexString(R.string.action_day_plan), onClick = { onDayPlanEvent(DayPlanEvent.Open) })
            }

            state.penalty?.let { penalty ->
                SystemWindow(
                    title = lexString(R.string.quest_kind_penalty),
                    accent = colors.critical,
                    modifier = Modifier.staggeredEntrance(1),
                ) {
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
                title = lexString(if (state.cleared) R.string.system_title_directive_cleared else R.string.system_title_daily_directive),
                accent = clearedPulseAccent(state.cleared, base = AwakenTheme.rankAccent(state.rank)),
                modifier = Modifier.staggeredEntrance(2),
                headerTrailing = {
                    Text(
                        text = lexString(R.string.today_progress, state.completed, state.total),
                        style = type.numericSmall,
                        color = colors.textSecondary,
                    )
                },
            ) {
                val directive = listOfNotNull(state.main) + state.paths + state.sides
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
                        text = lexString(R.string.today_habits),
                        style = type.windowTitle,
                        color = colors.textMuted,
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

            CampaignCardWindow(state, onOpenCampaign)

            state.discipline?.let { card ->
                DisciplineCardWindow(card, onOpenScreenTime, Modifier.staggeredEntrance(3))
            }

            state.systemLine?.let { line ->
                Text(text = line, style = type.body, color = colors.textSecondary, modifier = Modifier.staggeredEntrance(4).padding(horizontal = Spacing.xxs))
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
    main = previewRow("m", "Do push-ups", Stat.STR, 80, "reps", isMain = true),
    sides = listOf(
        previewRow("s1", "Do deep work", Stat.INT, 25, "min", done = true),
        previewRow("s2", "Walk your steps", Stat.VIT, 7_000, "steps"),
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


/** Screen Discipline on Today (GD §6.7): status and stake; the details live on the Screen Time screen. */
@Composable
private fun HeaderAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = AwakenTheme.colors.textPrimary, modifier = Modifier.size(AwakenTheme.dimens.icon))
    }
}

@Composable
private fun DisciplineCardWindow(card: DisciplineCard, onOpenScreenTime: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    SystemWindow(
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
    SystemWindow(
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
