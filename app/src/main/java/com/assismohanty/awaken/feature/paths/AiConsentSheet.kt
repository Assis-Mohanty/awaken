package com.assismohanty.awaken.feature.paths

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * The AI consent sheet (DESIGN_SYSTEM §4.8), shown once before the first request. Declining sends nothing and creates no
 * install ID; the app keeps working on templates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConsentSheet(onEnable: () -> Unit, onNotNow: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onNotNow,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AwakenTheme.colors.surfaceOverlay,
        shape = AwakenTheme.shapes.window,
    ) {
        AiConsentContent(onEnable, onNotNow, modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
fun AiConsentContent(onEnable: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(lexString(R.string.consent_title), style = type.windowTitle, color = colors.accent, modifier = Modifier.semantics { heading() })
        Text(lexString(R.string.consent_headline), style = type.title, color = colors.textPrimary)
        Text(lexString(R.string.consent_body), style = type.body, color = colors.textPrimary)
        Text(lexString(R.string.consent_rewards), style = type.body, color = colors.textSecondary)
        WhatIsSent()
        PrimaryButton(text = lexString(R.string.consent_enable), onClick = onEnable, modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs))
        SecondaryButton(text = lexString(R.string.consent_not_now), onClick = onNotNow, modifier = Modifier.fillMaxWidth())
    }
}

/** The expandable "What exactly is sent?" list, matching ARCHITECTURE §3.7 "Request content". */
@Composable
fun WhatIsSent(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        QuietButton(
            text = lexString(R.string.consent_what_is_sent),
            onClick = { open = !open },
        )
        if (open) {
            Text(lexString(R.string.consent_sent_title), style = AwakenTheme.type.label, color = AwakenTheme.colors.textPrimary)
            listOf(R.string.consent_sent_1, R.string.consent_sent_2, R.string.consent_sent_3, R.string.consent_sent_4).forEach { line ->
                Text("• " + lexString(line), style = AwakenTheme.type.body, color = AwakenTheme.colors.textPrimary)
            }
            Text(lexString(R.string.consent_not_sent), style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
        }
    }
}

@ComponentPreview
@Composable
private fun AiConsentPreview() {
    AwakenTheme { AiConsentContent(onEnable = {}, onNotNow = {}) }
}
