package com.assismohanty.awaken.core.designsystem.components

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.TimeOfDay
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
    /** Reps logged on the set counter so far (SELF rep quests only); null until the first set. */
    val progress: Int? = null,
    /** Today's timeline block (GD §14.5); null = Anytime. */
    val timeOfDay: TimeOfDay? = null,
)

/**
 * Quest row inside a card (DESIGN_SYSTEM §3): the stat's glyph on its pastel, title, target line and badges, and a round
 * check. Swipe right past the threshold completes (haptic tick at the threshold); tap or long-press opens details. Only
 * SELF quests show a check: HEALTH/TIMER quests complete through their verifier (GAME_DESIGN §6.5).
 */
@Composable
fun QuestRow(
    model: QuestRowModel,
    onComplete: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val dimens = AwakenTheme.dimens
    Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(
                thickness = dimens.hairline,
                color = AwakenTheme.palette.edge,
                modifier = Modifier.padding(start = dimens.iconWell + Spacing.sm),
            )
        }
        SwipeToComplete(model, onComplete, onOpen) {
            Row(
                modifier = Modifier.padding(vertical = dimens.questRowPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                StatWell(model.stat, dimmed = model.state == QuestRowState.CLOSED)
                QuestText(model, modifier = Modifier.weight(1f))
                CheckControl(model, onComplete)
            }
        }
    }
}

/**
 * One quest on Today's vertical timeline (DESIGN_SYSTEM §4.2): a node on a rail (filled once done) and the quest as its
 * own soft card. The main quest's card carries its stat's pastel. [first] / [last] trim the rail at the ends.
 */
@Composable
fun TimelineQuestRow(
    model: QuestRowModel,
    onComplete: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    last: Boolean = false,
) {
    val palette = AwakenTheme.palette
    val dimens = AwakenTheme.dimens
    val shape = AwakenTheme.shapes.inner
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TimelineNode(model, first = first, last = last, modifier = Modifier.fillMaxHeight())
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = Spacing.xxs)
                .clip(shape)
                .background(if (model.isMain && model.state != QuestRowState.CLOSED) palette.statTint(model.stat) else palette.panel)
                .border(dimens.hairline, if (model.isMain) Color.Transparent else palette.edge, shape),
        ) {
            SwipeToComplete(model, onComplete, onOpen) {
                Row(
                    modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm, end = Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    QuestText(model, modifier = Modifier.weight(1f))
                    CheckControl(model, onComplete)
                }
            }
        }
    }
}

/** The node and rail of [TimelineQuestRow]: done = filled accent with a check, open = a ring in the stat's colour. */
@Composable
private fun TimelineNode(model: QuestRowModel, first: Boolean, last: Boolean, modifier: Modifier = Modifier) {
    val palette = AwakenTheme.palette
    val dimens = AwakenTheme.dimens
    val rail = palette.edge
    Box(
        modifier = modifier
            .width(dimens.timelineNode)
            .drawBehind {
                val x = size.width / 2f
                val stroke = dimens.timelineRail.toPx()
                val nodeTop = size.height / 2f - dimens.timelineNode.toPx() / 2f
                val nodeBottom = size.height / 2f + dimens.timelineNode.toPx() / 2f
                if (!first) drawLine(rail, Offset(x, 0f), Offset(x, nodeTop), stroke)
                if (!last) drawLine(rail, Offset(x, nodeBottom), Offset(x, size.height), stroke)
            },
        contentAlignment = Alignment.Center,
    ) {
        val node = Modifier
            .size(dimens.timelineNode)
            .clip(AwakenTheme.shapes.pill)
        when (model.state) {
            QuestRowState.DONE -> Box(node.background(palette.system), contentAlignment = Alignment.Center) {
                Icon(AwakenGlyphs.Check, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(dimens.iconSmall))
            }
            QuestRowState.OPEN -> Box(
                node
                    .background(palette.panel)
                    .border(dimens.timelineRail, palette.statFill(model.stat), AwakenTheme.shapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AwakenGlyphs.stat(model.stat), contentDescription = null, tint = palette.stat(model.stat), modifier = Modifier.size(dimens.iconSmall))
            }
            QuestRowState.CLOSED -> Box(node.background(palette.edge))
        }
    }
}

/** Swipe right to complete (SELF, open quests only), tap or long-press to open. Wraps the row's visible content. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeToComplete(
    model: QuestRowModel,
    onComplete: () -> Unit,
    onOpen: () -> Unit,
    content: @Composable () -> Unit,
) {
    val palette = AwakenTheme.palette
    val haptics = AwakenTheme.haptics
    val motion = AwakenTheme.motion
    val canComplete = model.state == QuestRowState.OPEN && model.verification == VerificationSource.SELF
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
    val completeLabel = lexString(R.string.action_complete_quest)
    val openLabel = lexString(R.string.action_open_quest)
    Box {
        // Revealed behind the row only while swiping; at rest the transparent row must not show it.
        if (canComplete && offset > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(palette.verified.copy(alpha = SWIPE_WASH)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    AwakenGlyphs.Check,
                    contentDescription = null,
                    tint = palette.verified,
                    modifier = Modifier.padding(start = Spacing.md).size(AwakenTheme.dimens.icon),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowWidth = it.width }
                .graphicsLayer { translationX = offset }
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
                },
        ) {
            content()
        }
    }
}

/** The stat's glyph on its pastel disc. */
@Composable
fun StatWell(stat: Stat, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val palette = AwakenTheme.palette
    val dimens = AwakenTheme.dimens
    Box(
        modifier = modifier
            .size(dimens.iconWell)
            .clip(AwakenTheme.shapes.pill)
            .background(if (dimmed) palette.panelRaised else palette.statTint(stat)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            AwakenGlyphs.stat(stat),
            contentDescription = stat.label(),
            tint = if (dimmed) palette.fog else palette.stat(stat),
            modifier = Modifier.size(dimens.icon * WELL_GLYPH),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestText(model: QuestRowModel, modifier: Modifier = Modifier) {
    val palette = AwakenTheme.palette
    val type = AwakenTheme.type
    val dimmed = model.state == QuestRowState.CLOSED
    val decoration = if (dimmed) TextDecoration.LineThrough else TextDecoration.None
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = model.title,
            style = if (model.isMain) type.title else type.body.copy(fontWeight = FontWeight.SemiBold),
            color = if (dimmed) palette.fog else palette.bone,
            textDecoration = decoration,
        )
        Text(
            text = if (model.progress != null) {
                lexString(
                    R.string.quest_target_progress_line,
                    formatCount(model.progress),
                    formatCount(model.target),
                    unitLabel(model.unit, model.target),
                    model.difficulty.label(),
                )
            } else {
                lexString(R.string.quest_target_line, formatCount(model.target), unitLabel(model.unit, model.target), model.difficulty.label())
            },
            style = type.label,
            color = if (dimmed) palette.fog else palette.mist,
            textDecoration = decoration,
        )
        if (model.isMain || model.verification != VerificationSource.SELF || model.pathBadge != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                modifier = Modifier.padding(top = Spacing.xxs),
            ) {
                if (model.isMain) Pill(lexString(R.string.chip_main), palette.stat(model.stat), background = palette.panel)
                model.pathBadge?.let { badge ->
                    PathQuestBadge(
                        name = badge.name,
                        gate = badge.gateRank?.let { rank -> lexString(R.string.path_badge_gate, rank.name, badge.gateMinutes ?: rank.durationMinutes) },
                    )
                }
                VerifiedBadge(model.verification)
            }
        }
    }
}

@Composable
private fun CheckControl(model: QuestRowModel, onComplete: () -> Unit) {
    val palette = AwakenTheme.palette
    val dimens = AwakenTheme.dimens
    val canComplete = model.state == QuestRowState.OPEN && model.verification == VerificationSource.SELF
    when {
        model.state == QuestRowState.DONE -> Box(
            modifier = Modifier
                .padding(Spacing.xs)
                .size(dimens.sigil + Spacing.xxs)
                .clip(AwakenTheme.shapes.pill)
                .background(palette.system),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                AwakenGlyphs.Check,
                contentDescription = lexString(R.string.cd_quest_done, model.title),
                tint = palette.onAccent,
                modifier = Modifier.size(dimens.iconSmall),
            )
        }
        canComplete -> {
            val description = lexString(R.string.cd_complete_quest, model.title)
            IconButton(
                onClick = onComplete,
                modifier = Modifier
                    .size(dimens.touchTarget)
                    .semantics { contentDescription = description },
            ) {
                Box(
                    modifier = Modifier
                        .size(dimens.sigil + Spacing.xxs)
                        .border(dimens.accentWidth, palette.edge, AwakenTheme.shapes.pill),
                )
            }
        }
        else -> Unit
    }
}

private const val SWIPE_THRESHOLD = 0.35f
private const val SWIPE_MAX = 0.5f
private const val SWIPE_WASH = 0.14f
private const val WELL_GLYPH = 0.8f

internal val previewQuests = listOf(
    QuestRowModel("1", "Do push-ups", Stat.STR, 80, "reps", Difficulty.HARD, VerificationSource.SELF, isMain = true, state = QuestRowState.OPEN),
    QuestRowModel("2", "Do deep work", Stat.INT, 25, "min", Difficulty.NORMAL, VerificationSource.TIMER, isMain = false, state = QuestRowState.DONE),
    QuestRowModel("3", "Walk your steps", Stat.VIT, 7_000, "steps", Difficulty.NORMAL, VerificationSource.HEALTH, isMain = false, state = QuestRowState.OPEN),
    QuestRowModel("4", "Sit and meditate", Stat.WIL, 10, "min", Difficulty.NORMAL, VerificationSource.SELF, isMain = false, state = QuestRowState.CLOSED),
    QuestRowModel(
        "5", "Solve graph problems", Stat.INT, 2, "problems", Difficulty.NORMAL, VerificationSource.SELF, isMain = false, state = QuestRowState.OPEN,
        pathBadge = PathBadgeModel("Codeforces 1900"),
    ),
    QuestRowModel(
        "6", "Deep work on DP", Stat.INT, 50, "min", Difficulty.HARD, VerificationSource.TIMER, isMain = false, state = QuestRowState.OPEN,
        pathBadge = PathBadgeModel("Codeforces 1900", GateRank.C, 50),
    ),
)

@ComponentPreview
@Composable
private fun QuestRowPreview() {
    AwakenTheme {
        AwakenCard(title = "Habits", reveal = false, modifier = Modifier.padding(Spacing.gutter)) {
            previewQuests.forEachIndexed { index, quest -> QuestRow(quest, onComplete = {}, onOpen = {}, showDivider = index > 0) }
        }
    }
}

@ComponentPreview
@Composable
private fun TimelineQuestRowPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter)) {
            previewQuests.take(4).forEachIndexed { index, quest ->
                TimelineQuestRow(quest, onComplete = {}, onOpen = {}, first = index == 0, last = index == 3)
            }
        }
    }
}
