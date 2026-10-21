package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.path.Feedback

// ---- OracleStatus ------------------------------------------------------------------------------------------------

/** What the Oracle is doing for a Path this week (DESIGN_SYSTEM §3 OracleStatus). */
enum class OracleUiState { CHARTING, CHARTED, OFFLINE, FAILED, CONSENT_OFF }

/**
 * The inline state line inside a Path window: "Charting your week…" with a shimmer on its edge, "Week charted",
 * "Offline: your path updates when you reconnect" and "The System could not chart this week [Retry]".
 */
@Composable
fun OracleStatus(
    state: OracleUiState,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryNote: String? = null,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val (text, color) = when (state) {
        OracleUiState.CHARTING -> stringResource(R.string.oracle_charting) to colors.system
        OracleUiState.CHARTED -> stringResource(R.string.oracle_charted) to colors.mist
        OracleUiState.OFFLINE -> stringResource(R.string.oracle_offline) to colors.mist
        OracleUiState.FAILED -> stringResource(R.string.oracle_failed) to colors.ember
        OracleUiState.CONSENT_OFF -> stringResource(R.string.oracle_consent_needed) to colors.mist
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text, style = type.label, color = color, modifier = Modifier.weight(1f, fill = false))
            if (state == OracleUiState.FAILED && onRetry != null) {
                QuietButton(text = stringResource(R.string.oracle_retry), onClick = onRetry)
            }
        }
        if (state == OracleUiState.CHARTING) Shimmer(color)
        if (retryNote != null) Text(retryNote, style = type.caption, color = colors.mist)
    }
}

/** A hairline that pulses while the Oracle works; static when reduce-motion is on. */
@Composable
private fun Shimmer(color: Color) {
    val reduce = AwakenTheme.motion.reduceMotion
    val alpha = if (reduce) {
        SHIMMER_HIGH
    } else {
        val transition = rememberInfiniteTransition(label = "oracle-shimmer")
        val animated by transition.animateFloat(
            initialValue = SHIMMER_LOW,
            targetValue = SHIMMER_HIGH,
            animationSpec = infiniteRepeatable(tween(SHIMMER_MS, easing = LinearEasing), RepeatMode.Reverse),
            label = "oracle-shimmer-alpha",
        )
        animated
    }
    Box(modifier = Modifier.fillMaxWidth().height(AwakenTheme.dimens.hairline * 2).alpha(alpha).background(color))
}

private const val SHIMMER_LOW = 0.2f
private const val SHIMMER_HIGH = 0.9f
private const val SHIMMER_MS = 900

// ---- PathLadder --------------------------------------------------------------------------------------------------

@Immutable
data class LadderNode(val title: String, val summary: String, val exitCriterion: String)

/**
 * Vertical milestone ladder (DESIGN_SYSTEM §3): cleared milestones are filled chamfered nodes in the Path's stat colour,
 * the current one is lit with an edge and carries [progressLine], future ones are `edge` outlines with `mist` titles.
 * Tap a node to see its summary and exit criterion. [collapsed] shows only the current milestone.
 */
@Composable
fun PathLadder(
    nodes: List<LadderNode>,
    currentIndex: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    progressLine: String? = null,
    collapsed: Boolean = false,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val dimens = AwakenTheme.dimens
    var opened by rememberSaveable { mutableIntStateOf(NONE_OPEN) }
    val visible = if (collapsed) listOfNotNull(nodes.getOrNull(currentIndex)?.let { currentIndex to it }) else nodes.mapIndexed { i, n -> i to n }

    Column(modifier = modifier.fillMaxWidth()) {
        visible.forEachIndexed { position, (index, node) ->
            val state = when {
                index < currentIndex -> LadderState.CLEARED
                index == currentIndex -> LadderState.CURRENT
                else -> LadderState.FUTURE
            }
            val description = stringResource(
                when (state) {
                    LadderState.CLEARED -> R.string.cd_ladder_cleared
                    LadderState.CURRENT -> R.string.cd_ladder_current
                    LadderState.FUTURE -> R.string.cd_ladder_future
                },
                node.title,
            )
            val isLast = position == visible.lastIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clickable(role = Role.Button, onClickLabel = node.title) { opened = if (opened == index) NONE_OPEN else index }
                    .semantics(mergeDescendants = true) { contentDescription = description },
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
                    Node(state, accent)
                    if (!isLast) {
                        Box(modifier = Modifier.width(dimens.hairline).weight(1f).background(colors.edge))
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    Text(
                        text = node.title,
                        style = if (state == LadderState.CURRENT) type.title else type.body,
                        color = if (state == LadderState.FUTURE) colors.mist else colors.bone,
                    )
                    if (state == LadderState.CURRENT && progressLine != null) {
                        Text(progressLine, style = type.label, color = accent)
                    }
                    if (opened == index) {
                        Text(node.summary, style = type.body, color = colors.bone)
                        Text(stringResource(R.string.path_exit_criterion, node.exitCriterion), style = type.caption, color = colors.mist)
                    }
                }
            }
        }
    }
}

private enum class LadderState { CLEARED, CURRENT, FUTURE }

@Composable
private fun Node(state: LadderState, accent: Color) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val shape = AwakenTheme.shapes.button
    val size = NODE_SIZE
    Box(
        modifier = Modifier
            .size(size)
            .clearAndSetSemantics { }
            .then(
                when (state) {
                    LadderState.CLEARED -> Modifier.background(accent, shape)
                    LadderState.CURRENT -> Modifier
                        .background(accent.copy(alpha = AwakenAlpha.TINT), shape)
                        .border(dimens.edgeWidth * 2, colors.system, shape)
                    LadderState.FUTURE -> Modifier.border(dimens.hairline, colors.edge, shape)
                },
            ),
    )
}

private val NODE_SIZE = 14.dp
private const val NONE_OPEN = -1

// ---- PathQuestBadge ----------------------------------------------------------------------------------------------

/**
 * Names the Path a quest came from ("◇ Codeforces 1900"), so personal quests stand apart from template quests at a
 * glance. GATE-mode quests also show the gate they need ("C-Gate · 50 min").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PathQuestBadge(name: String, modifier: Modifier = Modifier, gate: String? = null) {
    val colors = AwakenTheme.colors
    val description = stringResource(R.string.cd_path_badge, name)
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Pill(text = "◇ $name", color = colors.mist, description = description)
        if (gate != null) Pill(text = gate, color = colors.verified, icon = AwakenGlyphs.Hourglass)
    }
}

// ---- FeedbackChips -----------------------------------------------------------------------------------------------

/** Three optional single-select chips on a Path quest's reward sheet; picking the chosen one again clears it. */
@Composable
fun FeedbackChips(selected: Feedback?, onSelect: (Feedback?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.feedback_label), style = AwakenTheme.type.caption, color = AwakenTheme.colors.mist)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Feedback.entries.forEach { feedback ->
                ChoiceChip(
                    text = stringResource(
                        when (feedback) {
                            Feedback.TOO_EASY -> R.string.feedback_too_easy
                            Feedback.RIGHT -> R.string.feedback_right
                            Feedback.TOO_HARD -> R.string.feedback_too_hard
                        },
                    ),
                    selected = selected == feedback,
                    onClick = { onSelect(if (selected == feedback) null else feedback) },
                )
            }
        }
    }
}

// ---- Previews ----------------------------------------------------------------------------------------------------

private val previewNodes = listOf(
    LadderNode("Foundations", "Lock in BFS, DFS and basic DP.", "Solve 10 problems rated 1400."),
    LadderNode("Graph Fluency", "Shortest paths and topological sort.", "Solve 8 graph problems rated 1600."),
    LadderNode("DP Depth", "Knapsack, intervals and bitmasks.", "Solve 8 DP problems rated 1700."),
    LadderNode("The 1900 Gate", "Live rated rounds.", "Reach 1900 rating."),
)

@ComponentPreview
@Composable
private fun PathLadderPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PathLadder(previewNodes, currentIndex = 1, accent = AwakenTheme.colors.stat(com.assismohanty.awaken.engine.model.Stat.INT), progressLine = "4 / 6 quests this week")
            PathLadder(previewNodes, currentIndex = 1, accent = AwakenTheme.colors.stat(com.assismohanty.awaken.engine.model.Stat.INT), collapsed = true)
        }
    }
}

@ComponentPreview
@Composable
private fun OracleStatusPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            OracleUiState.entries.forEach { OracleStatus(it, onRetry = {}) }
            OracleStatus(OracleUiState.FAILED, onRetry = null, retryNote = "Retries for this week are used up.")
        }
    }
}

@ComponentPreview
@Composable
private fun PathBadgeAndFeedbackPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PathQuestBadge("Codeforces 1900")
            PathQuestBadge("Codeforces 1900", gate = "C-Gate · 50 min")
            FeedbackChips(selected = Feedback.RIGHT, onSelect = {})
            FeedbackChips(selected = null, onSelect = {})
        }
    }
}
