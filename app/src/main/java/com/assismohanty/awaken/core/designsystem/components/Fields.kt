package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Outlined field colours for the System palette (works on both themes). */
@Composable
fun awakenTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AwakenTheme.colors.system,
    unfocusedBorderColor = AwakenTheme.colors.edge,
    focusedLabelColor = AwakenTheme.colors.system,
    cursorColor = AwakenTheme.colors.system,
    focusedTextColor = AwakenTheme.colors.bone,
    unfocusedTextColor = AwakenTheme.colors.bone,
)

/**
 * A compact outlined number field for set rows and nutrient forms. Keeps the hunter's own text while typing (so "62."
 * or an empty field don't snap back) and reports every value; [key] resets the text when the row it edits changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberField(
    value: Double,
    onValueChange: (Double) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    key: Any? = null,
    decimals: Boolean = true,
    suffix: String? = null,
) {
    var text by remember(key) { mutableStateOf(formatNumber(value)) }
    val interaction = remember { MutableInteractionSource() }
    val colors = awakenTextFieldColors()
    BasicTextField(
        value = text,
        onValueChange = { raw ->
            val cleaned = raw.replace(',', '.').filter { it.isDigit() || (decimals && it == '.') }.take(MAX_CHARS)
            text = cleaned
            onValueChange(cleaned.toDoubleOrNull() ?: 0.0)
        },
        singleLine = true,
        textStyle = AwakenTheme.type.label.copy(textAlign = TextAlign.End, color = AwakenTheme.colors.bone),
        cursorBrush = SolidColor(AwakenTheme.colors.system),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number, imeAction = ImeAction.Next),
        interactionSource = interaction,
        modifier = modifier
            .width(AwakenTheme.dimens.numberField)
            .defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget)
            .semantics { contentDescription = description },
        decorationBox = { inner ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = text,
                innerTextField = inner,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interaction,
                suffix = suffix?.let { { Text(it, style = AwakenTheme.type.caption, color = AwakenTheme.colors.mist) } },
                colors = colors,
                contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.xs),
                container = {
                    OutlinedTextFieldDefaults.Container(enabled = true, isError = false, interactionSource = interaction, colors = colors)
                },
            )
        },
    )
}

/** Up to two decimals, no trailing zeros, "." as separator (what NumberField accepts back); zero shows empty. */
fun formatNumber(value: Double): String =
    if (value == 0.0) "" else DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value)

private const val MAX_CHARS = 7
