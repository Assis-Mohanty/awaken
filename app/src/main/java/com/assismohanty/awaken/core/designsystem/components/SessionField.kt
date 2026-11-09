package com.assismohanty.awaken.core.designsystem.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Stat

/**
 * A session takes over the screen in the deep version of its stat's colour (design/DESIGN.md): white text, a round dial,
 * the actions at the bottom. The status bar icons turn light while it shows, so they read on the colour in either mode.
 */
@Composable
fun SessionField(stat: Stat, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = AwakenTheme.colors
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (controller != null && wasLight != null) controller.isAppearanceLightStatusBars = wasLight
        }
    }
    Box(modifier = modifier.fillMaxSize().background(colors.field(stat))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The title and the quiet line under it, centred, in white. */
@Composable
fun SessionHeader(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = AwakenTheme.type.title, color = colors.onField, textAlign = TextAlign.Center)
        subtitle?.let { Text(it, style = AwakenTheme.type.caption, color = colors.onFieldMuted, textAlign = TextAlign.Center) }
    }
}

/**
 * The round plate dial: a white arc for what is left over a faint track, the number inside and a caption under it.
 * Its diameter is the smaller of 296dp and 35% of the screen height (design/components.md).
 */
@Composable
fun SessionDial(fraction: Float, numeral: String, caption: String, description: String, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val diameter = minOf(DIAL_MAX, screenHeight * DIAL_HEIGHT_SHARE)
    val ring = AwakenTheme.dimens.sessionDialRing
    val track = colors.onFieldMuted.copy(alpha = TRACK_ALPHA)
    Box(
        modifier = modifier.size(diameter).semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = ring.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, FULL, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            val sweep = FULL * fraction.coerceIn(0f, 1f)
            if (sweep > 0f) drawArc(colors.onField, START, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(numeral, style = AwakenTheme.type.metricXL, color = colors.onField)
            Text(caption, style = AwakenTheme.type.caption, color = colors.onFieldMuted)
        }
    }
}

/** The one action on a field: a chalk pill with dark text, whichever mode the app is in. */
@Composable
fun FieldPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = AwakenTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = AwakenTheme.shapes.button,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.buttonOnField,
            contentColor = colors.onButtonOnField,
            disabledContainerColor = colors.onFieldMuted.copy(alpha = TRACK_ALPHA),
            disabledContentColor = colors.onFieldMuted,
        ),
        elevation = null,
        modifier = modifier.pressScale(interaction).defaultMinSize(minHeight = AwakenTheme.dimens.buttonHeight),
    ) {
        Text(text = text, style = AwakenTheme.type.title, textAlign = TextAlign.Center)
    }
}

/** The quiet action under it ("End session"): white text, no container. */
@Composable
fun FieldQuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        interactionSource = interaction,
        shape = AwakenTheme.shapes.button,
        colors = ButtonDefaults.textButtonColors(contentColor = AwakenTheme.colors.onField),
        modifier = modifier.pressScale(interaction).defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget),
    ) {
        Text(text = text, style = AwakenTheme.type.title)
    }
}

private val DIAL_MAX: Dp = 296.dp
private const val DIAL_HEIGHT_SHARE = 0.35f
private const val TRACK_ALPHA = 0.28f
private const val FULL = 360f
private const val START = -90f

@PhonePreviews
@Composable
private fun SessionFieldPreview() {
    AwakenTheme(reduceMotion = true) {
        SessionField(Stat.INT) {
            SessionHeader("15-minute session", "DSA, graphs · Intelligence")
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                SessionDial(fraction = 0.79f, numeral = "11:48", caption = "remaining", description = "11:48 remaining")
            }
            FieldPrimaryButton("Pause (5:00)", onClick = {}, modifier = Modifier.fillMaxWidth())
            FieldQuietButton("End session", onClick = {})
        }
    }
}
