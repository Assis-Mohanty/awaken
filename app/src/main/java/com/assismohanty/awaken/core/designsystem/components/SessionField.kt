package com.assismohanty.awaken.core.designsystem.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.LandscapePreviews
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Stat

/**
 * A session takes over the screen in the deep version of its stat's colour (design/DESIGN.md): white text, a round dial,
 * the actions at the bottom. The status bar icons turn light while it shows, so they read on the colour in either mode.
 * Lay a session out with [SessionScreen]; this is only the colour, the insets and the status bar.
 */
@Composable
fun SessionField(stat: Stat, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * A session screen that fits its space. Upright: the title, the dial with its status under it in the middle, the actions
 * at the bottom. On its side: the dial on the left at the full height, the title and the actions in a column on the right,
 * scrolling if a phone is too short. [body] draws the dial at the [Dp] diameter it is given and whatever sits under it.
 */
@Composable
fun SessionScreen(
    stat: Stat,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    track: (@Composable () -> Unit)? = null,
    /** Share of the height the dial may take upright; screens with more under the dial pass less. */
    dialShare: Float = PORTRAIT_HEIGHT_SHARE,
    body: @Composable ColumnScope.(diameter: Dp) -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    SessionField(stat, modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                // The dial takes the height, less a line for the status under it.
                val diameter = (maxHeight - LANDSCAPE_STATUS_ROOM).coerceIn(DIAL_MIN, DIAL_MAX)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { body(diameter) }
                    Column(
                        modifier = Modifier.weight(1f).widthIn(max = SIDE_COLUMN_MAX).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SessionHeader(title, subtitle)
                        track?.invoke()
                        actions()
                    }
                }
            } else {
                val diameter = minOf(DIAL_MAX, maxWidth, maxHeight * dialShare)
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    SessionHeader(title, subtitle)
                    track?.invoke()
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { body(diameter) }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        content = actions,
                    )
                }
            }
        }
    }
}

/**
 * The way to stop a session, kept out of the way: a round lock button that starts locked. Tapping it pops a panel over the
 * dial with the actions in [content] (Pause, End session); tapping it again locks it. The panel floats, so opening it never
 * moves or shrinks the dial. [unlockLabel] says what unlocking gives you.
 */
@Composable
fun SessionLock(
    stat: Stat,
    locked: Boolean,
    onLockedChange: (Boolean) -> Unit,
    unlockLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AwakenTheme.colors
    val motion = AwakenTheme.motion
    val lockLabel = lexString(R.string.session_lock)
    Box(modifier = modifier.fillMaxWidth().height(LOCK_SIZE), contentAlignment = Alignment.BottomCenter) {
        // The panel sits above the button and overflows upward over the dial instead of pushing it.
        AnimatedVisibility(
            visible = !locked,
            enter = fadeIn(tween(motion.duration(motion.quickMs))) +
                scaleIn(tween(motion.duration(motion.standardMs)), initialScale = POP_SCALE, transformOrigin = TransformOrigin(0.5f, 1f)),
            exit = fadeOut(tween(motion.duration(motion.quickMs))) +
                scaleOut(tween(motion.duration(motion.quickMs)), targetScale = POP_SCALE, transformOrigin = TransformOrigin(0.5f, 1f)),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                .offset(y = -(LOCK_SIZE + Spacing.sm)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AwakenTheme.shapes.window)
                    .background(colors.field(stat))
                    .border(AwakenTheme.dimens.hairline, colors.onFieldMuted.copy(alpha = TRACK_ALPHA), AwakenTheme.shapes.window)
                    .padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
        Box(
            modifier = Modifier
                .size(LOCK_SIZE)
                .clip(CircleShape)
                .background(if (locked) colors.field(stat) else colors.buttonOnField)
                .border(AwakenTheme.dimens.accentWidth, if (locked) colors.onField else colors.buttonOnField, CircleShape)
                .clickable(onClickLabel = if (locked) unlockLabel else lockLabel) { onLockedChange(!locked) }
                .semantics { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                AwakenGlyphs.Lock,
                contentDescription = if (locked) unlockLabel else lockLabel,
                tint = if (locked) colors.onField else colors.onButtonOnField,
                modifier = Modifier.size(AwakenTheme.dimens.icon),
            )
        }
    }
}

private val LOCK_SIZE: Dp = 56.dp
private const val POP_SCALE = 0.9f

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
 * The round plate dial: a white arc for what is left over a faint track, the number inside and a caption under it. The
 * numeral scales with the diameter (design/components.md).
 */
@Composable
fun SessionDial(diameter: Dp, fraction: Float, numeral: String, caption: String, description: String, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val ring = AwakenTheme.dimens.sessionDialRing
    val track = colors.onFieldMuted.copy(alpha = TRACK_ALPHA)
    // The numeral grows with the dial and shrinks for a long reading ("1:30:00") so it always sits inside the ring.
    val density = LocalDensity.current
    val numeralStyle = AwakenTheme.type.metricXL.let { base ->
        val byDial = diameter * NUMERAL_SHARE
        val byWidth = diameter * NUMERAL_FIT / (numeral.length * GLYPH_EM)
        val size = with(density) { minOf(byDial, byWidth).toSp() }
        base.copy(fontSize = size, lineHeight = size)
    }
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
            Text(numeral, style = numeralStyle, color = colors.onField)
            Text(caption, style = AwakenTheme.type.caption, color = colors.onFieldMuted)
        }
    }
}

/** A small filled pill on a field (nudge buttons): light text on a lighter tone of the field. */
@Composable
fun FieldPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        interactionSource = interaction,
        shape = AwakenTheme.shapes.button,
        colors = ButtonDefaults.buttonColors(containerColor = colors.onField.copy(alpha = PILL_ALPHA), contentColor = colors.onField),
        modifier = modifier.pressScale(interaction).defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget),
    ) {
        Text(text = text, style = AwakenTheme.type.title)
    }
}

private const val PILL_ALPHA = 0.18f

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

private val DIAL_MAX: Dp = 460.dp
private val DIAL_MIN: Dp = 120.dp
private val LANDSCAPE_STATUS_ROOM: Dp = 32.dp
private val SIDE_COLUMN_MAX: Dp = 360.dp
private const val PORTRAIT_HEIGHT_SHARE = 0.58f
private const val NUMERAL_SHARE = 0.27f
private const val NUMERAL_FIT = 0.74f
private const val GLYPH_EM = 0.6f
private const val TRACK_ALPHA = 0.28f
private const val FULL = 360f
private const val START = -90f

/** The lock closed (how a session starts) and open (Pause and End session popped over the dial). */
@PhonePreviews
@LandscapePreviews
@Composable
private fun SessionScreenPreview() {
    AwakenTheme(reduceMotion = true) { SessionLockPreviewContent(locked = true) }
}

@PhonePreviews
@LandscapePreviews
@Composable
private fun SessionScreenUnlockedPreview() {
    AwakenTheme(reduceMotion = true) { SessionLockPreviewContent(locked = false) }
}

@Composable
private fun SessionLockPreviewContent(locked: Boolean) {
    SessionScreen(
        stat = Stat.INT,
        title = "15-minute session",
        subtitle = "DSA, graphs · Intelligence",
        body = { diameter -> SessionDial(diameter, 0.79f, "11:48", "remaining", "11:48 remaining") },
        actions = {
            SessionLock(stat = Stat.INT, locked = locked, onLockedChange = {}, unlockLabel = "Unlock") {
                FieldPrimaryButton("Pause (5:00)", onClick = {}, modifier = Modifier.fillMaxWidth())
                FieldQuietButton("End session", onClick = {})
            }
        },
    )
}
