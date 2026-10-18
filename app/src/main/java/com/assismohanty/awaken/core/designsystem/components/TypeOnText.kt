package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import kotlinx.coroutines.delay

/**
 * System text type-on (DESIGN_SYSTEM §2.5) — only for window titles of important events. Reduce-motion or
 * [animate] = false shows the full text at once. TalkBack always reads the full text, never the partial one.
 */
@Composable
fun TypeOnText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    onFinished: () -> Unit = {},
) {
    val motion = AwakenTheme.motion
    val typeOn = animate && !motion.reduceMotion
    var shown by remember(text) { mutableIntStateOf(if (typeOn) 0 else text.length) }
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(text, typeOn) {
        if (typeOn) {
            while (shown < text.length) {
                delay(motion.typeOnCharMs)
                shown++
            }
        }
        finished()
    }
    Text(
        text = text.take(shown),
        style = style,
        color = color,
        modifier = modifier.semantics { contentDescription = text },
    )
}
