package com.assismohanty.awaken.core.designsystem.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * Section building blocks that used to be drawn inside feature screens (DESIGN_SYSTEM §8.4: features do layout only).
 * Each reproduces its old look exactly; the redesign restyles them here.
 */

/**
 * A ring the user holds for [holdMillis] to confirm (the campaign oath). Releasing early drains it. TalkBack's tap
 * action confirms at once. Shows [idleText] in the middle, [doneText] once full.
 */
@Composable
fun HoldToConfirmRing(
    label: String,
    idleText: String,
    doneText: String,
    enabled: Boolean,
    onConfirmed: () -> Unit,
    holdMillis: Int = HOLD_MS,
) {
    val palette = AwakenTheme.palette
    val haptics = AwakenTheme.haptics
    val motion = AwakenTheme.motion
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val confirmed by rememberUpdatedState(onConfirmed)
    val size = AwakenTheme.dimens.gateDialSize / 2
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .semantics {
                    contentDescription = label
                    onClick(label) { if (enabled) confirmed(); enabled }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            var job: Job? = null
                            job = scope.launch {
                                progress.animateTo(1f, tween(if (motion.reduceMotion) holdMillis / 3 else holdMillis, easing = LinearEasing))
                                haptics.confirm()
                                confirmed()
                            }
                            val released = tryAwaitRelease()
                            if (released && progress.value < 1f) {
                                job.cancel()
                                scope.launch { progress.animateTo(0f, tween(motion.duration(motion.quickMs))) }
                            }
                        },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = this.size.minDimension * RING_FRACTION
                val inset = stroke / 2
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(palette.edge, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(palette.crit, -90f, 360f * progress.value, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            }
            Text(if (progress.value >= 1f) doneText else idleText, style = AwakenTheme.type.windowTitle, color = palette.bone)
        }
    }
}

/**
 * A selectable tile (gate picker): tinted fill and accent border when [selected], hairline otherwise. [accent] is the
 * tile's meaning colour; [description] is its single TalkBack label.
 */
@Composable
fun SelectableTile(
    accent: Color,
    selected: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = AwakenTheme.palette
    val dimens = AwakenTheme.dimens
    val shape = AwakenTheme.shapes.window
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = dimens.gateTileHeight)
            .background(if (selected) accent.copy(alpha = AwakenAlpha.TINT) else palette.panel, shape)
            .border(if (selected) dimens.accentWidth else dimens.hairline, if (selected) accent else palette.edge, shape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        content = content,
    )
}

/** A small label/value block on a sunken well (Health metrics). */
@Composable
fun MetricTile(label: String, value: String, modifier: Modifier = Modifier, dim: Boolean = false) {
    val palette = AwakenTheme.palette
    Column(
        modifier = modifier
            .background(palette.panelRaised)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(label, style = AwakenTheme.type.caption, color = palette.mist)
        Text(value, style = AwakenTheme.type.numericSmall, color = if (dim) palette.fog else palette.bone)
    }
}

/** A square colour key for a chart legend. Decorative. */
@Composable
fun LegendSwatch(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Spacing.xs)
            .background(color),
    )
}

/** Stand-in while an app icon loads (or when it can't). Decorative. */
@Composable
fun IconPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(AwakenTheme.dimens.sigil)
            .clip(AwakenTheme.shapes.button)
            .background(AwakenTheme.palette.panelRaised),
    )
}

/** A cosmetic's picture: a frame outline or a filled theme chip. Decorative; the name is read. */
@Composable
fun CosmeticSwatch(color: Color, outlined: Boolean, modifier: Modifier = Modifier) {
    val dimens = AwakenTheme.dimens
    val base = modifier.size(dimens.sigil)
    Box(
        if (outlined) {
            base.border(dimens.hairline, color, AwakenTheme.shapes.button)
        } else {
            base.background(color, AwakenTheme.shapes.button)
        },
    )
}

/** The faint ember wash over a screen while a penalty quest is open (GD §7.3: visual pressure only). */
@Composable
fun Modifier.penaltyTint(active: Boolean): Modifier =
    then(if (active) Modifier.background(AwakenTheme.palette.ember.copy(alpha = AwakenAlpha.HAZE)) else Modifier)

/** Alpha for a heat band 0..4 (body map, heatmap), from the shared steps (DESIGN_SYSTEM §3). */
fun heatAlpha(band: Int): Float = AwakenAlpha.HEATMAP_STEPS[band.coerceIn(0, AwakenAlpha.HEATMAP_STEPS.lastIndex)]

/**
 * Lays the share card out at 360×450dp with density 3 (exactly 1080×1350px, DESIGN_SYSTEM §3) and records it into a
 * graphics layer without drawing it on screen, then hands back the bitmap.
 */
@Composable
fun ShareCapture(model: ShareCardModel, onCaptured: (Bitmap) -> Unit, onFailed: () -> Unit) {
    val layer = rememberGraphicsLayer()
    val captured by rememberUpdatedState(onCaptured)
    val failed by rememberUpdatedState(onFailed)
    Box(modifier = Modifier.size(0.dp).clearAndSetSemantics { }) {
        CompositionLocalProvider(LocalDensity provides Density(SHARE_DENSITY, fontScale = 1f)) {
            ShareCard(
                model = model,
                modifier = Modifier
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .drawWithContent { layer.record { this@drawWithContent.drawContent() } },
            )
        }
    }
    LaunchedEffect(Unit) {
        try {
            // Two frames: one to lay out and record, one so the recording is complete.
            withFrameNanos { }
            withFrameNanos { }
            captured(layer.toImageBitmap().asAndroidBitmap())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed()
        }
    }
}

private const val HOLD_MS = 1_500
private const val RING_FRACTION = 0.06f
private const val SHARE_DENSITY = 3f
