package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * One caption line above set 1 of an exercise: an up-arrow and "Last: 50 kg × 12, 12, 12 → try 52.5 kg × 8" (GD §16.6).
 * A suggestion only; nothing here is required or rewarded.
 */
@Composable
fun ProgressionHint(text: String, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(AwakenGlyphs.ArrowUp, contentDescription = null, tint = colors.accent, modifier = Modifier.size(HINT_ICON))
        Text(text, style = AwakenTheme.type.caption, color = colors.textSecondary, modifier = Modifier.weight(1f))
    }
}

/**
 * "New record" pill on a set that broke a personal record (GD §16.7). It scales in once, with a light haptic; both are
 * skipped under reduce-motion / the haptics toggle. Not a reward: the chip pays nothing.
 */
@Composable
fun PrChip(modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val motion = AwakenTheme.motion
    val haptics = AwakenTheme.haptics
    val scale = remember { Animatable(if (motion.reduceMotion) 1f else CHIP_FROM) }
    LaunchedEffect(Unit) {
        haptics.confirm()
        if (scale.value < 1f) scale.animateTo(1f, tween(motion.standardMs, easing = motion.standardEasing))
    }
    Pill(
        text = lexString(R.string.gym_pr_chip),
        color = colors.reward,
        icon = AwakenGlyphs.Star,
        modifier = modifier.scale(scale.value),
    )
}

/**
 * A barbell with the plates for one side drawn on both ends, sized by weight (GD §16.8). [platesPerSide] is heaviest first;
 * [labels] gives the text under each plate of the right side. TalkBack hears [description] only.
 */
@Composable
fun PlateDiagram(platesPerSide: List<Double>, labels: List<String>, description: String, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val heaviest = platesPerSide.maxOrNull() ?: 1.0
    BoxWithConstraints(modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        val count = platesPerSide.size.coerceAtLeast(1)
        val plateWidth = ((maxWidth - BAR_STUB * 2) / (count * 2)).coerceAtMost(PLATE_WIDTH_MAX) - PLATE_GAP
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.height(PLATE_AREA)) {
                platesPerSide.asReversed().forEach { plate -> Plate(plate, heaviest, plateWidth) }
                Spacer(
                    Modifier.width(BAR_STUB * 2).height(BAR_THICKNESS).background(colors.textMuted),
                )
                platesPerSide.forEach { plate -> Plate(plate, heaviest, plateWidth) }
            }
            if (labels.isNotEmpty()) {
                val slot = plateWidth + PLATE_GAP
                Row(modifier = Modifier.width(slot * count * 2 + BAR_STUB * 2)) {
                    // Skip the left plates and the bar's middle, then one label per right-hand plate.
                    Spacer(Modifier.width(slot * count + BAR_STUB * 2))
                    labels.forEach { label ->
                        Text(
                            label,
                            style = AwakenTheme.type.caption,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                            softWrap = false,
                            modifier = Modifier.width(slot).wrapContentWidth(unbounded = true),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Plate(weight: Double, heaviest: Double, width: Dp) {
    val colors = AwakenTheme.colors
    val height = PLATE_MIN_HEIGHT + (PLATE_AREA - PLATE_MIN_HEIGHT) * (weight / heaviest).toFloat()
    Row {
        Spacer(
            Modifier
                .width(width)
                .height(height)
                .background(colors.accent.copy(alpha = PLATE_FILL), RoundedCornerShape(PLATE_RADIUS))
                .border(BorderStroke(PLATE_BORDER, colors.accent), RoundedCornerShape(PLATE_RADIUS)),
        )
        Spacer(Modifier.width(PLATE_GAP))
    }
}

private val HINT_ICON = 16.dp
private const val CHIP_FROM = 0.7f
private val BAR_STUB = 24.dp
private val BAR_THICKNESS = 6.dp
private val PLATE_AREA = 88.dp
private val PLATE_MIN_HEIGHT = 28.dp
private val PLATE_WIDTH_MAX = 36.dp
private val PLATE_GAP = 3.dp
private val PLATE_RADIUS = 4.dp
private val PLATE_BORDER = 1.dp
private const val PLATE_FILL = 0.25f

@ComponentPreview
@Composable
private fun ProgressionHintPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ProgressionHint("Last: 50 kg × 12, 12, 12 → try 52.5 kg × 8")
            ProgressionHint("Last: 50 kg × 12, 12, 10 → try 50 kg × 12, 12, 11")
            ProgressionHint("Add weight or a harder variation")
        }
    }
}

@ComponentPreview
@Composable
private fun PrChipPreview() {
    AwakenTheme(reduceMotion = true) { PrChip() }
}

@ComponentPreview
@Composable
private fun PlateDiagramPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PlateDiagram(listOf(25.0, 15.0), listOf("25", "15"), "25 and 15 kilograms on each side")
            PlateDiagram(listOf(20.0, 1.25), listOf("20", "1.25"), "20 and 1.25 kilograms on each side")
            PlateDiagram(emptyList(), emptyList(), "Empty bar")
        }
    }
}
