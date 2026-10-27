package com.assismohanty.awaken.feature.trial

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.EmptyState
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.ListRow
import com.assismohanty.awaken.core.designsystem.components.QuestRow
import com.assismohanty.awaken.core.designsystem.components.QuestRowModel
import com.assismohanty.awaken.core.designsystem.components.QuestRowState
import com.assismohanty.awaken.core.designsystem.components.RankCrest
import com.assismohanty.awaken.core.designsystem.components.RingCard
import com.assismohanty.awaken.core.designsystem.components.RingSpec
import com.assismohanty.awaken.core.designsystem.components.SkeletonCard
import com.assismohanty.awaken.core.designsystem.components.formatCount
import com.assismohanty.awaken.core.designsystem.components.label
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
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

/**
 * Rank Trial detail (GD §5): the trial as the large title, a ring card for stages cleared (3 stages, the 5-day window
 * underneath), the stages as rows (today's is a live quest row), and the rule.
 */
@Composable
fun TrialDetailScreen(
    state: TrialDetailUiState,
    onEvent: (TrialDetailEvent) -> Unit,
    onBack: () -> Unit,
    onOpenQuest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loaded = !state.loading && !state.none
    LargeTitleScaffold(
        title = if (loaded) lexString(R.string.trial_available, state.rank.name) else "",
        onBack = onBack,
        modifier = modifier,
        actions = { if (loaded) RankCrest(rank = state.rank, size = AwakenTheme.dimens.crestSmall) },
    ) {
        when {
            state.loading -> SkeletonCard()
            state.none -> EmptyState(line = lexString(R.string.trial_none), actionLabel = lexString(R.string.action_back), onAction = onBack)
            else -> TrialContent(state, onEvent, onOpenQuest)
        }
    }
}

@Composable
private fun TrialContent(state: TrialDetailUiState, onEvent: (TrialDetailEvent) -> Unit, onOpenQuest: (String) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0])
    val cleared = state.stages.count { it is StageState.Cleared }
    val total = state.stages.size
    val start = state.start
    val expires = state.expires
    RingCard(
        label = lexString(R.string.trial_stages_label),
        value = formatCount(cleared),
        unit = lexString(R.string.today_ring_unit, total),
        secondary = lexPlural(R.plurals.trial_days_left, state.daysLeft, state.daysLeft),
        rings = listOf(RingSpec(if (total > 0) cleared.toFloat() / total else 0f, AwakenTheme.rankAccent(state.rank))),
        description = lexString(R.string.trial_ring_cd, cleared, total),
    )
    AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
        state.stages.forEach { stage ->
            when (stage) {
                is StageState.Today -> QuestRow(
                    model = stage.quest,
                    onComplete = { onEvent(TrialDetailEvent.Complete(stage.quest.id)) },
                    onOpen = { onOpenQuest(stage.quest.id) },
                    showDivider = false,
                )
                is StageState.Cleared -> StageRow(stage.stat, lexString(R.string.trial_stage_cleared), done = true)
                is StageState.Tomorrow -> StageRow(stage.stat, lexString(R.string.trial_stage_tomorrow), done = false)
            }
        }
    }
    AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth(), spacing = Spacing.xs) {
            if (start != null && expires != null) {
                Text(lexString(R.string.trial_window, formatter.format(start), formatter.format(expires)), style = type.windowTitle, color = colors.textPrimary)
            }
            Text(lexString(R.string.trial_rule), style = type.body, color = colors.textSecondary)
    }
}

@Composable
private fun StageRow(stat: Stat, status: String, done: Boolean) {
    val colors = AwakenTheme.colors
    ListRow(title = stat.label(), icon = AwakenGlyphs.stat(stat), iconTint = colors.stat(stat), secondary = status) {
        if (done) Icon(AwakenGlyphs.Check, contentDescription = null, tint = colors.positive, modifier = Modifier.size(AwakenTheme.dimens.icon))
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
