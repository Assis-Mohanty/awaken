package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme

/**
 * A yes/no question before something the hunter may regret (DESIGN_SYSTEM §3): title, one plain sentence, the action in
 * [destructive] critical red or accent, and Cancel. Undo still follows where the action supports it.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val colors = AwakenTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceElevated,
        shape = AwakenTheme.shapes.window,
        title = { Text(title, style = AwakenTheme.type.title, color = colors.textPrimary) },
        text = { Text(body, style = AwakenTheme.type.body, color = colors.textSecondary) },
        confirmButton = {
            PrimaryButton(text = confirm, onClick = onConfirm, color = if (destructive) colors.critical else colors.accent)
        },
        dismissButton = { QuietButton(text = dismiss, onClick = onDismiss) },
    )
}

@ComponentPreview
@Composable
private fun ConfirmDialogPreview() {
    AwakenTheme(reduceMotion = true) {
        ConfirmDialog(
            title = "Delete \"Read 10 pages\"?",
            body = "It leaves Today and won't come back. Your XP, stats and history stay.",
            confirm = "Delete",
            dismiss = "Cancel",
            onConfirm = {},
            onDismiss = {},
            destructive = true,
        )
    }
}
