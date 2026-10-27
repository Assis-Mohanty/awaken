package com.assismohanty.awaken.feature.settings

import android.content.res.AssetManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenBottomSheet
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Open-source licences (DESIGN_SYSTEM §4.7): every notice in `assets/licenses`, shown as plain text in a sheet. */
@Composable
fun LicencesSheet(onDismiss: () -> Unit) {
    val assets = LocalContext.current.assets
    val notices by produceState(initialValue = emptyList<Pair<String, String>>(), assets) {
        value = withContext(Dispatchers.IO) { readNotices(assets) }
    }
    AwakenBottomSheet(onDismiss = onDismiss, title = lexString(R.string.settings_licences)) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            notices.forEach { (name, text) ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(name, style = AwakenTheme.type.windowTitle, color = AwakenTheme.colors.textPrimary)
                    Text(text, style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
                }
            }
        }
    }
}

private fun readNotices(assets: AssetManager): List<Pair<String, String>> =
    assets.list(LICENCE_DIR).orEmpty().sorted().map { file ->
        val title = file.substringBeforeLast('.').replace('_', ' ')
        title to assets.open("$LICENCE_DIR/$file").bufferedReader().use { it.readText().trim() }
    }

private const val LICENCE_DIR = "licenses"
