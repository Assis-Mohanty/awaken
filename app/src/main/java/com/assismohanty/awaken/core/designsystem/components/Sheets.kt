package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * Bottom sheet (DESIGN_SYSTEM §3): the card colour, 28dp top corners over a scrim, a short handle, a 19sp title and 20dp
 * side padding. Opens fully expanded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AwakenBottomSheet(onDismiss: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = AwakenTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceElevated,
        scrimColor = colors.scrim,
        shape = AwakenTheme.shapes.sheet,
        dragHandle = { SheetHandle() },
    ) {
        SheetContent(title, content)
    }
}

private const val HANDLE_ALPHA = 0.6f

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .padding(vertical = Spacing.sm)
            .size(width = Spacing.xl + Spacing.xxs, height = Spacing.xxs)
            .clip(AwakenTheme.shapes.pill)
            .background(AwakenTheme.colors.textSecondary.copy(alpha = HANDLE_ALPHA)),
    )
}

@Composable
private fun SheetContent(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        title?.let { Text(it, style = AwakenTheme.type.section, color = AwakenTheme.colors.textPrimary, modifier = Modifier.semantics { heading() }) }
        content()
    }
}

/** A label / value row for sheets and detail cards ("Sets · 12"), value in tabular figures. */
@Composable
fun ValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AwakenTheme.type.body, color = AwakenTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = AwakenTheme.type.numeric, color = AwakenTheme.colors.textPrimary)
    }
}

@ComponentPreview
@Composable
private fun SheetContentPreview() {
    AwakenTheme {
        Column(modifier = Modifier.background(AwakenTheme.colors.surfaceElevated)) {
            SheetHandle()
            SheetContent("Chest") {
                ValueRow("Sets", "12")
                ValueRow("Volume", "4,320 kg")
            }
        }
    }
}
