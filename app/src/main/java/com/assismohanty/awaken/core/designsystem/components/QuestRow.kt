package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource
import kotlinx.coroutines.launch

enum class QuestRowState { OPEN, DONE, CLOSED }

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
    val offset = remember(model.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val completeLabel = stringResource(R.string.action_complete_quest)
    val openLabel = stringResource(R.string.action_open_quest)

    Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) HorizontalDivider(thickness = dimens.hairline, color = colors.edge)
        Box {
            // Revealed behind the row while swiping.
            if (canComplete) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(colors.system.copy(alpha = AwakenAlpha.GLOW)),
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
                    .graphicsLayer { translationX = offset.value }
                    .background(if (canComplete && offset.value > 0f) colors.panelRaised else Color.Transparent)
                    .then(
                        if (canComplete) {
                            Modifier.pointerInput(model.id) {
                                val threshold = size.width * SWIPE_THRESHOLD
                                var crossed = false
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        val complete = offset.value >= threshold
                                        scope.launch { offset.animateTo(0f, tween(motion.duration(motion.quickMs))) }
                                        crossed = false
                                        if (complete) onComplete()
                                    },
                                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                                ) { _, dragAmount ->
                                    val next = (offset.value + dragAmount).coerceIn(0f, size.width * SWIPE_MAX)
                                    if (!crossed && next >= threshold) {
                                        crossed = true
                                        haptics.tick()
                                    } else if (crossed && next < threshold) {
                                        crossed = false
                                    }
                                    scope.launch { offset.snapTo(next) }
                                }
                            }
                        } else {
                            Modifier
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
                        text = stringResource(R.string.quest_target_line, formatCount(model.target), model.unit, model.difficulty.label()),
                        style = type.label,
                        color = if (dimmed) colors.fog else colors.mist,
                        textDecoration = decoration,
                    )
                    if (model.isMain || model.verification != VerificationSource.SELF) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                            if (model.isMain) Pill(stringResource(R.string.chip_main), colors.system)
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
)

@ComponentPreview
@Composable
private fun QuestRowPreview() {
    AwakenTheme {
        SystemWindow(title = "DAILY DIRECTIVE", edgeColor = AwakenTheme.colors.rankC, reveal = false, modifier = Modifier.padding(Spacing.gutter)) {
            previewQuests.forEachIndexed { index, quest -> QuestRow(quest, onComplete = {}, onOpen = {}, showDivider = index > 0) }
        }
    }
}
