package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/** Primary action: filled `system`, chamfered 8dp on one corner (DESIGN_SYSTEM §2.3). [color] = `ember` for destructive. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = AwakenTheme.colors.system,
) {
    val colors = AwakenTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = AwakenTheme.shapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = colors.void,
            disabledContainerColor = colors.edge,
            disabledContentColor = colors.fog,
        ),
        modifier = modifier.defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget),
    ) {
        Text(text = text, style = AwakenTheme.type.label)
    }
}

/** Secondary action: outlined in `edge`. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AwakenTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = AwakenTheme.shapes.button,
        border = BorderStroke(AwakenTheme.dimens.hairline, if (enabled) colors.edge else colors.edge.copy(alpha = DISABLED_ALPHA)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.bone, disabledContentColor = colors.fog),
        modifier = modifier.defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget),
    ) {
        Text(text = text, style = AwakenTheme.type.label)
    }
}

/** Low-emphasis action. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = AwakenTheme.colors.system, disabledContentColor = AwakenTheme.colors.fog),
        modifier = modifier.defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget),
    ) {
        Text(text = text, style = AwakenTheme.type.label)
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
            PrimaryButton("Abandon gate", onClick = {}, color = AwakenTheme.colors.ember)
            SecondaryButton("Reroll", onClick = {})
            SecondaryButton("Reroll", onClick = {}, enabled = false)
            QuietButton("Not now", onClick = {})
        }
    }
}
