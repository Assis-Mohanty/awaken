package com.assismohanty.awaken.reelcounter.ui.today

import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.reelcounter.R
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp

@StringRes
internal fun ShortVideoApp.labelRes(): Int = when (this) {
    ShortVideoApp.INSTAGRAM -> R.string.rc_app_instagram
    ShortVideoApp.YOUTUBE -> R.string.rc_app_youtube
    ShortVideoApp.TIKTOK -> R.string.rc_app_tiktok
    ShortVideoApp.FACEBOOK -> R.string.rc_app_facebook
    ShortVideoApp.SNAPCHAT -> R.string.rc_app_snapchat
}

@Composable
fun TodayDebugRouteContent(onOpenInspector: (() -> Unit)?, viewModel: TodayDebugViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    TodayDebugScreen(
        state = state,
        onOpenAccessibility = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        onOpenInspector = onOpenInspector,
    )
}

/** Plain text on purpose: the real Stats screen replaces it in Phase 5. */
@Composable
fun TodayDebugScreen(
    state: TodayDebugUiState,
    onOpenAccessibility: () -> Unit,
    onOpenInspector: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.rc_today_title), fontSize = 28.sp)
        Text(
            stringResource(
                if (state.serviceConnected) R.string.rc_today_service_connected else R.string.rc_today_service_disconnected,
            ),
        )
        Text(stringResource(R.string.rc_today_total, state.total), fontSize = 20.sp)
        state.rows.forEach { row ->
            Text(stringResource(R.string.rc_today_row, stringResource(row.app.labelRes()), row.count, row.seconds))
        }
        Button(onClick = onOpenAccessibility) { Text(stringResource(R.string.rc_today_open_accessibility)) }
        if (onOpenInspector != null) {
            TextButton(onClick = onOpenInspector) { Text(stringResource(R.string.rc_today_open_inspector)) }
        }
    }
}
