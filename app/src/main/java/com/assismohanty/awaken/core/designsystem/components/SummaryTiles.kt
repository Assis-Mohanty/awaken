package com.assismohanty.awaken.core.designsystem.components

import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.charts.Segment
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Stat
import java.text.NumberFormat

/** What the Today screen-time tile shows. Never 0 min for something it couldn't read. */
sealed interface ScreenTimeTileModel {
    data object Loading : ScreenTimeTileModel

    /** Usage access isn't granted: "Grant access", no number. */
    data object NoAccess : ScreenTimeTileModel

    /** Access is on but the phone reports nothing for today. */
    data object NotTracked : ScreenTimeTileModel

    data class Tracked(val distracting: Int, val productive: Int, val neutral: Int) : ScreenTimeTileModel {
        val total: Int get() = distracting + productive + neutral
    }
}

/** What the Today health tile shows. Null fields are unknown (not allowed or no source), never shown as 0. */
sealed interface HealthTileModel {
    data object Loading : HealthTileModel

    /** Health isn't connected, or nothing records any of the four metrics: "No data" and a way to set a source up. */
    data object NoData : HealthTileModel

    data class Ready(
        val steps: Long?,
        val distanceKm: Double?,
        val activeKcal: Long?,
        val sleepMinutes: Int?,
        val stepGoal: Int = com.assismohanty.awaken.engine.StepGoalRules.DEFAULT_GOAL,
    ) : HealthTileModel
}

/**
 * Today's screen time as one ring split Distracting / Productive / Neutral (the calorie ring's style) with the total in
 * the middle and a three-line legend. Without access it offers [onGrantAccess] instead of a number. Tap opens details.
 */
@Composable
fun ScreenTimeTile(model: ScreenTimeTileModel, onClick: () -> Unit, onGrantAccess: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    AwakenCard(modifier = modifier, reveal = false, onClick = onClick) {
        CardLabel(lexString(R.string.tile_screen_time), AwakenGlyphs.Phone, colors.textPrimary)
        when (model) {
            ScreenTimeTileModel.Loading -> TileNote(lexString(R.string.tile_loading))
            ScreenTimeTileModel.NoAccess -> {
                TileNote(lexString(R.string.tile_screen_time_no_access))
                Spacer(Modifier.weight(1f))
                SecondaryButton(text = lexString(R.string.tile_grant_access), onClick = onGrantAccess, modifier = Modifier.fillMaxWidth())
            }
            ScreenTimeTileModel.NotTracked -> {
                Text(lexString(R.string.screentime_not_tracked), style = type.numericSmall, color = colors.textPrimary)
                TileNote(lexString(R.string.tile_screen_time_not_tracked))
            }
            is ScreenTimeTileModel.Tracked -> {
                val total = formatMinutesShort(model.total)
                val parts = listOf(
                    Triple(lexString(R.string.screentime_category_distracting), model.distracting, colors.critical),
                    Triple(lexString(R.string.screentime_category_productive), model.productive, colors.positive),
                    Triple(lexString(R.string.screentime_category_neutral), model.neutral, colors.textSecondary),
                )
                val description = lexString(
                    R.string.cd_tile_screen_time,
                    total,
                    formatMinutes(model.distracting),
                    formatMinutes(model.productive),
                    formatMinutes(model.neutral),
                )
                Chart(
                    ChartSpec.SegmentedRing(
                        segments = parts.map { (_, minutes, color) -> Segment(minutes.toFloat(), color) },
                        description = description,
                        size = AwakenTheme.dimens.tileRing * textGraphicScale(),
                        stroke = AwakenTheme.dimens.tileRingStroke,
                    ),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(total, style = type.numericSmall, color = colors.textPrimary, maxLines = 1)
                }
                // Minutes first, so on a narrow tile only the label's tail can ellipsize.
                parts.forEach { (label, minutes, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        LegendSwatch(color)
                        Text(formatMinutesShort(minutes), style = type.caption, color = colors.textPrimary, maxLines = 1)
                        Text(label, style = type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/**
 * Today's body numbers as a compact 2×2 grid: steps, distance, active kcal and last night's sleep; an unknown value is
 * "—". With no data at all it says so and offers [onSetUp] (never a made-up number). Tap opens details.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthTile(model: HealthTileModel, onClick: () -> Unit, onSetUp: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    AwakenCard(modifier = modifier, reveal = false, onClick = onClick) {
        CardLabel(lexString(R.string.tile_health), AwakenGlyphs.Vitality, colors.accent)
        when (model) {
            HealthTileModel.Loading -> TileNote(lexString(R.string.tile_loading))
            HealthTileModel.NoData -> {
                Text(lexString(R.string.health_no_data), style = AwakenTheme.type.numericSmall, color = colors.textPrimary)
                TileNote(lexString(R.string.tile_health_no_data))
                Spacer(Modifier.weight(1f))
                SecondaryButton(text = lexString(R.string.tile_set_up_source), onClick = onSetUp, modifier = Modifier.fillMaxWidth())
            }
            is HealthTileModel.Ready -> {
                val numbers = NumberFormat.getIntegerInstance(currentLocale())
                val oneDecimal = NumberFormat.getNumberInstance(currentLocale()).apply { maximumFractionDigits = 1; minimumFractionDigits = 1 }
                val none = lexString(R.string.tile_value_none)
                val sleep = model.sleepMinutes?.let { lexString(R.string.health_duration_hm, it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) }
                // Known steps become the goal ring (GD §16.11); unknown steps stay a dash in the grid.
                val figures = listOfNotNull(
                    if (model.steps == null) lexString(R.string.health_metric_steps) to none else null,
                    lexString(R.string.health_metric_distance) to (model.distanceKm?.let { lexString(R.string.health_value_distance, oneDecimal.format(it)) } ?: none),
                    lexString(R.string.tile_kcal_short) to (model.activeKcal?.let { lexString(R.string.health_value_kcal, numbers.format(it)) } ?: none),
                    lexString(R.string.tile_sleep) to (sleep ?: none),
                )
                model.steps?.let { StepGoalRing(steps = it, goal = model.stepGoal) }
                // 2×2 where two figures fit side by side; one column on small phones so nothing truncates. (FlowRow, not
                // BoxWithConstraints: Today sizes the tile row by intrinsics.)
                FlowRow(
                    maxItemsInEachRow = 2,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    figures.forEach { (label, value) ->
                        TileFigure(label, value, Modifier.widthIn(min = AwakenTheme.dimens.tileFigureMinWidth).weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TileNote(text: String) {
    Text(text, style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
}

@Composable
private fun TileFigure(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    Column(modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" }) {
        Text(value, style = AwakenTheme.type.numericSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = AwakenTheme.type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "54m", "2h", "2h 57m": durations sized for a half-width tile. */
@Composable
internal fun formatMinutesShort(minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0 -> lexString(R.string.tile_duration_m, rest)
        rest == 0 -> lexString(R.string.tile_duration_h, hours)
        else -> lexString(R.string.tile_duration_hm, hours, rest)
    }
}

private const val MINUTES_PER_HOUR = 60

@PhonePreviews
@Composable
private fun SummaryTilesPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                ScreenTimeTile(ScreenTimeTileModel.Tracked(54, 92, 31), {}, {}, Modifier.weight(1f).fillMaxHeight())
                HealthTile(HealthTileModel.Ready(8_412, 6.2, 412, 452), {}, {}, Modifier.weight(1f).fillMaxHeight())
            }
            Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                ScreenTimeTile(ScreenTimeTileModel.NoAccess, {}, {}, Modifier.weight(1f).fillMaxHeight())
                HealthTile(HealthTileModel.NoData, {}, {}, Modifier.weight(1f).fillMaxHeight())
            }
            Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                ScreenTimeTile(ScreenTimeTileModel.NotTracked, {}, {}, Modifier.weight(1f).fillMaxHeight())
                HealthTile(HealthTileModel.Ready(null, null, null, 405), {}, {}, Modifier.weight(1f).fillMaxHeight())
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

/** Reel Counter's tile on Today: reels watched so far today. Only shown while the feature is switched on. */
@Composable
fun ReelsTile(total: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    AwakenCard(modifier = modifier, reveal = false, onClick = onClick) {
        CardLabel(lexString(R.string.tile_reels), AwakenGlyphs.Phone, colors.textPrimary)
        Text(total.toString(), style = AwakenTheme.type.numeric, color = colors.textPrimary)
        TileNote(lexString(R.string.tile_reels_note))
    }
}

@ComponentPreview
@Composable
private fun ReelsTilePreview() {
    AwakenTheme(reduceMotion = true) { ReelsTile(total = 38, onClick = {}, modifier = Modifier.fillMaxWidth()) }
}
