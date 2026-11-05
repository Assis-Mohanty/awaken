package com.assismohanty.awaken.feature.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.ListRow
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.SectionHeader
import com.assismohanty.awaken.core.designsystem.components.formatMinutes
import com.assismohanty.awaken.core.designsystem.components.staggeredEntrance
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/** Where the Focus tab can send the hunter. Each one is an existing screen; the tab only lays them out. */
data class FocusHubActions(
    val onStart: () -> Unit = {},
    val onOpenReport: () -> Unit = {},
    val onOpenFocusLock: () -> Unit = {},
    val onOpenAllowedApps: () -> Unit = {},
    val onOpenReelCounter: () -> Unit = {},
    val onOpenReelSetup: () -> Unit = {},
    val onOpenReelLimits: () -> Unit = {},
)

/** Wires the Focus tab to its two small view models. */
@Composable
fun FocusHubRoute(
    actions: FocusHubActions,
    modifier: Modifier = Modifier,
    cardViewModel: FocusCardViewModel = hiltViewModel(),
    hubViewModel: FocusHubViewModel = hiltViewModel(),
) {
    val focus by cardViewModel.uiState.collectAsStateWithLifecycle()
    val hub by hubViewModel.uiState.collectAsStateWithLifecycle()
    FocusHubScreen(focus = focus, hub = hub, actions = actions, modifier = modifier)
}

/**
 * The Focus tab: the one place for everything about attention. Today's focus and the way to start a session lead; the
 * blockers (Focus Lock, Reel counter) and the reports follow. New focus tools get a row here.
 */
@Composable
fun FocusHubScreen(
    focus: FocusCardState,
    hub: FocusHubUiState,
    actions: FocusHubActions,
    modifier: Modifier = Modifier,
) {
    LargeTitleScaffold(title = lexString(R.string.nav_focus), modifier = modifier) {
        FocusHero(focus = focus, onStart = actions.onStart, modifier = Modifier.staggeredEntrance(0))

        SectionHeader(title = lexString(R.string.focus_hub_section_blockers))
        AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
            ListRow(
                title = lexString(R.string.focus_lock_title),
                icon = AwakenGlyphs.Lock,
                iconTint = AwakenTheme.colors.accent,
                secondary = lexString(R.string.focus_hub_lock_note),
                onClick = actions.onOpenFocusLock,
                trailing = { Chevron() },
            )
            ListRow(
                title = lexString(R.string.focus_lock_allowed_title),
                icon = AwakenGlyphs.Shield,
                iconTint = AwakenTheme.colors.accent,
                secondary = lexString(R.string.focus_hub_allowed_note),
                onClick = actions.onOpenAllowedApps,
                trailing = { Chevron() },
            )
            ListRow(
                title = lexString(R.string.reel_title),
                icon = AwakenGlyphs.Phone,
                iconTint = AwakenTheme.colors.accent,
                secondary = hub.reelsToday?.let { lexPlural(R.plurals.focus_hub_reels_today, it, it) }
                    ?: lexString(R.string.focus_hub_reel_note),
                onClick = if (hub.reelsToday == null) actions.onOpenReelSetup else actions.onOpenReelCounter,
                trailing = { Chevron() },
            )
            ListRow(
                title = lexString(R.string.reel_open_limits),
                icon = AwakenGlyphs.Hourglass,
                iconTint = AwakenTheme.colors.accent,
                secondary = lexString(R.string.focus_hub_limits_note),
                onClick = actions.onOpenReelLimits,
                trailing = { Chevron() },
            )
        }

        SectionHeader(title = lexString(R.string.focus_hub_section_reports))
        AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
            ListRow(
                title = lexString(R.string.focus_report_title),
                icon = AwakenGlyphs.Bars,
                iconTint = AwakenTheme.colors.accent,
                secondary = lexString(R.string.focus_hub_report_note),
                onClick = actions.onOpenReport,
                trailing = { Chevron() },
            )
        }
    }
}

/** Today's focus as the biggest thing on the tab, with progress to the daily goal, the 7-day total, the streak and Start. */
@Composable
private fun FocusHero(focus: FocusCardState, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    AwakenCard(modifier = modifier.fillMaxWidth(), reveal = false) {
        Text(lexString(R.string.focus_hub_today_caption), style = type.overline, color = colors.textSecondary)
        Text(formatMinutes(focus.todayMinutes), style = type.metricXL, color = colors.textPrimary)
        if (focus.goal > 0) {
            Chart(ChartSpec.Progress(progress = focus.progress, color = colors.accent))
            Text(lexString(R.string.focus_goal_of, focus.todayMinutes, focus.goal), style = type.caption, color = colors.textSecondary)
        } else {
            Text(lexString(R.string.focus_hub_no_goal), style = type.caption, color = colors.textSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), modifier = Modifier.fillMaxWidth()) {
            HeroStat(formatMinutes(focus.weekMinutes), lexString(R.string.focus_hub_week_label))
            if (focus.goal > 0) HeroStat(focus.streak.toString(), lexString(R.string.focus_hub_streak_label))
        }
        PrimaryButton(text = lexString(R.string.focus_card_start), onClick = onStart, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun HeroStat(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(value, style = AwakenTheme.type.metricL, color = AwakenTheme.colors.textPrimary)
        Text(label, style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
    }
}

@Composable
private fun Chevron() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            AwakenGlyphs.ChevronRight,
            contentDescription = null,
            tint = AwakenTheme.colors.textSecondary,
            modifier = Modifier.size(AwakenTheme.dimens.iconSmall),
        )
    }
}

@PhonePreviews
@Composable
private fun FocusHubPreview() {
    AwakenTheme {
        FocusHubScreen(
            focus = FocusCardState(todayMinutes = 42, weekMinutes = 310, goal = 60, streak = 5),
            hub = FocusHubUiState(reelsToday = 18),
            actions = FocusHubActions(),
        )
    }
}

/** No goal yet and the Reel counter off: the hero says how to set a goal, the counter row explains itself. */
@PhonePreviews
@Composable
private fun FocusHubFreshPreview() {
    AwakenTheme { FocusHubScreen(focus = FocusCardState(), hub = FocusHubUiState(), actions = FocusHubActions()) }
}
