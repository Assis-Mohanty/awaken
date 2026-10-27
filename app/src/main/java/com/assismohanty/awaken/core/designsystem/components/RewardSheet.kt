package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.BreakdownItem
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource
import com.assismohanty.awaken.engine.path.Feedback

@Immutable
data class RewardModel(
    val xp: Int,
    val statXp: Map<Stat, Int>,
    val gold: Int,
    val fragments: Int,
    val crit: Boolean,
    val capped: Boolean,
    val difficulty: Difficulty,
    val verification: VerificationSource,
    /** Straight from the engine's QuestReward — the UI never recomputes a number (invariant 4). */
    val breakdown: List<BreakdownItem>,
    /** Path quests show the feedback chips (DS §3); they never change the reward. */
    val pathQuest: Boolean = false,
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
 * Quest complete (DESIGN_SYSTEM §3): restrained and celebratory. A thin ring closes around a check, the XP counts up as
 * the one big number, a haptic lands (crit: a double click), the gains follow as list rows and the formula as clean
 * label / value rows. No confetti, no glow.
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
        if (model.crit) haptics.crit() else haptics.confirm()
        shownXp.animateTo(model.xp.toFloat(), tween(motion.duration(motion.countUpMs), easing = motion.standardEasing))
    }
    val finalXp = lexString(R.string.xp_gain, formatCount(model.xp))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Rings(listOf(RingSpec(1f, if (model.crit) colors.reward else colors.positive)), size = AwakenTheme.dimens.avatar * 2)
            Icon(AwakenGlyphs.Check, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(AwakenTheme.dimens.icon * 1.5f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = lexString(if (model.crit) R.string.system_title_critical_clear else R.string.system_title_quest_cleared),
                style = type.title,
                color = colors.textSecondary,
                modifier = Modifier.semantics { heading() },
            )
            BigValue(
                value = "+" + formatCount(shownXp.value.toInt()),
                unit = lexString(R.string.reward_xp_unit),
                large = true,
                modifier = Modifier.clearAndSetSemantics { contentDescription = finalXp },
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            model.statXp.forEach { (stat, xp) ->
                GainRow(AwakenGlyphs.stat(stat), colors.stat(stat), stat.label(), "+" + formatCount(xp))
            }
            if (model.gold > 0) {
                GainRow(AwakenGlyphs.Coin, colors.reward, lexString(R.string.reward_gold), "+" + formatCount(model.gold))
            }
            if (model.fragments > 0) {
                GainRow(AwakenGlyphs.Fragment, colors.stat(Stat.INT), lexPlural(R.plurals.fragment_gain, model.fragments, model.fragments), "")
            }
        }
        Breakdown(model)
        if (model.capped) Text(lexString(R.string.cap_reached_note), style = type.caption, color = colors.textSecondary)
        if (model.pathQuest) FeedbackChips(selected = feedback, onSelect = onFeedback)
        PrimaryButton(text = lexString(R.string.action_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun GainRow(icon: ImageVector, tint: Color, label: String, value: String) {
    ListRow(title = label, icon = icon, iconTint = tint) {
        Text(value, style = AwakenTheme.type.numeric, color = AwakenTheme.colors.textPrimary)
    }
}

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
            BreakdownItem.Kind.CRIT -> lexString(R.string.reward_row_crit) to "×" + formatMultiplier(item.value, fixed = false)
            BreakdownItem.Kind.DAILY_CAP -> lexString(R.string.reward_row_cap) to "×" + formatMultiplier(item.value, fixed = true)
        }
    }
    // A pressed-colour well: the sheet itself is card-coloured, so a card would vanish into it.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AwakenTheme.shapes.inner)
            .background(colors.surfaceOverlay)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        rows.forEach { (label, value) -> FormulaRow(label, value, colors.textSecondary) }
        HorizontalDivider(thickness = AwakenTheme.dimens.hairline, color = colors.divider)
        FormulaRow(lexString(R.string.reward_row_total), lexString(R.string.reward_total_value, formatCount(model.xp)), colors.textPrimary)
    }
}

@Composable
private fun FormulaRow(label: String, value: String, labelColor: Color) {
    Row(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AwakenTheme.type.body, color = labelColor, modifier = Modifier.weight(1f))
        Text(value, style = AwakenTheme.type.numericSmall, color = AwakenTheme.colors.textPrimary)
    }
}

internal val previewReward = RewardModel(
    xp = 94,
    statXp = mapOf(Stat.STR to 94, Stat.WIL to 28),
    gold = 47,
    fragments = 0,
    crit = false,
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
            RewardSheetContent(
                previewReward.copy(
                    xp = 188, crit = true, fragments = 1,
                    breakdown = previewReward.breakdown + BreakdownItem(BreakdownItem.Kind.CRIT, 2.0),
                ),
                onDone = {},
            )
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
