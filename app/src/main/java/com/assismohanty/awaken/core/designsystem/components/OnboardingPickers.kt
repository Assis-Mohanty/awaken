package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.onboarding.Bmi
import com.assismohanty.awaken.engine.onboarding.BmiBand
import com.assismohanty.awaken.engine.onboarding.BodyMath
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** The item sitting in the middle of a snapping list: the first visible one, or the next once it has scrolled past half. */
private fun LazyListState.centerIndex(): Int {
    val first = layoutInfo.visibleItemsInfo.firstOrNull() ?: return firstVisibleItemIndex
    return firstVisibleItemIndex + if (firstVisibleItemScrollOffset > first.size / 2) 1 else 0
}

/** Reports the centred item as it changes (with a haptic tick) and follows [selectedIndex] when it is changed from outside. */
@Composable
private fun CenteredIndexEffects(state: LazyListState, count: Int, selectedIndex: Int, onSelected: (Int) -> Unit) {
    val haptics = AwakenTheme.haptics
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelected by rememberUpdatedState(onSelected)
    LaunchedEffect(state, count) {
        snapshotFlow { state.centerIndex() }
            .distinctUntilChanged()
            .drop(1)
            .collect { index ->
                val clamped = index.coerceIn(0, (count - 1).coerceAtLeast(0))
                if (clamped != currentSelected) {
                    haptics.tick()
                    currentOnSelected(clamped)
                }
            }
    }
    LaunchedEffect(selectedIndex, count) {
        if (!state.isScrollInProgress && count > 0 && state.centerIndex() != selectedIndex) state.scrollToItem(selectedIndex)
    }
}

/**
 * A snapping wheel (age, bedtime, wake time): one value per row, the selected row centred in a highlight band and the
 * rows above and below fading out. Flings with momentum and settles on a row; every new value ticks. TalkBack reads
 * [description] with the value and offers next and previous value actions.
 */
@Composable
fun <T> WheelPicker(
    options: List<T>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    label: (T) -> String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val rowHeight = dimens.onbWheelRow
    val rows = dimens.onbWheelRows
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0)))
    val scope = rememberCoroutineScope()
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    CenteredIndexEffects(state, options.size, selectedIndex, onSelected)
    val next = lexString(R.string.onb_wheel_next)
    val previous = lexString(R.string.onb_wheel_previous)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight * rows)
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = options.getOrNull(selectedIndex)?.let(label).orEmpty()
                customActions = listOf(
                    CustomAccessibilityAction(next) {
                        if (selectedIndex < options.lastIndex) scope.launch { state.animateScrollToItem(selectedIndex + 1) }
                        true
                    },
                    CustomAccessibilityAction(previous) {
                        if (selectedIndex > 0) scope.launch { state.animateScrollToItem(selectedIndex - 1) }
                        true
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .clip(AwakenTheme.shapes.inner)
                .background(colors.surfaceOverlay),
        )
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(vertical = rowHeight * (rows / 2)),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(options) { index, option ->
                val distance by remember(state) {
                    derivedStateOf {
                        val info = state.layoutInfo
                        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                        if (item == null) {
                            rows.toFloat()
                        } else {
                            // Item offsets run from the content start, which content padding pushes away from the viewport start.
                            val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            abs((item.offset + item.size / 2f) - centre) / rowPx
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(rowHeight), contentAlignment = Alignment.Center) {
                    Text(
                        text = label(option),
                        style = if (distance < FOCUS_DISTANCE) AwakenTheme.type.numeric else AwakenTheme.type.body,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha((1f - distance / (rows / 2 + 1)).coerceIn(MIN_FADE, 1f)),
                    )
                }
            }
        }
    }
}

/**
 * A ruler (height, weight): ticks in a list that snaps under a fixed needle at the middle. [tick] is the selected tick
 * number, from [first] to [last]; every [majorEvery]th tick is taller. Vertical rulers grow upwards, like a height chart;
 * horizontal ones grow to the right. [majorLabel] writes a number beside each taller tick. TalkBack reads [description] with [valueText] and offers next and previous actions.
 */
@Composable
fun RulerPicker(
    first: Int,
    last: Int,
    tick: Int,
    onTick: (Int) -> Unit,
    majorEvery: Int,
    valueText: (Int) -> String,
    description: String,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    majorLabel: ((Int) -> String)? = null,
) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val count = last - first + 1
    val index = (tick - first).coerceIn(0, count - 1)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = index)
    val scope = rememberCoroutineScope()
    CenteredIndexEffects(state, count, index) { onTick(first + it) }
    val next = lexString(R.string.onb_wheel_next)
    val previous = lexString(R.string.onb_wheel_previous)
    val cell = dimens.onbRulerTickGap
    BoxWithConstraints(
        modifier = modifier
            .then(if (vertical) Modifier.fillMaxHeight().width(dimens.onbRulerSpan) else Modifier.fillMaxWidth().height(dimens.onbRulerSpan))
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = valueText(tick)
                customActions = listOf(
                    CustomAccessibilityAction(next) {
                        if (index < count - 1) scope.launch { state.animateScrollToItem(index + 1) }
                        true
                    },
                    CustomAccessibilityAction(previous) {
                        if (index > 0) scope.launch { state.animateScrollToItem(index - 1) }
                        true
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val viewport: Dp = if (vertical) maxHeight else maxWidth
        val pad = ((viewport - cell) / 2).coerceAtLeast(0.dp)
        val flingBehavior = rememberSnapFlingBehavior(state)
        if (vertical) {
            LazyColumn(
                state = state,
                flingBehavior = flingBehavior,
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = pad),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(List(count) { first + it }) { _, value -> RulerTick(value, majorEvery, vertical = true, cell = cell, majorLabel = majorLabel) }
            }
        } else {
            LazyRow(
                state = state,
                flingBehavior = flingBehavior,
                contentPadding = PaddingValues(horizontal = pad),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(List(count) { first + it }) { _, value -> RulerTick(value, majorEvery, vertical = false, cell = cell, majorLabel = majorLabel) }
            }
        }
        // The needle: fixed in the middle, the tick under it is the value.
        if (vertical) {
            Box(Modifier.align(Alignment.CenterEnd).width(dimens.onbRulerNeedle).height(dimens.accentWidth).background(colors.accent))
        } else {
            Box(Modifier.align(Alignment.BottomCenter).height(dimens.onbRulerNeedle).width(dimens.accentWidth).background(colors.accent))
        }
    }
}

@Composable
private fun RulerTick(value: Int, majorEvery: Int, vertical: Boolean, cell: Dp, majorLabel: ((Int) -> String)?) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val major = value % majorEvery == 0
    val length = if (major) dimens.onbRulerMajorTick else dimens.onbRulerTick
    val thickness = if (major) dimens.accentWidth else dimens.hairline
    val tone = if (major) colors.textSecondary else colors.textMuted
    val label = if (major) majorLabel?.invoke(value) else null
    if (vertical) {
        Box(modifier = Modifier.fillMaxWidth().height(cell), contentAlignment = Alignment.CenterEnd) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.onbRulerLabelGap)) {
                if (label != null) {
                    Text(label, style = AwakenTheme.type.caption, color = colors.textSecondary, softWrap = false, modifier = Modifier.wrapContentHeight(unbounded = true))
                }
                Box(Modifier.width(length).height(thickness).background(tone))
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxHeight().width(cell), contentAlignment = Alignment.BottomCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(dimens.onbRulerLabelGap)) {
                if (label != null) {
                    Text(label, style = AwakenTheme.type.caption, color = colors.textSecondary, softWrap = false, modifier = Modifier.wrapContentWidth(unbounded = true))
                }
                Box(Modifier.height(length).width(thickness).background(tone))
            }
        }
    }
}

/** The big value above a ruler, with its unit: "170 cm". */
@Composable
fun BigValue(value: String, unit: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(value, style = AwakenTheme.type.metricXL, color = AwakenTheme.colors.textPrimary)
        Text(unit, style = AwakenTheme.type.metricUnit, color = AwakenTheme.colors.textSecondary, modifier = Modifier.padding(bottom = Spacing.xs))
    }
}

/** A two-option unit switch (cm / ft, kg / lb) that ticks when it changes. Changing it never changes the stored value. */
@Composable
fun UnitToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = AwakenTheme.haptics
    SegmentedControl(
        options = options,
        selected = selected,
        onSelect = {
            if (it != selected) haptics.tick()
            onSelect(it)
        },
        modifier = modifier,
    )
}

/**
 * Live BMI under the weight ruler (GD §17 OB7). Information only: the number, then either a three-band scale with a badge
 * (age 20 and over) or one neutral line. Colour is never the only carrier: the band is also written.
 */
@Composable
fun BmiCard(bmi: Bmi, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val motion = AwakenTheme.motion
    val bandText = lexString(
        when (bmi.band) {
            BmiBand.BELOW -> R.string.onb_bmi_below
            BmiBand.WITHIN -> R.string.onb_bmi_within
            BmiBand.ABOVE -> R.string.onb_bmi_above
        },
    )
    val value = formatNumber(bmi.value)
    val fraction by animateFloatAsState(
        bmi.scaleFraction.toFloat(),
        tween(motion.duration(motion.quickMs), easing = motion.quickEasing),
        label = "bmiBadge",
    )
    AwakenCard(
        modifier = modifier.fillMaxWidth(),
        title = lexString(R.string.onb_bmi_title),
        reveal = false,
    ) {
        val line = if (bmi.showScale) bandText else lexString(R.string.onb_bmi_neutral)
        val summary = lexString(R.string.onb_bmi_cd, value, line)
        Column(
            modifier = Modifier.clearAndSetSemantics { contentDescription = summary },
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(value, style = AwakenTheme.type.metricL, color = colors.textPrimary)
            if (bmi.showScale) {
                val below = (BodyMath.BMI_BELOW - BodyMath.BMI_SCALE_MIN).toFloat()
                val within = (BodyMath.BMI_ABOVE - BodyMath.BMI_BELOW).toFloat()
                val above = (BodyMath.BMI_SCALE_MAX - BodyMath.BMI_ABOVE).toFloat()
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(dimens.onbBmiBadge)) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(dimens.onbBmiBar)
                            .clip(AwakenTheme.shapes.pill),
                    ) {
                        Box(Modifier.weight(below).fillMaxHeight().background(colors.chartSeries3))
                        Box(Modifier.weight(within).fillMaxHeight().background(colors.positive))
                        Box(Modifier.weight(above).fillMaxHeight().background(colors.chartSeries3))
                    }
                    Box(
                        modifier = Modifier
                            .offset(x = (maxWidth - dimens.onbBmiBadge) * fraction)
                            .size(dimens.onbBmiBadge)
                            .clip(AwakenTheme.shapes.pill)
                            .background(colors.surface)
                            .padding(dimens.accentWidth)
                            .clip(AwakenTheme.shapes.pill)
                            .background(colors.textPrimary),
                    )
                }
            }
            Text(line, style = AwakenTheme.type.body, color = colors.textSecondary)
            Text(lexString(R.string.onb_bmi_note), style = AwakenTheme.type.caption, color = colors.textMuted)
        }
    }
}

private const val FOCUS_DISTANCE = 0.5f
private const val MIN_FADE = 0.15f

@ComponentPreview
@Composable
private fun WheelPickerPreview() {
    AwakenTheme(reduceMotion = true) {
        Box(Modifier.padding(Spacing.gutter)) {
            WheelPicker(options = (13..100).toList(), selectedIndex = 12, onSelected = {}, label = { it.toString() }, description = "Age")
        }
    }
}

@ComponentPreview
@Composable
private fun RulerPickerHorizontalPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            BigValue("70", "kg")
            RulerPicker(first = 60, last = 600, tick = 140, onTick = {}, majorEvery = 10, valueText = { it.toString() }, description = "Weight", majorLabel = { (it / 2).toString() })
        }
    }
}

@ComponentPreview
@Composable
private fun RulerPickerVerticalPreview() {
    AwakenTheme(reduceMotion = true) {
        Row(Modifier.padding(Spacing.gutter).height(260.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            BigValue("170", "cm")
            RulerPicker(first = 120, last = 230, tick = 170, onTick = {}, majorEvery = 10, valueText = { it.toString() }, description = "Height", vertical = true, majorLabel = { it.toString() })
        }
    }
}

@ComponentPreview
@Composable
private fun BmiCardPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            BmiCard(BodyMath.bmi(170.0, 70.0, age = 25))
            BmiCard(BodyMath.bmi(170.0, 90.0, age = 16))
            UnitToggle(listOf("cm", "ft"), selected = 0, onSelect = {})
        }
    }
}
