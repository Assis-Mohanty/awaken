package com.assismohanty.awaken.feature.trial

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.EmptyState
import com.assismohanty.awaken.core.designsystem.components.QuestRow
import com.assismohanty.awaken.core.designsystem.components.QuestRowModel
import com.assismohanty.awaken.core.designsystem.components.QuestRowState
import com.assismohanty.awaken.core.designsystem.components.RankCrest
import com.assismohanty.awaken.core.designsystem.components.StatChip
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TrialDetailRoute(
    onBack: () -> Unit,
    onOpenQuest: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrialDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TrialDetailScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, onOpenQuest = onOpenQuest, modifier = modifier)
}

/** Rank Trial detail (GD §5): the three stages, the 5-day window and the rule. */
@Composable
fun TrialDetailScreen(
    state: TrialDetailUiState,
    onEvent: (TrialDetailEvent) -> Unit,
    onBack: () -> Unit,
    onOpenQuest: (String) -> Unit,
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
                state.loading -> Unit
                state.none -> EmptyState(line = stringResource(R.string.trial_none), actionLabel = stringResource(R.string.action_back), onAction = onBack)
                else -> TrialContent(state, onEvent, onOpenQuest)
            }
        }
    }
}

@Composable
private fun TrialContent(state: TrialDetailUiState, onEvent: (TrialDetailEvent) -> Unit, onOpenQuest: (String) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0])
    SystemWindow(
        title = stringResource(R.string.trial_available, state.rank.name),
        accent = AwakenTheme.rankAccent(state.rank),
        headerTrailing = { RankCrest(rank = state.rank, size = AwakenTheme.dimens.crestSmall) },
    ) {
        val start = state.start
        val expires = state.expires
        if (start != null && expires != null) {
            Text(stringResource(R.string.trial_window, formatter.format(start), formatter.format(expires)), style = type.label, color = colors.bone)
        }
        Text(pluralStringResource(R.plurals.trial_days_left, state.daysLeft, state.daysLeft), style = type.label, color = colors.mist)
        Text(stringResource(R.string.trial_rule), style = type.caption, color = colors.mist)

        state.stages.forEachIndexed { index, stage ->
            if (index > 0) HorizontalDivider(thickness = AwakenTheme.dimens.hairline, color = colors.edge)
            when (stage) {
                is StageState.Today -> QuestRow(
                    model = stage.quest,
                    onComplete = { onEvent(TrialDetailEvent.Complete(stage.quest.id)) },
                    onOpen = { onOpenQuest(stage.quest.id) },
                    showDivider = false,
                )
                is StageState.Cleared -> StageLine(stage.stat, stringResource(R.string.trial_stage_cleared), colors.verified)
                is StageState.Tomorrow -> StageLine(stage.stat, stringResource(R.string.trial_stage_tomorrow), colors.mist)
            }
        }
    }
}

@Composable
private fun StageLine(stat: Stat, status: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AwakenTheme.dimens.questRowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        StatChip(stat)
        Text(status, style = AwakenTheme.type.label, color = color)
    }
}

@PhonePreviews
@Composable
private fun TrialDetailPreview() {
    val start = LocalDate.of(2026, 10, 19)
    AwakenTheme {
        TrialDetailScreen(
            state = TrialDetailUiState(
                loading = false,
                rank = Rank.D,
                start = start,
                expires = start.plusDays(4),
                daysLeft = 3,
                stages = listOf(
                    StageState.Cleared(Stat.WIL, start),
                    StageState.Today(
                        Stat.CHA,
                        QuestRowModel("q", "Open Door", Stat.CHA, 3, "talks", Difficulty.HARD, VerificationSource.SELF, isMain = false, state = QuestRowState.OPEN),
                    ),
                    StageState.Tomorrow(Stat.STR),
                ),
            ),
            onEvent = {},
            onBack = {},
            onOpenQuest = {},
        )
    }
}
