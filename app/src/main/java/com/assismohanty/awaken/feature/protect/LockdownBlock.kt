package com.assismohanty.awaken.feature.protect

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
import com.assismohanty.awaken.core.designsystem.components.IconCircle
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.feature.quest.shortClock
import java.time.Duration

/**
 * What Lockdown shows over an app that is not allowed (GD §19.2): the time left, which app, and one way out of the cover,
 * back to the session. There is no end button here or anywhere else.
 */
@Composable
fun LockdownBlockContent(
    appLabel: String,
    appIcon: ImageBitmap?,
    remaining: Duration,
    onBackToSession: () -> Unit,
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
                text = lexString(R.string.lockdown_block_left, shortClock(remaining.toMillis())),
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
                text = lexString(R.string.lockdown_block_unavailable, appLabel),
                style = type.body,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            PrimaryButton(text = lexString(R.string.lockdown_block_back), onClick = onBackToSession, modifier = Modifier.fillMaxWidth())
        }
    }
}

@PhonePreviews
@Composable
private fun LockdownBlockPreview() {
    AwakenTheme(reduceMotion = true) {
        LockdownBlockContent("Instagram", null, Duration.ofMinutes(18).plusSeconds(42), {})
    }
}

@PhonePreviews
@Composable
private fun LockdownBlockLongNamePreview() {
    AwakenTheme(reduceMotion = true) {
        LockdownBlockContent("A very long app name that keeps going and going", null, Duration.ofHours(2).plusSeconds(5), {})
    }
}
