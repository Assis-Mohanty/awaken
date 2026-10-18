package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * Top-anchored mini window for achievements ("TITLE UNLOCKED · Unbroken") — DESIGN_SYSTEM §3. The host decides when it
 * shows and hides (Phase 6); this component only animates in/out and announces itself to TalkBack.
 */
@Composable
fun SystemToast(
    visible: Boolean,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    accent: Color = AwakenTheme.colors.crit,
    icon: ImageVector = AwakenGlyphs.VerifiedSeal,
) {
    val motion = AwakenTheme.motion
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (motion.reduceMotion) fadeIn(tween(motion.quickMs)) else slideInVertically(tween(motion.standardMs)) { -it } + fadeIn(),
        exit = if (motion.reduceMotion) fadeOut(tween(motion.quickMs)) else slideOutVertically(tween(motion.standardMs)) { -it } + fadeOut(),
    ) {
        SystemWindow(
            title = null,
            accent = accent,
            reveal = false,
            modifier = Modifier
                .widthIn(max = AwakenTheme.dimens.toastMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(AwakenTheme.dimens.icon))
                Column {
                    Text(title, style = AwakenTheme.type.windowTitle, color = accent)
                    Text(message, style = AwakenTheme.type.body, color = AwakenTheme.colors.bone)
                }
            }
        }
    }
}

@ComponentPreview
@Composable
private fun SystemToastPreview() {
    AwakenTheme {
        SystemToast(visible = true, title = "TITLE UNLOCKED", message = "Unbroken")
    }
}
