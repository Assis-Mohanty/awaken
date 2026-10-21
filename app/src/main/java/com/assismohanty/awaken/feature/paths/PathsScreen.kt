package com.assismohanty.awaken.feature.paths

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.EmptyState
import com.assismohanty.awaken.core.designsystem.components.LadderNode
import com.assismohanty.awaken.core.designsystem.components.OracleStatus
import com.assismohanty.awaken.core.designsystem.components.OracleUiState
import com.assismohanty.awaken.core.designsystem.components.PathLadder
import com.assismohanty.awaken.core.designsystem.components.Pill
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.label
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.CreateBlock
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.path.PathStatus
import com.assismohanty.awaken.engine.path.ProgressionSignal
import com.assismohanty.awaken.engine.path.SlotOutcome

@Composable
fun PathsRoute(
    onOpenPath: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PathsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PathsScreen(state = state, onEvent = viewModel::onEvent, onOpenPath = onOpenPath, modifier = modifier)
}

/** Paths tab (DESIGN_SYSTEM §4.8): up to three Path windows, or the empty state, plus the create action. */
@Composable
fun PathsScreen(
    state: PathsUiState,
    onEvent: (PathsEvent) -> Unit,
    onOpenPath: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.loading) return@Column
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            if (state.paths.isEmpty()) {
                EmptyState(
                    line = stringResource(R.string.paths_empty_line),
                    actionLabel = stringResource(R.string.paths_create),
                    onAction = { onEvent(PathsEvent.Create) },
                )
            } else {
                state.paths.forEach { path ->
                    PathWindow(path = path, onEvent = onEvent, onOpen = { onOpenPath(path.id) })
                }
                CreateAction(state, onEvent)
            }
        }
    }
    if (state.showConsent) {
        AiConsentSheet(onEnable = { onEvent(PathsEvent.EnableConsent) }, onNotNow = { onEvent(PathsEvent.DeclineConsent) })
    }
    state.editor?.let { editor -> PathEditorSheet(state = editor, onEvent = { onEvent(PathsEvent.Editor(it)) }) }
}

@Composable
private fun CreateAction(state: PathsUiState, onEvent: (PathsEvent) -> Unit) {
    val reason = when (state.createBlock) {
        CreateBlock.LIMIT -> R.string.paths_create_limit
        CreateBlock.TODAY -> R.string.paths_create_today
        null -> if (state.consentGiven) null else R.string.paths_consent_off
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // A missing consent does not disable Create: it opens the consent sheet first.
        SecondaryButton(
            text = stringResource(R.string.paths_create),
            onClick = { onEvent(PathsEvent.Create) },
            enabled = state.createBlock == null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (reason != null && state.createBlock != null) {
            Text(stringResource(reason), style = AwakenTheme.type.caption, color = AwakenTheme.colors.mist)
        }
    }
}

/** One Path: goal as title, stat sigil, the ladder collapsed to the current milestone, this week's quests, Oracle state. */
@Composable
private fun PathWindow(path: PathCardUi, onEvent: (PathsEvent) -> Unit, onOpen: () -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val accent = colors.stat(path.primaryStat)
    val openDescription = stringResource(R.string.path_open_description, path.goal)
    SystemWindow(
        title = path.goal.uppercase(),
        accent = accent,
        reveal = false,
        modifier = Modifier
            .clickable(role = Role.Button, onClickLabel = openDescription, onClick = onOpen)
            .semantics { contentDescription = openDescription },
        headerTrailing = {
            Icon(
                AwakenGlyphs.stat(path.primaryStat),
                contentDescription = path.primaryStat.label(),
                tint = accent,
                modifier = Modifier.size(AwakenTheme.dimens.sigil),
            )
        },
    ) {
        StatusPills(path)
        if (path.ladder.isNotEmpty()) {
            PathLadder(
                nodes = path.ladder,
                currentIndex = path.currentIndex,
                accent = accent,
                collapsed = true,
                progressLine = stringResource(R.string.path_milestone_position, path.currentIndex + 1, path.ladder.size),
            )
        }
        path.advanceTo?.let { next -> AdvanceMoment(next, onAdvance = { onEvent(PathsEvent.Advance(path.id)) }, onStay = { onEvent(PathsEvent.Stay(path.id)) }) }
        path.recalibration?.let { signal ->
            Text(stringResource(signal.lineRes()), style = type.label, color = colors.system)
        }
        WeekQuests(path)
        path.oracle?.let { oracle ->
            OracleStatus(
                state = oracle,
                onRetry = if (path.canRetry) ({ onEvent(PathsEvent.Retry(path.id)) }) else null,
                retryNote = if (path.retryLimitReached) stringResource(R.string.oracle_retry_limit) else null,
            )
        }
    }
}

@Composable
internal fun StatusPills(path: PathCardUi) {
    val colors = AwakenTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        when (path.status) {
            PathStatus.PAUSED -> Pill(text = stringResource(R.string.path_status_paused), color = colors.mist)
            PathStatus.PLANNING -> Pill(text = stringResource(R.string.path_status_planning), color = colors.system)
            else -> Pill(text = stringResource(R.string.path_status_active), color = colors.verified)
        }
        if (path.weekTotal > 0) {
            Text(
                pluralStringResource(R.plurals.path_week_progress, path.weekTotal, path.weekDone, path.weekTotal),
                style = AwakenTheme.type.caption,
                color = colors.mist,
            )
        }
    }
}

/** "MILESTONE CLEARED" with the next milestone title; no reward numbers, there are none (DS §4.8). */
@Composable
internal fun AdvanceMoment(next: String, onAdvance: () -> Unit, onStay: () -> Unit) {
    SystemWindow(title = stringResource(R.string.advance_title), accent = AwakenTheme.colors.system, reveal = false) {
        Text(stringResource(R.string.advance_body, next), style = AwakenTheme.type.body, color = AwakenTheme.colors.bone)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(text = stringResource(R.string.advance_confirm), onClick = onAdvance)
            QuietButton(text = stringResource(R.string.advance_stay), onClick = onStay)
        }
    }
}

@Composable
internal fun WeekQuests(path: PathCardUi) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    if (path.quests.isEmpty()) {
        if (path.oracle == OracleUiState.CHARTED) {
            Text(stringResource(R.string.path_no_quests_yet), style = type.caption, color = colors.mist)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        path.quests.forEach { quest ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = AwakenTheme.dimens.icon),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(quest.dayLabel, style = type.caption, color = colors.mist, modifier = Modifier.widthIn(min = Spacing.xl))
                Text(
                    quest.title,
                    style = type.body,
                    color = if (quest.outcome == SlotOutcome.COMPLETED) colors.mist else colors.bone,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(quest.outcome.labelRes()), style = type.caption, color = quest.outcome.color())
            }
            if (quest.carried) {
                Text(stringResource(R.string.slot_carried), style = type.caption, color = colors.mist)
            }
        }
    }
}

internal fun ProgressionSignal.lineRes(): Int = when (this) {
    ProgressionSignal.UP -> R.string.recal_up
    ProgressionSignal.HOLD -> R.string.recal_hold
    ProgressionSignal.DOWN -> R.string.recal_down
}

private fun SlotOutcome.labelRes(): Int = when (this) {
    SlotOutcome.PENDING -> R.string.slot_pending
    SlotOutcome.COMPLETED -> R.string.slot_completed
    SlotOutcome.EXPIRED -> R.string.slot_expired
    SlotOutcome.SKIPPED -> R.string.slot_skipped
    else -> R.string.slot_unscheduled
}

@Composable
private fun SlotOutcome.color() = when (this) {
    SlotOutcome.COMPLETED -> AwakenTheme.colors.verified
    SlotOutcome.EXPIRED -> AwakenTheme.colors.ember
    else -> AwakenTheme.colors.mist
}

// ---- Previews ----------------------------------------------------------------------------------------------------

private val previewLadder = listOf(
    LadderNode("Foundations", "Lock in BFS, DFS and basic DP.", "Solve 10 problems rated 1400."),
    LadderNode("Graph Fluency", "Shortest paths and topological sort.", "Solve 8 graph problems rated 1600."),
    LadderNode("Speed", "Solve faster under contest conditions.", "Place top 30% in two contests."),
)

private fun previewCard(
    id: String = "p1",
    goal: String = "Reach 1900 on Codeforces",
    oracle: OracleUiState? = OracleUiState.CHARTED,
    status: PathStatus = PathStatus.ACTIVE,
    quests: List<PathQuestLineUi> = listOf(
        PathQuestLineUi("s1", "Solve two graph problems", SlotOutcome.COMPLETED, "Mon", null, false),
        PathQuestLineUi("s2", "Review one editorial", SlotOutcome.PENDING, "Wed", null, false),
        PathQuestLineUi("s3", "Virtual contest, 50 minutes", SlotOutcome.PENDING, "Fri", null, true),
    ),
    canRetry: Boolean = false,
    advanceTo: String? = null,
    recalibration: ProgressionSignal? = ProgressionSignal.UP,
) = PathCardUi(
    id = id,
    goal = goal,
    primaryStat = Stat.INT,
    secondaryStat = null,
    status = status,
    ladder = previewLadder,
    currentIndex = 1,
    weekDone = quests.count { it.outcome == SlotOutcome.COMPLETED },
    weekTotal = quests.size,
    quests = quests,
    oracle = oracle,
    canRetry = canRetry,
    retryLimitReached = false,
    recalibration = recalibration,
    advanceTo = advanceTo,
)

@PhonePreviews
@Composable
private fun PathsScreenPreview() {
    AwakenTheme {
        PathsScreen(
            state = PathsUiState(loading = false, consentGiven = true, paths = listOf(previewCard(advanceTo = "Speed"), previewCard(id = "p2", goal = "Run a 10K", status = PathStatus.PAUSED, oracle = null, quests = emptyList(), recalibration = null))),
            onEvent = {},
            onOpenPath = {},
        )
    }
}

@PhonePreviews
@Composable
private fun PathsEmptyPreview() {
    AwakenTheme { PathsScreen(state = PathsUiState(loading = false), onEvent = {}, onOpenPath = {}) }
}

@PhonePreviews
@Composable
private fun PathsOfflineAndFailedPreview() {
    AwakenTheme {
        PathsScreen(
            state = PathsUiState(
                loading = false,
                consentGiven = true,
                createBlock = CreateBlock.LIMIT,
                paths = listOf(
                    previewCard(oracle = OracleUiState.OFFLINE, quests = emptyList(), recalibration = null),
                    previewCard(id = "p2", goal = "Learn Japanese", oracle = OracleUiState.FAILED, canRetry = true, quests = emptyList(), recalibration = null),
                    previewCard(id = "p3", goal = "Read 12 books", oracle = OracleUiState.CHARTING, status = PathStatus.PLANNING, quests = emptyList(), recalibration = null),
                ),
            ),
            onEvent = {},
            onOpenPath = {},
        )
    }
}
