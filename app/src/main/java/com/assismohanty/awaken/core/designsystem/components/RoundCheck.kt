package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * A round check, the Plates replacement for the square Material checkbox: an outlined ring that fills with the accent and a
 * tick when [checked]. With [onCheckedChange] set it is its own 48dp touch target; with null it only draws, for a row that
 * is itself toggleable.
 */
@Composable
fun RoundCheck(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val size = dimens.sigil - Spacing.xxs
    val control = if (onCheckedChange == null) {
        modifier
    } else {
        modifier
            .size(dimens.touchTarget)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
    }
    Box(modifier = control.alpha(if (enabled) 1f else DISABLED_ALPHA), contentAlignment = Alignment.Center) {
        if (checked) {
            Box(
                modifier = Modifier.size(size).clip(AwakenTheme.shapes.pill).background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AwakenGlyphs.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(dimens.iconSmall))
            }
        } else {
            Box(modifier = Modifier.size(size).border(dimens.accentWidth, colors.textMuted, AwakenTheme.shapes.pill))
        }
    }
}

private const val DISABLED_ALPHA = 0.4f

@ComponentPreview
@Composable
private fun RoundCheckPreview() {
    AwakenTheme(reduceMotion = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            RoundCheck(checked = false, onCheckedChange = {})
            RoundCheck(checked = true, onCheckedChange = {})
            RoundCheck(checked = false, onCheckedChange = {}, enabled = false)
            RoundCheck(checked = true, onCheckedChange = {}, enabled = false)
        }
    }
}
