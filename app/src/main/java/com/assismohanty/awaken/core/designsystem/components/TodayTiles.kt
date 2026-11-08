package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import java.text.NumberFormat

/** Today's focus against the daily goal. [goal] is 0 until it has been read. */
data class FocusTileModel(val todayMinutes: Int = 0, val goal: Int = 0)

/** What one tile shows: the label, the value, an optional note line, and where a tap goes. */
private data class TileSpec(val label: String, val value: String, val note: String?, val onClick: () -> Unit)

/**
 * The three metric tiles under Today's plan (IA §3): focus, screen time and steps. Each is a label, one number and a note, and
 * opens the screen where that thing lives. A missing permission or source is said in the note and fixed by the tap; a number is
 * never invented. Side by side where they fit, one under the other on small phones and at large text.
 */
@Composable
fun TodayTiles(
    focus: FocusTileModel,
    screenTime: ScreenTimeTileModel,
    health: HealthTileModel,
    onOpenFocus: () -> Unit,
    onOpenScreenTime: () -> Unit,
    onOpenSteps: () -> Unit,
    onGrantUsageAccess: () -> Unit,
    onSetUpHealth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val none = lexString(R.string.tile_value_none)
    val numbers = NumberFormat.getIntegerInstance(currentLocale())
    val focusSpec = TileSpec(
        label = lexString(R.string.tile_focus),
        value = formatMinutesShort(focus.todayMinutes),
        note = if (focus.goal > 0) lexString(R.string.tile_of_minutes, focus.goal) else null,
        onClick = onOpenFocus,
    )
    val screenLabel = lexString(R.string.tile_screen_time)
    val screenSpec = when (screenTime) {
        ScreenTimeTileModel.Loading, ScreenTimeTileModel.NotTracked -> TileSpec(screenLabel, none, null, onOpenScreenTime)
        ScreenTimeTileModel.NoAccess -> TileSpec(screenLabel, none, lexString(R.string.tile_grant_access), onGrantUsageAccess)
        is ScreenTimeTileModel.Tracked -> TileSpec(
            screenLabel,
            formatMinutesShort(screenTime.total),
            lexString(R.string.tile_distracting_note, formatMinutesShort(screenTime.distracting)),
            onOpenScreenTime,
        )
    }
    val stepsLabel = lexString(R.string.health_metric_steps)
    val stepsSpec = when (health) {
        HealthTileModel.Loading -> TileSpec(stepsLabel, none, null, onOpenSteps)
        HealthTileModel.NoData -> TileSpec(stepsLabel, none, lexString(R.string.tile_set_up_source), onSetUpHealth)
        is HealthTileModel.Ready -> TileSpec(
            stepsLabel,
            health.steps?.let { numbers.format(it) } ?: none,
            health.steps?.let { lexString(R.string.tile_of_steps, numbers.format(health.stepGoal)) },
            onOpenSteps,
        )
    }
    val specs = listOf(focusSpec, screenSpec, stepsSpec)
    if (isLargeText() || isSmallPhone()) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            specs.forEach { MetricTileCard(it, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            specs.forEach { MetricTileCard(it, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

@Composable
private fun MetricTileCard(spec: TileSpec, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val description = listOfNotNull(spec.label, spec.value, spec.note).joinToString(", ")
    AwakenCard(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        reveal = false,
        onClick = spec.onClick,
        contentPadding = PaddingValues(Spacing.md),
    ) {
        Text(spec.label, style = AwakenTheme.type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(spec.value, style = AwakenTheme.type.numericSmall, color = colors.textPrimary, maxLines = 1)
        spec.note?.let { Text(it, style = AwakenTheme.type.caption, color = colors.textSecondary, maxLines = 2) }
    }
}

@PhonePreviews
@Composable
private fun TodayTilesPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            TodayTiles(
                focus = FocusTileModel(42, 60),
                screenTime = ScreenTimeTileModel.Tracked(distracting = 54, productive = 92, neutral = 31),
                health = HealthTileModel.Ready(steps = 8_412, distanceKm = 6.2, activeKcal = 412, sleepMinutes = 452),
                onOpenFocus = {}, onOpenScreenTime = {}, onOpenSteps = {}, onGrantUsageAccess = {}, onSetUpHealth = {},
            )
            TodayTiles(
                focus = FocusTileModel(),
                screenTime = ScreenTimeTileModel.NoAccess,
                health = HealthTileModel.NoData,
                onOpenFocus = {}, onOpenScreenTime = {}, onOpenSteps = {}, onGrantUsageAccess = {}, onSetUpHealth = {},
            )
        }
    }
}
