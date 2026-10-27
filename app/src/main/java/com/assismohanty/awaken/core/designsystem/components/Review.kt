package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/** "2 / 5" above a review step (DESIGN_SYSTEM §8.1 ReviewFlow). */
@Composable
fun ReviewStepIndicator(step: Int, total: Int, modifier: Modifier = Modifier) {
    Text(
        text = lexString(R.string.review_step, step, total),
        style = AwakenTheme.type.label,
        color = AwakenTheme.colors.textSecondary,
        modifier = modifier,
    )
}

/**
 * An optional free-text answer with a live "120 / 280" counter (ReviewTextStep / ReviewIntentionStep). Input past
 * [maxChars] code points is cut here as the hunter types; the engine cleans it again on save.
 */
@Composable
fun ReviewTextField(
    prompt: String,
    value: String,
    onValueChange: (String) -> Unit,
    maxChars: Int,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
) {
    val count = value.codePointCount(0, value.length)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(prompt, style = AwakenTheme.type.section, color = AwakenTheme.colors.textPrimary)
        OutlinedTextField(
            value = value,
            onValueChange = { next ->
                val points = next.codePointCount(0, next.length)
                onValueChange(if (points <= maxChars) next else next.substring(0, next.offsetByCodePoints(0, maxChars)))
            },
            label = { Text(lexString(R.string.review_text_label)) },
            supportingText = {
                Text(lexString(R.string.review_counter, count, maxChars))
            },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else MULTI_LINES,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = awakenTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** [Skip] and [Next] with the same weight (DESIGN_SYSTEM §8.1): skipping is never the lesser choice. */
@Composable
fun ReviewButtons(
    nextLabel: String,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onSkip: (() -> Unit)? = null,
    nextEnabled: Boolean = true,
) {
    Row(modifier = modifier.fillMaxWidth().padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (onSkip != null) {
            SecondaryButton(text = lexString(R.string.action_skip), onClick = onSkip, modifier = Modifier.weight(1f))
        }
        PrimaryButton(text = nextLabel, onClick = onNext, enabled = nextEnabled, modifier = Modifier.weight(1f))
    }
}

private const val MULTI_LINES = 3

@ComponentPreview
@Composable
private fun ReviewComponentsPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ReviewStepIndicator(step = 2, total = 5)
            ReviewTextField(prompt = "What went well?", value = "Three runs, all before work.", onValueChange = {}, maxChars = 280)
            ReviewButtons(nextLabel = "Next", onNext = {}, onSkip = {})
        }
    }
}
