package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
private const val MILLIS_LINE_HEIGHT = 1.2f
private const val PAUSED_ALPHA = 0.45f
private val TILE_CORNER = 28.dp
private val HAIRLINE = 2.dp

/**
 * The stopwatch as a flip-clock face (hours only once there are some, then minutes, seconds), with milliseconds in a
 * large line beneath. The face fills the width it is given: the digits are sized from the space, in a row on wide
 * screens and stacked on tall ones. One TalkBack node reads the whole time.
 */
@Composable
fun StopwatchFace(elapsed: Duration, modifier: Modifier = Modifier, paused: Boolean = false) {
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
        // The largest em that fits both ways: the tiles across the width, and the tiles plus the milliseconds line down the height.
        val millisLine = MILLIS_FACTOR * glyphsPerTile * MILLIS_LINE_HEIGHT
        val n = groups.size
        val emByWidth = if (wide) (maxWidth - gap * (n - 1)) / n / glyphsPerTile else maxWidth / glyphsPerTile
        val emByHeight = if (wide) {
            (maxHeight - gap) / (TILE_HEIGHT_PER_EM + millisLine)
        } else {
            (maxHeight - gap * n) / (n * TILE_HEIGHT_PER_EM + millisLine)
        }
        val emDp = minOf(emByWidth, emByHeight)
        val tileWidthLimited: Dp = emDp * glyphsPerTile
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
            modifier = Modifier.fillMaxSize().alpha(if (paused) PAUSED_ALPHA else 1f).clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
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

/**
 * One tile of the face. When its digits change, the top half folds down and the new bottom half falls into place, like a
 * flip clock. Nothing moves while the digits are the same, and with Reduce motion the digits simply change.
 */
@Composable
private fun FlipTile(text: String, style: androidx.compose.ui.text.TextStyle, width: Dp, height: Dp) {
    val colors = AwakenTheme.colors
    val motion = AwakenTheme.motion
    // What is on the tile at rest; while a flip runs it still holds the previous digits.
    var shown by remember { mutableStateOf(text) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(text) {
        if (text == shown) return@LaunchedEffect
        if (motion.reduceMotion) {
            shown = text
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(FLIP_MS, easing = LinearEasing))
        shown = text
        progress.snapTo(0f)
    }
    val flipping = shown != text
    val density = LocalDensity.current
    val tile = colors.surfaceElevated
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(TILE_CORNER))
            .background(tile),
        contentAlignment = Alignment.Center,
    ) {
        if (!flipping) {
            Text(text, style = style, color = colors.textPrimary)
        } else {
            val p = progress.value
            // Behind the flaps: the new top half and the old bottom half.
            TileHalf(text, style, tile, width, height, top = true)
            TileHalf(shown, style, tile, width, height, top = false)
            if (p < HALF) {
                // The old top half folds down about the seam.
                TileHalf(
                    shown, style, tile, width, height, top = true,
                    modifier = Modifier.graphicsLayer {
                        rotationX = FLIP_ANGLE * (p / HALF)
                        cameraDistance = CAMERA_DISTANCE * density.density
                    },
                )
            } else {
                // The new bottom half falls into place from the seam.
                TileHalf(
                    text, style, tile, width, height, top = false,
                    modifier = Modifier.graphicsLayer {
                        rotationX = -FLIP_ANGLE * (1f - (p - HALF) / HALF)
                        cameraDistance = CAMERA_DISTANCE * density.density
                    },
                )
            }
        }
        // The flip-clock seam across the middle.
        Box(modifier = Modifier.fillMaxWidth().height(HAIRLINE).background(colors.surface))
    }
}

/** Half of a tile: the digits drawn at full size and clipped to the top or the bottom half. */
@Composable
private fun TileHalf(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    background: androidx.compose.ui.graphics.Color,
    width: Dp,
    height: Dp,
    top: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(HalfShape(top))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = AwakenTheme.colors.textPrimary)
    }
}

private class HalfShape(private val top: Boolean) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val half = size.height / 2f
        val rect = if (top) {
            androidx.compose.ui.geometry.Rect(0f, 0f, size.width, half)
        } else {
            androidx.compose.ui.geometry.Rect(0f, half, size.width, size.height)
        }
        return androidx.compose.ui.graphics.Outline.Rectangle(rect)
    }
}

private const val FLIP_MS = 320
private const val HALF = 0.5f
private const val FLIP_ANGLE = 90f
private const val CAMERA_DISTANCE = 14f

@PhonePreviews
@Composable
private fun StopwatchFacePreview() {
    AwakenTheme {
        Box(modifier = Modifier.width(360.dp).background(AwakenTheme.colors.surface)) {
            StopwatchFace(Duration.ofMinutes(37).plusSeconds(5).plusMillis(482))
        }
    }
}
