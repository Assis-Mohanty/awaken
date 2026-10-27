package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * Primary action (DESIGN_SYSTEM §3): a solid pill in the primary ink with page-coloured text, 52dp tall. Actions read by
 * weight, not hue, so [color] only matters for a destructive action: pass `critical`. Any other colour is ignored.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = AwakenTheme.colors.accent,
) {
    val colors = AwakenTheme.colors
    val fill = if (color == colors.critical) colors.critical else colors.accent
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = AwakenTheme.shapes.button,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        colors = ButtonDefaults.buttonColors(
            containerColor = fill,
            contentColor = colors.onAccent,
            disabledContainerColor = colors.surfaceOverlay,
            disabledContentColor = colors.textMuted,
        ),
        elevation = null,
        modifier = modifier.pressScale(interaction).defaultMinSize(minHeight = AwakenTheme.dimens.buttonHeight),
    ) {
        Text(text = text, style = AwakenTheme.type.title, textAlign = TextAlign.Center)
    }
}

/** Secondary action: the same pill in the pressed-card colour, so it reads on the page and inside a card alike. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AwakenTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = AwakenTheme.shapes.button,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.surfaceOverlay,
            contentColor = colors.textPrimary,
            disabledContainerColor = colors.surfaceOverlay,
            disabledContentColor = colors.textMuted,
        ),
        elevation = null,
        modifier = modifier.pressScale(interaction).defaultMinSize(minHeight = AwakenTheme.dimens.buttonHeight),
    ) {
        Text(text = text, style = AwakenTheme.type.title, textAlign = TextAlign.Center)
    }
}

/** Low-emphasis action: ink text, no container. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = AwakenTheme.shapes.button,
        colors = ButtonDefaults.textButtonColors(contentColor = AwakenTheme.colors.textPrimary, disabledContentColor = AwakenTheme.colors.textMuted),
        modifier = modifier.pressScale(interaction).defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget),
    ) {
        Text(text = text, style = AwakenTheme.type.title)
    }
}

@ComponentPreview
@Composable
private fun ButtonsPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            PrimaryButton("Complete quest", onClick = {})
            PrimaryButton("Complete quest", onClick = {}, enabled = false)
            PrimaryButton("End session", onClick = {}, color = AwakenTheme.colors.critical)
            SecondaryButton("Reroll", onClick = {})
            SecondaryButton("Reroll", onClick = {}, enabled = false)
            QuietButton("Not now", onClick = {})
        }
    }
}
