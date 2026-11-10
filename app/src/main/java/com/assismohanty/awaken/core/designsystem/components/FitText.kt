package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme

/**
 * One line of text in the app's own type, as big as the space allows: the largest size at which the line fits both the
 * width and the height it is given. The size is worked out from [text] with every digit read as 0 (the widest), so a
 * clock does not change size as its digits change. One TalkBack node that reads [text].
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = AwakenTheme.colors.textPrimary,
    maxSize: TextUnit = MAX_SIZE.sp,
) {
    val measurer = rememberTextMeasurer()
    val lineRatio = if (style.lineHeight.isSpecified && style.fontSize.isSpecified) style.lineHeight.value / style.fontSize.value else DEFAULT_LINE_RATIO
    fun sized(size: Float): TextStyle = style.copy(fontSize = size.sp, lineHeight = (size * lineRatio).sp)
    val template = text.map { if (it.isDigit()) '0' else it }.joinToString("")
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val size = remember(template, widthPx, heightPx, style, maxSize) {
            var low = MIN_SIZE
            var high = maxSize.value
            repeat(STEPS) {
                val mid = (low + high) / 2f
                val laid = measurer.measure(template, sized(mid), maxLines = 1, softWrap = false)
                if (laid.size.width <= widthPx && laid.size.height <= heightPx) low = mid else high = mid
            }
            low.sp
        }
        Text(
            text = text,
            style = sized(size.value),
            color = color,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { contentDescription = text },
        )
    }
}

private const val MIN_SIZE = 12f
private const val DEFAULT_LINE_RATIO = 1.1f
private const val MAX_SIZE = 400f
private const val STEPS = 14

@ComponentPreview
@Composable
private fun FitTextPreview() {
    AwakenTheme(reduceMotion = true) {
        Box(Modifier.size(width = 320.dp, height = 140.dp)) { FitText("18:42", AwakenTheme.type.metricXL) }
    }
}
