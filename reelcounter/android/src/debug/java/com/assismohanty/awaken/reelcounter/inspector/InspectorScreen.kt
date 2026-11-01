package com.assismohanty.awaken.reelcounter.inspector

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.reelcounter.R

/** Debug only. Plain Material 3 on purpose: it is not part of the product design. */
@Composable
fun InspectorScreen(onBack: () -> Unit, viewModel: InspectorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InspectorContent(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

@Composable
internal fun InspectorContent(state: InspectorUiState, onEvent: (InspectorEvent) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.rc_inspector_back)) }
        Text(stringResource(R.string.rc_inspector_title), fontSize = 22.sp)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = state.enabled, onCheckedChange = { onEvent(InspectorEvent.Toggle(it)) })
            Text(stringResource(R.string.rc_inspector_toggle))
        }

        val openName = state.openName
        if (openName != null) {
            Text(openName, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { copy(context, state.openText) }) { Text(stringResource(R.string.rc_inspector_copy)) }
                TextButton(onClick = { onEvent(InspectorEvent.Delete(openName)) }) {
                    Text(stringResource(R.string.rc_inspector_delete))
                }
                TextButton(onClick = { onEvent(InspectorEvent.Close) }) { Text(stringResource(R.string.rc_inspector_close)) }
            }
            Text(state.openText, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 12.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rc_inspector_files), modifier = Modifier.weight(1f))
                TextButton(onClick = { onEvent(InspectorEvent.Refresh) }) {
                    Text(stringResource(R.string.rc_inspector_refresh))
                }
            }
            if (state.files.isEmpty()) Text(stringResource(R.string.rc_inspector_no_files))
            state.files.forEach { name ->
                TextButton(onClick = { onEvent(InspectorEvent.Open(name)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(name, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Text(stringResource(R.string.rc_inspector_rules_title), fontSize = 18.sp)
        Text(stringResource(R.string.rc_inspector_rules_source, state.rulesSource.name))
        OutlinedTextField(
            value = state.rulesText,
            onValueChange = { onEvent(InspectorEvent.RulesChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            minLines = 8,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onEvent(InspectorEvent.SaveRules) }) { Text(stringResource(R.string.rc_inspector_save_rules)) }
            TextButton(onClick = { onEvent(InspectorEvent.ResetRules) }) {
                Text(stringResource(R.string.rc_inspector_reset_rules))
            }
        }
        state.rulesMessage?.let { Text(it) }
    }
}

private fun copy(context: Context, value: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Reel Counter dump", value))
}
