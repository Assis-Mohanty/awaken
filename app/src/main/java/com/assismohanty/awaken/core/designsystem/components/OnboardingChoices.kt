package com.assismohanty.awaken.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/** One option of an [ImageChoiceGrid]: a stable [key], the words, an optional picture and an optional [detail] line that opens under the label when selected. */
data class ChoiceItem(val key: String, val label: String, @DrawableRes val image: Int? = null, val detail: String? = null)

/**
 * An option card for the quiz steps: a picture over a label, or just the label. The selected card gets an accent outline and
 * a filled check; in a multi-select an empty ring shows on the others. Selecting ticks. Checkbox semantics when [multi],
 * radio otherwise; the whole card is the touch target.
 */
@Composable
fun ImageChoiceCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    multi: Boolean,
    modifier: Modifier = Modifier,
    @DrawableRes image: Int? = null,
    detail: String? = null,
) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val haptics = AwakenTheme.haptics
    val choose = {
        haptics.tick()
        onClick()
    }
    val selection = if (multi) {
        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { choose() })
    } else {
        Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = choose)
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = if (image == null) dimens.touchTarget else dimens.onbCardMinHeight)
            .clip(AwakenTheme.shapes.inner)
            .background(colors.surfaceElevated)
            .border(if (selected) dimens.accentWidth else dimens.edgeWidth, if (selected) colors.accent else colors.divider, AwakenTheme.shapes.inner)
            .then(selection),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(tween(AwakenTheme.motion.duration(AwakenTheme.motion.standardMs), easing = AwakenTheme.motion.standardEasing))
                .padding(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (image != null) {
                Image(
                    painter = painterResource(image),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(dimens.onbCardImage),
                )
            }
            Text(
                text = label,
                style = AwakenTheme.type.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = if (image == null) Spacing.lg else Spacing.xxs, vertical = if (image == null) Spacing.xxs else Spacing.zero),
            )
            if (selected && detail != null) {
                Text(
                    text = detail,
                    style = AwakenTheme.type.caption,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Spacing.xs)
                .size(dimens.onbCheck)
                .clip(AwakenTheme.shapes.pill)
                .background(if (selected) colors.accent else colors.surfaceElevated)
                .then(if (selected) Modifier else Modifier.border(dimens.edgeWidth, colors.divider, AwakenTheme.shapes.pill)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(AwakenGlyphs.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(dimens.iconSmall))
        }
    }
}

/**
 * The options of one quiz step in a grid of [columns] cards. [multi] picks any number (the caller decides what a pick
 * clears, e.g. "None"); otherwise one at a time. Scrolls on its own when the cards don't fit.
 */
@Composable
fun ImageChoiceGrid(
    items: List<ChoiceItem>,
    selectedKeys: Set<String>,
    onToggle: (String) -> Unit,
    multi: Boolean,
    modifier: Modifier = Modifier,
    columns: Int = 2,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        contentPadding = PaddingValues(bottom = Spacing.md),
    ) {
        items(items, key = { it.key }) { item ->
            ImageChoiceCard(
                label = item.label,
                selected = item.key in selectedKeys,
                onClick = { onToggle(item.key) },
                multi = multi,
                image = item.image,
                detail = item.detail,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A picture that is only decoration: the step's heading says what it is. Crossfades when [image] changes. */
@Composable
fun OnbIllustration(@DrawableRes image: Int, modifier: Modifier = Modifier) {
    val motion = AwakenTheme.motion
    Crossfade(
        targetState = image,
        animationSpec = tween(motion.duration(motion.standardMs), easing = motion.standardEasing),
        modifier = modifier,
        label = "onboardingIllustration",
    ) { id ->
        Image(
            painter = painterResource(id),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The body-shape control: the picture for [stage] (1..[stages]) crossfading as a slider with one stop per stage moves.
 * Every move ticks. TalkBack reads "Stage 3 of 5" and [description].
 */
@Composable
fun StageSlider(
    stage: Int,
    onStage: (Int) -> Unit,
    stages: Int,
    imageFor: (Int) -> Int,
    description: String,
    stageText: String,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val haptics = AwakenTheme.haptics
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        OnbIllustration(image = imageFor(stage), modifier = Modifier.weight(1f).fillMaxWidth())
        Text(stageText, style = AwakenTheme.type.title, color = colors.textPrimary)
        Slider(
            value = stage.toFloat(),
            onValueChange = {
                val next = it.toInt().coerceIn(1, stages)
                if (next != stage) {
                    haptics.tick()
                    onStage(next)
                }
            },
            valueRange = 1f..stages.toFloat(),
            steps = stages - 2,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.accentMuted,
                activeTickColor = colors.onAccent,
                inactiveTickColor = colors.textMuted,
            ),
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = description
                stateDescription = stageText
            },
        )
    }
}

@ComponentPreview
@Composable
private fun ImageChoiceGridPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(Modifier.padding(Spacing.gutter).height(dimensionsPreviewHeight), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ImageChoiceGrid(
                items = listOf(
                    ChoiceItem("a", "Strength", R.drawable.onb_improve_strength_n),
                    ChoiceItem("b", "Focus", R.drawable.onb_improve_focus_n),
                    ChoiceItem("c", "Sleep", R.drawable.onb_improve_sleep_n),
                    ChoiceItem("d", "Learning", R.drawable.onb_improve_learning_n),
                ),
                selectedKeys = setOf("b", "c"),
                onToggle = {},
                multi = true,
            )
        }
    }
}

@ComponentPreview
@Composable
private fun TextChoiceCardsPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            ImageChoiceCard(label = "Female", selected = false, onClick = {}, multi = false, modifier = Modifier.fillMaxWidth())
            ImageChoiceCard(label = "Prefer not to say", selected = true, onClick = {}, multi = false, modifier = Modifier.fillMaxWidth())
        }
    }
}

@ComponentPreview
@Composable
private fun StageSliderPreview() {
    AwakenTheme(reduceMotion = true) {
        Row(Modifier.padding(Spacing.gutter).height(dimensionsPreviewHeight)) {
            StageSlider(
                stage = 3, onStage = {}, stages = 5, imageFor = { R.drawable.onb_shape_3_n },
                description = "Body shape", stageText = "Stage 3 of 5", modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val dimensionsPreviewHeight = 360.dp
