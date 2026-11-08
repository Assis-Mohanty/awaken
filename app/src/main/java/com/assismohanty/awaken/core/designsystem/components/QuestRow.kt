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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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
    /** The penalty quest (GD §7.3): a normal row with a coral "Comeback" chip. */
    val comeback: Boolean = false,
    /** Set for habit quests (GD §16.1-16.2): the type's own controls and chips. */
    val habit: HabitRowModel? = null,
)

/**
 * Quest row inside a card (DESIGN_SYSTEM §3, the list-row pattern): the stat's glyph in a 36dp tinted circle, title, a
 * target line and small chips, and a hollow check circle. Rows are split by an inset hairline. Swipe right past the
 * threshold completes (haptic tick at the threshold); tap or long-press opens details. Only SELF quests show a check:
 * HEALTH/TIMER quests complete through their verifier (GAME_DESIGN §6.5). [onLongPress] (habits: Archive / Delete)
 * replaces the long-press-to-open and is also offered to TalkBack as [longPressLabel].
 */
@Composable
fun QuestRow(
    model: QuestRowModel,
    onComplete: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    longPressLabel: String? = null,
    habitActions: HabitRowActions = HabitRowActions(),
) {
    val dimens = AwakenTheme.dimens
    Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(
                thickness = dimens.hairline,
                color = AwakenTheme.colors.divider,
                modifier = Modifier.padding(start = dimens.iconWell + Spacing.sm),
            )
        }
        SwipeToComplete(model, onComplete, onOpen, onLongPress, longPressLabel) {
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
        model.habit?.let { habit ->
            HabitRowControls(
                row = model,
                habit = habit,
                actions = habitActions,
                modifier = Modifier.padding(start = dimens.iconWell + Spacing.sm),
            )
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
    onLongPress: (() -> Unit)?,
    longPressLabel: String?,
    content: @Composable () -> Unit,
) {
    val colors = AwakenTheme.colors
    val haptics = AwakenTheme.haptics
    val motion = AwakenTheme.motion
    val canComplete = model.state == QuestRowState.OPEN && model.verification == VerificationSource.SELF && model.habit?.completesByHand != false
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
                    .background(colors.positive.copy(alpha = SWIPE_WASH)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    AwakenGlyphs.Check,
                    contentDescription = null,
                    tint = colors.positive,
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
                .combinedClickable(
                    onClickLabel = openLabel,
                    onClick = onOpen,
                    onLongClickLabel = longPressLabel,
                    onLongClick = onLongPress ?: onOpen,
                )
                .semantics {
                    customActions = buildList {
                        if (canComplete) add(CustomAccessibilityAction(completeLabel) { onComplete(); true })
                        if (onLongPress != null && longPressLabel != null) add(CustomAccessibilityAction(longPressLabel) { onLongPress(); true })
                    }
                },
        ) {
            content()
        }
    }
}

/** The stat's glyph in its 36dp tinted circle; neutral when the quest is closed. */
@Composable
fun StatWell(stat: Stat, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    Box(
        modifier = modifier
            .size(dimens.iconWell)
            .clip(AwakenTheme.shapes.pill)
            .background(if (dimmed) colors.surfaceOverlay else colors.statTint(stat)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            AwakenGlyphs.stat(stat),
            contentDescription = stat.label(),
            tint = if (dimmed) colors.textMuted else colors.statGlyph(stat),
            modifier = Modifier.size(dimens.icon * WELL_GLYPH),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestText(model: QuestRowModel, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val dimmed = model.state == QuestRowState.CLOSED
    val done = model.state == QuestRowState.DONE
    val decoration = if (dimmed) TextDecoration.LineThrough else TextDecoration.None
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs / 2)) {
        Text(
            text = model.title,
            style = type.windowTitle,
            color = if (dimmed || done) colors.textSecondary else colors.textPrimary,
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
            style = type.caption,
            color = if (dimmed) colors.textMuted else colors.textSecondary,
            textDecoration = decoration,
        )
        if (model.isMain || model.comeback || model.verification != VerificationSource.SELF || model.pathBadge != null || model.habit?.hasChip() == true) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                modifier = Modifier.padding(top = Spacing.xxs),
            ) {
                if (model.comeback) Pill(lexString(R.string.chip_comeback), colors.critical)
                if (model.isMain) Pill(lexString(R.string.chip_main), colors.textPrimary, background = colors.surfaceOverlay)
                model.pathBadge?.let { badge ->
                    PathQuestBadge(
                        name = badge.name,
                        gate = badge.gateRank?.let { rank -> lexString(R.string.path_badge_gate, rank.name, badge.gateMinutes ?: rank.durationMinutes) },
                    )
                }
                model.habit?.let { HabitTypeChip(it, model.target) }
                VerifiedBadge(model.verification)
            }
        }
    }
}

@Composable
private fun CheckControl(model: QuestRowModel, onComplete: () -> Unit) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val canComplete = model.state == QuestRowState.OPEN && model.verification == VerificationSource.SELF && model.habit?.completesByHand != false
    when {
        model.state == QuestRowState.DONE -> Box(
            modifier = Modifier
                .padding(Spacing.sm)
                .size(dimens.sigil - Spacing.xxs)
                .clip(AwakenTheme.shapes.pill)
                .background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                AwakenGlyphs.Check,
                contentDescription = lexString(R.string.cd_quest_done, model.title),
                tint = colors.onAccent,
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
                        .size(dimens.sigil - Spacing.xxs)
                        .border(dimens.accentWidth, colors.textMuted, AwakenTheme.shapes.pill),
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

