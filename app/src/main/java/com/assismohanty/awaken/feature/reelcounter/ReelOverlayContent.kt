package com.assismohanty.awaken.feature.reelcounter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.BigValue
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.ValueRow
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp

/** One line of the bubble's panel: an app, how many reels and how many minutes today. */
data class ReelPanelRow(val app: ShortVideoApp, val count: Int, val minutes: Long)

/** The card that opens when the bubble is tapped. Tapping it closes it. */
@Composable
fun ReelPanelContent(rows: List<ReelPanelRow>, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    AwakenCard(modifier = modifier, reveal = false, onClick = onClose) {
        Text(lexString(R.string.reel_panel_title), style = type.section, color = colors.textPrimary)
        if (rows.isEmpty()) {
            Text(lexString(R.string.reel_panel_empty), style = type.body, color = colors.textSecondary)
        } else {
            rows.forEach {
                ValueRow(label = lexString(it.app.labelRes()), value = lexString(R.string.reel_panel_row, it.count, it.minutes))
            }
        }
    }
}

/**
 * The full-screen reminder at a daily limit: the count, the limit, [Close app] and, while snoozes remain, "5 more".
 * [snoozeSize] is how many more reels one snooze gives.
 */
@Composable
fun ReelNudgeContent(
    app: ShortVideoApp,
    count: Int,
    limit: Int?,
    snoozesLeft: Int,
    snoozeSize: Int,
    onClose: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val full = Modifier.fillMaxWidth()
    ScreenBackground(modifier = modifier) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            BigValue(value = count.toString(), unit = null, large = true)
            Text(lexPlural(R.plurals.reel_nudge_count, count, count), style = type.headline, color = colors.textPrimary, textAlign = TextAlign.Center)
            if (limit != null) {
                Text(
                    lexString(R.string.reel_nudge_detail, lexString(app.labelRes()), limit),
                    style = type.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(Spacing.md))
            PrimaryButton(lexString(R.string.reel_nudge_close), onClose, full)
            if (snoozesLeft > 0) {
                SecondaryButton(lexString(R.string.reel_nudge_snooze, snoozeSize), onSnooze, full)
                Text(lexString(R.string.reel_nudge_snoozes_left, snoozesLeft), style = type.caption, color = colors.textMuted)
            } else {
                Text(lexString(R.string.reel_nudge_no_snoozes), style = type.caption, color = colors.textMuted)
            }
        }
    }
}

@PhonePreviews
@Composable
private fun ReelNudgePreview() {
    AwakenTheme(reduceMotion = true) {
        ReelNudgeContent(ShortVideoApp.INSTAGRAM, count = 50, limit = 50, snoozesLeft = 2, snoozeSize = 5, onClose = {}, onSnooze = {})
    }
}

@PhonePreviews
@Composable
private fun ReelNudgeNoSnoozesPreview() {
    AwakenTheme(reduceMotion = true) {
        ReelNudgeContent(ShortVideoApp.TIKTOK, count = 63, limit = 50, snoozesLeft = 0, snoozeSize = 5, onClose = {}, onSnooze = {})
    }
}

@PhonePreviews
@Composable
private fun ReelPanelPreview() {
    AwakenTheme(reduceMotion = true) {
        ReelPanelContent(
            rows = listOf(ReelPanelRow(ShortVideoApp.INSTAGRAM, 23, 41), ReelPanelRow(ShortVideoApp.YOUTUBE, 9, 12)),
            onClose = {},
        )
    }
}

@PhonePreviews
@Composable
private fun ReelPanelEmptyPreview() {
    AwakenTheme(reduceMotion = true) { ReelPanelContent(rows = emptyList(), onClose = {}) }
}
