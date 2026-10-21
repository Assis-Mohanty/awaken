package com.assismohanty.awaken.feature.paths

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.LadderNode
import com.assismohanty.awaken.core.designsystem.components.OracleStatus
import com.assismohanty.awaken.core.designsystem.components.OracleUiState
import com.assismohanty.awaken.core.designsystem.components.PathLadder
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.label
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.path.PathStatus
import com.assismohanty.awaken.engine.path.ProgressionSignal
import com.assismohanty.awaken.engine.path.SlotOutcome

@Composable
fun PathDetailRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: PathDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PathDetailScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

/** Path detail (DESIGN_SYSTEM §4.8): the full ladder, this week's quests, Oracle state and edit / pause / archive. */
@Composable
fun PathDetailScreen(
    state: PathDetailUiState,
    onEvent: (PathDetailEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Archiving removes the Path from the observed list; there is nothing left to show.
    LaunchedEffect(state.loading, state.path == null) {
        if (!state.loading && state.path == null) onBack()
    }
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
        val path = state.path ?: return@Column
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            val accent = colors.stat(path.primaryStat)
            SystemWindow(
                title = stringResource(R.string.path_detail_title),
                accent = accent,
                reveal = false,
                headerTrailing = {
                    Icon(
                        AwakenGlyphs.stat(path.primaryStat),
                        contentDescription = path.primaryStat.label(),
                        tint = accent,
                        modifier = Modifier.size(AwakenTheme.dimens.sigil),
                    )
                },
            ) {
                Text(path.goal, style = AwakenTheme.type.title, color = colors.bone)
                StatusPills(path)
                path.recalibration?.let { Text(stringResource(it.lineRes()), style = AwakenTheme.type.label, color = colors.system) }
                path.oracle?.let { oracle ->
                    OracleStatus(
                        state = oracle,
                        onRetry = if (path.canRetry) ({ onEvent(PathDetailEvent.Retry) }) else null,
                        retryNote = if (path.retryLimitReached) stringResource(R.string.oracle_retry_limit) else null,
                    )
                }
            }
            path.advanceTo?.let { next ->
                AdvanceMoment(next, onAdvance = { onEvent(PathDetailEvent.Advance) }, onStay = { onEvent(PathDetailEvent.Stay) })
            }
            if (path.ladder.isNotEmpty()) {
                SystemWindow(title = stringResource(R.string.path_ladder_title), accent = accent, reveal = false) {
                    PathLadder(nodes = path.ladder, currentIndex = path.currentIndex, accent = accent)
                }
            }
            SystemWindow(title = stringResource(R.string.path_week_title), accent = accent, reveal = false) {
                WeekQuests(path)
                if (path.quests.isEmpty() && path.oracle != OracleUiState.CHARTED) {
                    Text(stringResource(R.string.path_no_quests_yet), style = AwakenTheme.type.caption, color = colors.mist)
                }
            }
            Actions(path, state, onEvent)
        }
    }
    if (state.showConsent) {
        AiConsentSheet(onEnable = { onEvent(PathDetailEvent.EnableConsent) }, onNotNow = { onEvent(PathDetailEvent.DeclineConsent) })
    }
    state.editor?.let { editor -> PathEditorSheet(state = editor, onEvent = { onEvent(PathDetailEvent.Editor(it)) }) }
}

@Composable
private fun Actions(path: PathCardUi, state: PathDetailUiState, onEvent: (PathDetailEvent) -> Unit) {
    val colors = AwakenTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SecondaryButton(
            text = stringResource(R.string.path_action_edit),
            onClick = { onEvent(PathDetailEvent.Edit) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.replanUsed) {
            Text(stringResource(R.string.path_action_replan_used), style = AwakenTheme.type.caption, color = colors.mist)
        }
        if (path.status.canPause()) {
            SecondaryButton(
                text = stringResource(R.string.path_action_pause),
                onClick = { onEvent(PathDetailEvent.Pause) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (path.status.canResume()) {
            PrimaryButton(
                text = stringResource(R.string.path_action_resume),
                onClick = { onEvent(PathDetailEvent.Resume) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.confirmArchive) {
            SystemWindow(title = null, accent = colors.ember, reveal = false) {
                Text(stringResource(R.string.path_archive_confirm), style = AwakenTheme.type.body, color = colors.bone)
                PrimaryButton(
                    text = stringResource(R.string.path_action_archive),
                    onClick = { onEvent(PathDetailEvent.ConfirmArchive) },
                    color = colors.ember,
                    modifier = Modifier.fillMaxWidth(),
                )
                QuietButton(text = stringResource(R.string.action_cancel), onClick = { onEvent(PathDetailEvent.CancelArchive) })
            }
        } else {
            QuietButton(text = stringResource(R.string.path_action_archive), onClick = { onEvent(PathDetailEvent.AskArchive) })
        }
    }
}

// ---- Previews ----------------------------------------------------------------------------------------------------

private val previewDetail = PathCardUi(
    id = "p1",
    goal = "Reach 1900 on Codeforces",
    primaryStat = Stat.INT,
    secondaryStat = null,
    status = PathStatus.ACTIVE,
    ladder = listOf(
        LadderNode("Foundations", "Lock in BFS, DFS and basic DP.", "Solve 10 problems rated 1400."),
        LadderNode("Graph Fluency", "Shortest paths and topological sort.", "Solve 8 graph problems rated 1600."),
        LadderNode("Speed", "Solve faster under contest conditions.", "Place top 30% in two contests."),
    ),
    currentIndex = 1,
    weekDone = 1,
    weekTotal = 2,
    quests = listOf(
        PathQuestLineUi("s1", "Solve two graph problems", SlotOutcome.COMPLETED, "Mon", null, false),
        PathQuestLineUi("s2", "Review one editorial", SlotOutcome.PENDING, "Wed", null, false),
    ),
    oracle = OracleUiState.CHARTED,
    canRetry = false,
    retryLimitReached = false,
    recalibration = ProgressionSignal.HOLD,
    advanceTo = null,
)

@PhonePreviews
@Composable
private fun PathDetailPreview() {
    AwakenTheme {
        PathDetailScreen(state = PathDetailUiState(loading = false, path = previewDetail), onEvent = {}, onBack = {})
    }
}

@PhonePreviews
@Composable
private fun PathDetailPausedArchivePreview() {
    AwakenTheme {
        PathDetailScreen(
            state = PathDetailUiState(
                loading = false,
                path = previewDetail.copy(status = PathStatus.PAUSED, oracle = null, quests = emptyList(), recalibration = null),
                replanUsed = true,
                confirmArchive = true,
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
