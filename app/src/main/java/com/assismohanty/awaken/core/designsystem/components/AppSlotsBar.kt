package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/** One of the three app slots: empty (a dashed circle with a plus) or filled with an app's icon. */
data class AppSlot(val icon: ImageBitmap?, val filled: Boolean, val description: String)

/**
 * The bottom bar of a Lockdown: three slots for the apps that stay usable, in a pill, and a round call button beside it.
 * A slot is a button ([onSlot]); the call button opens the phone ([onCall], described by [callDescription]).
 */
@Composable
fun AppSlotsBar(
    slots: List<AppSlot>,
    callDescription: String,
    onSlot: (Int) -> Unit,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val slotSize = AwakenTheme.dimens.touchTarget + Spacing.xxs
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .clip(AwakenTheme.shapes.pill)
                .background(colors.surfaceOverlay)
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            slots.forEachIndexed { index, slot ->
                Box(
                    modifier = Modifier
                        .size(slotSize)
                        .clip(AwakenTheme.shapes.pill)
                        .clickable(role = Role.Button) { onSlot(index) }
                        .semantics { contentDescription = slot.description },
                    contentAlignment = Alignment.Center,
                ) {
                    if (slot.icon != null) {
                        Image(bitmap = slot.icon, contentDescription = null, modifier = Modifier.size(slotSize - Spacing.xs).clip(AwakenTheme.shapes.pill))
                    } else {
                        val ink = colors.textMuted
                        Canvas(modifier = Modifier.size(slotSize - Spacing.xs)) {
                            drawCircle(
                                ink,
                                style = Stroke(width = DASH_STROKE, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH))),
                                radius = size.minDimension / 2f - DASH_STROKE,
                            )
                        }
                        Icon(AwakenGlyphs.Plus, contentDescription = null, tint = ink, modifier = Modifier.size(AwakenTheme.dimens.icon))
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .size(slotSize + Spacing.xs * 2)
                .clip(AwakenTheme.shapes.pill)
                .background(colors.accentMuted)
                .clickable(role = Role.Button, onClick = onCall)
                .semantics { contentDescription = callDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(AwakenGlyphs.Call, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(AwakenTheme.dimens.icon))
        }
    }
}

private const val DASH = 8f
private const val DASH_STROKE = 3f

@ComponentPreview
@Composable
private fun AppSlotsBarPreview() {
    AwakenTheme(reduceMotion = true) {
        AppSlotsBar(
            slots = listOf(AppSlot(null, false, "Add an app"), AppSlot(null, false, "Add an app"), AppSlot(null, false, "Add an app")),
            callDescription = "Phone",
            onSlot = {},
            onCall = {},
        )
    }
}
