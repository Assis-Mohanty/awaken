package com.assismohanty.awaken.feature.focus

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.feature.screentime.openUsageAccessSettings
import com.assismohanty.awaken.feature.settings.BedtimeSettingsRoute

/** Phone-down nights as its own screen, one tap from the Focus tab. The window is the same one Settings shows. */
@Composable
fun NightsEntry(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LargeTitleScaffold(title = lexString(R.string.bedtime_settings_title), onBack = onBack, modifier = modifier) {
        BedtimeSettingsRoute(onGrantAccess = { openUsageAccessSettings(context) })
    }
}
