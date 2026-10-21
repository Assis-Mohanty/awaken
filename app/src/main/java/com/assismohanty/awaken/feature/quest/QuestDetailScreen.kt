package com.assismohanty.awaken.feature.quest

import android.text.format.DateUtils
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.DifficultyChip
import com.assismohanty.awaken.core.designsystem.components.EmptyState
import com.assismohanty.awaken.core.designsystem.components.PathQuestBadge
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.StatChip
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.VerifiedBadge
import com.assismohanty.awaken.core.designsystem.components.formatCount
import com.assismohanty.awaken.core.designsystem.components.label
import com.assismohanty.awaken.core.designsystem.components.unitLabel
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.QuestReason
import com.assismohanty.awaken.feature.paths.ReportSheet
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource

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

/** Quest detail (DESIGN_SYSTEM §4.3). */
@Composable
fun QuestDetailScreen(
    state: QuestDetailUiState,
    onEvent: (QuestDetailEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xxs)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = colors.bone)
            }
        }
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
        ) {
            when {
                state.notFound -> EmptyState(line = stringResource(R.string.msg_error), actionLabel = stringResource(R.string.action_back), onAction = onBack)
                state.loading -> Unit
                else -> QuestDetailContent(state, onEvent)
            }
        }
    }
}

@Composable
private fun QuestDetailContent(state: QuestDetailUiState, onEvent: (QuestDetailEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val accent = if (state.type == QuestType.PENALTY) colors.ember else colors.stat(state.stat)
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
    SystemWindow(title = stringResource(state.type.kindRes), accent = accent) {
        Text(state.title, style = type.display, color = colors.bone, modifier = Modifier.semantics { heading() })
        Text(
            text = stringResource(R.string.quest_target_line, formatCount(state.target), unitLabel(state.unit, state.target), state.difficulty.label()),
            style = type.title,
            color = colors.mist,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            StatChip(state.stat)
            DifficultyChip(state.difficulty)
            VerifiedBadge(state.verification)
        }
        state.pathName?.let { name ->
            PathQuestBadge(
                name = name,
                gate = state.gateRank?.let { rank -> stringResource(R.string.path_badge_gate, rank.name, rank.durationMinutes) },
            )
        }
        if (state.description.isNotBlank()) Text(state.description, style = type.body, color = colors.bone)

        // Stat split: the primary stat takes full XP, the secondary 30 % (ENGINE_DECISIONS #5).
        Text(stringResource(R.string.quest_split_primary, state.stat.label()), style = type.label, color = colors.stat(state.stat))
        state.secondaryStat?.let { secondary ->
            Text(stringResource(R.string.quest_split_secondary, secondary.label()), style = type.label, color = colors.stat(secondary))
        }

        Text(stringResource(state.verification.explanationRes), style = type.body, color = colors.mist)
        if (state.minDurationSec > 0 && state.status == QuestStatus.ACTIVE && state.verification == VerificationSource.SELF) {
            Text(
                text = stringResource(R.string.quest_min_duration, DateUtils.formatElapsedTime(state.minDurationSec.toLong())),
                style = type.caption,
                color = colors.mist,
            )
        }
        if (state.status == QuestStatus.STARTED) {
            val text = if (state.readyInSeconds > 0) {
                stringResource(R.string.quest_ready_in, DateUtils.formatElapsedTime(state.readyInSeconds))
            } else {
                stringResource(R.string.quest_started)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Icon(AwakenGlyphs.Hourglass, contentDescription = null, tint = colors.system, modifier = Modifier.size(AwakenTheme.dimens.iconSmall))
                Text(text, style = type.label, color = colors.system)
            }
        }

        state.rationale?.let { rationale ->
            Text(
                text = stringResource(R.string.quest_path_rationale_label),
                style = type.windowTitle,
                color = colors.fog,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Text(rationale, style = type.body, color = colors.bone)
        }
        state.reason?.takeIf { state.rationale == null }?.let { reason ->
            Text(
                text = stringResource(R.string.quest_reason_label),
                style = type.windowTitle,
                color = colors.fog,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Text(reasonText(reason), style = type.body, color = colors.bone)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (state.canEnterGate) {
                state.gateRank?.let { rank ->
                    Text(stringResource(R.string.quest_gate_required), style = type.caption, color = colors.mist)
                    PrimaryButton(
                        stringResource(R.string.action_enter_path_gate, rank.name, rank.durationMinutes),
                        onClick = { onEvent(QuestDetailEvent.EnterGate) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (state.canBegin) {
                PrimaryButton(stringResource(R.string.action_begin), onClick = { onEvent(QuestDetailEvent.Begin) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            }
            if (state.verification == VerificationSource.SELF && (state.status == QuestStatus.ACTIVE || state.status == QuestStatus.STARTED)) {
                val complete = stringResource(R.string.action_complete_quest)
                if (state.canBegin) {
                    SecondaryButton(complete, onClick = { onEvent(QuestDetailEvent.Complete) }, enabled = state.canComplete && !state.busy, modifier = Modifier.fillMaxWidth())
                } else {
                    PrimaryButton(complete, onClick = { onEvent(QuestDetailEvent.Complete) }, enabled = state.canComplete && !state.busy, modifier = Modifier.fillMaxWidth())
                }
            }
            if (state.canUndo) {
                SecondaryButton(stringResource(R.string.action_undo), onClick = { onEvent(QuestDetailEvent.Undo) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            }
            if (state.canReroll) {
                val label = if (state.rerollCost == 0) {
                    stringResource(R.string.action_reroll)
                } else {
                    stringResource(R.string.action_reroll_paid, state.rerollCost)
                }
                SecondaryButton(label, onClick = { onEvent(QuestDetailEvent.Reroll) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            }
            if (state.reportable) {
                QuietButton(stringResource(R.string.action_report_quest), onClick = { showReport = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            }
        }
    }
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
    }

private val VerificationSource.explanationRes: Int
    @StringRes get() = when (this) {
        VerificationSource.SELF -> R.string.verify_self
        VerificationSource.TIMER -> R.string.verify_timer
        VerificationSource.HEALTH -> R.string.verify_health
    }

@Composable
private fun reasonText(reason: QuestReason): String = when (reason) {
    is QuestReason.Main -> stringResource(R.string.reason_main, reason.stat.label())
    is QuestReason.Focus -> stringResource(R.string.reason_focus, reason.stat.label())
    is QuestReason.Neglected -> pluralStringResource(R.plurals.reason_neglected, reason.days.toInt(), reason.stat.label(), reason.days.toInt())
    is QuestReason.New -> stringResource(R.string.reason_new, reason.stat.label())
    QuestReason.Balance -> stringResource(R.string.reason_balance)
    QuestReason.Penalty -> stringResource(R.string.reason_penalty)
    QuestReason.Habit -> stringResource(R.string.reason_habit)
}

private val previewState = QuestDetailUiState(
    loading = false,
    title = "Deep Dive",
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
