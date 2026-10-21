package com.assismohanty.awaken.feature.paths

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.ReportReason

/** "Report this quest" (PRD F21, DESIGN_SYSTEM §4.8): pick why; the quest leaves today's Directive at once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(onReport: (ReportReason) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AwakenTheme.colors.panelRaised,
        shape = AwakenTheme.shapes.window,
    ) {
        ReportSheetContent(onReport, onDismiss, modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
fun ReportSheetContent(onReport: (ReportReason) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            stringResource(R.string.report_title),
            style = type.windowTitle,
            color = colors.ember,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.report_prompt), style = type.body, color = colors.bone)
        ReportReason.entries.forEach { reason ->
            SecondaryButton(text = stringResource(reason.labelRes()), onClick = { onReport(reason) }, modifier = Modifier.fillMaxWidth())
        }
        QuietButton(text = stringResource(R.string.action_cancel), onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}

private fun ReportReason.labelRes(): Int = when (this) {
    ReportReason.UNSAFE -> R.string.report_reason_unsafe
    ReportReason.NOT_RELEVANT -> R.string.report_reason_not_relevant
    ReportReason.IMPOSSIBLE -> R.string.report_reason_impossible
    ReportReason.OFFENSIVE -> R.string.report_reason_offensive
    ReportReason.OTHER -> R.string.report_reason_other
}

@ComponentPreview
@Composable
private fun ReportSheetPreview() {
    AwakenTheme { ReportSheetContent(onReport = {}, onCancel = {}) }
}
