package com.assismohanty.awaken.feature.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.GateBackground
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Rank

/**
 * The privacy policy (ARCH §3.4). Health Connect opens it from its permission screens through the
 * `ACTION_SHOW_PERMISSIONS_RATIONALE` filter (Android 13-) and the `VIEW_PERMISSION_USAGE` alias (Android 14+); Settings
 * opens it too. It must match the policy linked from the Play listing (PLAY_STORE_LAUNCH §7).
 */
class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AwakenTheme { PrivacyPolicyScreen(onClose = ::finish) }
        }
    }
}

@Composable
fun PrivacyPolicyScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val paragraphs = listOf(
        R.string.privacy_developer,
        R.string.privacy_storage,
        R.string.privacy_ai,
        R.string.privacy_reports,
        R.string.privacy_health,
        R.string.privacy_backups,
        R.string.privacy_children,
        R.string.privacy_changes,
    )
    GateBackground(rank = Rank.E, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = Spacing.contentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.privacy_title),
                        style = type.windowTitle,
                        color = colors.bone,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    QuietButton(text = stringResource(R.string.action_back), onClick = onClose)
                }
                Text(stringResource(R.string.privacy_effective), style = type.label, color = colors.mist)
                paragraphs.forEach { Text(stringResource(it), style = type.body, color = colors.bone) }
            }
        }
    }
}

@PhonePreviews
@Composable
private fun PrivacyPolicyPreview() {
    AwakenTheme { PrivacyPolicyScreen(onClose = {}) }
}
