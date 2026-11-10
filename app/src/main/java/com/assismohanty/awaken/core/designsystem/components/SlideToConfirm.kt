package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * A bar with a handle that has to be dragged all the way across to do something (start, or leave). Letting go before
 * [CONFIRM_FRACTION] of the way slides it back; past it the handle goes to the end and [onConfirmed] runs once, then the
 * handle returns. The label fades as the handle moves. Under reduce-motion the handle snaps instead of gliding.
 * One TalkBack node, with the action offered as a custom action so it can be done without dragging.
 */
@Composable
fun SlideToConfirm(
    text: String,
    handle: ImageVector,
    description: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    handleColor: Color = AwakenTheme.colors.accent,
) {
    val colors = AwakenTheme.colors
    val motion = AwakenTheme.motion
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var trackWidth by remember { mutableIntStateOf(0) }
    val trackHeight = AwakenTheme.dimens.touchTarget + Spacing.md
    val handleSize = trackHeight - Spacing.xxs * 2
    val density = LocalDensity.current
    val travel = with(density) { (trackWidth - handleSize.toPx() - Spacing.xxs.toPx() * 2).coerceAtLeast(1f) }
    val progress = (offset.value / travel).coerceIn(0f, 1f)

    suspend fun back() {
        if (motion.reduceMotion) offset.snapTo(0f) else offset.animateTo(0f, tween(motion.standardMs, easing = motion.standardEasing))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(AwakenTheme.shapes.pill)
            .background(colors.surfaceOverlay)
            .onSizeChanged { trackWidth = it.width }
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (enabled) customActions = listOf(CustomAccessibilityAction(text) { onConfirmed(); true })
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = AwakenTheme.type.label,
            color = if (enabled) colors.textSecondary else colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xxl).alpha(1f - progress),
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .padding(Spacing.xxs)
                .size(handleSize)
                .clip(AwakenTheme.shapes.pill)
                .background(if (enabled) handleColor else colors.surfaceSunken)
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    state = rememberDraggableState { delta -> scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, travel)) } },
                    onDragStopped = {
                        scope.launch {
                            if (offset.value >= travel * CONFIRM_FRACTION) {
                                offset.animateTo(travel, tween(if (motion.reduceMotion) 0 else motion.quickMs))
                                onConfirmed()
                            }
                            back()
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(handle, contentDescription = null, tint = if (enabled) colors.onAccent else colors.textMuted, modifier = Modifier.size(AwakenTheme.dimens.icon))
        }
    }
}

private const val CONFIRM_FRACTION = 0.9f

@ComponentPreview
@Composable
private fun SlideToConfirmPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SlideToConfirm("Slide to exit", AwakenGlyphs.Shield, "Slide to exit", {})
            SlideToConfirm("Slide to start", AwakenGlyphs.Lock, "Slide to start", {}, enabled = false)
        }
    }
}
