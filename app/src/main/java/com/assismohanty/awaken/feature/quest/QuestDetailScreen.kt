package com.assismohanty.awaken.feature.quest

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.BigValue
import com.assismohanty.awaken.core.designsystem.components.DifficultyChip
import com.assismohanty.awaken.core.designsystem.components.EmptyState
import com.assismohanty.awaken.core.designsystem.components.InsightCard
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.ListRow
import com.assismohanty.awaken.core.designsystem.components.PathQuestBadge
import com.assismohanty.awaken.core.designsystem.components.Pill
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SkeletonCard
import com.assismohanty.awaken.core.designsystem.components.StatChip
import com.assismohanty.awaken.core.designsystem.components.VerifiedBadge
import com.assismohanty.awaken.core.designsystem.components.formatCount
import com.assismohanty.awaken.core.designsystem.components.label
import com.assismohanty.awaken.core.designsystem.components.unitLabel
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.QuestReason
import com.assismohanty.awaken.engine.SetCounter
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource
import com.assismohanty.awaken.feature.paths.ReportSheet

@Composable
fun QuestDetailRoute(
    onBack: () -> Unit,
    onOpenGates: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuestDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onBack() }
    LaunchedEffect(state.enteredGate) { if (state.enteredGate) onOpenGates() }
    QuestDetailScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

/**
 * Quest detail (DESIGN_SYSTEM §4.3): the quest's kind over its title as the large title, then one card with the target
 * as the big number and its chips, the started state as an insight, what it trains and how it is checked as rows, the
 * set counter, why the System chose it, and the actions at the end.
 */
@Composable
fun QuestDetailScreen(
    state: QuestDetailUiState,
    onEvent: (QuestDetailEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loaded = !state.loading && !state.notFound
    LargeTitleScaffold(
        title = if (loaded) state.title else "",
        subtitle = if (loaded) lexString(state.type.kindRes) else null,
        onBack = onBack,
        modifier = modifier,
    ) {
        when {
            state.notFound -> EmptyState(line = lexString(R.string.msg_error), actionLabel = lexString(R.string.action_back), onAction = onBack)
            state.loading -> SkeletonCard()
            else -> QuestDetailContent(state, onEvent)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestDetailContent(state: QuestDetailUiState, onEvent: (QuestDetailEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    var showReport by rememberSaveable { mutableStateOf(false) }
    if (showReport) {
        ReportSheet(
            onReport = { reason ->
                showReport = false
                onEvent(QuestDetailEvent.Report(reason))
            },
            onDismiss = { showReport = false },
        )
    }

    // The target is the one big number.
    AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth()) {
        BigValue(formatCount(state.target), unitLabel(state.unit, state.target), large = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (state.type == QuestType.PENALTY) {
                Pill(lexString(R.string.chip_comeback), colors.critical)
            }
            StatChip(state.stat)
            DifficultyChip(state.difficulty)
            VerifiedBadge(state.verification)
        }
        state.pathName?.let { name ->
            PathQuestBadge(
                name = name,
                gate = state.gateRank?.let { rank -> lexString(R.string.path_badge_gate, rank.name, rank.durationMinutes) },
            )
        }
        if (state.description.isNotBlank()) Text(state.description, style = type.body, color = colors.textSecondary)
    }

    if (state.status == QuestStatus.STARTED) {
        val text = if (state.readyInSeconds > 0) {
            lexString(R.string.quest_ready_in, DateUtils.formatElapsedTime(state.readyInSeconds))
        } else {
            lexString(R.string.quest_started)
        }
        InsightCard(text = text, icon = AwakenGlyphs.Hourglass, tint = colors.stat(state.stat))
    }

    // What it trains (ENGINE_DECISIONS #5: primary full XP, secondary 30 %) and how it is checked.
    AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
        ListRow(
            title = lexString(R.string.quest_split_primary, state.stat.label()),
            icon = AwakenGlyphs.stat(state.stat),
            iconTint = colors.stat(state.stat),
        )
        state.secondaryStat?.let { secondary ->
            ListRow(
                title = lexString(R.string.quest_split_secondary, secondary.label()),
                icon = AwakenGlyphs.stat(secondary),
                iconTint = colors.stat(secondary),
            )
        }
        ListRow(
            title = lexString(state.verification.explanationRes),
            icon = state.verification.glyph,
            iconTint = colors.positive,
            secondary = if (state.minDurationSec > 0 && state.status == QuestStatus.ACTIVE && state.verification == VerificationSource.SELF) {
                lexString(R.string.quest_min_duration, DateUtils.formatElapsedTime(state.minDurationSec.toLong()))
            } else {
                null
            },
        )
    }

    state.setCounter?.let { counter ->
        SetCounterBlock(counter, target = state.target, unit = state.unit, busy = state.busy, onEvent = onEvent)
    }

    state.rationale?.let { rationale ->
        AwakenCard(title = lexString(R.string.quest_path_rationale_label), reveal = false, modifier = Modifier.fillMaxWidth()) {
            Text(rationale, style = type.body, color = colors.textPrimary)
        }
    }
    state.reason?.takeIf { state.rationale == null }?.let { reason ->
        AwakenCard(title = lexString(R.string.quest_reason_label), reveal = false, modifier = Modifier.fillMaxWidth()) {
            Text(reasonText(reason), style = type.body, color = colors.textPrimary)
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (state.canEnterGate) {
            state.gateRank?.let { rank ->
                Text(lexString(R.string.quest_gate_required), style = type.caption, color = colors.textSecondary)
                PrimaryButton(
                    lexString(R.string.action_enter_path_gate, rank.name, rank.durationMinutes),
                    onClick = { onEvent(QuestDetailEvent.EnterGate) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (state.canBegin) {
            PrimaryButton(lexString(R.string.action_begin), onClick = { onEvent(QuestDetailEvent.Begin) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        }
        if (state.verification == VerificationSource.SELF && (state.status == QuestStatus.ACTIVE || state.status == QuestStatus.STARTED)) {
            val complete = lexString(R.string.action_complete_quest)
            if (state.canBegin) {
                SecondaryButton(complete, onClick = { onEvent(QuestDetailEvent.Complete) }, enabled = state.canComplete && !state.busy, modifier = Modifier.fillMaxWidth())
            } else {
                PrimaryButton(complete, onClick = { onEvent(QuestDetailEvent.Complete) }, enabled = state.canComplete && !state.busy, modifier = Modifier.fillMaxWidth())
            }
        }
        if (state.canUndo) {
            SecondaryButton(lexString(R.string.action_undo), onClick = { onEvent(QuestDetailEvent.Undo) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        }
        if (state.canReroll) {
            val label = if (state.rerollCost == 0) {
                lexString(R.string.action_reroll)
            } else {
                lexString(R.string.action_reroll_paid, state.rerollCost)
            }
            SecondaryButton(label, onClick = { onEvent(QuestDetailEvent.Reroll) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        }
        if (state.reportable) {
            QuietButton(lexString(R.string.action_report_quest), onClick = { showReport = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Tap-per-set counter for SELF rep quests (GD §6.5): progress only, the hunter still taps Complete. The running total is
 * the card's number; each set is a row; a − / + stepper sets the next set's reps.
 */
@Composable
private fun SetCounterBlock(counter: SetCounterUiState, target: Int, unit: String, busy: Boolean, onEvent: (QuestDetailEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    // Restarts at the suggested value after every logged or undone set.
    var reps by rememberSaveable(counter.sets.size) { mutableIntStateOf(counter.suggestedReps) }
    AwakenCard(title = lexString(R.string.quest_sets_title), reveal = false, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = lexString(R.string.quest_sets_total, counter.total, target, unitLabel(unit, target)),
            style = type.metricL,
            color = colors.textPrimary,
        )
        counter.sets.forEachIndexed { index, setReps ->
            Text(lexString(R.string.quest_set_line, index + 1, setReps), style = type.body, color = colors.textSecondary)
        }
        counter.restSeconds?.let { seconds ->
            Text(lexString(R.string.quest_sets_rest, DateUtils.formatElapsedTime(seconds)), style = type.label, color = colors.textPrimary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val fewer = lexString(R.string.cd_reps_fewer)
            val more = lexString(R.string.cd_reps_more)
            SecondaryButton(
                text = "−",
                onClick = { reps = (reps - 1).coerceAtLeast(1) },
                modifier = Modifier.semantics { contentDescription = fewer },
            )
            Text(
                lexString(R.string.quest_set_reps_value, reps),
                style = type.numeric,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = "+",
                onClick = { reps = (reps + 1).coerceAtMost(SetCounter.MAX_REPS_PER_SET) },
                modifier = Modifier.semantics { contentDescription = more },
            )
        }
        PrimaryButton(
            lexString(R.string.action_log_set),
            onClick = { onEvent(QuestDetailEvent.AddSet(reps)) },
            enabled = !busy && !counter.full,
            modifier = Modifier.fillMaxWidth(),
        )
        if (counter.sets.isNotEmpty()) {
            QuietButton(lexString(R.string.action_undo_set), onClick = { onEvent(QuestDetailEvent.UndoSet) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        }
        Text(
            text = lexString(if (counter.full) R.string.quest_sets_full else R.string.quest_sets_note),
            style = type.caption,
            color = colors.textSecondary,
        )
    }
}

private val VerificationSource.glyph: ImageVector
    get() = when (this) {
        VerificationSource.SELF -> AwakenGlyphs.Check
        VerificationSource.TIMER -> AwakenGlyphs.Timer
        VerificationSource.HEALTH -> AwakenGlyphs.VerifiedSeal
        VerificationSource.USAGE -> AwakenGlyphs.Phone
    }

private val QuestType.kindRes: Int
    @StringRes get() = when (this) {
        QuestType.MAIN -> R.string.quest_kind_main
        QuestType.SIDE -> R.string.quest_kind_side
        QuestType.HABIT -> R.string.quest_kind_habit
        QuestType.PENALTY -> R.string.quest_kind_penalty
        QuestType.TRIAL -> R.string.quest_kind_trial
        QuestType.GATE -> R.string.quest_kind_gate
        QuestType.PATH -> R.string.quest_kind_path
        QuestType.DISCIPLINE -> R.string.quest_kind_discipline
    }

private val VerificationSource.explanationRes: Int
    @StringRes get() = when (this) {
        VerificationSource.SELF -> R.string.verify_self
        VerificationSource.TIMER -> R.string.verify_timer
        VerificationSource.HEALTH -> R.string.verify_health
        VerificationSource.USAGE -> R.string.verify_usage
    }

@Composable
private fun reasonText(reason: QuestReason): String = when (reason) {
    is QuestReason.Main -> lexString(R.string.reason_main, reason.stat.label())
    is QuestReason.Focus -> lexString(R.string.reason_focus, reason.stat.label())
    is QuestReason.Neglected -> lexPlural(R.plurals.reason_neglected, reason.days.toInt(), reason.stat.label(), reason.days.toInt())
    is QuestReason.New -> lexString(R.string.reason_new, reason.stat.label())
    QuestReason.Balance -> lexString(R.string.reason_balance)
    QuestReason.Penalty -> lexString(R.string.reason_penalty)
    QuestReason.Habit -> lexString(R.string.reason_habit)
}

private val previewState = QuestDetailUiState(
    loading = false,
    title = "Do deep work",
    description = "Study one topic with no distractions.",
    type = QuestType.SIDE,
    stat = Stat.INT,
    secondaryStat = Stat.WIL,
    target = 25,
    unit = "min",
    difficulty = Difficulty.NORMAL,
    verification = VerificationSource.SELF,
    minDurationSec = 300,
    reason = QuestReason.Neglected(Stat.INT, 3),
    canBegin = true,
    canReroll = true,
)

@PhonePreviews
@Composable
private fun QuestDetailPreview() {
    AwakenTheme { QuestDetailScreen(previewState, onEvent = {}, onBack = {}) }
}

@PhonePreviews
@Composable
private fun QuestDetailStartedPreview() {
    AwakenTheme {
        QuestDetailScreen(
            previewState.copy(status = QuestStatus.STARTED, readyInSeconds = 142, canBegin = false, rerollCost = 40),
            onEvent = {},
            onBack = {},
        )
    }
}
