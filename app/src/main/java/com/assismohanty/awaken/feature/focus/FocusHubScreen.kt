package com.assismohanty.awaken.feature.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.BigValue
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.ListRow
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.ScreenTimeCard
import com.assismohanty.awaken.core.designsystem.components.ScreenTimeTileModel
import com.assismohanty.awaken.core.designsystem.components.SectionHeader
import com.assismohanty.awaken.core.designsystem.components.formatMinutes
import com.assismohanty.awaken.core.designsystem.components.staggeredEntrance
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.LandscapePreviews
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.feature.screentime.openUsageAccessSettings
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Where the Focus tab can send the hunter. Each one is an existing screen; the tab only lays them out. */
data class FocusHubActions(
    val onStart: () -> Unit = {},
    val onOpenReport: () -> Unit = {},
    val onOpenFocusLock: () -> Unit = {},
    val onOpenAllowedApps: () -> Unit = {},
    val onOpenScreenTime: () -> Unit = {},
    val onOpenNights: () -> Unit = {},
    val onOpenReelCounter: () -> Unit = {},
    val onOpenReelSetup: () -> Unit = {},
    val onOpenReelLimits: () -> Unit = {},
    val onOpenPermissions: () -> Unit = {},
    val onGrantUsageAccess: () -> Unit = {},
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
    val context = LocalContext.current
    // Usage access and the Android switches change outside the app, so read them again each time the tab is shown.
    LifecycleResumeEffect(hubViewModel) {
        hubViewModel.refresh()
        onPauseOrDispose { }
    }
    FocusHubScreen(
        focus = focus,
        hub = hub,
        actions = actions.copy(onGrantUsageAccess = { openUsageAccessSettings(context) }),
        modifier = modifier,
    )
}

/**
 * The Focus tab: the one place for everything about attention (design/IA.md section 2). Today's focus and the way to start
 * a session lead, then screen time, then blockers, reels and the reports. New focus tools get a row here.
 */
@Composable
fun FocusHubScreen(
    focus: FocusCardState,
    hub: FocusHubUiState,
    actions: FocusHubActions,
    modifier: Modifier = Modifier,
) {
    LargeTitleScaffold(title = lexString(R.string.nav_focus), subtitle = hub.date?.let(::formatDay), modifier = modifier) {
        FocusHero(focus = focus, onStart = actions.onStart, modifier = Modifier.staggeredEntrance(0))
        ScreenTimeCard(model = hub.screenTime, onClick = actions.onOpenScreenTime, onGrantAccess = actions.onGrantUsageAccess)

        SectionHeader(title = lexString(R.string.focus_hub_section_blockers))
        HubRows {
            HubRow(
                title = lexString(R.string.focus_lock_title),
                glyph = AwakenGlyphs.Lock,
                secondary = if (hub.lockEnabled) {
                    lexPlural(R.plurals.focus_hub_lock_on, hub.allowedCount, hub.allowedCount)
                } else {
                    lexString(R.string.focus_hub_lock_off)
                },
                onClick = actions.onOpenFocusLock,
            )
            HubRow(
                title = lexString(R.string.focus_lock_allowed_title),
                glyph = AwakenGlyphs.Shield,
                secondary = lexPlural(R.plurals.focus_hub_allowed_count, hub.allowedCount, hub.allowedCount),
                onClick = actions.onOpenAllowedApps,
            )
            HubRow(
                title = lexString(R.string.today_limits_title),
                glyph = AwakenGlyphs.Hourglass,
                secondary = lexString(R.string.focus_hub_limits_note),
                onClick = actions.onOpenScreenTime,
            )
            HubRow(
                title = lexString(R.string.bedtime_settings_title),
                glyph = AwakenGlyphs.Moon,
                secondary = if (hub.nightsEnabled) {
                    lexString(R.string.focus_hub_nights_from, formatClock(hub.nightsBedtimeMinute))
                } else {
                    lexString(R.string.focus_hub_nights_off)
                },
                onClick = actions.onOpenNights,
            )
        }

        SectionHeader(title = lexString(R.string.focus_hub_section_reels))
        HubRows {
            HubRow(
                title = lexString(R.string.reel_title),
                glyph = AwakenGlyphs.Phone,
                secondary = hub.reelsToday?.let { lexPlural(R.plurals.focus_hub_reels_today, it, it) }
                    ?: lexString(R.string.focus_hub_reel_note),
                onClick = if (hub.reelsToday == null) actions.onOpenReelSetup else actions.onOpenReelCounter,
            )
            HubRow(
                title = lexString(R.string.reel_open_limits),
                glyph = AwakenGlyphs.Hourglass,
                secondary = lexString(R.string.focus_hub_limits_note),
                onClick = actions.onOpenReelLimits,
            )
        }

        SectionHeader(title = lexString(R.string.focus_hub_section_more))
        HubRows {
            HubRow(
                title = lexString(R.string.focus_report_title),
                glyph = AwakenGlyphs.Bars,
                secondary = lexString(R.string.focus_hub_week_total, formatMinutes(focus.weekMinutes)),
                onClick = actions.onOpenReport,
            )
            HubRow(
                title = lexString(R.string.access_title),
                glyph = AwakenGlyphs.Shield,
                secondary = lexString(if (hub.permissionsNeedAttention) R.string.focus_hub_perm_attention else R.string.focus_hub_perm_ok),
                onClick = actions.onOpenPermissions,
            )
        }
    }
}

/** One card of rows; a row opens its screen. */
@Composable
private fun HubRows(content: @Composable () -> Unit) {
    AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
        content()
    }
}

@Composable
private fun HubRow(title: String, glyph: androidx.compose.ui.graphics.vector.ImageVector, secondary: String, onClick: () -> Unit) {
    ListRow(
        title = title,
        icon = glyph,
        iconTint = AwakenTheme.colors.accent,
        secondary = secondary,
        onClick = onClick,
        trailing = { Chevron() },
    )
}

/** Today's focus as the biggest thing on the tab: the number, the bar to the daily goal, the streak and Start. */
@Composable
private fun FocusHero(focus: FocusCardState, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    AwakenCard(modifier = modifier.fillMaxWidth(), reveal = false) {
        Text(lexString(R.string.focus_hub_today_caption), style = type.caption, color = colors.textSecondary)
        val hours = focus.todayMinutes / MINUTES_PER_HOUR
        val unit = lexString(R.string.focus_hub_minutes_unit)
        if (hours == 0) {
            BigValue(focus.todayMinutes.toString(), unit, large = true)
        } else {
            BigValue(lexString(R.string.focus_hub_hours_value, hours, focus.todayMinutes % MINUTES_PER_HOUR), unit, large = true)
        }
        if (focus.goal > 0) {
            Chart(ChartSpec.Progress(progress = focus.progress, color = colors.accent))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(lexString(R.string.focus_goal_of, focus.todayMinutes, focus.goal), style = type.caption, color = colors.textSecondary)
                Text(
                    lexPlural(R.plurals.focus_hub_streak_line, focus.streak, focus.streak),
                    style = type.caption,
                    color = colors.textSecondary,
                )
            }
        } else {
            Text(lexString(R.string.focus_hub_no_goal), style = type.caption, color = colors.textSecondary)
        }
        PrimaryButton(text = lexString(R.string.focus_card_start), onClick = onStart, modifier = Modifier.fillMaxWidth())
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

private fun formatDay(date: LocalDate): String = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()).format(date)

private fun formatClock(minuteOfDay: Int): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(LocalTime.ofSecondOfDay(minuteOfDay * SECONDS_PER_MINUTE))

private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_MINUTE = 60L

@PhonePreviews
@LandscapePreviews
@Composable
private fun FocusHubPreview() {
    AwakenTheme {
        FocusHubScreen(
            focus = FocusCardState(todayMinutes = 42, weekMinutes = 310, goal = 60, streak = 5),
            hub = FocusHubUiState(
                reelsToday = 18,
                date = LocalDate.of(2026, 11, 6),
                screenTime = ScreenTimeTileModel.Tracked(distracting = 38, productive = 74, neutral = 35),
                lockEnabled = true,
                allowedCount = 3,
                nightsEnabled = true,
                nightsBedtimeMinute = 22 * MINUTES_PER_HOUR + 30,
            ),
            actions = FocusHubActions(),
        )
    }
}

/** No goal yet, no usage access, Focus Lock and the Reel counter off, and something for Android to allow. */
@PhonePreviews
@Composable
private fun FocusHubFreshPreview() {
    AwakenTheme {
        FocusHubScreen(
            focus = FocusCardState(),
            hub = FocusHubUiState(date = LocalDate.of(2026, 11, 6), screenTime = ScreenTimeTileModel.NoAccess, permissionsNeedAttention = true),
            actions = FocusHubActions(),
        )
    }
}
