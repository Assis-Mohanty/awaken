package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * The switch (design/components.md): a 52x32 pill. Off is an outlined track with a small dot; on is an ink track with a
 * round thumb carrying a check, so the state never relies on colour. It only shows state: the row that holds it is the
 * toggle target and carries the semantics.
 */
@Composable
fun AwakenSwitch(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = AwakenTheme.colors
    val motion = AwakenTheme.motion
    val shape = AwakenTheme.shapes.pill
    val thumb by animateDpAsState(if (checked) THUMB_ON else THUMB_OFF, tween(motion.duration(motion.quickMs), easing = motion.standardEasing), label = "switchThumb")
    val x by animateDpAsState(if (checked) THUMB_X_ON else THUMB_X_OFF, tween(motion.duration(motion.quickMs), easing = motion.standardEasing), label = "switchX")
    Box(
        modifier = modifier
            .size(width = TRACK_WIDTH, height = TRACK_HEIGHT)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(if (checked) colors.accent else colors.surfaceOverlay)
            .then(if (checked) Modifier else Modifier.border(OUTLINE, colors.textSecondary, shape)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = x)
                .size(thumb)
                .clip(shape)
                .background(if (checked) colors.onAccent else colors.textSecondary),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(AwakenGlyphs.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(CHECK))
        }
    }
}

private val TRACK_WIDTH = 52.dp
private val TRACK_HEIGHT = 32.dp
private val THUMB_ON = 24.dp
private val THUMB_OFF = 16.dp
private val THUMB_X_ON = 24.dp
private val THUMB_X_OFF = 8.dp
private val OUTLINE = 2.dp
private val CHECK = 16.dp
private const val DISABLED_ALPHA = 0.4f

@ComponentPreview
@Composable
private fun AwakenSwitchPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AwakenSwitch(checked = true)
            AwakenSwitch(checked = false)
            AwakenSwitch(checked = true, enabled = false)
            AwakenSwitch(checked = false, enabled = false)
        }
    }
}
