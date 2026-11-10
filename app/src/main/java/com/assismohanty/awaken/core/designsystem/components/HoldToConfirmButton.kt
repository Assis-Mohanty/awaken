package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * A button that does something only while it is held: a press reports `true`, letting go (or the finger leaving the
 * button) reports `false`, and the caller owns the clock. [progress] (0..1) fills the button from the left so the hold
 * can be seen; it is drawn, never animated here. One TalkBack node described by [description].
 */
@Composable
fun HoldToConfirmButton(
    text: String,
    progress: Float,
    description: String,
    onPressedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AwakenTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AwakenTheme.dimens.touchTarget)
            .clip(AwakenTheme.shapes.pill)
            .background(colors.surfaceOverlay)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            onPressedChange(true)
                            try {
                                tryAwaitRelease()
                            } finally {
                                onPressedChange(false)
                            }
                        },
                    )
                }
            }
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(colors.critical.copy(alpha = FILL_ALPHA)),
        )
        Text(
            text = text,
            style = AwakenTheme.type.label,
            color = if (enabled) colors.textPrimary else colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

private const val FILL_ALPHA = 0.45f

@ComponentPreview
@Composable
private fun HoldToConfirmButtonPreview() {
    AwakenTheme(reduceMotion = true) {
        androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm)) {
            HoldToConfirmButton("Hold to start", 0f, "Hold", {})
            HoldToConfirmButton("Keep holding", 0.6f, "Hold", {})
        }
    }
}
