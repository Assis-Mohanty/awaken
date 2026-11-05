package com.assismohanty.awaken.feature.focuslock

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.components.IconCircle
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.feature.quest.shortClock
import java.time.Duration

/**
 * The cover Focus Lock puts over an app during a running session (GD 8.2): how long is left (or counted, for a
 * stopwatch), which app was opened, and two ways out. [time] is pushed by the service once a second.
 */
@Composable
fun FocusLockOverlayContent(
    appLabel: String,
    appIcon: ImageBitmap?,
    time: Duration,
    countsUp: Boolean,
    onBackToFocus: () -> Unit,
    onEndAndOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    ScreenBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.lg)
                .widthIn(max = Spacing.contentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = lexString(
                    if (countsUp) R.string.focus_lock_overlay_counted else R.string.focus_lock_overlay_left,
                    shortClock(time.toMillis()),
                ),
                style = type.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            if (appIcon != null) {
                Image(bitmap = appIcon, contentDescription = null, modifier = Modifier.size(AwakenTheme.dimens.crestLarge))
            } else {
                IconCircle(AwakenGlyphs.Lock, colors.accent, size = AwakenTheme.dimens.crestLarge)
            }
            Text(
                text = lexString(R.string.focus_lock_overlay_question, appLabel),
                style = type.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = lexString(R.string.focus_lock_overlay_note),
                style = type.caption,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            PrimaryButton(
                text = lexString(R.string.focus_lock_back_to_focus),
                onClick = onBackToFocus,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = lexString(R.string.focus_lock_end_and_open),
                onClick = onEndAndOpen,
                color = colors.critical,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PhonePreviews
@Composable
private fun FocusLockOverlayPreview() {
    AwakenTheme(reduceMotion = true) {
        FocusLockOverlayContent(
            appLabel = "Instagram",
            appIcon = null,
            time = Duration.ofMinutes(18).plusSeconds(42),
            countsUp = false,
            onBackToFocus = {},
            onEndAndOpen = {},
        )
    }
}

@PhonePreviews
@Composable
private fun FocusLockOverlayStopwatchPreview() {
    AwakenTheme(reduceMotion = true) {
        FocusLockOverlayContent(
            appLabel = "YouTube",
            appIcon = null,
            time = Duration.ofMinutes(7).plusSeconds(5),
            countsUp = true,
            onBackToFocus = {},
            onEndAndOpen = {},
        )
    }
}
