package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import java.time.Duration

/** A stopwatch reading split into the groups the face shows. */
data class StopwatchReading(val hours: Int, val minutes: Int, val seconds: Int, val millis: Int) {
    val spoken: String get() = "%d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
}

fun Duration.toStopwatchReading(): StopwatchReading {
    val total = toMillis().coerceAtLeast(0)
    return StopwatchReading(
        hours = (total / MILLIS_PER_HOUR).toInt(),
        minutes = (total / MILLIS_PER_MINUTE % MINUTES_PER_HOUR).toInt(),
        seconds = (total / MILLIS_PER_SECOND % SECONDS_PER_MINUTE).toInt(),
        millis = (total % MILLIS_PER_SECOND).toInt(),
    )
}

private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 3_600_000L
private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_MINUTE = 60L

// Face geometry: how many digit widths a tile needs, and how the tiles share the space.
private const val TILE_GLYPHS = 2
private const val GLYPH_WIDTH_PER_EM = 0.62f
private const val TILE_PADDING_EM = 0.45f
private const val MILLIS_FACTOR = 0.34f
private const val TILE_HEIGHT_PER_EM = 1.25f
private const val MILLIS_LINE_ALLOWANCE = 1.6f
private const val MAX_TILE_SHARE_OF_HEIGHT = 0.42f
private val TILE_CORNER = 28.dp
private val HAIRLINE = 2.dp

/**
 * The stopwatch as a flip-clock face (hours only once there are some, then minutes, seconds), with milliseconds in a
 * large line beneath. The face fills the width it is given: the digits are sized from the space, in a row on wide
 * screens and stacked on tall ones. One TalkBack node reads the whole time.
 */
@Composable
fun StopwatchFace(elapsed: Duration, modifier: Modifier = Modifier) {
    val reading = elapsed.toStopwatchReading()
    val colors = AwakenTheme.colors
    val groups = buildList {
        if (reading.hours > 0) add("%02d".format(reading.hours))
        add("%02d".format(reading.minutes))
        add("%02d".format(reading.seconds))
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth().semantics { contentDescription = reading.spoken }) {
        val wide = maxWidth > maxHeight
        val gap = Spacing.sm
        // The em that makes TILE_GLYPHS digits (plus padding) exactly fill a tile.
        val glyphsPerTile = TILE_GLYPHS * GLYPH_WIDTH_PER_EM + TILE_PADDING_EM
        val tileWidth: Dp = if (wide) (maxWidth - gap * (groups.size - 1)) / groups.size else maxWidth
        // Wide: never taller than a share of the height. Tall: the stacked tiles and the milliseconds line must fit.
        val heightLimit = if (wide) {
            maxHeight * MAX_TILE_SHARE_OF_HEIGHT * groups.size
        } else {
            maxHeight / (groups.size * TILE_HEIGHT_PER_EM / glyphsPerTile + MILLIS_FACTOR * MILLIS_LINE_ALLOWANCE)
        }
        val tileWidthLimited = minOf(tileWidth, heightLimit)
        val emDp = tileWidthLimited / glyphsPerTile
        val tileHeight = emDp * TILE_HEIGHT_PER_EM
        val density = LocalDensity.current
        val digitSize = with(density) { emDp.toSp() }
        val millisSize = with(density) { (emDp * MILLIS_FACTOR * glyphsPerTile).toSp() }
        val digitStyle = AwakenTheme.type.metricL.copy(
            fontSize = digitSize,
            lineHeight = digitSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontFeatureSettings = "tnum",
        )
        Column(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    groups.forEach { FlipTile(it, digitStyle, tileWidthLimited, tileHeight) }
                }
            } else {
                groups.forEach { FlipTile(it, digitStyle, tileWidthLimited, tileHeight) }
            }
            Text(
                ".%03d".format(reading.millis),
                style = digitStyle.copy(fontSize = millisSize, lineHeight = millisSize, fontWeight = FontWeight.SemiBold),
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun FlipTile(text: String, style: androidx.compose.ui.text.TextStyle, width: Dp, height: Dp) {
    val colors = AwakenTheme.colors
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(TILE_CORNER))
            .background(colors.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = colors.textPrimary)
        // The flip-clock seam across the middle.
        Box(modifier = Modifier.fillMaxWidth().height(HAIRLINE).background(colors.surface))
    }
}

@PhonePreviews
@Composable
private fun StopwatchFacePreview() {
    AwakenTheme {
        Box(modifier = Modifier.width(360.dp).background(AwakenTheme.colors.surface)) {
            StopwatchFace(Duration.ofMinutes(37).plusSeconds(5).plusMillis(482))
        }
    }
}
