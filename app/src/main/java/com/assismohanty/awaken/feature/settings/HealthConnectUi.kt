package com.assismohanty.awaken.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.health.connect.client.PermissionController
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.health.HealthGateway
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme

/** What the screen must do next to connect Health Connect (ARCH §3.4). */
enum class HealthAction {
    /** Health Connect isn't installed or needs an update: open its Play Store page. */
    OPEN_STORE,

    /** Show the System's rationale before the permission sheet (Settings; onboarding step 5 is itself the rationale). */
    SHOW_RATIONALE,

    /** Launch Health Connect's own permission sheet for the three read permissions. */
    REQUEST_PERMISSIONS,
}

/**
 * Performs a pending [HealthAction] that needs the Activity: the Play Store link or the permission sheet. The caller
 * clears the action in [onHandled]; [SHOW_RATIONALE] is drawn by the screen, so it is ignored here.
 */
@Composable
fun HealthActionEffect(
    action: HealthAction?,
    permissions: Set<String>,
    onPermissionsResult: (Set<String>) -> Unit,
    onHandled: () -> Unit,
) {
    val context = LocalContext.current
    val result by rememberUpdatedState(onPermissionsResult)
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        result(granted)
    }
    LaunchedEffect(action) {
        when (action) {
            null, HealthAction.SHOW_RATIONALE -> return@LaunchedEffect
            HealthAction.OPEN_STORE -> openHealthConnectListing(context)
            HealthAction.REQUEST_PERMISSIONS -> launcher.launch(permissions)
        }
        onHandled()
    }
}

/** The System's rationale, shown before Health Connect's own sheet (ARCH §3.4: rationale first). */
@Composable
fun HealthRationaleDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    val colors = AwakenTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        SystemWindow(title = stringResource(R.string.onboarding_health_title), accent = colors.verified) {
            Text(stringResource(R.string.onboarding_health_body), style = AwakenTheme.type.body, color = colors.bone)
            Text(stringResource(R.string.health_rationale_scope), style = AwakenTheme.type.caption, color = colors.mist)
            PrimaryButton(text = stringResource(R.string.action_connect), onClick = onContinue, modifier = Modifier.fillMaxWidth())
            QuietButton(text = stringResource(R.string.action_not_now), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun openHealthConnectListing(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, HealthGateway.PLAY_STORE_URI.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(market)
    } catch (e: ActivityNotFoundException) {
        // No Play Store app (emulators, some devices): fall back to the web listing.
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, HealthGateway.PLAY_STORE_WEB_URI.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
