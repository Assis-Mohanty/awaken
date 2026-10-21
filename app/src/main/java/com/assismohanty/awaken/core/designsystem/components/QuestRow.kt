package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource

enum class QuestRowState { OPEN, DONE, CLOSED }

@Immutable
data class PathBadgeModel(val name: String, val gateRank: GateRank? = null, val gateMinutes: Int? = null)

@Immutable
data class QuestRowModel(
    val id: String,
    val title: String,
    val stat: Stat,
    val target: Int,
    val unit: String,
    val difficulty: Difficulty,
    val verification: VerificationSource,
    val isMain: Boolean,
    val state: QuestRowState,
    /** Set for Path quests: which Path they came from and, for GATE-mode quests, the gate they need (DS §3). */
    val pathBadge: PathBadgeModel? = null,
)

/**
 * Quest row (DESIGN_SYSTEM §3): no card — rows live inside one SystemWindow, separated by hairlines. Swipe right past
 * the threshold completes (haptic tick when the threshold is crossed); tap or long-press opens details. Only SELF quests
 * show a check control: HEALTH/TIMER quests complete through their verifier (GAME_DESIGN §6.5).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun QuestRow(
    model: QuestRowModel,
    onComplete: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val dimens = AwakenTheme.dimens
    val haptics = AwakenTheme.haptics
    val motion = AwakenTheme.motion
    val canComplete = model.state == QuestRowState.OPEN && model.verification == VerificationSource.SELF
    val dimmed = model.state == QuestRowState.CLOSED
    val textColor = if (dimmed) colors.fog else colors.bone
    val decoration = if (dimmed) TextDecoration.LineThrough else TextDecoration.None
    // Plain state updated synchronously per drag delta; the snap-back animates the same value, so nothing can race.
    var offset by remember(model.id) { mutableFloatStateOf(0f) }
    var rowWidth by remember { mutableIntStateOf(0) }
    var crossed by remember(model.id) { mutableStateOf(false) }
    val dragState = rememberDraggableState { delta ->
        val threshold = rowWidth * SWIPE_THRESHOLD
        offset = (offset + delta).coerceIn(0f, rowWidth * SWIPE_MAX)
        if (!crossed && offset >= threshold) {
            crossed = true
            haptics.tick()
        } else if (crossed && offset < threshold) {
            crossed = false
        }
    }
    val completeLabel = stringResource(R.string.action_complete_quest)
    val openLabel = stringResource(R.string.action_open_quest)

    Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) HorizontalDivider(thickness = dimens.hairline, color = colors.edge)
        Box {
            // Revealed behind the row only while swiping; at rest the transparent row must not show it.
            if (canComplete && offset > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(colors.system.copy(alpha = AwakenAlpha.TINT)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Icon(
                        AwakenGlyphs.Check,
                        contentDescription = null,
                        tint = colors.system,
                        modifier = Modifier.padding(start = Spacing.md).size(dimens.icon),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { rowWidth = it.width }
                    .graphicsLayer { translationX = offset }
                    .background(if (canComplete && offset > 0f) colors.panelRaised else Color.Transparent)
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        enabled = canComplete,
                        onDragStopped = {
                            val complete = crossed
                            crossed = false
                            if (complete) onComplete()
                            animate(offset, 0f, animationSpec = tween(motion.duration(motion.quickMs))) { value, _ -> offset = value }
                        },
                    )
                    .combinedClickable(onClickLabel = openLabel, onClick = onOpen, onLongClick = onOpen)
                    .semantics {
                        if (canComplete) customActions = listOf(CustomAccessibilityAction(completeLabel) { onComplete(); true })
                    }
                    .padding(vertical = dimens.questRowPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    AwakenGlyphs.stat(model.stat),
                    contentDescription = model.stat.label(),
                    tint = if (dimmed) colors.fog else colors.stat(model.stat),
                    modifier = Modifier.size(dimens.sigil),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(
                        text = model.title,
                        style = if (model.isMain) type.title else type.title.copy(fontSize = type.body.fontSize),
                        color = textColor,
                        textDecoration = decoration,
                    )
                    Text(
                        text = stringResource(R.string.quest_target_line, formatCount(model.target), unitLabel(model.unit, model.target), model.difficulty.label()),
                        style = type.label,
                        color = if (dimmed) colors.fog else colors.mist,
                        textDecoration = decoration,
                    )
                    if (model.isMain || model.verification != VerificationSource.SELF || model.pathBadge != null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                            if (model.isMain) Pill(stringResource(R.string.chip_main), colors.system)
                            model.pathBadge?.let { badge ->
                                PathQuestBadge(
                                    name = badge.name,
                                    gate = badge.gateRank?.let { rank -> stringResource(R.string.path_badge_gate, rank.name, badge.gateMinutes ?: rank.durationMinutes) },
                                )
                            }
                            VerifiedBadge(model.verification)
                        }
                    }
                }
                CheckControl(model, canComplete, onComplete)
            }
        }
    }
}

@Composable
private fun CheckControl(model: QuestRowModel, canComplete: Boolean, onComplete: () -> Unit) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    when {
        model.state == QuestRowState.DONE -> Icon(
            AwakenGlyphs.Check,
            contentDescription = stringResource(R.string.cd_quest_done, model.title),
            tint = colors.system,
            modifier = Modifier.padding(Spacing.sm).size(dimens.icon),
        )
        canComplete -> {
            val description = stringResource(R.string.cd_complete_quest, model.title)
            IconButton(
                onClick = onComplete,
                modifier = Modifier
                    .size(dimens.touchTarget)
                    .semantics { contentDescription = description },
            ) {
                Box(
                    modifier = Modifier
                        .size(dimens.icon)
                        .border(dimens.edgeWidth, colors.edge, AwakenTheme.shapes.button),
                )
            }
        }
        else -> Unit
    }
}

private const val SWIPE_THRESHOLD = 0.35f
private const val SWIPE_MAX = 0.5f

internal val previewQuests = listOf(
    QuestRowModel("1", "Iron Arms", Stat.STR, 80, "reps", Difficulty.HARD, VerificationSource.SELF, isMain = true, state = QuestRowState.OPEN),
    QuestRowModel("2", "Inner Gate", Stat.INT, 25, "min", Difficulty.NORMAL, VerificationSource.TIMER, isMain = false, state = QuestRowState.DONE),
    QuestRowModel("3", "Footfall Tally", Stat.VIT, 7_000, "steps", Difficulty.NORMAL, VerificationSource.HEALTH, isMain = false, state = QuestRowState.OPEN),
    QuestRowModel("4", "Still Water", Stat.WIL, 10, "min", Difficulty.NORMAL, VerificationSource.SELF, isMain = false, state = QuestRowState.CLOSED),
    QuestRowModel(
        "5", "Graph Hunt", Stat.INT, 2, "problems", Difficulty.NORMAL, VerificationSource.SELF, isMain = false, state = QuestRowState.OPEN,
        pathBadge = PathBadgeModel("Codeforces 1900"),
    ),
    QuestRowModel(
        "6", "Deep Work Siege", Stat.INT, 50, "min", Difficulty.HARD, VerificationSource.TIMER, isMain = false, state = QuestRowState.OPEN,
        pathBadge = PathBadgeModel("Codeforces 1900", GateRank.C, 50),
    ),
)

@ComponentPreview
@Composable
private fun QuestRowPreview() {
    AwakenTheme {
        SystemWindow(title = "DAILY DIRECTIVE", accent = AwakenTheme.colors.rankC, reveal = false, modifier = Modifier.padding(Spacing.gutter)) {
            previewQuests.forEachIndexed { index, quest -> QuestRow(quest, onComplete = {}, onOpen = {}, showDivider = index > 0) }
        }
    }
}
