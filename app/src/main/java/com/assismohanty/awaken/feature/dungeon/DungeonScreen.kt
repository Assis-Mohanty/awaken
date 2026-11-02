package com.assismohanty.awaken.feature.dungeon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.db.entity.DungeonEntity
import com.assismohanty.awaken.core.data.db.entity.DungeonStatus
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.ConfirmDialog
import com.assismohanty.awaken.core.designsystem.components.DungeonStageTrack
import com.assismohanty.awaken.core.designsystem.components.GateBackground
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.StageMark
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.formatCount
import com.assismohanty.awaken.core.designsystem.components.label
import com.assismohanty.awaken.core.designsystem.lexicon.Term
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.lexicon.termString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.AbandonDungeonUseCase
import com.assismohanty.awaken.core.domain.DungeonOverview
import com.assismohanty.awaken.core.domain.DungeonView
import com.assismohanty.awaken.core.domain.EnterDungeonResult
import com.assismohanty.awaken.core.domain.EnterDungeonUseCase
import com.assismohanty.awaken.core.domain.ObserveDungeonUseCase
import com.assismohanty.awaken.core.domain.StageState
import com.assismohanty.awaken.engine.DungeonEntry
import com.assismohanty.awaken.engine.DungeonRules
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DungeonUiState(
    val loading: Boolean = true,
    val overview: DungeonOverview? = null,
    val lengthDays: Int = DungeonRules.LENGTHS.first(),
    val stat: Stat = Stat.STR,
    val confirmEnter: Boolean = false,
    val confirmAbandon: Boolean = false,
    /** Why the last attempt to enter was refused, if it was. */
    val refused: DungeonEntry? = null,
) {
    val fee: Int get() = DungeonRules.fee(lengthDays) ?: 0
    val canAfford: Boolean get() = (overview?.gold ?: 0L) >= fee
}

sealed interface DungeonEvent {
    data class PickLength(val days: Int) : DungeonEvent
    data class PickStat(val stat: Stat) : DungeonEvent
    data object AskEnter : DungeonEvent
    data object DismissEnter : DungeonEvent
    data object ConfirmEnter : DungeonEvent
    data object AskAbandon : DungeonEvent
    data object DismissAbandon : DungeonEvent
    data object ConfirmAbandon : DungeonEvent
}

@HiltViewModel
class DungeonViewModel @Inject constructor(
    observe: ObserveDungeonUseCase,
    private val enter: EnterDungeonUseCase,
    private val abandon: AbandonDungeonUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(DungeonUiState())

    /** Re-reads once a minute while the screen is on, so a stage that arrives with the new day shows up. */
    private val ticks = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }

    val uiState: StateFlow<DungeonUiState> = combine(observe(ticks), local) { overview, local ->
        local.copy(loading = false, overview = overview)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DungeonUiState())

    fun onEvent(event: DungeonEvent) {
        when (event) {
            is DungeonEvent.PickLength -> local.update { it.copy(lengthDays = event.days, refused = null) }
            is DungeonEvent.PickStat -> local.update { it.copy(stat = event.stat) }
            DungeonEvent.AskEnter -> local.update { it.copy(confirmEnter = true) }
            DungeonEvent.DismissEnter -> local.update { it.copy(confirmEnter = false) }
            DungeonEvent.ConfirmEnter -> viewModelScope.launch {
                val state = local.value
                val result = enter(state.lengthDays, state.stat)
                local.update { it.copy(confirmEnter = false, refused = (result as? EnterDungeonResult.Refused)?.reason) }
            }
            DungeonEvent.AskAbandon -> local.update { it.copy(confirmAbandon = true) }
            DungeonEvent.DismissAbandon -> local.update { it.copy(confirmAbandon = false) }
            DungeonEvent.ConfirmAbandon -> viewModelScope.launch {
                abandon()
                local.update { it.copy(confirmAbandon = false) }
            }
        }
    }

    private companion object {
        const val TICK_MS = 60_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun DungeonRoute(
    onBack: () -> Unit,
    onOpenQuest: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DungeonViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DungeonScreen(state, viewModel::onEvent, onBack, onOpenQuest, modifier)
}

/**
 * The Dungeon screen (DESIGN_SYSTEM §9.3; "Challenge" in Clean words): locked below level 10, a picker of length and stat, the
 * run in progress, or how the last one ended. Failing only costs the fee already paid.
 */
@Composable
fun DungeonScreen(state: DungeonUiState, onEvent: (DungeonEvent) -> Unit, onBack: () -> Unit, onOpenQuest: (String) -> Unit, modifier: Modifier = Modifier) {
    val overview = state.overview
    GateBackground(rank = Rank.E, modifier = modifier) {
        LargeTitleScaffold(title = termString(Term.DUNGEON), onBack = onBack) {
            when {
                state.loading || overview == null -> Unit
                !overview.unlocked -> LockedWindow(overview.level)
                else -> {
                    val current = overview.current
                    if (current != null && current.dungeon.status == DungeonStatus.ACTIVE) {
                        RunningWindow(current, onEvent, onOpenQuest)
                    } else {
                        current?.let { ResultWindow(it) }
                        PickerWindow(state, overview, onEvent)
                    }
                }
            }
        }
    }
    if (state.confirmEnter) {
        val left = (state.overview?.gold ?: 0L) - state.fee
        ConfirmDialog(
            title = lexString(R.string.dungeon_confirm_title, state.fee),
            body = lexString(R.string.dungeon_confirm_body, formatCount(left)),
            confirm = lexString(R.string.dungeon_confirm_action),
            dismiss = lexString(R.string.action_cancel),
            onConfirm = { onEvent(DungeonEvent.ConfirmEnter) },
            onDismiss = { onEvent(DungeonEvent.DismissEnter) },
        )
    }
    if (state.confirmAbandon) {
        ConfirmDialog(
            title = lexString(R.string.dungeon_abandon_title),
            body = lexString(R.string.dungeon_abandon_body),
            confirm = lexString(R.string.dungeon_abandon),
            dismiss = lexString(R.string.action_cancel),
            onConfirm = { onEvent(DungeonEvent.ConfirmAbandon) },
            onDismiss = { onEvent(DungeonEvent.DismissAbandon) },
        )
    }
}

@Composable
private fun LockedWindow(level: Int) {
    val colors = AwakenTheme.colors
    SystemWindow(title = lexString(R.string.dungeon_locked_title, DungeonRules.UNLOCK_LEVEL), accent = colors.accent, reveal = false) {
        Text(lexString(R.string.dungeon_locked_body, level), style = AwakenTheme.type.body, color = colors.textSecondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickerWindow(state: DungeonUiState, overview: DungeonOverview, onEvent: (DungeonEvent) -> Unit) {
    val colors = AwakenTheme.colors
    SystemWindow(title = null, accent = colors.accent, reveal = false) {
        Text(lexString(R.string.dungeon_picker_intro), style = AwakenTheme.type.body, color = colors.textSecondary)
        DungeonRules.LENGTHS.forEach { days ->
            val fee = requireNotNull(DungeonRules.fee(days))
            val reward = requireNotNull(DungeonRules.reward(days))
            val description = lexString(R.string.dungeon_length_card_cd, days, fee, reward)
            AwakenCard(
                reveal = false,
                onClick = { onEvent(DungeonEvent.PickLength(days)) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
            ) {
                Text(
                    lexString(R.string.dungeon_length_card, days, fee, reward),
                    style = if (days == state.lengthDays) AwakenTheme.type.title else AwakenTheme.type.body,
                    color = if (days == state.lengthDays) colors.accent else colors.textPrimary,
                )
            }
        }
        Text(lexString(R.string.dungeon_stat_label), style = AwakenTheme.type.label, color = colors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Stat.entries.forEach { stat ->
                ChoiceChip(text = stat.label(), selected = stat == state.stat, onClick = { onEvent(DungeonEvent.PickStat(stat)) }, color = colors.stat(stat))
            }
        }
        if (!state.canAfford) {
            Text(lexString(R.string.dungeon_not_enough, formatCount(overview.gold)), style = AwakenTheme.type.caption, color = colors.textSecondary)
        }
        state.refused?.takeIf { it == DungeonEntry.ALREADY_ACTIVE }?.let {
            Text(lexString(R.string.dungeon_active_already), style = AwakenTheme.type.caption, color = colors.textSecondary)
        }
        PrimaryButton(
            text = lexString(R.string.dungeon_enter, state.fee),
            onClick = { onEvent(DungeonEvent.AskEnter) },
            enabled = state.canAfford,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RunningWindow(view: DungeonView, onEvent: (DungeonEvent) -> Unit, onOpenQuest: (String) -> Unit) {
    val colors = AwakenTheme.colors
    val dungeon = view.dungeon
    val marks = view.stages.map {
        when (it) {
            StageState.DONE -> StageMark.DONE
            StageState.TODAY -> StageMark.TODAY
            StageState.UPCOMING -> StageMark.UPCOMING
            StageState.FAILED -> StageMark.FAILED
        }
    }
    val current = (view.stages.indexOfFirst { it == StageState.TODAY }.takeIf { it >= 0 } ?: view.stages.count { it == StageState.DONE }) + 1
    val done = view.stages.count { it == StageState.DONE }
    SystemWindow(title = lexString(R.string.dungeon_running_title), accent = colors.accent, reveal = false) {
        DungeonStageTrack(marks, description = lexString(R.string.dungeon_track_cd, current.coerceAtMost(dungeon.lengthDays), dungeon.lengthDays, done))
        Text(
            lexString(R.string.dungeon_stage_of, current.coerceAtMost(dungeon.lengthDays), dungeon.lengthDays),
            style = AwakenTheme.type.title,
            color = colors.textPrimary,
        )
        Text(dungeon.stat.label(), style = AwakenTheme.type.label, color = colors.stat(dungeon.stat))
        when {
            view.todayQuestId != null ->
                PrimaryButton(text = lexString(R.string.dungeon_open_today), onClick = { onOpenQuest(view.todayQuestId) }, modifier = Modifier.fillMaxWidth())
            view.stages.firstOrNull() == StageState.UPCOMING ->
                Text(lexString(R.string.dungeon_starts_tomorrow), style = AwakenTheme.type.body, color = colors.textSecondary)
            else -> Text(lexString(R.string.dungeon_stage_waiting), style = AwakenTheme.type.body, color = colors.textSecondary)
        }
        QuietButton(text = lexString(R.string.dungeon_abandon), onClick = { onEvent(DungeonEvent.AskAbandon) })
    }
}

@Composable
private fun ResultWindow(view: DungeonView) {
    val colors = AwakenTheme.colors
    val cleared = view.dungeon.status == DungeonStatus.CLEARED
    SystemWindow(
        title = lexString(if (cleared) R.string.dungeon_cleared_title else R.string.dungeon_failed_title),
        accent = if (cleared) colors.reward else colors.accent,
        reveal = false,
    ) {
        Text(
            if (cleared) {
                lexString(R.string.dungeon_cleared_body, DungeonRules.reward(view.dungeon.lengthDays) ?: 0)
            } else {
                lexString(R.string.dungeon_failed_body)
            },
            style = AwakenTheme.type.body,
            color = colors.textSecondary,
        )
    }
}

// Previews

private fun dungeon(status: DungeonStatus, days: Int = 3) = DungeonEntity(
    id = "d1", lengthDays = days, stat = Stat.INT, fee = DungeonRules.fee(days) ?: 0,
    enteredGameDate = LocalDate.of(2026, 11, 2), firstStageDate = LocalDate.of(2026, 11, 3), status = status,
)

private fun state(level: Int = 12, gold: Long = 520, current: DungeonView? = null) =
    DungeonUiState(loading = false, overview = DungeonOverview(level, gold, current))

@PhonePreviews
@Composable
private fun DungeonLockedPreview() = AwakenTheme(reduceMotion = true) { DungeonScreen(state(level = 7), {}, {}, {}) }

@PhonePreviews
@Composable
private fun DungeonPickerPreview() = AwakenTheme(reduceMotion = true) { DungeonScreen(state(), {}, {}, {}) }

@PhonePreviews
@Composable
private fun DungeonNotEnoughGoldPreview() = AwakenTheme(reduceMotion = true) { DungeonScreen(state(gold = 60), {}, {}, {}) }

@PhonePreviews
@Composable
private fun DungeonRunningPreview() = AwakenTheme(reduceMotion = true) {
    val view = DungeonView(dungeon(DungeonStatus.ACTIVE, 5), listOf(StageState.DONE, StageState.TODAY, StageState.UPCOMING, StageState.UPCOMING, StageState.UPCOMING), "q1")
    DungeonScreen(state(current = view), {}, {}, {})
}

@PhonePreviews
@Composable
private fun DungeonClearedPreview() = AwakenTheme(reduceMotion = true) {
    DungeonScreen(state(current = DungeonView(dungeon(DungeonStatus.CLEARED), List(3) { StageState.DONE }, null)), {}, {}, {})
}

@PhonePreviews
@Composable
private fun DungeonFailedPreview() = AwakenTheme(reduceMotion = true) {
    DungeonScreen(state(current = DungeonView(dungeon(DungeonStatus.FAILED), listOf(StageState.DONE, StageState.FAILED, StageState.UPCOMING), null)), {}, {}, {})
}

