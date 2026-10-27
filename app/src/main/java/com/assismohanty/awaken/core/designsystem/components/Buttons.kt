package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/** Primary action (DESIGN_SYSTEM §3): a filled pill in the accent, 52dp tall. [color] = `critical` for destructive. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = AwakenTheme.palette.system,
) {
    val palette = AwakenTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = AwakenTheme.shapes.button,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = palette.onAccent,
            disabledContainerColor = palette.panelRaised,
            disabledContentColor = palette.fog,
        ),
        elevation = null,
        modifier = modifier.pressScale(interaction).defaultMinSize(minHeight = AwakenTheme.dimens.buttonHeight),
    ) {
        Text(text = text, style = AwakenTheme.type.label.copy(fontWeight = FontWeight.SemiBold, fontSize = AwakenTheme.type.body.fontSize))
    }
}

/** Secondary action: a hairline pill. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = AwakenTheme.palette
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = AwakenTheme.shapes.button,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        border = BorderStroke(AwakenTheme.dimens.hairline, if (enabled) palette.edge else palette.edge.copy(alpha = DISABLED_ALPHA)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = palette.panel,
            contentColor = palette.bone,
            disabledContentColor = palette.fog,
        ),
        modifier = modifier.pressScale(interaction).defaultMinSize(minHeight = AwakenTheme.dimens.buttonHeight),
    ) {
        Text(text = text, style = AwakenTheme.type.label.copy(fontWeight = FontWeight.SemiBold, fontSize = AwakenTheme.type.body.fontSize))
    }
}

/** Low-emphasis action: accent text, no container. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = AwakenTheme.shapes.button,
        colors = ButtonDefaults.textButtonColors(contentColor = AwakenTheme.palette.system, disabledContentColor = AwakenTheme.palette.fog),
        modifier = modifier.pressScale(interaction).defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget),
    ) {
        Text(text = text, style = AwakenTheme.type.label.copy(fontWeight = FontWeight.SemiBold))
    }
}

private const val DISABLED_ALPHA = 0.5f

@ComponentPreview
@Composable
private fun ButtonsPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            PrimaryButton("Complete quest", onClick = {})
            PrimaryButton("Complete quest", onClick = {}, enabled = false)
            PrimaryButton("End session", onClick = {}, color = AwakenTheme.palette.ember)
            SecondaryButton("Reroll", onClick = {})
            SecondaryButton("Reroll", onClick = {}, enabled = false)
            QuietButton("Not now", onClick = {})
        }
    }
}
