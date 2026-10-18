package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.BreakdownItem
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource

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
)

/** Bottom sheet wrapper; the content is separately previewable and testable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardSheet(model: RewardModel, onDone: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AwakenTheme.colors.panelRaised,
        shape = AwakenTheme.shapes.window,
    ) {
        RewardSheetContent(model, onDone, modifier = Modifier.navigationBarsPadding())
    }
}

/**
 * "QUEST CLEARED" (or "CRITICAL CLEAR" in `crit`): the XP counts up, then stat split, gold, fragments and the formula
 * breakdown (DESIGN_SYSTEM §3). One orchestrated moment: confirm haptic (crit: double click) when it opens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RewardSheetContent(model: RewardModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val motion = AwakenTheme.motion
    val haptics = AwakenTheme.haptics
    val accent = if (model.crit) colors.crit else colors.system
    val shownXp = remember(model) { Animatable(if (motion.reduceMotion) model.xp.toFloat() else 0f) }
    LaunchedEffect(model) {
        if (model.crit) haptics.crit() else haptics.confirm()
        shownXp.animateTo(model.xp.toFloat(), tween(motion.duration(COUNT_UP_MS), easing = motion.standardEasing))
    }
    val finalXp = stringResource(R.string.xp_gain, formatCount(model.xp))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(if (model.crit) R.string.system_title_critical_clear else R.string.system_title_quest_cleared),
            style = type.windowTitle,
            color = accent,
        )
        Text(
            text = stringResource(R.string.xp_gain, formatCount(shownXp.value.toInt())),
            style = type.display,
            color = colors.bone,
            modifier = Modifier.semantics { contentDescription = finalXp },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            model.statXp.forEach { (stat, xp) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Icon(AwakenGlyphs.stat(stat), contentDescription = null, tint = colors.stat(stat), modifier = Modifier.size(AwakenTheme.dimens.iconSmall))
                    Text(stringResource(R.string.stat_gain, stat.label(), formatCount(xp)), style = type.label, color = colors.bone)
                }
            }
            if (model.gold > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Icon(AwakenGlyphs.Coin, contentDescription = null, tint = colors.crit, modifier = Modifier.size(AwakenTheme.dimens.iconSmall))
                    Text(stringResource(R.string.gold_gain, formatCount(model.gold)), style = type.label, color = colors.bone)
                }
            }
            if (model.fragments > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Icon(AwakenGlyphs.Fragment, contentDescription = null, tint = colors.aether, modifier = Modifier.size(AwakenTheme.dimens.iconSmall))
                    Text(pluralStringResource(R.plurals.fragment_gain, model.fragments, model.fragments), style = type.label, color = colors.bone)
                }
            }
        }
        Text(text = breakdownText(model), style = type.caption, color = colors.mist)
        if (model.capped) Text(stringResource(R.string.cap_reached_note), style = type.caption, color = colors.mist)
        PrimaryButton(
            text = stringResource(R.string.action_done),
            onClick = onDone,
            color = accent,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "40 base × 1.5 Normal × 1.20 streak × 1.30 verified = 94 XP" from the engine's breakdown items. */
@Composable
private fun breakdownText(model: RewardModel): String {
    val parts = model.breakdown.mapNotNull { item ->
        when (item.kind) {
            BreakdownItem.Kind.BASE -> stringResource(R.string.breakdown_base, formatMultiplier(item.value, fixed = false))
            BreakdownItem.Kind.DIFFICULTY ->
                stringResource(R.string.breakdown_difficulty, formatMultiplier(item.value, fixed = false), model.difficulty.label())
            BreakdownItem.Kind.STREAK -> stringResource(R.string.breakdown_streak, formatMultiplier(item.value, fixed = true))
            BreakdownItem.Kind.VERIFICATION -> when (model.verification) {
                VerificationSource.SELF -> null // ×1.00 adds nothing to explain
                VerificationSource.HEALTH -> stringResource(R.string.breakdown_verified, formatMultiplier(item.value, fixed = true))
                VerificationSource.TIMER -> stringResource(R.string.breakdown_timed, formatMultiplier(item.value, fixed = true))
            }
            BreakdownItem.Kind.CRIT -> stringResource(R.string.breakdown_crit, formatMultiplier(item.value, fixed = false))
            BreakdownItem.Kind.DAILY_CAP -> stringResource(R.string.breakdown_cap, formatMultiplier(item.value, fixed = true))
        }
    }
    return parts.joinToString(separator = " × ") + " " + stringResource(R.string.breakdown_total, formatCount(model.xp))
}

private const val COUNT_UP_MS = 700

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
