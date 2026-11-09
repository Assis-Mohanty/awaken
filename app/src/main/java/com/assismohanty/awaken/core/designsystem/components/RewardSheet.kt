package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.BreakdownItem
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource
import com.assismohanty.awaken.engine.path.Feedback

@Immutable
data class RewardModel(
    val xp: Int,
    val statXp: Map<Stat, Int>,
    val gold: Int,
    val capped: Boolean,
    val difficulty: Difficulty,
    val verification: VerificationSource,
    /** Straight from the engine's QuestReward — the UI never recomputes a number (invariant 4). */
    val breakdown: List<BreakdownItem>,
    /** Path quests show the feedback chips (DS §3); they never change the reward. */
    val pathQuest: Boolean = false,
    /** Set when this completion crossed into a new level. */
    val levelReached: Int? = null,
    /** Set when this completion cleared the last Rank-up test stage. */
    val rankReached: Rank? = null,
)

/** Bottom sheet wrapper; the content is separately previewable and testable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardSheet(model: RewardModel, onDone: () -> Unit, feedback: Feedback? = null, onFeedback: (Feedback?) -> Unit = {}) {
    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AwakenTheme.colors.surfaceElevated,
        shape = AwakenTheme.shapes.sheet,
    ) {
        // Scrolls on short screens so Done is always reachable.
        RewardSheetContent(
            model,
            onDone,
            modifier = Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding(),
            feedback = feedback,
            onFeedback = onFeedback,
        )
    }
}

/**
 * Quest complete (DESIGN_SYSTEM §3): restrained. A thin ring closes around a check, the XP counts up as the one big
 * number, a haptic lands, the gains follow as list rows (plus a line when a level or rank was reached) and the formula as
 * clean label / value rows. No confetti, no glow.
 */
@Composable
fun RewardSheetContent(
    model: RewardModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    feedback: Feedback? = null,
    onFeedback: (Feedback?) -> Unit = {},
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val motion = AwakenTheme.motion
    val haptics = AwakenTheme.haptics
    val shownXp = remember(model) { Animatable(if (motion.reduceMotion) model.xp.toFloat() else 0f) }
    LaunchedEffect(model) {
        haptics.confirm()
        shownXp.animateTo(model.xp.toFloat(), tween(motion.duration(motion.countUpMs), easing = motion.standardEasing))
    }
    val finalXp = lexString(R.string.xp_gain, formatCount(model.xp))

    val moment = model.levelReached != null || model.rankReached != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = lexString(R.string.system_title_quest_cleared),
            style = type.title,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        model.levelReached?.let { level -> Moment(lexString(R.string.reward_level_reached, level)) { LevelRing(level) } }
        model.rankReached?.let { rank -> Moment(lexString(R.string.reward_rank_reached, rank.name)) { RankBadge(rank, AwakenTheme.dimens.avatar * 2) } }
        if (!moment) {
            BigValue(
                value = "+" + formatCount(shownXp.value.toInt()),
                unit = lexString(R.string.reward_xp_unit),
                large = true,
                modifier = Modifier.align(Alignment.CenterHorizontally).clearAndSetSemantics { contentDescription = finalXp },
            )
        }
        Breakdown(model)
        if (model.gold > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Icon(AwakenGlyphs.Coin, contentDescription = null, tint = colors.reward, modifier = Modifier.size(AwakenTheme.dimens.icon))
                Text(lexString(R.string.reward_gold_gain, formatCount(model.gold)), style = type.numeric, color = colors.textPrimary)
            }
        }
        if (model.capped) Text(lexString(R.string.cap_reached_note), style = type.caption, color = colors.textSecondary)
        if (model.pathQuest) FeedbackChips(selected = feedback, onSelect = onFeedback)
        PrimaryButton(text = lexString(R.string.action_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

/** The level or rank just reached: a ring or badge on the left, the line beside it. */
@Composable
private fun Moment(text: String, mark: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        mark()
        Text(text, style = AwakenTheme.type.label, color = AwakenTheme.colors.textPrimary)
    }
}

@Composable
private fun LevelRing(level: Int) {
    Box(contentAlignment = Alignment.Center) {
        Rings(listOf(RingSpec(LEVEL_RING_FILL, AwakenTheme.colors.accent)), size = AwakenTheme.dimens.avatar * 2)
        Text(level.toString(), style = AwakenTheme.type.numeric, color = AwakenTheme.colors.textPrimary)
    }
}

private const val LEVEL_RING_FILL = 0.04f

/** "Base 40 · Normal ×1.5 · Streak ×1.20 · Verified ×1.30 · Total 94 XP", one row each, from the engine's items. */
@Composable
private fun Breakdown(model: RewardModel) {
    val colors = AwakenTheme.colors
    val rows = model.breakdown.mapNotNull { item ->
        when (item.kind) {
            BreakdownItem.Kind.BASE -> lexString(R.string.reward_row_base) to formatMultiplier(item.value, fixed = false)
            BreakdownItem.Kind.DIFFICULTY -> model.difficulty.label() to "×" + formatMultiplier(item.value, fixed = false)
            BreakdownItem.Kind.STREAK -> lexString(R.string.reward_row_streak) to "×" + formatMultiplier(item.value, fixed = true)
            BreakdownItem.Kind.VERIFICATION -> when (model.verification) {
                VerificationSource.SELF -> null // ×1.00 adds nothing to explain
                VerificationSource.HEALTH, VerificationSource.USAGE ->
                    lexString(R.string.reward_row_verified) to "×" + formatMultiplier(item.value, fixed = true)
                VerificationSource.TIMER -> lexString(R.string.reward_row_timed) to "×" + formatMultiplier(item.value, fixed = true)
            }
            BreakdownItem.Kind.CRIT -> null // historic ledger rows only; nothing creates this kind any more
            BreakdownItem.Kind.DAILY_CAP -> lexString(R.string.reward_row_cap) to "×" + formatMultiplier(item.value, fixed = true)
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { (label, value) ->
            FormulaRow(label, value, colors.textSecondary)
            HorizontalDivider(thickness = AwakenTheme.dimens.hairline, color = colors.divider)
        }
        FormulaRow(lexString(R.string.reward_row_total), lexString(R.string.reward_total_value, formatCount(model.xp)), colors.textPrimary)
    }
}

@Composable
private fun FormulaRow(label: String, value: String, labelColor: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AwakenTheme.type.body, color = labelColor, modifier = Modifier.weight(1f))
        Text(value, style = AwakenTheme.type.numericSmall, color = AwakenTheme.colors.textPrimary)
    }
}

internal val previewReward = RewardModel(
    xp = 94,
    statXp = mapOf(Stat.STR to 94, Stat.WIL to 28),
    gold = 47,
    capped = false,
    difficulty = Difficulty.NORMAL,
    verification = VerificationSource.HEALTH,
    breakdown = listOf(
        BreakdownItem(BreakdownItem.Kind.BASE, 40.0),
        BreakdownItem(BreakdownItem.Kind.DIFFICULTY, 1.5),
        BreakdownItem(BreakdownItem.Kind.STREAK, 1.2),
        BreakdownItem(BreakdownItem.Kind.VERIFICATION, 1.3),
    ),
)

@ComponentPreview
@Composable
private fun RewardSheetPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            RewardSheetContent(previewReward, onDone = {})
            RewardSheetContent(previewReward.copy(levelReached = 12, rankReached = Rank.D), onDone = {})
            RewardSheetContent(
                previewReward.copy(
                    xp = 15, gold = 0, capped = true, verification = VerificationSource.SELF, statXp = mapOf(Stat.STR to 15),
                    breakdown = previewReward.breakdown + BreakdownItem(BreakdownItem.Kind.DAILY_CAP, 0.25),
                ),
                onDone = {},
            )
        }
    }
}
