package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * Full-screen chapter reveal (GD §11: "each unlock = full-screen reveal"). The chapter number types on; the body is
 * shown whole so it reads at the user's pace. Reduce-motion skips the type-on (via [TypeOnText]).
 */
@Composable
fun ChapterReveal(number: Int, title: String, body: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    GateBackground(tint = colors.system, modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = Spacing.contentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                TypeOnText(
                    text = stringResource(R.string.status_chapter_number, number),
                    style = type.windowTitle,
                    color = colors.mist,
                )
                Text(title, style = type.display, color = colors.bone, modifier = Modifier.semantics { heading() })
                Text(body, style = type.body, color = colors.bone)
                PrimaryButton(
                    text = stringResource(R.string.action_continue),
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md),
                )
            }
        }
    }
}

@Composable
fun ChapterRevealDialog(number: Int, title: String, body: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        ChapterReveal(number = number, title = title, body = body, onDismiss = onDismiss)
    }
}

@PhonePreviews
@Composable
private fun ChapterRevealPreview() {
    AwakenTheme(reduceMotion = true) {
        ChapterReveal(
            number = 2,
            title = "The First Gate",
            body = "By the fifth level you see it: the Gates were always here. The unread book. The run you postponed.",
            onDismiss = {},
        )
    }
}
